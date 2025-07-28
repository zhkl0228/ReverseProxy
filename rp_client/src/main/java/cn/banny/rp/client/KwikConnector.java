package cn.banny.rp.client;

import cn.banny.rp.forward.PortForwarder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.kwik.core.QuicClientConnection;
import tech.kwik.core.log.NullLogger;
import tech.kwik.core.log.SysOutLogger;

import java.io.Closeable;
import java.net.URI;
import java.time.Duration;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;

class KwikConnector implements Closeable {

    private static final Logger log = LoggerFactory.getLogger(KwikConnector.class);

    final Queue<QuicClientConnection> connections = new LinkedBlockingQueue<>();

    boolean closed;

    @Override
    public void close() {
        closed = true;
        QuicClientConnection connection;
        while ((connection = connections.poll()) != null) {
            connection.close();
        }
    }

    private final String key;
    private final String serverHost;
    private final int listenPort;

    KwikConnector(String key, String serverHost, int listenPort) {
        this.key = key;
        this.serverHost = serverHost;
        this.listenPort = listenPort;
    }

    private class KwikConnection implements Runnable {
        private final String clientHost;
        private final int clientPort;
        private final byte[] uuid;

        KwikConnection(String clientHost, int clientPort, byte[] uuid) {
            this.clientHost = clientHost;
            this.clientPort = clientPort;
            this.uuid = uuid;
        }

        @Override
        public void run() {
            QuicClientConnection clientConnection = connections.poll();
            log.debug("Check kwik {} connector: clientConnection={}, connectionSize={}", key, clientConnection, connections.size());
            if (clientConnection != null &&
                    !clientConnection.isConnected()) {
                clientConnection.close();
                clientConnection = null;
            }
            if (clientConnection == null) {
                try {
                    QuicClientConnection.Builder builder = QuicClientConnection.newBuilder()
                            .preferIPv4()
                            .noServerCertificateCheck()
                            .maxOpenPeerInitiatedBidirectionalStreams(PortForwarder.MAX_OPEN_BIDIRECTIONAL_STREAMS)
                            .maxOpenPeerInitiatedUnidirectionalStreams(0)
                            .applicationProtocol(PortForwarder.APPLICATION_PROTOCOL);
                    tech.kwik.core.log.Logger clientLogger;
                    if (log.isDebugEnabled()) {
                        clientLogger = new SysOutLogger();
                        clientLogger.logDebug(true);
                    } else {
                        clientLogger = new NullLogger();
                    }
                    log.debug("Try connect kwik: {}, connectionSize={}", key, connections.size());
                    long start = System.currentTimeMillis();
                    clientConnection = builder
                            .uri(URI.create(String.format("https://%s:%d", serverHost, listenPort)))
                            .logger(clientLogger)
                            .connectTimeout(Duration.ofSeconds(15))
                            .maxIdleTimeout(Duration.ofMinutes(30))
                            .build();
                    clientConnection.connect();
                    log.debug("Connect kwik: {}, offset={}ms", key, System.currentTimeMillis() - start);
                } catch (Exception e) {
                    if (clientConnection != null) {
                        clientConnection.close();
                    }
                    log.warn("connect kwik: {}, connections={}", key, connections, e);
                    return;
                }
            }
            new KwikProxy(KwikConnector.this, clientConnection, serverHost, listenPort, clientHost, clientPort, uuid).forward();
        }
    }

    final void start(String host, int port, byte[] uuid) {
        new Thread(new KwikConnection(host, port, uuid)).start();
    }
}
