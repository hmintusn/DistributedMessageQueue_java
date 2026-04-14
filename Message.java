import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.util.Arrays;
import java.util.Optional;

public class Message {
    private final MessageType type;
    private final byte[] data;
    
    public Message(MessageType type, byte[] data) {
        this.type = type;
        this.data = data;
    }
    
    public MessageType getType() {
        return type;
    }

    public byte[] getData() {
        return data;
    }
    
    
    // parse: bytes -> Message
    private static Optional<Message> parseMessage(byte[] streamMessage){
        if (streamMessage.length == 0) {
            return Optional.empty();
        }
        byte[] data;
        switch (MessageType.fromByte(streamMessage[0])) {
            case ECHO: 
            data = Arrays.copyOfRange(streamMessage, 1, streamMessage.length);
            return Optional.of(new Message(MessageType.ECHO, data));
            case R_ECHO:
                data = Arrays.copyOfRange(streamMessage, 1, streamMessage.length);
                return Optional.of(new Message(MessageType.R_ECHO, data));
            case P_REG:
                data = Arrays.copyOfRange(streamMessage, 1, streamMessage.length);
                return Optional.of(new Message(MessageType.P_REG, data));
            case R_P_REG:
                data = new byte[]{streamMessage[1]};
                return Optional.of(new Message(MessageType.R_P_REG, data));
            case P_CM:
                data = new byte[]{streamMessage[1]};
                return Optional.of(new Message(MessageType.P_CM, data));
            case R_P_CM:
                data = new byte[]{streamMessage[1]};
                return Optional.of(new Message(MessageType.R_P_CM, data));
            default:
                return Optional.empty();
        }
    }
                    
    // [ 6 1 h e l l o]
    private static byte[] readFromStream(BufferedInputStream bis){
        try{
            final int length = bis.read(); 
            if (length <= 0) { // validate data.length
                return new byte[0];
            }
            var message = new byte[length]; // message includes: type + data 
            bis.read(message, 0, length);
            return message;
        }catch(Exception e){
            System.out.println("Something wrong when reading from stream");
            e.printStackTrace();
            return new byte[0];
        }
    }    
                    
    public static Optional<Message> readMessageFromStream(BufferedInputStream bis){
        try {
            byte[] data = readFromStream(bis);
            if(data.length == 0){
                return Optional.empty();
            }
            return parseMessage(data);
        } catch (Exception e) {
            System.out.println("Something wrong when read message from stream");
            e.printStackTrace();
            return Optional.empty();
        }
    }
    
    public static void writeMessageToStream(BufferedOutputStream bos, Message message){
        try {
            System.out.println("Send message to stream");
            byte[] data = message.getData();
            // Write length = type (1 byte) + data
            bos.write((byte) (data.length + 1));     
            
            // Write message type
            bos.write(message.getType().getCode());     
            
            // Write data
            bos.write(data, 0, data.length);
            bos.flush();    
        } catch (Exception e) {
            System.out.println("Something wrong when read message from stream");
            e.printStackTrace();
        }
    }

    @Override
    public String toString() {
        return "Message{type=%s, data=%s}".formatted(type, new String(data));
    }
}