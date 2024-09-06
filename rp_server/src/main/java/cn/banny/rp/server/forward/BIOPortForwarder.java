package cn.banny.rp.server.forward;

import cn.banny.rp.ReverseProxy;
import cn.banny.rp.forward.PortForwarder;
import cn.banny.rp.forward.RouteForwarder;
import cn.banny.rp.server.AbstractRoute;
import org.apache.mina.util.DaemonThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BIOPortForwarder extends AbstractPortForwarder implements PortForwarder, Runnable {

    private static final Logger log = LoggerFactory.getLogger(BIOPortForwarder.class);

    public BIOPortForwarder(int inPort, String outHost, int outPort, AbstractRoute route) {
        super(inPort, outHost, outPort, route);
    }

    private boolean canStop;
    private ServerSocket serverSocket;
    private ExecutorService executorService;

    @Override
    public int start() throws IOException {
        executorService = Executors.newCachedThreadPool(new DaemonThreadFactory());

        canStop = false;
        serverSocket = new ServerSocket();
        serverSocket.bind(new InetSocketAddress(inPort));
        serverSocket.setReuseAddress(true);

        newThread(this).start();
        listenPort = serverSocket.getLocalPort();
        return listenPort;
    }

    @Override
    public void stop() {
        canStop = true;
        ReverseProxy.closeQuietly(serverSocket);
        for(RouteForwarder forwarder : getForwarders()) {
            ReverseProxy.closeQuietly(forwarder);
        }
        executorService.shutdown();
    }

    @Override
    public void run() {
        while (!canStop) {
            try {
                Socket socket = serverSocket.accept();
                RouteForwarder forwarder = createForward(socket);
                forwards.put(forwarder.hashCode(), forwarder);
            } catch (IOException e) {
                if (canStop) {
                    log.debug(e.getMessage(), e);
                } else {
                    log.warn(e.getMessage(), e);
                }
            }
        }
    }

    private RouteForwarder createForward(Socket socket) {
        return new BIORouteForwarder(socket, this, route, outHost, outPort, executorService);
    }

}
