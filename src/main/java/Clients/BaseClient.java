package Clients;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.List;
import java.util.concurrent.ExecutionException;

public abstract class BaseClient {
    protected IOException inThreadException = null;

    public List<Integer> sortData(ServerSocket server, List<Integer> data)
            throws IOException, ExecutionException, InterruptedException {
        return null;
    }
    public List<Integer> sortData(InetAddress addr, int port, List<Integer> data)
            throws IOException, ExecutionException, InterruptedException {
        return null;
    }
}