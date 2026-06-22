
import common.Constants;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import protocol.ConsumerRegisterRequest;

public class Consumer {
    private int port;
    private int topicId;
    private int groupId;

    public Consumer(int port, int topicId, int groupId) {
        this.port = port;
        this.topicId = topicId;
        this.groupId = groupId;
    }

    public int getGroupId() {
        return groupId;
    }

    public int getPort() {
        return port;
    }

    public int getTopicId() {
        return topicId;
    }


    public void startConsumerServer(){
        try{
            // Socket + bind + listen (FIRST - before registering with broker)
            final ServerSocket server = new ServerSocket(port, 123, 
                InetAddress.getByName("127.0.0.1"));  
            System.out.println("Consumer server listening on port " + port);
            
            // Send port to broker for registering (AFTER server is ready)
            sendRegistrationToBroker();
            
            System.out.println("Waiting for broker connection...");
            Socket socket = server.accept(); // Accept TCP connection
            var bis = new BufferedInputStream(socket.getInputStream());
            var bos = new BufferedOutputStream(socket.getOutputStream());
  
            while(true){
                // Read message for consume
                Optional<Message> message = Message.readMessageFromStream(bis); 
                if (message.isEmpty()) {
                    break;
                }
                System.out.printf("Receive P_CM from broker: %s%n", message.get());
                TimeUnit.SECONDS.sleep(1);
                var response = new byte[]{1};

                // Write R_P_CM
                Message.writeMessageToStream(bos, new Message(MessageType.R_P_CM, response));
            }
        }catch(Exception e){
            System.out.println("Something wrong when starting Consumer server");
        }
    }

    private void sendRegistrationToBroker(){
        try(
            // socket + connect
            Socket socket = new Socket("127.0.0.1", Constants.BROKER_PORT);
            
            BufferedInputStream bis = new BufferedInputStream(socket.getInputStream());
            BufferedOutputStream bos = new BufferedOutputStream(socket.getOutputStream());
        ){

            // Write Consumer register message
            ConsumerRegisterRequest crr = new ConsumerRegisterRequest(port, topicId, groupId);
            Message.writeMessageToStream(bos, new Message(MessageType.C_REG, crr.toByte()));


            // Read back from the stream
            Optional<Message> message = Message.readMessageFromStream(bis); 
            if(message.isPresent()){
                System.out.printf("Receive from broker: %s%n", message.get());
            }
        } catch (Exception e) {
            System.out.println("There is problem with sending registration data to broker");
        }
    }
}
