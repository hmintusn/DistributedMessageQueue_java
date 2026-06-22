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
        Reverse connection (pull): 
        - The Producer registers port with the Broker 
        - The Broker establishes a dedicated connection to the Producer and pulls messages through that channel 
        (with one spawned thread/connection).

        Dedicated channel: 
        - Bottleneck: Establishing a TCP connection for every message is inefficient due to the 
        overhead of the 3-way handshake, kernel resource allocation,  and connection teardown (TIME_WAIT). 
    */

    private final Map<Integer, Topic> idToTopic; 
    public Broker() {
        this.idToTopic = new HashMap<Integer, Topic>();
    }

    // Stream: [1 byte of length, 1 byte of type, the rest of byte of message]
    public void startBrokerServer(){
        try{
            // Socket + bind + Liste;n
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
        idToTopic.get(topicId).getMessagQueue().push(consumeData);
        idToTopic.get(topicId).getMessagQueue().debug();
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
                System.out.println("Connected to producer at port " + port);
                
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
                    Message response = new Message(MessageType.R_P_REG, responseData);
                    
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
        System.out.printf("Producer register at consumer register port: %d, topicId: %d, consumerGroupId  %n", 
            conRegPort, topicId, groupId);
        
        // Check if topic exist, and then create one if not
        Topic topic = getOrCreateTopic(topicId);

        // Check if topic exist, and then create one if not
        CreateResult<ConsumerGroup> result = getOrCreateConsumerGroup(groupId, topicId);
        ConsumerGroup group = result.value();
        startConsumerGroupConsumption(topic.getTopicId(), group.getGroupId());
        try{
            Socket connection = new Socket("127.0.0.1", conRegPort);
            System.out.println("Connected to consumer at port " + conRegPort);
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
                var consumers = idToTopic.get(topicId).getConsumerGroups().get(groupId).getConsumers();
                var topic = idToTopic.get(topicId);
                var offset = topic.getConsumerGroups().get(groupId).getOffset();

                // Get the peek of message queue for consumption 
                byte[] consumeMessage = topic.getMessagQueue().peekAt(offset);
                for (ConsumerGroup.ConsumerConnection consumer : consumers){
                    if (consumer.status) {
                        BufferedInputStream bis = new BufferedInputStream(consumer.connection.getInputStream());
                        BufferedOutputStream bos = new BufferedOutputStream(consumer.connection.getOutputStream());
                        
                        // Write P_CM message to ready consumer
                        consumer.status = false;
                        Message.writeMessageToStream(bos, new Message(MessageType.P_CM, consumeMessage));;

                        // Read ack
                        Message parsedMessage = Message.readMessageFromStream(bis).get();
                        if (parsedMessage == null) {
                            System.out.println("Parsed message is empty");
                            break; 
                        }
                        System.out.println(parsedMessage);
                        if (parsedMessage.getType() == MessageType.R_P_CM){
                            consumer.status = true;
                        }
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
        // Anytime create a new topic -> assign a thread to stop and and pop message
        if(topic == null){
            Topic createdTopic = new Topic(topicId);
            idToTopic.put(topicId, createdTopic);
            new Thread(() ->{
                stopAndPop(createdTopic);
            }).start();
        }
        return topic;
    }

    // Topic lock: protects shared message queue and consumer group collection
    // ConsumerGroup lock: protects concurrent offset updates during pop
    private void stopAndPop(Topic topic){
        while(true){
            try {
                TimeUnit.SECONDS.sleep(10);
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
                    topic.getMessagQueue().pop();
                }
                for (var cgroup : topic.getConsumerGroups()){
                    cgroup.getLock().unlock();
                }
            }
            topic.getLock().unlock();
        }
    }

    private CreateResult<ConsumerGroup> getOrCreateConsumerGroup(int groupId, int topicId){
        var cgroups = idToTopic.get(topicId).getConsumerGroups();
        return cgroups.stream()
            .filter(group -> group.getGroupId() == groupId)
            .findFirst()
            .map(group -> new CreateResult<>(group, false))
            .orElseGet(() ->{
                ConsumerGroup newGroup = new ConsumerGroup(groupId, 0);
                cgroups.add(newGroup);
                return new CreateResult<>(newGroup, true);
            });
    }

}
