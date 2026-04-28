package protocol;

import java.nio.ByteBuffer;

public class ConsumerRegisterRequest {
    // 4 byte for port 
    // 4 byte for topic id 
    // 4 byte for consumer group id 
    private final int port;
    private final int topicId;
    private final int consumerGroupId;

    public ConsumerRegisterRequest(int port, int topicId, int consumerGroupId) {
        this.port = port;
        this.topicId = topicId;
        this.consumerGroupId = consumerGroupId;
    }

    // Instead of bit-shifting, using ByteBuffer for simplification
    public byte[] toByte() {
        ByteBuffer buffer = ByteBuffer.allocate(8);
        buffer.putInt(port);
        buffer.putInt(topicId);
        buffer.putInt(consumerGroupId);
        return buffer.array();
    }

    public static ConsumerRegisterRequest fromByte(byte[] data) {
        ByteBuffer buffer = ByteBuffer.wrap(data);
        int port = buffer.getInt();
        int topicId = buffer.getInt();
        int consumerGroupId = buffer.getInt();
        return new ConsumerRegisterRequest(port, topicId, consumerGroupId);
    }

    public int getPort() { return port; }
    public int getTopicId() { return topicId; }
    public int getConsumerGroupId() { return consumerGroupId; }
}
