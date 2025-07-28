package cn.banny.rp.server.forward;

import cn.banny.rp.ReverseProxy;
import cn.banny.rp.forward.RouteForwarder;
import cn.banny.rp.forward.StreamSocket;
import cn.banny.rp.server.AbstractRoute;
import cn.banny.rp.socks.bio.ShutdownListener;
import cn.banny.rp.socks.bio.SocksShutdownListener;
import cn.banny.rp.socks.bio.StreamPipe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.Exchanger;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

class NewBIORouteForwarder extends AbstractChannelForwarder implements RouteForwarder, Runnable {

    private static final Logger log = LoggerFactory.getLogger(NewBIORouteForwarder.class);

    private Socket socket;
    private final NewBIOPortForwarder forwarderListener;
    private final ExecutorService executorService;
    private final Exchanger<Socket> exchanger;
    private final AbstractRoute route;
    private final ByteBuffer startProxyBuffer;
    private final int exchangerKey;

    NewBIORouteForwarder(Socket socket, NewBIOPortForwarder forwarderListener, AbstractRoute route, String host, int port, ExecutorService executorService,
                         int listenPort) {
        this.socket = socket;
        this.forwarderListener = forwarderListener;
        this.executorService = executorService;
        this.route = route;
        this.exchanger = new Exchanger<>();

        UUID uuid = UUID.randomUUID();
        ByteBuffer buffer = ByteBuffer.allocate(16);
        buffer.putLong(uuid.getMostSignificantBits());
        buffer.putLong(uuid.getLeastSignificantBits());
        this.exchangerKey = Arrays.hashCode(buffer.array());
        forwarderListener.exchangerMap.put(exchangerKey, exchanger);
        startProxyBuffer = createStartProxy(listenPort, host, port, buffer.array());
        executorService.submit(this);
    }

    private ByteBuffer createStartProxy(int listenPort, String host, int port, byte[] uuid) {
        ByteBuffer buffer = ByteBuffer.allocate(16 + host.length() + 16);
        buffer.order(ByteOrder.BIG_ENDIAN);
        buffer.position(4);
        buffer.put((byte) 0x1c);
        buffer.putShort((short) listenPort);
        ReverseProxy.writeUTF(buffer, host);
        buffer.putShort((short) port);
        buffer.put(uuid);
        buffer.flip();
        return buffer;
    }

    @Override
    public void run() {
        log.debug("start accept channel socket");
        DateFormat dateFormat = new SimpleDateFormat("[yyyy-MM-dd HH:mm:ss]");
        route.sendRequest(startProxyBuffer);
        Socket client = null;
        try {
            client = exchanger.exchange(null, 30, TimeUnit.SECONDS);
            Socket server = this.socket;
            String threadName = dateFormat.format(new Date()) + String.format("NewBIORouteForwarder@0x%x ", hashCode() & 0xffffffffL) +
                    client.getRemoteSocketAddress() + " => " + server.getRemoteSocketAddress();
            startStreamForward(StreamSocket.forSocket(client), StreamSocket.forSocket(server), threadName, executorService);
            this.socket = null;
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

    static void startStreamForward(StreamSocket client, StreamSocket server, String threadName, ExecutorService executorService) throws IOException {
        InputStream serverIn = null, clientIn = null;
        OutputStream serverOut = null, clientOut = null;
        try {
            ShutdownListener listener = new SocksShutdownListener(threadName);
            serverIn = server.getInputStream();
            serverOut = server.getOutputStream();
            clientIn = client.getInputStream();
            clientOut = client.getOutputStream();
            executorService.submit(new StreamPipe(server, serverIn, client, clientOut, listener));
            executorService.submit(new StreamPipe(client, clientIn, server, serverOut, listener));
        } catch (Exception e) {
            ReverseProxy.closeQuietly(serverIn);
            ReverseProxy.closeQuietly(clientIn);
            ReverseProxy.closeQuietly(serverOut);
            ReverseProxy.closeQuietly(clientOut);
            throw e;
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
        ReverseProxy.closeQuietly(socket);
        forwarderListener.exchangerMap.remove(exchangerKey);
        forwarderListener.notifyForwarderClosed(this);
    }

}
