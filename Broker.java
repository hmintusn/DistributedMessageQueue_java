import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;

public class Broker {
    
    /*
        Reverse connection: 
        - Producer register port for Broker to push message
        - Broker connect to Producer via a dedicated channel (1 thread spawned)

        Dedicated channel: 
        - Bottleneck: Establishing a TCP connection for every message is inefficient due to the 
        overhead of the 3-way handshake, kernel resource allocation,  and connection teardown (TIME_WAIT). 
    */

    private Map<Integer, Topic> idToTopic; 
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
                    Optional<Message> response = processBrokerMessage(parsedMessage.get());
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
    public Optional<Message> processBrokerMessage (Message message){
        byte[] response;
        switch (message.getType()) {
            case ECHO:
                response = handleEcho(message.getData());
                return Optional.of(new Message(MessageType.R_ECHO, response));
            case P_REG:
                response = handleProducerRegister(message.getData());
                return Optional.of(new Message(MessageType.R_P_REG, response));
            default:
                return Optional.empty();
        }
    }

    public byte[] handleEcho (byte[] echoData){
        String input = new String(echoData);
        String output = "Echo: " + input;
        return output.getBytes();
    }

    public byte[] handleProducerConsumeMessage(byte[] consumeData, int topicId){
        idToTopic.get(topicId).getMessagQueue().push(consumeData);
        idToTopic.get(topicId).getMessagQueue().debug();
        return new byte[1];
    }

    public byte[] handleProducerRegister (byte[] producerRegistererData){
        // Parse port and topicId
        ProducerRegisterRequest prr = ProducerRegisterRequest.fromByte(producerRegistererData);
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
                    byte[] responseData = handleProducerConsumeMessage(parsedMessage.getData(), topic.getTopicId());
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

    private Topic getOrCreateTopic(int topicId){
        Topic topic = idToTopic.get(topicId);
        if(topic == null){
            topic = new Topic(topicId);
            idToTopic.put(topicId, topic);
        }
        return topic;
    }

}
