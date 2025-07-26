package cn.banny.rp.client;

import cn.banny.rp.ReverseProxy;
import cn.banny.rp.forward.PortForwarder;
import cn.banny.rp.forward.KwikSocket;
import cn.banny.rp.forward.StreamSocket;
import cn.banny.rp.socks.bio.ShutdownListener;
import cn.banny.rp.socks.bio.SocksShutdownListener;
import cn.banny.rp.socks.bio.StreamPipe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.kwik.core.QuicClientConnection;
import tech.kwik.core.log.NullLogger;
import tech.kwik.core.log.SysOutLogger;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.Date;
import java.util.concurrent.TimeUnit;

class KwikStarter implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(KwikStarter.class);

    static {
        System.setProperty("tech.kwik.core.no-security-warnings", "true");
    }

    private final String serverHost;
    private final int listenPort;
    private final String host;
    private final int port;
    private final byte[] uuid;

    KwikStarter(String serverHost, int listenPort, String host, int port, byte[] uuid) {
        this.serverHost = serverHost;
        this.listenPort = listenPort;
        this.host = host;
        this.port = port;
        this.uuid = uuid;
    }

    @Override
    public void run() {
        log.debug("Start kwik starter: server={}:{}", serverHost, listenPort);
        DateFormat dateFormat = new SimpleDateFormat("[yyyy-MM-dd HH:mm:ss]");
        String threadName = dateFormat.format(new Date()) + "KwikStarter " +
                serverHost + ":" + listenPort + " => " + host + ":" + port;
        Thread.currentThread().setName(threadName);

        Socket client = null;
        InputStream serverIn = null;
        OutputStream serverOut = null;
        InputStream clientIn = null;
        OutputStream clientOut = null;
        KwikSocket server = null;
        try {
            QuicClientConnection.Builder builder = QuicClientConnection.newBuilder();
            builder.preferIPv4();
            builder.noServerCertificateCheck();
            builder.applicationProtocol(PortForwarder.APPLICATION_PROTOCOL);
            tech.kwik.core.log.Logger clientLogger;
            if (log.isDebugEnabled()) {
                clientLogger = new SysOutLogger();
                clientLogger.logDebug(true);
            } else {
                clientLogger = new NullLogger();
            }
            QuicClientConnection connection = builder
                    .uri(URI.create(String.format("https://%s:%d", serverHost, listenPort)))
                    .logger(clientLogger)
                    .connectTimeout(Duration.ofSeconds(15))
                    .build();
            connection.connect();
            tech.kwik.core.QuicStream clientStream = connection.createStream(true);
            server = new KwikSocket(connection, clientStream);

            serverOut = clientStream.getOutputStream();
            if (uuid != null) {
                serverOut.write(uuid);
                serverOut.flush();
            }

            client = new Socket();
            client.setSoTimeout((int) TimeUnit.DAYS.toMillis(1));
            client.connect(new InetSocketAddress(host, port), 3000);

            serverIn = server.getInputStream();
            clientIn = client.getInputStream();
            clientOut = client.getOutputStream();
            ShutdownListener listener = new SocksShutdownListener(null);
            StreamSocket clientStreamSocket = StreamSocket.forSocket(client);
            new Thread(new StreamPipe(server, serverIn, clientStreamSocket, clientOut, listener), threadName).start();
            new Thread(new StreamPipe(clientStreamSocket, clientIn, server, serverOut, listener), threadName).start();
        } catch (Exception e) {
            ReverseProxy.closeQuietly(serverIn);
            ReverseProxy.closeQuietly(clientIn);
            ReverseProxy.closeQuietly(serverOut);
            ReverseProxy.closeQuietly(clientOut);
            ReverseProxy.closeQuietly(server);
            ReverseProxy.closeQuietly(client);
            log.info("parseKwikProxy client={}:{}, server={}:{}", host, port, serverHost, listenPort, e);
        }
    }
}
