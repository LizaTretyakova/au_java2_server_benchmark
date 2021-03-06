package Clients;

import Metrics.MetricsAggregator;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.List;
import java.util.concurrent.ExecutionException;

public abstract class BaseClient {
    protected IOException inThreadException = null;

    public List<Integer> sortData(InetAddress addr, int port, int x, int d, List<Integer> data, MetricsAggregator ma)
            throws IOException, ExecutionException, InterruptedException {
        return null;
    }
}
