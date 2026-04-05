import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Optional;
import java.util.Scanner;

public class Producer {
    private void sendPortDataToBroker(int port){
        try{
            // socket + connect
            Socket socket = new Socket("127.0.0.1", Constants.BROKER_PORT);
            
            BufferedInputStream bis = new BufferedInputStream(socket.getInputStream());
            BufferedOutputStream bos = new BufferedOutputStream(socket.getOutputStream());
            Scanner sc = new Scanner(System.in);

            String portStr = String.valueOf(port);
            byte[] portData = portStr.getBytes();
            byte[] data = new byte[portData.length + 1];
            data[0] = (byte) portData.length; 
            System.arraycopy(portData, 0, data, 1, portData.length);

            // Write port
            Message.writeMessageToStream(bos, new Message(MessageType.P_REG, data));

            // Read back from the stream
            Optional<Message> response = Message.readMessageFromStream(bis); 
            if(response.isPresent()){
                System.out.printf("Message: %s%n", response.get());
            }
        } catch (Exception e) {
            System.out.println("There is problem with sending port data to broker");
        }
    }

    public void startProducerServer(int port){
        try{
            // Send port to broker for registering
            sendPortDataToBroker(port);

            // Socket + bind + listen
            final ServerSocket server = new ServerSocket(port, 123, 
                InetAddress.getByName("127.0.0.1")); //backlog 
            System.out.println("Server waiting client...");
            
            Socket socket = server.accept(); // Accept TCP connection
            var bis = new BufferedInputStream(socket.getInputStream());
            var bos = new BufferedOutputStream(socket.getOutputStream());
            Scanner sc = new Scanner(System.in);
  
            while(true){
                // Read user input
                byte[] input = sc.nextLine().getBytes();
                byte[] data = new byte[input.length + 1];
                data[0] = (byte) input.length; 
                System.arraycopy(input, 0, data, 1, input.length);

                // Write echo message
                Message.writeMessageToStream(bos, new Message(MessageType.ECHO, data));

                // Read back from the stream
                Optional<Message> response = Message.readMessageFromStream(bis); 
                if(response.isPresent()){
                    System.out.printf("Message: %s%n", response.get());
                }
            }
        }catch(Exception e){
            System.out.println("Something wrong when starting server");
        }
    }
}
