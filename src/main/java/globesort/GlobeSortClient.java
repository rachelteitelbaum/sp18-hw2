package globesort;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import net.sourceforge.argparse4j.*;
import net.sourceforge.argparse4j.inf.*;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.lang.RuntimeException;
import java.lang.Exception;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Level;
import java.util.logging.Logger;

public class GlobeSortClient {

    private final ManagedChannel serverChannel;
    private final GlobeSortGrpc.GlobeSortBlockingStub serverStub;

	private static int MAX_MESSAGE_SIZE = 100 * 1024 * 1024;

    private String serverStr;

    public GlobeSortClient(String ip, int port) {
        this.serverChannel = ManagedChannelBuilder.forAddress(ip, port)
				.maxInboundMessageSize(MAX_MESSAGE_SIZE)
                .usePlaintext(true).build();
        this.serverStub = GlobeSortGrpc.newBlockingStub(serverChannel);

        this.serverStr = ip + ":" + port;
    }

    public void run(Integer[] values) throws Exception {
        System.out.println("Pinging " + serverStr + "...");
        long startTime = System.nanoTime();
        serverStub.ping(Empty.newBuilder().build());
        long latencyPingNano = System.nanoTime() - startTime;
        double latencyPing = (latencyPingNano / 1.0E09);
        System.out.println("Ping successful. Latency in seconds:" + latencyPing);
        System.out.println();

        System.out.println("Requesting server to sort array");
        IntArray request = IntArray.newBuilder().addAllValues(Arrays.asList(values)).build();
        startTime = System.nanoTime();
        IntArray response = serverStub.sortIntegers(request);
        long endTime = System.nanoTime();
        System.out.println("Sorted array");
        System.out.println();

        System.out.println("Number of Integers Sorted:" + values.length);
        System.out.println("Number of bytes traveling one way across the network:" + (values.length*4));
        System.out.println();
 
	/* measure application latency */
 	double sortTime = (response.getSortTime() / 1.0E09);
        double appLatency = (endTime/1.0E09) - (startTime/1.0E09);
        double appThroughput = values.length / appLatency;
        System.out.println("Total Time to sort:" + appLatency);
        System.out.println("Application Throughput in integers/sec:" + appThroughput);
        System.out.println();

        /* measure one way network latency */
        double netLatency = ((endTime/1.0E09) - (startTime/1.0E09) - sortTime) / 2;
        double netThroughput = (values.length * 4) / netLatency;
        System.out.println("Total Time to across network:" + netLatency);
        System.out.println("One way network throughput in bytes/sec:" + netThroughput);
        System.out.println();
        
    }

    public void shutdown() throws InterruptedException {
        serverChannel.shutdown().awaitTermination(2, TimeUnit.SECONDS);
    }

    private static Integer[] genValues(int numValues) {
        ArrayList<Integer> vals = new ArrayList<Integer>();
        Random randGen = new Random();
        for(int i : randGen.ints(numValues).toArray()){
            vals.add(i);
        }
        return vals.toArray(new Integer[vals.size()]);
    }

    private static Namespace parseArgs(String[] args) {
        ArgumentParser parser = ArgumentParsers.newFor("GlobeSortClient").build()
                .description("GlobeSort client");
        parser.addArgument("server_ip").type(String.class)
                .help("Server IP address");
        parser.addArgument("server_port").type(Integer.class)
                .help("Server port");
        parser.addArgument("num_values").type(Integer.class)
                .help("Number of values to sort");

        Namespace res = null;
        try {
            res = parser.parseArgs(args);
        } catch (ArgumentParserException e) {
            parser.handleError(e);
            System.exit(1);
        }
        return res;
    }

    public static void main(String[] args) throws Exception {
        Namespace cmd_args = parseArgs(args);
        if (cmd_args == null) {
            throw new RuntimeException("Argument parsing failed");
        }

        Integer[] values = genValues(cmd_args.getInt("num_values"));

        GlobeSortClient client = new GlobeSortClient(cmd_args.getString("server_ip"), cmd_args.getInt("server_port"));
        try {
            client.run(values);
        } finally {
            client.shutdown();
        }
    }
}
