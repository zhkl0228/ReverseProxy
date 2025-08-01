package cn.banny.rp.server.forward;

import cn.banny.rp.ReverseProxy;
import cn.banny.rp.forward.ForwarderListener;
import cn.banny.rp.forward.RouteForwarder;
import cn.banny.rp.forward.StreamSocket;
import cn.banny.rp.server.AbstractRoute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.ExecutorService;

class BIORouteForwarder extends AbstractChannelForwarder implements RouteForwarder, Runnable {

    private static final Logger log = LoggerFactory.getLogger(BIORouteForwarder.class);

    private Socket socket;
    private final ForwarderListener forwarderListener;
    private final ExecutorService executorService;
    private final AbstractRoute route;

    private ServerSocket serverSocket;
    private final ByteBuffer startProxyBuffer;

    BIORouteForwarder(Socket socket, ForwarderListener forwarderListener, AbstractRoute route, String host, int port, ExecutorService executorService) throws IOException {
        this.socket = socket;
        this.forwarderListener = forwarderListener;
        this.executorService = executorService;
        this.route = route;

        serverSocket = new ServerSocket();
        serverSocket.setSoTimeout(30000); // 等待30秒连接超时
        serverSocket.bind(new InetSocketAddress(0));

        startProxyBuffer = createStartProxy(serverSocket.getLocalPort(), host, port);
        executorService.submit(this);
    }

    @Override
    public void run() {
        log.debug("start accept channel socket on port: {}", serverSocket.getLocalPort());
        DateFormat dateFormat = new SimpleDateFormat("[yyyy-MM-dd HH:mm:ss]");
        route.sendRequest(startProxyBuffer);
        Socket client = null;
        try {
            client = serverSocket.accept();
            ReverseProxy.closeQuietly(serverSocket);
            serverSocket = null;
            Socket server = this.socket;
            String threadName = dateFormat.format(new Date()) + "RouteForwarder => " +
                    client.getRemoteSocketAddress() + " => " + server.getRemoteSocketAddress();
            NewBIORouteForwarder.startStreamForward(StreamSocket.forSocket(client), StreamSocket.forSocket(server), threadName, executorService);
            this.socket = null;
        } catch (SocketTimeoutException e) {
            log.warn("Channel server socket accept failed: socket={}", socket, e);
            ReverseProxy.closeQuietly(client);
        } catch (IOException e) {
            log.debug("start forward failed: socket={}", socket, e);
            ReverseProxy.closeQuietly(client);
        } catch (Exception e) {
            log.warn("start forward failed: socket={}", socket, e);
            ReverseProxy.closeQuietly(client);
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
