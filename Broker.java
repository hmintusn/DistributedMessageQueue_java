import common.Constants;
import common.CreateResult;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.TimeUnit;
import protocol.ConsumerRegisterRequest;
import protocol.ProducerRegisterRequest;

public class Broker {
    
    /*
        Connection: 
        - The Producer registers port with the Broker 
        - The Broker establishes a dedicated connection (listen) to the Producer/Consumer
        - The Producer/Consumer push mesage to Broker
        (with one spawned thread/connection).

        Dedicated channel: 
        - Bottleneck: Establishing a TCP connection for every message is inefficient due to the 
        overhead of the 3-way handshake, kernel resource allocation,  and connection teardown (TIME_WAIT). 

        Reeantrant lock: 
        - Topic lock: protects shared message queue and consumer group collection
        - ConsumerGroup lock: protects concurrent offset updates during pop
    */

    private final Map<Integer, Topic> idToTopic; 
    public Broker() {
        this.idToTopic = new HashMap<Integer, Topic>();
    }

    // Stream: [1 byte of length, 1 byte of type, the rest of byte of message]
    public void startBrokerServer(){
        try{
            // Socket + bind + Listen
            final ServerSocket server = new ServerSocket(Constants.BROKER_PORT, 123, 
                InetAddress.getByName("127.0.0.1")); //backlog 
            System.out.println("Server waiting client...");
            
            // Multiplex: Connection start and close connection sequetially 
            //          -> make 1 port can serve multiple connection
            while(true){
                Socket socket = server.accept(); // Accept TCP connection
                var bis = new BufferedInputStream(socket.getInputStream());
                var bos = new BufferedOutputStream(socket.getOutputStream());

                // Read 
                Optional<Message> parsedMessage = Message.readMessageFromStream(bis);
                if (parsedMessage.isPresent()) {
                    // Write back
                    Optional<Message> response = handleMessageToBroker(parsedMessage.get());
                    if(response.isPresent()){
                        Message.writeMessageToStream(bos, response.get()); 
                    }
                }

                // Close the socket to allow new connection  
                bis.close();
                bos.close();
                socket.close();
            }
        }catch(Exception e){
            System.out.println("Something wrong when starting server");
            e.printStackTrace();
        }
    }

    // Process: message -> message
    public Optional<Message> handleMessageToBroker (Message message){
        byte[] response;
        switch (message.getType()) {
            case ECHO:
                response = processEcho(message.getData());
                return Optional.of(new Message(MessageType.R_ECHO, response));
            case P_REG:
                response = processProducerRegisterMessage(message.getData());
                return Optional.of(new Message(MessageType.R_P_REG, response));
            case C_REG:
                response = processConsumerRegisterMessage(message.getData());
                return Optional.of(new Message(MessageType.R_C_REG, response));
            default:
                return Optional.empty();
        }
    }

    public byte[] processEcho (byte[] echoData){
        String input = new String(echoData);
        String output = "Echo: " + input;
        return output.getBytes();
    }

    public byte[] handleProducerConsumeMessage (byte[] consumeData, int topicId){
        idToTopic.get(topicId).getMessageQueue().push(consumeData);
        idToTopic.get(topicId).getMessageQueue().debug();
        return new byte[1];
    }

    
    public byte[] processProducerRegisterMessage (byte[] producerRegistererRawData){
        // Parse port and topicId
        ProducerRegisterRequest prr = ProducerRegisterRequest.fromByte(producerRegistererRawData);
        int port = prr.getPort();
        int topicId = prr.getTopicId();
        System.out.printf("Producer register at port: %d and topicId: %d %n", port, topicId);
        
        Topic topic = getOrCreateTopic(topicId);
        
        // Spawn a thread (Dedicated channel)
        new Thread(() -> {
            try {
                Socket socket = new Socket("127.0.0.1", port);
                System.out.println("Connected to Producer at port " + port);
                
                BufferedInputStream bis = new BufferedInputStream(socket.getInputStream());
                BufferedOutputStream bos = new BufferedOutputStream(socket.getOutputStream());
                
                while (true) {
                    Message parsedMessage = Message.readMessageFromStream(bis).get();
                    if (parsedMessage == null) {
                        System.out.println("Parsed message is empty");
                        break; 
                    }
                    System.out.println(parsedMessage);
                    byte[] responseData = handleProducerConsumeMessage(parsedMessage.getData(), 
                        topic.getTopicId());
                    Message response = new Message(MessageType.R_P_CM, responseData);
                    
                    // Write back
                    Message.writeMessageToStream(bos, response);
                }
                socket.close();
            } catch (Exception e) {
                System.out.println("Error in producer connection thread");
                e.printStackTrace();
            }
        }).start();
        return new byte[]{0};
    }
    
