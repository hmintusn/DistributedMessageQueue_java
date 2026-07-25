package mq;

import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
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

    public void incOffset(){
        offset++;
    }
    

    public void addConsumer(Socket socket) {
        consumers.add(new ConsumerConnection(true, socket));
    }

    @Getter
    @Setter
    @AllArgsConstructor
    public static class ConsumerConnection {
        public boolean isAvailable;
        public Socket connection;
    }
}
