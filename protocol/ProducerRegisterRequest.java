package protocol;

import java.nio.ByteBuffer;

public class ProducerRegisterRequest {
    // 4 byte for port 
    // 4 byte for topic id 
    private final int port;
    private final int topicId;

    public ProducerRegisterRequest(int port, int topicId) {
        this.port = port;
        this.topicId = topicId;
    }

    // Instead of bit-shifting, using ByteBuffer for simplification
    public byte[] toByte() {
        ByteBuffer buffer = ByteBuffer.allocate(8);
        buffer.putInt(port);
        buffer.putInt(topicId);
        return buffer.array();
    }

    public static ProducerRegisterRequest fromByte(byte[] data) {
        ByteBuffer buffer = ByteBuffer.wrap(data);
        int port = buffer.getInt();
        int topicId = buffer.getInt();
        return new ProducerRegisterRequest(port, topicId);
    }

    public int getPort() { return port; }
    public int getTopicId() { return topicId; }
}