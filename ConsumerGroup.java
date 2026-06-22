
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;


public class ConsumerGroup {
    private int groupId;
    private int offset;
    private final ReentrantLock lock = new ReentrantLock();
    private List<ConsumerConnection> consumers;

    public ConsumerGroup(int groupId, int offset) {
        this.groupId = groupId;
        this.offset = offset;
        this.consumers = new ArrayList<>();
    }


    public int getGroupId() {
        return groupId;
    }

    public void setOffset(int offset) {
        this.offset = offset;
    }

    public int getOffset() {
        return offset;
    }

    public void incOffset(){
        offset++;
    }
    
    public ReentrantLock getLock() {
        return lock;
    }

    public List<ConsumerConnection> getConsumers() {
        return consumers;
    }

    public void addConsumer(Socket socket) {
        consumers.add(new ConsumerConnection(true, socket));
    }

    public static class ConsumerConnection {
        public boolean isAvailable;
        public Socket connection;

        public ConsumerConnection(boolean isAvailable, Socket connection) {
            this.isAvailable = isAvailable;
            this.connection = connection;
        }
    }
}
