package cn.banny.rp.server.forward;

import cn.banny.rp.ReverseProxy;
import cn.banny.rp.forward.ForwarderListener;
import cn.banny.rp.forward.RouteForwarder;
import cn.banny.rp.server.AbstractRoute;
import cn.banny.rp.socks.bio.CountDownShutdownListener;
import cn.banny.rp.socks.bio.StreamPipe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.ExecutorService;

public class BIORouteForwarder extends AbstractChannelForwarder implements RouteForwarder, Runnable {

    private static final Logger log = LoggerFactory.getLogger(BIORouteForwarder.class);

    private Socket socket;
    private final ForwarderListener forwarderListener;
    private final ExecutorService executorService;

    private ServerSocket serverSocket;

    BIORouteForwarder(Socket socket, ForwarderListener forwarderListener, AbstractRoute route, String host, int port, ExecutorService executorService) throws IOException {
        this.socket = socket;
        this.forwarderListener = forwarderListener;
        this.executorService = executorService;

        serverSocket = new ServerSocket();
        serverSocket.setSoTimeout(30000); // 等待30秒连接超时
        serverSocket.bind(new InetSocketAddress(0));

        executorService.submit(this);
        route.sendRequest(createStartProxy(serverSocket.getLocalPort(), host, port));
    }

    @Override
    public void run() {
        log.debug("start accept channel socket on port: {}", serverSocket.getLocalPort());
        DateFormat dateFormat = new SimpleDateFormat("[yyyy-MM-dd HH:mm:ss]");
        StringBuilder builder = new StringBuilder(dateFormat.format(new Date())).append("RouteForwarder => ");
        try (Socket client = serverSocket.accept();
             Socket server = this.socket) {
            builder.append(client.getRemoteSocketAddress()).append(" => ").append(server.getRemoteSocketAddress());
            this.socket = null;
            ReverseProxy.closeQuietly(serverSocket);
            serverSocket = null;
            try (InputStream serverIn = server.getInputStream();
                 OutputStream serverOut = server.getOutputStream();
                 InputStream clientIn = client.getInputStream();
                 OutputStream clientOut = client.getOutputStream()) {
                CountDownShutdownListener listener = new CountDownShutdownListener(builder.toString());
                executorService.submit(new StreamPipe(server, serverIn, client, clientOut, listener));
                executorService.submit(new StreamPipe(client, clientIn, server, serverOut, listener));
                listener.waitCountDown();
            }
        } catch (SocketTimeoutException e) {
            log.warn("Channel server socket accept failed: listenPort={}, socket={}", serverSocket.getLocalPort(), socket, e);
        } catch (IOException e) {
            log.debug("start forward failed: listenPort={}, socket={}", serverSocket.getLocalPort(), socket, e);
        } catch (Exception e) {
            log.warn("start forward failed: listenPort={}, socket={}", serverSocket.getLocalPort(), socket, e);
        } finally {
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
