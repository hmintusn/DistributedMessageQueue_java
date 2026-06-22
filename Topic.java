import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

public class Topic {
    private int topicId;
    private Queue messagQueue;
    private final ReentrantLock lock = new ReentrantLock();
    // Multiple cgroups can consume from the same topic 
    // -> Topic include list of them
    private List<ConsumerGroup> consumerGroups;

    public Topic(int topicId) {
        this.topicId = topicId;
        this.messagQueue = new Queue();
        this.consumerGroups = new ArrayList<>();
    }

    public int getTopicId() {
        return topicId;
    }

    public Queue getMessagQueue() {
        return messagQueue;
    }

    public ReentrantLock getLock() {
        return lock;
    }

    public List<ConsumerGroup> getConsumerGroups() { 
        return consumerGroups;
    }

}
