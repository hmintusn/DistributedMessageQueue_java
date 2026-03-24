import java.io.*;
import java.net.*;

public class TCPServer {
    public static void main(String[] args) throws Exception {
        try {
            // Socket + bind + Listen
            final ServerSocket server = new ServerSocket(1234, 123, InetAddress.getByName("127.0.0.1"));
            System.out.println("Server waiting client...");
            
            // Accept connection
            Socket socket = server.accept();
            while (true) {
                BufferedInputStream bis = new BufferedInputStream(socket.getInputStream());
                BufferedOutputStream bos = new BufferedOutputStream(socket.getOutputStream());
                
                // Read: Header(1 byte) + Data 
                int header = bis.read(); 
                byte[] data = new byte[header];
                bis.read(data, 0, header);
                System.out.println("Receive from client:" + new String(data));

                // Write back echo
                System.out.println("Echo to client");
                bos.write(header);
                bos.write(data, 0, header);
                bos.flush();
            } 

        } catch (Exception e) {
            System.out.println("There is problem with server socket");
        }
    }
}