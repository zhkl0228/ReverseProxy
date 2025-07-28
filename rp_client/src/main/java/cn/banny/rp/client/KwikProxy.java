package cn.banny.rp.client;

import cn.banny.rp.ReverseProxy;
import cn.banny.rp.forward.KwikSocket;
import cn.banny.rp.forward.PortForwarder;
import cn.banny.rp.forward.StreamSocket;
import cn.banny.rp.socks.bio.ShutdownListener;
import cn.banny.rp.socks.bio.SocksShutdownListener;
import cn.banny.rp.socks.bio.StreamPipe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.kwik.core.QuicClientConnection;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.TimeUnit;

class KwikProxy {

    private static final Logger log = LoggerFactory.getLogger(KwikProxy.class);

    private final KwikConnector connector;
    private final QuicClientConnection connection;
    private final String serverHost;
    private final int listenPort;
    private final String host;
    private final int port;
    private final byte[] uuid;

    KwikProxy(KwikConnector connector, QuicClientConnection connection, String serverHost, int listenPort, String host, int port, byte[] uuid) {
        this.connector = connector;
        this.connection = connection;
        this.serverHost = serverHost;
        this.listenPort = listenPort;
        this.host = host;
        this.port = port;
        this.uuid = uuid;
    }

    final void forward() {
        log.debug("Start kwik proxy: server={}:{}", serverHost, listenPort);
        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String threadName = String.format("[%s]KwikProxy@0x%x %s:%d => %s:%d", dateFormat.format(new Date()), hashCode() & 0xffffffffL, serverHost, listenPort, host, port);
        Thread.currentThread().setName(threadName);

        Socket client = null;
        InputStream serverIn = null;
        OutputStream serverOut = null;
        InputStream clientIn = null;
        OutputStream clientOut = null;
        KwikSocket server = null;
        try {
            long start = System.currentTimeMillis();
            final tech.kwik.core.QuicStream clientStream = connection.createStream(true);
            log.debug("Try open kwik stream: {}", clientStream);
            server = new KwikSocket(connection, clientStream);
            serverOut = server.getOutputStream();
            serverOut.write(uuid);
            serverOut.flush();
            serverIn = server.getInputStream();
            long connectServerOffset = System.currentTimeMillis() - start;

            client = new Socket();
            client.setSoTimeout((int) TimeUnit.DAYS.toMillis(1));
            client.connect(new InetSocketAddress(host, port), 3000);
            clientIn = client.getInputStream();
            clientOut = client.getOutputStream();
            long connectClientOffset = System.currentTimeMillis() - start;
            log.debug("Connect kwik {}:{}: connectServerOffset={}ms, connectClientOffset={}ms, clientStream={}", serverHost, listenPort, connectServerOffset, connectClientOffset, clientStream);

            ShutdownListener listener = new SocksShutdownListener(null) {
                @Override
                public void onStreamClosed() {
                    if (connector.closed || clientStream.getStreamId() >= PortForwarder.MAX_OPEN_BIDIRECTIONAL_STREAMS) {
                        connection.close();
                    } else {
                        connector.connections.offer(connection);
                    }
                    log.debug("onStreamClosed size={}, streamId={}", connector.connections.size(), clientStream.getStreamId());
                }
            };
            StreamSocket clientStreamSocket = StreamSocket.forSocket(client);
            String name = threadName + " " + clientStream;
            new Thread(new StreamPipe(server, serverIn, clientStreamSocket, clientOut, listener), name).start();
            new Thread(new StreamPipe(clientStreamSocket, clientIn, server, serverOut, listener), name).start();
        } catch (Exception e) {
            ReverseProxy.closeQuietly(serverIn);
            ReverseProxy.closeQuietly(clientIn);
            ReverseProxy.closeQuietly(serverOut);
            ReverseProxy.closeQuietly(clientOut);
            ReverseProxy.closeQuietly(server);
            ReverseProxy.closeQuietly(client);
            connection.close();
            log.info("parseKwikProxy client={}:{}, server={}:{}", host, port, serverHost, listenPort, e);
        }
    }
}