    public byte[] processConsumerRegisterMessage (byte[] consumerRegisterRawData){
        // Parse port and topicId
        ConsumerRegisterRequest crr = ConsumerRegisterRequest.fromByte(consumerRegisterRawData);
        int conRegPort = crr.getPort();
        int topicId = crr.getTopicId();
        int groupId = crr.getConsumerGroupId();
        System.out.printf("Consumer register at port: %d, topicId: %d, consumerGroupId %d%n", 
            conRegPort, topicId, groupId);
        
        // Check if topic exist, and then create one if not
        Topic topic = getOrCreateTopic(topicId);

        // Check if topic exist, and then create one if not
        CreateResult<ConsumerGroup> result = getOrCreateConsumerGroup(groupId, topicId);
        ConsumerGroup group = result.value();
        startConsumerGroupConsumption(topic.getTopicId(), group.getGroupId());
        try{
            Socket connection = new Socket("127.0.0.1", conRegPort);
            System.out.println("Connected to Consumer at port " + conRegPort);
            //Add consumer to consumer group
            idToTopic.get(topicId).getConsumerGroups().get(groupId).addConsumer(connection);

        }catch (Exception e) {
            e.printStackTrace();
        }
        return new byte[]{0};
    }

    public void startConsumerGroupConsumption(int topicId, int groupId){
        new Thread(() -> {
            try {
                while(true){
                    var cgroup = idToTopic.get(topicId).getConsumerGroups().get(groupId);
                    var consumers = cgroup.getConsumers();
                    var topic = idToTopic.get(topicId);
                    var offset = topic.getConsumerGroups().get(groupId).getOffset();
                    
                    cgroup.getLock().lock();
                    
                    // Get the peek of message queue for consumption 
                    byte[] consumeMessage = topic.getMessageQueue().peekAt(offset);
                    for (ConsumerGroup.ConsumerConnection consumer : consumers){
                        if (consumer.isAvailable) {
                            BufferedInputStream bis = new BufferedInputStream(consumer.connection.getInputStream());
                            BufferedOutputStream bos = new BufferedOutputStream(consumer.connection.getOutputStream());
                            
                            // Write P_CM message to ready consumer
                            consumer.isAvailable = false;
                            Message.writeMessageToStream(bos, new Message(MessageType.P_CM, consumeMessage));
    
                            // Read ack
                            Message parsedMessage = Message.readMessageFromStream(bis).get();
                            if (parsedMessage == null) {
                                System.out.println("Parsed message is empty");
                                break; 
                            } 
                            System.out.println(parsedMessage);
                            if (parsedMessage.getType() == MessageType.R_P_CM){
                                consumer.isAvailable = true;
                            }
                            cgroup.incOffset();
                        }
                        cgroup.getLock().unlock();
                    }
                }
            } catch (Exception e) {
                System.out.println("Error in producer connection thread");
                e.printStackTrace();
            }
        }).start();
    }
    
    private Topic getOrCreateTopic(int topicId){
        Topic topic = idToTopic.get(topicId);
        if (topic != null){
            return topic;
        }

        // Anytime create a new topic -> assign a thread to stop and and pop message
        Topic createdTopic = new Topic(topicId);
        idToTopic.put(topicId, createdTopic);
        new Thread(() ->{
            stopAndPop(createdTopic);
        }).start();
        return createdTopic;
    }

    private void stopAndPop(Topic topic){
        while(true){
            try {
                TimeUnit.SECONDS.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            
            topic.getLock().lock();
            int minOffset = Integer.MAX_VALUE;
            for (var cgroup : topic.getConsumerGroups()){
                minOffset = Math.min(minOffset, cgroup.getOffset());
            }
            System.out.printf("Running stop and pop, min offset: %d%n", minOffset);

            if (minOffset != Integer.MAX_VALUE) {
                for (var cgroup : topic.getConsumerGroups()){
                    cgroup.getLock().lock();
                    cgroup.setOffset(cgroup.getOffset() - minOffset);
                }
                while(minOffset-->0){
                    topic.getMessageQueue().pop();
                }
                for (var cgroup : topic.getConsumerGroups()){
                    cgroup.getLock().unlock();
                }
            }
            topic.getLock().unlock();
        }
    }

    private CreateResult<ConsumerGroup> getOrCreateConsumerGroup(int groupId, int topicId){
        var topic = idToTopic.get(topicId);
        topic.getLock().lock();
        try {
            var cgroups = topic.getConsumerGroups();
            return cgroups.stream()
                .filter(group -> group.getGroupId() == groupId)
                .findFirst()
                .map(group -> new CreateResult<>(group, false))
                .orElseGet(() ->{
                    ConsumerGroup newGroup = new ConsumerGroup(groupId, 0);
                    // Avoid race condition to multiple thread append topic.cgroup at the same time
                    cgroups.add(newGroup);
                    return new CreateResult<>(newGroup, true);
                });
        } finally {
            topic.getLock().unlock();
        }
    }

}
