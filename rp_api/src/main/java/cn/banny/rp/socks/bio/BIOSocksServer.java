package cn.banny.rp.socks.bio;

import cn.banny.rp.ReverseProxy;
import cn.banny.rp.socks.ServerSocketFactory;
import cn.banny.rp.socks.SocketFactory;
import cn.banny.rp.socks.SocksServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BIOSocksServer implements SocksServer, Runnable {

    private static final Logger log = LoggerFactory.getLogger(BIOSocksServer.class);

    private final InetSocketAddress bindAddress;

    public BIOSocksServer(InetSocketAddress bindAddress) {
        super();
        this.bindAddress = bindAddress;
    }

    private ExecutorService executorService;
    private ServerSocket serverSocket;

    @Override
    public void start() throws Exception {
        executorService = Executors.newVirtualThreadPerTaskExecutor();

        serverSocket = serverSocketFactory == null ? new ServerSocket() : serverSocketFactory.newServerSocket();
        serverSocket.bind(bindAddress);
        log.debug("start bind port: {}", getBindPort());
        canStop = false;
        active = true;
        executorService.submit(this);
    }

    private boolean active;

    @Override
    public void setActive(boolean active) {
        this.active = active;
    }

    private SocketFactory socketFactory;

    @Override
    public void setSocketFactory(SocketFactory socketFactory) {
        this.socketFactory = socketFactory;
    }

    private ServerSocketFactory serverSocketFactory;

    @Override
    public void setServerSocketFactory(ServerSocketFactory serverSocketFactory) {
        this.serverSocketFactory = serverSocketFactory;
    }

    private boolean canStop;

    @Override
    public void run() {
        while (!canStop) {
            try {
                Socket socket = serverSocket.accept();
                if (!active) { // 没有启用
                    ReverseProxy.closeQuietly(socket);
                    continue;
                }

                new SocksHandler(executorService, socket, socketFactory).handle();
            } catch (IOException e) {
                log.debug(e.getMessage(), e);
            }
        }
    }

    @Override
    public void stopSilent() {
        canStop = true;
        ReverseProxy.closeQuietly(serverSocket);
        executorService.shutdown();
    }

    @Override
    public int getBindPort() {
        return serverSocket.getLocalPort();
    }

}
