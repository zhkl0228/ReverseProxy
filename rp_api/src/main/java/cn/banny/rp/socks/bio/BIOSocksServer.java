package cn.banny.rp.socks.bio;

import cn.banny.rp.socks.SocketFactory;
import cn.banny.rp.socks.SocksServer;
import cn.banny.utils.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

public class BIOSocksServer implements SocksServer, Runnable, ThreadFactory {

    private static final Logger log = LoggerFactory.getLogger(BIOSocksServer.class);

    private final InetSocketAddress bind;

    public BIOSocksServer(InetSocketAddress bind) {
        super();
        this.bind = bind;
    }

    @Override
    public Thread newThread(Runnable runnable) {
        Thread thread = Executors.defaultThreadFactory().newThread(runnable);
        thread.setDaemon(true);
        DateFormat dateFormat = new SimpleDateFormat("[yyyy-MM-dd HH:mm:ss]");
        thread.setName(dateFormat.format(new Date()) + getClass().getSimpleName());
        return thread;
    }

    private ExecutorService executorService;
    private ServerSocket serverSocket;

    @Override
    public void start() throws Exception {
        executorService = Executors.newCachedThreadPool(this);

        serverSocket = new ServerSocket();
        serverSocket.bind(bind);
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

    private boolean canStop;

    @Override
    public void run() {
        while (!canStop) {
            try {
                Socket socket = serverSocket.accept();
                if (!active) { // 没有启用
                    IOUtils.close(socket);
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
        IOUtils.close(serverSocket);
        executorService.shutdown();
    }

    @Override
    public int getBindPort() {
        return serverSocket.getLocalPort();
    }

}
