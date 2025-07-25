package cn.banny.rp.client;

import cn.banny.rp.ReverseProxy;
import cn.banny.rp.client.ssl.SocksOverTls;
import cn.banny.rp.forward.StreamSocket;
import cn.banny.rp.socks.bio.ShutdownListener;
import cn.banny.rp.socks.bio.SocksShutdownListener;
import cn.banny.rp.socks.bio.StreamPipe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.TimeUnit;

class ProxyStarter implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(ProxyStarter.class);

    private final String serverHost;
    private final int listenPort;
    private final String host;
    private final int port;
    private final byte[] uuid;
    private final boolean socksOverTls;

    ProxyStarter(String serverHost, int listenPort, String host, int port, byte[] uuid,
                 boolean socksOverTls) {
        this.serverHost = serverHost;
        this.listenPort = listenPort;
        this.host = host;
        this.port = port;
        this.uuid = uuid;
        this.socksOverTls = socksOverTls;
    }

    int readTimeoutInMillis;
    int connectTimeoutInMillis;

    @Override
    public void run() {
        log.debug("Start proxy starter: NewBIO={}, server={}:{}", uuid != null, serverHost, listenPort);
        DateFormat dateFormat = new SimpleDateFormat("[yyyy-MM-dd HH:mm:ss]");
        StringBuilder builder = new StringBuilder(dateFormat.format(new Date())).append("ProxyStarter ");
        builder.append(serverHost).append(":").append(listenPort).append(" => ").append(host).append(":").append(port);
        if (connectTimeoutInMillis > 0) {
            builder.append(" with connectTimeout=").append(connectTimeoutInMillis);
        }
        if (readTimeoutInMillis > 0) {
            builder.append(" with readTimeout=").append(readTimeoutInMillis);
        }
        String threadName = builder.toString();
        Thread.currentThread().setName(threadName);
        Socket server = null;
        Socket client = null;
        InputStream serverIn = null;
        OutputStream serverOut = null;
        InputStream clientIn = null;
        OutputStream clientOut = null;
        Throwable openSocksSocketException = null;
        final Date startConnectTime = new Date();
        Date openSocksSocketExceptionTime = null;
        try {
            if (socksOverTls) {
                try {
                    server = SocksOverTls.openSocksSocket(serverHost, listenPort, 10000);
                    if (readTimeoutInMillis > 0) {
                        server.setSoTimeout(readTimeoutInMillis);
                    } else {
                        server.setSoTimeout((int) TimeUnit.DAYS.toMillis(1));
                    }
                } catch (Throwable t) {
                    openSocksSocketExceptionTime = new Date();
                    openSocksSocketException = t;
                }
            }
            if (server == null) {
                server = new Socket();
                if (readTimeoutInMillis > 0) {
                    server.setSoTimeout(readTimeoutInMillis);
                } else {
                    server.setSoTimeout((int) TimeUnit.DAYS.toMillis(1));
                }
                if (connectTimeoutInMillis > 0) {
                    server.connect(new InetSocketAddress(serverHost, listenPort), connectTimeoutInMillis);
                } else {
                    server.connect(new InetSocketAddress(serverHost, listenPort), 15000);
                }
                if (openSocksSocketException != null) {
                    dateFormat = new SimpleDateFormat("[yyyy-MM-dd HH:mm:ss SSS]");
                    log.debug("[{}][{}]openSocksSocket failed: {}:{} => {}:{}",
                            dateFormat.format(startConnectTime),
                            dateFormat.format(openSocksSocketExceptionTime),
                            serverHost, listenPort, host, port, openSocksSocketException);
                }
            }

            serverOut = server.getOutputStream();
            if (uuid != null) {
                serverOut.write(uuid);
                serverOut.flush();
            }

            client = new Socket();
            if (readTimeoutInMillis > 0) {
                client.setSoTimeout(readTimeoutInMillis);
            } else {
                client.setSoTimeout((int) TimeUnit.DAYS.toMillis(1));
            }

            if (connectTimeoutInMillis > 0) {
                client.connect(new InetSocketAddress(host, port), connectTimeoutInMillis);
            } else {
                client.connect(new InetSocketAddress(host, port), 3000);
            }
            serverIn = server.getInputStream();
            clientIn = client.getInputStream();
            clientOut = client.getOutputStream();
            ShutdownListener listener = new SocksShutdownListener(null);
            StreamSocket s1 =  StreamSocket.forSocket(server);
            StreamSocket s2 =  StreamSocket.forSocket(client);
            new Thread(new StreamPipe(s1, serverIn, s2, clientOut, listener), threadName).start();
            new Thread(new StreamPipe(s2, clientIn, s1, serverOut, listener), threadName).start();
        } catch (Exception e) {
            ReverseProxy.closeQuietly(serverIn);
            ReverseProxy.closeQuietly(clientIn);
            ReverseProxy.closeQuietly(serverOut);
            ReverseProxy.closeQuietly(clientOut);
            ReverseProxy.closeQuietly(server);
            ReverseProxy.closeQuietly(client);
            if (openSocksSocketException != null) {
                log.warn("[{}][{}]openSocksSocket failed: {}:{} => {}:{}",
                        dateFormat.format(startConnectTime),
                        dateFormat.format(openSocksSocketExceptionTime),
                        serverHost, listenPort, host, port, openSocksSocketException);
            }
            log.info("parseStartProxy client={}:{}, server={}:{}", host, port, serverHost, listenPort, e);
        }
    }
}
