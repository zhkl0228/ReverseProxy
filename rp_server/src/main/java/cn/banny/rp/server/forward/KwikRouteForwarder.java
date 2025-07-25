package cn.banny.rp.server.forward;

import cn.banny.rp.ReverseProxy;
import cn.banny.rp.forward.QuicSocket;
import cn.banny.rp.forward.RouteForwarder;
import cn.banny.rp.forward.StreamSocket;
import cn.banny.rp.server.AbstractRoute;
import cn.banny.rp.socks.bio.CountDownShutdownListener;
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

class KwikRouteForwarder extends AbstractChannelForwarder implements RouteForwarder, Runnable {

    private static final Logger log = LoggerFactory.getLogger(KwikRouteForwarder.class);

    private Socket socket;
    private final KwikPortForwarder forwarderListener;
    private final ExecutorService executorService;
    private final Exchanger<QuicSocket> exchanger;
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
        this.exchangerKey = Arrays.hashCode(buffer.array());
        forwarderListener.exchangerMap.put(exchangerKey, exchanger);
        startProxyBuffer = createStartProxy(listenPort, host, port, buffer.array());
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
        DateFormat dateFormat = new SimpleDateFormat("[yyyy-MM-dd HH:mm:ss]");
        route.sendRequest(startProxyBuffer);
        try (QuicSocket client = exchanger.exchange(null, 30, TimeUnit.SECONDS);
             Socket server = this.socket) {
            this.socket = null;
            String threadName = dateFormat.format(new Date()) + "KwikRouteForwarder " +
                    client.getStreamId() + " => " + server.getRemoteSocketAddress();
            startCountDownStreamForward(client, server, threadName, executorService);
        } catch (IOException e) {
            log.debug("start forward failed: socket={}", socket, e);
        } catch (Exception e) {
            log.warn("start forward failed: socket={}", socket, e);
        } finally {
            close();
        }
    }

    private static void startCountDownStreamForward(QuicSocket client, Socket server, String threadName, ExecutorService executorService) throws IOException, InterruptedException {
        try (InputStream serverIn = server.getInputStream();
             OutputStream serverOut = server.getOutputStream();
             InputStream clientIn = client.getInputStream();
             OutputStream clientOut = client.getOutputStream()) {
            CountDownShutdownListener listener = new CountDownShutdownListener(threadName);
            StreamSocket serverStreamSocket = StreamSocket.forSocket(server);
            executorService.submit(new StreamPipe(serverStreamSocket, serverIn, client, clientOut, listener));
            executorService.submit(new StreamPipe(client, clientIn, serverStreamSocket, serverOut, listener));
            listener.waitCountDown();
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
