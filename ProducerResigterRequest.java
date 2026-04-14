
import java.nio.ByteBuffer;

class ProducerRegisterRequest {
    // 2 byte for port (port must be < 2^16)
    // 2 byte for topic id 
    private final int port;
    private final int topicId;

    public ProducerRegisterRequest(int port, int topicId) {
        this.port = port;
        this.topicId = topicId;
    }

    public byte[] encode() {
        ByteBuffer buffer = ByteBuffer.allocate(4);
        buffer.putShort((short) port);
        buffer.putShort((short) topicId);
        return buffer.array();
    }

    public static ProducerRegisterRequest decode(byte[] data) {
        ByteBuffer buffer = ByteBuffer.wrap(data);
        int port = buffer.getShort();
        int topicId = buffer.getShort();
        return new ProducerRegisterRequest(port, topicId);
    }

    public int getPort() { return port; }
    public int getTopicId() { return topicId; }
}