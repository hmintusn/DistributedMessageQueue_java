public class Queue {
    // Fixed-size slot ring buffer
    private final int TOTAL_SIZE = Constants.MAX_MESSAGE_SIZE * Constants.QUEUE_CAPACITY;

    private final byte[] buffer = new byte[TOTAL_SIZE];
    private final byte[] sizes = new byte[Constants.QUEUE_CAPACITY];

    private int head;
    private int tail;
    
    /* 
        e.g. MAX_MESSAGE_SIZE = 5
        head(pop)    ....     tail(push)
        |s l o t _ |s l o t _|....
    */

    public Queue() {
        this.head = 0;
        this.tail = 0;
    }

    public void push(byte[] data){
        if(data.length > Constants.MAX_MESSAGE_SIZE){
            throw new IllegalArgumentException("Message to large");
        }
        if(isFull()){
            throw new IllegalArgumentException("Queue is full");
        }
        int slotIndex = tail / Constants.MAX_MESSAGE_SIZE;
        sizes[slotIndex] = (byte) data.length;  // actual size of the data in slot 
        
        // Copy data to slot
        System.arraycopy(data, 0, buffer, tail, data.length);

        // If tail reaches the end, reset it to the beginning
        tail = (tail + Constants.MAX_MESSAGE_SIZE) % TOTAL_SIZE;

    }

    public byte[] pop(){
        if (isEmpty()) {
            return null; 
        }
        int slotIndex = head / Constants.MAX_MESSAGE_SIZE;
        int size = sizes[slotIndex];

        byte[] data = new byte[Constants.MAX_MESSAGE_SIZE];
        System.arraycopy(buffer, head, data, 0, size);

        head = (head + Constants.MAX_MESSAGE_SIZE) % TOTAL_SIZE;

        return data;
        
    }
    protected boolean isEmpty() {
        return head == tail;
    }

    protected boolean isFull() {
        return ((tail + Constants.MAX_MESSAGE_SIZE) % TOTAL_SIZE) == head;
    }

    
    public void debug(){
        System.out.println("Debug queue");
        int cur = head;
        while(cur != tail){
            int slotIndex = cur / Constants.MAX_MESSAGE_SIZE;
            int size = sizes[slotIndex];

            byte[] data = new byte[size];
            System.arraycopy(buffer, cur, data, 0, size);
            System.out.println(new String(data) + " | ");

            cur = (cur + Constants.MAX_MESSAGE_SIZE) % TOTAL_SIZE;
        }
    }
}
