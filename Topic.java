import java.util.ArrayList;
import java.util.List;

public class Topic {
    private int topicId;
    private Queue messagQueue;
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

    public List<ConsumerGroup> getConsumerGroups() { 
        return consumerGroups;
    }

}
