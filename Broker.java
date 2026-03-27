import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.Optional;

public class Broker {
    // Stream: [1 byte of length, 1 byte of type, the rest of byte of message]
    private Integer brokerPort = 10000;

    public void startBrokerServer(){
        try{
            // Socket + bind + Listen
            final ServerSocket server = new ServerSocket(brokerPort, 123, 
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

    
    // public Optional<Message> parseBrokerMessage(byte[] message){
    //     switch (MessageType.fromByte(message[0])) {
    //         case ECHO:
    //             byte[] payload = Arrays.copyOfRange(message, 1, message.length);
    //             return Optional.of(new Message(MessageType.ECHO, payload));
                
    //             default:
    //                 return Optional.empty();
    //     }
    // }
    
    // Process: message -> message
    public Optional<Message> processBrokerMessage (Message message){
        byte[] response;
        switch (message.getType()) {
            case ECHO:
                response = handleEcho(message.getData());
                return Optional.of(new Message(MessageType.ECHO, response));
            case P_REG:
                response = handleProducerRegister(message.getData());
                return Optional.of(new Message(MessageType.P_REG, response));
            default:
                return Optional.empty();
        }
    }

    public byte[] handleEcho (byte[] echoData){
        String input = new String(echoData);
        String output = "I have received: " + input;
        return output.getBytes();
    }

    /*
        Reverse connection: Broker(client) - Producer(server)
        - Broker connect to Producer
        - Producer register port for Broker to push message
    */
    public byte[] handleProducerRegister (byte[] producerRegistererData){
        // Parse port 
        String portStr = new String(producerRegistererData);
        int port = Integer.parseInt(portStr);
        System.out.println("Producer register at port: " + port);

        // Spawn thread 
        new Thread(() -> {
            try {
                Socket socket = new Socket("127.0.0.1", port);
                System.out.println("Connected to producer at port " + port);

                BufferedInputStream bis = new BufferedInputStream(socket.getInputStream());
                BufferedOutputStream bos = new BufferedOutputStream(socket.getOutputStream());

                while (true) {
                    Optional<Message> parsedMessage = Message.readMessageFromStream(bis);
                    if (parsedMessage .isEmpty()) {
                        System.out.println("Parsed message is empty");
                        break; 
                    }
                    Optional<Message> response = processBrokerMessage(parsedMessage.get());

                    // Write back
                    response.ifPresent(resp -> Message.writeMessageToStream(bos, resp));
                }
                socket.close();
            } catch (Exception e) {
                System.out.println("Error in producer connection thread");
                e.printStackTrace();
            }
        }).start();
        return new byte[]{0};
    }
}
