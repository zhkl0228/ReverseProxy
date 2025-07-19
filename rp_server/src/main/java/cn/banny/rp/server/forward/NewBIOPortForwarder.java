package cn.banny.rp.server.forward;

import cn.banny.rp.ReverseProxy;
import cn.banny.rp.forward.PortForwarder;
import cn.banny.rp.forward.RouteForwarder;
import cn.banny.rp.server.AbstractRoute;
import org.apache.commons.collections4.map.PassiveExpiringMap;
import org.apache.mina.util.DaemonThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.Exchanger;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

class NewBIOPortForwarder extends AbstractPortForwarder implements PortForwarder, Runnable {

    private static final Logger log = LoggerFactory.getLogger(NewBIOPortForwarder.class);

    NewBIOPortForwarder(boolean bindLocal, int inPort, String outHost, int outPort, AbstractRoute route) {
        super(bindLocal, inPort, outHost, outPort, route);
    }

    private boolean canStop;
    private ServerSocket clientServer;
    private ServerSocket serverSocket;
    private ExecutorService executorService;

    @Override
    public int start() throws IOException {
        executorService = Executors.newCachedThreadPool(new DaemonThreadFactory());

        canStop = false;
        clientServer = new ServerSocket();
        clientServer.bind(new InetSocketAddress(0));
        clientServer.setReuseAddress(true);

        serverSocket = new ServerSocket();
        serverSocket.bind(createBindAddress());
        serverSocket.setReuseAddress(true);

        newThread(new ClientServerHandler()).start();
        newThread(this).start();
        listenPort = serverSocket.getLocalPort();
        return listenPort;
    }

    @Override
    public void stop() {
        canStop = true;
        ReverseProxy.closeQuietly(serverSocket);
        ReverseProxy.closeQuietly(clientServer);
        for(RouteForwarder forwarder : getForwarders()) {
            ReverseProxy.closeQuietly(forwarder);
        }
        executorService.shutdown();
    }

    final Map<Integer, Exchanger<Socket>> exchangerMap = new PassiveExpiringMap<>(1, TimeUnit.MINUTES);

    private class CheckClientHandler implements Runnable {
        private final Socket socket;
        public CheckClientHandler(Socket socket) {
            this.socket = socket;
        }
        @Override
        public void run() {
            InputStream inputStream = null;
            try {
                socket.setSoTimeout(5000);
                inputStream = socket.getInputStream();
                byte[] uuid = new byte[16];
                new DataInputStream(inputStream).readFully(uuid);
                Exchanger<Socket> exchanger = exchangerMap.remove(Arrays.hashCode(uuid));
                if (exchanger != null) {
                    socket.setSoTimeout(0);
                    exchanger.exchange(socket, 5, TimeUnit.SECONDS);
                } else {
                    throw new IllegalStateException("No exchanger for uuid: " + Arrays.toString(uuid));
                }
            } catch(Throwable t) {
                t.printStackTrace(System.err);
                log.debug("check client failed.", t);
                ReverseProxy.closeQuietly(inputStream);
                ReverseProxy.closeQuietly(socket);
            }
        }
    }

    private class ClientServerHandler implements Runnable {
        @Override
        public void run() {
            while (!canStop) {
                Socket socket = null;
                try {
                    socket = clientServer.accept();
                    newThread(new CheckClientHandler(socket)).start();
                } catch (Throwable e) {
                    ReverseProxy.closeQuietly(socket);
                    if (canStop) {
                        log.debug("checkForward", e);
                    } else {
                        log.warn("checkForward", e);
                    }
                }
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
                    log.debug("NewBIO.createForward", e);
                } else {
                    log.warn("NewBIO.createForward", e);
                }
            }
        }
    }

    private RouteForwarder createForward(Socket socket) {
        return new NewBIORouteForwarder(socket, this, route, outHost, outPort, executorService, clientServer.getLocalPort());
    }
}
