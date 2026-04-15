public class Application {
    public static void main(String[] args) {
        
        // Print args (like fmt.Println(os.Args))
        System.out.println(java.util.Arrays.toString(args));

        if (args.length < 1) {
            System.out.println("Usage: java Application <server|producer> [port]");
            return;
        }

        if ("broker".equals(args[0])) {
            Broker broker = new Broker();
            try {
                broker.startBrokerServer();
            } catch (Exception e) {
                System.out.printf("Error starting broker: %s%n", e.getMessage());
            }

        } else if ("producer".equals(args[0])) {
            System.out.println("Trying to start producer processes");

            if (args.length < 2) {
                System.out.println("Missing port argument");
                return;
            }

            try {
                int port = Integer.parseInt(args[1]);
                int topicId = Integer.parseInt(args[2]);
                Producer producer = new Producer(port, topicId);
                producer.startProducerServer(); 
            } catch (NumberFormatException e) {
                System.out.println("Invalid port: " + args[1]);
            }
        }
    }
}