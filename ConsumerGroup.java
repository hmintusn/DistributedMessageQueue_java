
public class ConsumerGroup {
    private int groupId;
    private int offset;

    public ConsumerGroup(int groupId, int offset) {
        this.groupId = groupId;
        this.offset = offset;
    }

    public int getGroupId() {
        return groupId;
    }

    public int getOffset() {
        return offset;
    }

}
