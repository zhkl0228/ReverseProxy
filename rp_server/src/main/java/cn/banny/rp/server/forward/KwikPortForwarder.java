package cn.banny.rp.server.forward;

import cn.banny.rp.ReverseProxy;
import cn.banny.rp.forward.PortForwarder;
import cn.banny.rp.forward.QuicSocket;
import cn.banny.rp.forward.RouteForwarder;
import cn.banny.rp.server.AbstractRoute;
import org.apache.commons.collections4.map.PassiveExpiringMap;
import org.apache.mina.util.DaemonThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.kwik.core.QuicConnection;
import tech.kwik.core.QuicStream;
import tech.kwik.core.log.NullLogger;
import tech.kwik.core.log.SysOutLogger;
import tech.kwik.core.server.ApplicationProtocolConnection;
import tech.kwik.core.server.ApplicationProtocolConnectionFactory;
import tech.kwik.core.server.ServerConnectionConfig;
import tech.kwik.core.server.ServerConnector;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.KeyStore;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.Exchanger;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

class KwikPortForwarder extends AbstractPortForwarder implements PortForwarder, Runnable, ApplicationProtocolConnectionFactory {

    private static final Logger log = LoggerFactory.getLogger(KwikPortForwarder.class);

    KwikPortForwarder(boolean bindLocal, int inPort, String outHost, int outPort, AbstractRoute route) {
        super(bindLocal, inPort, outHost, outPort, route);
    }

    private boolean canStop;
    private ExecutorService executorService;
    private ServerSocket serverSocket;
    private DatagramSocket datagramSocket;
    private ServerConnector serverConnector;

    @Override
    public int start() throws IOException {
        executorService = Executors.newCachedThreadPool(new DaemonThreadFactory());

        canStop = false;
        serverSocket = new ServerSocket();
        serverSocket.bind(createBindAddress());
        serverSocket.setReuseAddress(true);
        try (InputStream inputStream = getClass().getResourceAsStream("/server.jks")) {
            final String STORE_PASSWORD = "rp_pass";
            final String KEY_PASSWORD = "rp_pass";
            KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
            ks.load(inputStream, STORE_PASSWORD.toCharArray());
            ServerConnectionConfig serverConnectionConfig = ServerConnectionConfig.builder()
                    .maxOpenPeerInitiatedBidirectionalStreams(Short.MAX_VALUE)
                    .maxOpenPeerInitiatedUnidirectionalStreams(Short.MAX_VALUE)
                    .build();
            ServerConnector.Builder builder = ServerConnector.builder();
            builder.withKeyStore(ks, "rp_server", KEY_PASSWORD.toCharArray());
            tech.kwik.core.log.Logger serverLogger;
            if (log.isDebugEnabled()) {
                serverLogger = new SysOutLogger();
                serverLogger.logDebug(true);
            } else {
                serverLogger = new NullLogger();
            }
            datagramSocket = new DatagramSocket(inPort);
            serverConnector = builder
                    .withPort(1) // placeholder
                    .withSocket(datagramSocket)
                    .withConfiguration(serverConnectionConfig)
                    .withLogger(serverLogger)
                    .build();

            serverConnector.registerApplicationProtocol(APPLICATION_PROTOCOL, this);
            serverConnector.start();
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            log.warn("start kwik", e);
            throw new IOException("start kwik server failed", e);
        }

        newThread(this).start();
        listenPort = datagramSocket.getLocalPort();
        return listenPort;
    }

    @Override
    public void stop() {
        canStop = true;
        ReverseProxy.closeQuietly(serverSocket);
        if (serverConnector != null) {
            serverConnector.close();
        }
        ReverseProxy.closeQuietly(datagramSocket);
        for(RouteForwarder forwarder : getForwarders()) {
            ReverseProxy.closeQuietly(forwarder);
        }
        executorService.shutdown();
    }

    final Map<Integer, Exchanger<QuicSocket>> exchangerMap = new PassiveExpiringMap<>(1, TimeUnit.MINUTES);

    @Override
    public ApplicationProtocolConnection createConnection(String protocol, final QuicConnection quicConnection) {
        log.debug("createConnection protocol={}", protocol);
        return new ApplicationProtocolConnection() {
            @Override
            public void acceptPeerInitiatedStream(tech.kwik.core.QuicStream serverStream) {
                log.debug("acceptPeerInitiatedStream serverStream={}", serverStream);
                executorService.submit(new AcceptPeerInitiatedStream(quicConnection, serverStream));
            }
        };
    }

    private class AcceptPeerInitiatedStream implements Runnable {
        private final QuicConnection quicConnection;
        private final QuicStream quicStream;
        AcceptPeerInitiatedStream(QuicConnection quicConnection, QuicStream quicStream) {
            this.quicConnection = quicConnection;
            this.quicStream = quicStream;
        }
        @Override
        public void run() {
            InputStream inputStream = null;
            try {
                inputStream = quicStream.getInputStream();
                byte[] uuid = new byte[16];
                new DataInputStream(inputStream).readFully(uuid);
                Exchanger<QuicSocket> exchanger = exchangerMap.remove(Arrays.hashCode(uuid));
                if (exchanger != null) {
                    exchanger.exchange(new QuicSocket(quicConnection, quicStream), 5, TimeUnit.SECONDS);
                } else {
                    throw new IllegalStateException("No exchanger for uuid: " + Arrays.toString(uuid));
                }
            } catch(Throwable t) {
                t.printStackTrace(System.err);
                log.debug("acceptPeerInitiatedStream failed.", t);
                ReverseProxy.closeQuietly(inputStream);
                quicConnection.close();
            }
        }
    }

    @Override
    public void run() {
        while (!canStop) {
            Socket socket = null;
            try {
                socket = serverSocket.accept();
                RouteForwarder forwarder = createForward(socket);
                forwards.put(forwarder.hashCode(), forwarder);
            } catch (Throwable e) {
                ReverseProxy.closeQuietly(socket);
                if (canStop) {
                    log.debug("Kwik.createForward", e);
                } else {
                    log.warn("Kwik.createForward", e);
                }
            }
        }
    }

    private RouteForwarder createForward(Socket socket) {
        return new KwikRouteForwarder(socket, this, route, outHost, outPort, executorService, datagramSocket.getLocalPort());
    }
}
