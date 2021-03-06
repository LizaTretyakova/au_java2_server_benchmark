package Metrics;

import Clients.TCPClient;
import Clients.UDPClient;
import Servers.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.knowm.xchart.QuickChart;
import org.knowm.xchart.SwingWrapper;
import org.knowm.xchart.XYChart;

import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class MetricsAggregator implements BaseMetricsAggregator {

    public static final String NAME = "results";
    public static final String INFO = "info";
    public static final String REQUEST = "request";
    public static final String CLIENT = "client";
    public static final String AVG = "avg";

    private static final Logger LOGGER = LogManager.getLogger(MetricsAggregator.class);

    private final String arch;
    private final int x;
    private final Parameter n;
    private final Parameter m;
    private final Parameter d;
    private Parameter changing = null;

    private final List<Long> requestTime = new ArrayList<>();
    private final List<Long> requestBuf = new ArrayList<>();
    private final List<Long> clientTime = new ArrayList<>();
    private final List<Long> clientBuf = new ArrayList<>();
    private final List<Long> avgTime = new ArrayList<>();
    private final List<Long> avgBuf = new ArrayList<>();

    public MetricsAggregator(String arch, int x, Parameter n, Parameter m, Parameter d) {
        this.arch = arch;
        this.x = x;
        this.n = n;
        this.m = m;
        this.d = d;
    }

    public MetricsAggregator(String arch, int x, Parameter n, Parameter m, Parameter d, InetAddress addr, int port) {
        this.arch = arch;
        this.x = x;
        this.n = n;
        this.m = m;
        this.d = d;

        Thread socketThread = new Thread(() -> {
            try(
                    Socket socket = new Socket(addr, port);
                    DataInputStream input = new DataInputStream(socket.getInputStream());
                    DataOutputStream output = new DataOutputStream(socket.getOutputStream());
            ) {
                while(true) {
                    int notification = input.readInt();
                    LOGGER.error("Read notification: " + Integer.toString(notification));
                    if (notification == BaseMetricsAggregator.REQUEST) {
                        LOGGER.error("Submitting REQUEST");
                        submitRequest(input.readLong());
                    } else if (notification == BaseMetricsAggregator.CLIENT) {
                        LOGGER.error("Submitting CLIENT");
                        submitClient(input.readLong());
                    } else {
                        RuntimeException e = new RuntimeException("Unknown input from server");
                        LOGGER.error(e.getMessage(), e);
                        throw e;
                    }
                }
            } catch (IOException e) {
                LOGGER.error("Connection closed. No metrics from server will be available.", e);
            }
        });
        socketThread.start();
    }

    @Override
    public synchronized void submitRequest(long val) {
        LOGGER.error("Got a request val: " + Long.toString(val));
        requestBuf.add(val);
    }

    @Override
    public synchronized void submitClient(long val) {
        LOGGER.error("Got a client val: " + Long.toString(val));
        clientBuf.add(val);
    }

    public synchronized void submitAvg(long val) {
        avgBuf.add(val);
    }

    public void submitRequestClientAvg(long request, long client, long avg) {
        requestTime.add(request);
        clientTime.add(client);
        avgTime.add(avg);
    }

    public void submit() {
        long requestAvg = requestBuf.stream().collect(Collectors.averagingLong(x -> x)).longValue();
        long clientAvg = clientBuf.stream().collect(Collectors.averagingLong(x -> x)).longValue();
        long avgAvg = avgBuf.stream().collect(Collectors.averagingLong(x -> x)).longValue();

        LOGGER.error("requestBuf size: " + Integer.toString(requestBuf.size()));
        LOGGER.error("clientsBuf size: " + Integer.toString(clientBuf.size()));
        LOGGER.error("avgBuf size: " + Integer.toString(avgBuf.size()));

        requestBuf.clear();
        clientBuf.clear();
        avgBuf.clear();

        submitRequestClientAvg(requestAvg, clientAvg, avgAvg);
    }

    private FileWriter createFile(String purpose) throws IOException {
        if (!Files.exists(Paths.get(NAME))) {
            Files.createDirectory(Paths.get(NAME));
        }

        String dop = changing == null ? "" : changing.getName();
        String filename =
                Paths.get(NAME, NAME + arch + purpose + dop + "_" + Long.toString(System.currentTimeMillis()) + ".csv").toString();
        return new FileWriter(new File(filename));
    }

    public void storeInfo() throws IOException {
        FileWriter writer = createFile(INFO);

        writer.append(arch);
        writer.append("\n");
        writer.append(Integer.toString(x));
        writer.append("\n");
        n.writeTo(writer);
        m.writeTo(writer);
        d.writeTo(writer);
        writer.append("\n");
        writer.flush();
        writer.close();
    }

    private void storeMetric(String metricName, List<Long> val) throws IOException {
        FileWriter writer = createFile(metricName);
        if(changing == null) {
            createChanging();
        }
        writer.append(metricName + ", " + changing.getName() + "\n");
//        writer.append("\n");
        int i = 0;
        for(long a: val) {
            i++;
            int tmp = i;
            writer.append(Long.toString(a));
            writer.append(", ");
            writer.append(Integer.toString(Launcher.countParam(changing, tmp)));
            writer.append("\n");
        }
        writer.flush();
        writer.close();
    }

    public void storeRequest() throws IOException {
        storeMetric(REQUEST, requestTime);
    }
    public void storeClient() throws IOException {
        storeMetric(CLIENT, clientTime);
    }
    public void storeAvg() throws IOException {
        storeMetric(AVG, avgTime);
    }

    public void store() throws IOException {
        storeInfo();

        storeRequest();
        storeClient();
        storeAvg();
    }

    public static void drawMetric(String metricName, String xName, String yName, List<Long> xData, List<Long> yData) {
       // Create Chart
        XYChart chart = QuickChart.getChart(metricName, xName, yName, yName + "(" + xName + ")", xData, yData);

        // Show it
        new SwingWrapper(chart).displayChart();
    }

    public void draw() {
        createChanging();

        List<Long> xAxis = new ArrayList<>();
        for(long i = (long) changing.getStart(); i < (long) changing.getEnd(); i += changing.getStep()) {
            xAxis.add(i);
        }

        drawMetric("Request time", "Request time", changing.getName(), xAxis, requestTime);
        drawMetric("Client time", "Client time", changing.getName(), xAxis, clientTime);
        drawMetric("Avg time", "Avg time", changing.getName(), xAxis, avgTime);
    }

    private void createChanging() {
        if(n.isChanging()) {
            changing = n;
        } else if (m.isChanging()) {
            changing = m;
        } else if (d.isChanging()) {
            changing = d;
        }
    }
}
// MerlinArthur, lol
