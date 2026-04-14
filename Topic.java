public class Topic {
    private int topicId;
    private Queue messagQueue;

    public Topic(int topicId) {
        this.topicId = topicId;
        this.messagQueue = new Queue();
    }

    public int getTopicId() {
        return topicId;
    }

    public Queue getMessagQueue() {
        return messagQueue;
    }

}
