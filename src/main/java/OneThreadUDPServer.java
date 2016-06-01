import java.io.*;
import java.net.DatagramPacket;
import java.net.InetAddress;


public class OneThreadUDPServer extends BaseUDPServer {

    @Override
    protected void processClient() {
        while(true) {
            try {
                //buffer for reading new packets
                byte[] input = new byte[PACKET_SIZE];
                //output stream
                ByteArrayOutputStream output = new ByteArrayOutputStream(PACKET_SIZE);
                //packet for accepting [no addresses -- we do not send anything]
                DatagramPacket packet = new DatagramPacket(input, PACKET_SIZE);
                //receive data [blocking method]
                server.receive(packet);

                //extract info from packet -- where to return
                InetAddress address = packet.getAddress();
                int port = packet.getPort();

                new Thread(() -> {
                    processClientCore(
                            new DataInputStream(new ByteArrayInputStream(input)),
                            new DataOutputStream(output)
                    );
                    DatagramPacket clientPacket = new DatagramPacket(output.toByteArray(), output.size(), address, port);
                    sendPacket(clientPacket);
                }).start();
            } catch (IOException e) {
                e.printStackTrace();
                if(workThreadException == null) {
                    workThreadException = e;
                }
                throw new RuntimeException(e);
            }
        }
    }
}
