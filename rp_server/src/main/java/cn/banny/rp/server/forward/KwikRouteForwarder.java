package cn.banny.rp.server.forward;

import cn.banny.rp.ReverseProxy;
import cn.banny.rp.forward.KwikSocket;
import cn.banny.rp.forward.RouteForwarder;
import cn.banny.rp.forward.StreamSocket;
import cn.banny.rp.server.AbstractRoute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
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

class KwikRouteForwarder extends AbstractChannelForwarder implements RouteForwarder, Runnable {

    private static final Logger log = LoggerFactory.getLogger(KwikRouteForwarder.class);

    private Socket socket;
    private final KwikPortForwarder forwarderListener;
    private final ExecutorService executorService;
    private final Exchanger<KwikSocket> exchanger;
    private final AbstractRoute route;
    private final ByteBuffer startProxyBuffer;
    private final int exchangerKey;

    KwikRouteForwarder(Socket socket, KwikPortForwarder forwarderListener, AbstractRoute route, String host, int port, ExecutorService executorService,
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
        byte[] bytes = buffer.array();
        this.exchangerKey = Arrays.hashCode(bytes);
        forwarderListener.exchangerMap.put(exchangerKey, exchanger);
        startProxyBuffer = createStartProxy(listenPort, host, port, bytes);
        executorService.submit(this);
    }

    private ByteBuffer createStartProxy(int listenPort, String host, int port, byte[] uuid) {
        ByteBuffer buffer = ByteBuffer.allocate(16 + host.length() + 16);
        buffer.order(ByteOrder.BIG_ENDIAN);
        buffer.position(4);
        buffer.put((byte) 0x1d);
        buffer.putShort((short) listenPort);
        ReverseProxy.writeUTF(buffer, host);
        buffer.putShort((short) port);
        buffer.put(uuid);
        buffer.flip();
        return buffer;
    }

    @Override
    public void run() {
        log.debug("start accept kwik socket");
        route.sendRequest(startProxyBuffer);
        KwikSocket client = null;
        try {
            client = exchanger.exchange(null, 15, TimeUnit.SECONDS);
            Socket server = this.socket;
            DateFormat dateFormat = new SimpleDateFormat("[yyyy-MM-dd HH:mm:ss]");
            String threadName = dateFormat.format(new Date()) + String.format("KwikRouteForwarder @0x%x", hashCode() & 0xffffffffL) +
                    client.getRemoteSocketAddress() + " => " + server.getRemoteSocketAddress();
            NewBIORouteForwarder.startStreamForward(client, StreamSocket.forSocket(server), threadName, executorService);
            this.socket = null;
        } catch (IOException e) {
            log.debug("start forward failed: socket={}", socket, e);
            ReverseProxy.closeQuietly(client);
        } catch (Throwable e) {
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
        ReverseProxy.closeQuietly(socket);
        forwarderListener.exchangerMap.remove(exchangerKey);
        forwarderListener.notifyForwarderClosed(this);
    }

}
