import java.io.*;
import java.net.*;
import java.util.Scanner;

public class TCPClient {
    public static void main(String[] args) throws Exception {
        try{
            // socket + connect
            Socket socket = new Socket("127.0.0.1", 1234);
            
            // Write + send to server 
            BufferedInputStream bis = new BufferedInputStream(socket.getInputStream());
            BufferedOutputStream bos = new BufferedOutputStream(socket.getOutputStream());
            Scanner sc = new Scanner(System.in);
            while (true) {
                String input = sc.nextLine();
                byte[] data = input.getBytes();
                int header = data.length; 

                System.out.println("Send to server");
                // message = [header, data]
                bos.write(header);
                bos.write(data);
                bos.flush();

                // Read 
                int dataLength = bis.read(); 
                byte[] rcvData = new byte[dataLength];
                bis.read(rcvData, 0, dataLength);
                System.out.println("Receive from server:" + new String(rcvData));

            }

            // bis.close();
            // bos.close();
            // sc.close();
            // socket.close();
        } catch (Exception e) {
            System.out.println("There is problem with server socket");
        }
    }
}