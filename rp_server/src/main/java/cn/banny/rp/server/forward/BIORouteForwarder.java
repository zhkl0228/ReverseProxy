package cn.banny.rp.server.forward;

import cn.banny.rp.ReverseProxy;
import cn.banny.rp.forward.ForwarderListener;
import cn.banny.rp.forward.RouteForwarder;
import cn.banny.rp.server.AbstractRoute;
import cn.banny.rp.socks.bio.ShutdownListener;
import cn.banny.rp.socks.bio.SocksShutdownListener;
import cn.banny.rp.socks.bio.StreamPipe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;

public class BIORouteForwarder extends AbstractChannelForwarder implements RouteForwarder, Runnable {

    private static final Logger log = LoggerFactory.getLogger(BIORouteForwarder.class);

    private final Socket socket;
    private final ForwarderListener forwarderListener;
    private final ExecutorService executorService;

    private ServerSocket serverSocket;

    BIORouteForwarder(Socket socket, ForwarderListener forwarderListener, AbstractRoute route, String host, int port, ExecutorService executorService) {
        this.socket = socket;
        this.forwarderListener = forwarderListener;
        this.executorService = executorService;

        try {
            serverSocket = new ServerSocket();
            serverSocket.setSoTimeout(30000); // 等待30秒连接超时
            serverSocket.bind(new InetSocketAddress(0));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }

        executorService.submit(this);
        route.sendRequest(createStartProxy(serverSocket.getLocalPort(), host, port));
    }

    @Override
    public void run() {
        try {
            log.debug("start accept channel socket on port: " + serverSocket.getLocalPort());
            Socket socket = serverSocket.accept();
            ReverseProxy.closeQuietly(serverSocket);
            serverSocket = null;

            ShutdownListener listener = new SocksShutdownListener(null);
            executorService.submit(new StreamPipe(this.socket, this.socket.getInputStream(), socket, socket.getOutputStream(), listener));
            executorService.submit(new StreamPipe(socket, socket.getInputStream(), this.socket, this.socket.getOutputStream(), listener));
        } catch (IOException e) {
            log.debug("Channel server socket accept failed: listenPort=" + serverSocket.getLocalPort(), e);
            close();
        }
    }

    @Override
    public void writeData(ByteBuffer buffer) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void checkForwarder() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void close() {
        ReverseProxy.closeQuietly(serverSocket);
        ReverseProxy.closeQuietly(socket);
        forwarderListener.notifyForwarderClosed(this);
    }

}
