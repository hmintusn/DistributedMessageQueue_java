import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Optional;
import java.util.Scanner;

public class Producer {

    private int port;
    private int topicId;

    public Producer(int port, int topicId) {
        this.port = port;
        this.topicId = topicId;
    }

    private void sendRegistrationToBroker(){
        try{
            // socket + connect
            Socket socket = new Socket("127.0.0.1", Constants.BROKER_PORT);
            
            BufferedInputStream bis = new BufferedInputStream(socket.getInputStream());
            BufferedOutputStream bos = new BufferedOutputStream(socket.getOutputStream());

            // Write producer register message
            ProducerRegisterRequest prr = new ProducerRegisterRequest(port, topicId);
            Message.writeMessageToStream(bos, new Message(MessageType.P_REG, prr.toByte()));

            // Read back from the stream
            Optional<Message> response = Message.readMessageFromStream(bis); 
            if(response.isPresent()){
                System.out.println(response.get());
            }
        } catch (Exception e) {
            System.out.println("There is problem with sending registration data to broker");
        }
    }

    public void startProducerServer(){
        try{
            // Socket + bind + listen (FIRST - before registering with broker)
            final ServerSocket server = new ServerSocket(port, 123, 
                InetAddress.getByName("127.0.0.1")); //backlog 
            System.out.println("Producer server listening on port " + port);
            
            // Send port to broker for registering (AFTER server is ready)
            sendRegistrationToBroker();
            
            System.out.println("Waiting for broker connection...");
            Socket socket = server.accept(); // Accept TCP connection
            var bis = new BufferedInputStream(socket.getInputStream());
            var bos = new BufferedOutputStream(socket.getOutputStream());
            Scanner sc = new Scanner(System.in);
  
            while(true){
                // Read from stdin 
                byte[] input = sc.nextLine().getBytes();

                // Write P_CM
                Message.writeMessageToStream(bos, new Message(MessageType.P_CM, input));
                
                // Write echo message
                // Message.writeMessageToStream(bos, new Message(MessageType.ECHO, input));

                // Read back from the stream
                Optional<Message> response = Message.readMessageFromStream(bis); 
                if(response.isPresent()){
                    System.out.println(response.get());
                }
            }
        }catch(Exception e){
            System.out.println("Something wrong when starting producer server");
        }
    }
}
