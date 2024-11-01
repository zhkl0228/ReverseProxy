package cn.banny.rp.server;

import cn.banny.rp.ReverseProxy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SocketChannel;

class SocketWrap extends Socket {

    private static final Logger log = LoggerFactory.getLogger(SocketWrap.class);

    private final AbstractRoute route;

    SocketWrap(AbstractRoute route) {
        this.route = route;
    }

    private ByteBuffer createForwardSocket(int listenPort, InetSocketAddress socketAddress, int connectTimeoutInMillis) {
        log.debug("createForwardSocket listenPort={}, socketAddress={}, connectTimeoutInMillis={}, readTimeoutInMillis={}", listenPort, socketAddress, connectTimeoutInMillis, readTimeoutInMillis);
        String host = socketAddress.getHostString();
        ByteBuffer buffer = ByteBuffer.allocate(24 + host.length());
        buffer.order(ByteOrder.BIG_ENDIAN);
        buffer.position(4);
        buffer.put((byte) 0x1b);
        buffer.putShort((short) listenPort);
        ReverseProxy.writeUTF(buffer, host);
        buffer.putShort((short) socketAddress.getPort());
        buffer.putInt(readTimeoutInMillis);
        buffer.putInt(connectTimeoutInMillis);
        buffer.flip();
        return buffer;
    }

    private Socket wrapped;

    @Override
    public void connect(SocketAddress endpoint) throws IOException {
        connect(endpoint, 60000);
    }

    @Override
    public void connect(SocketAddress endpoint, int connectTimeoutInMillis) throws IOException {
        InetSocketAddress socketAddress = (InetSocketAddress) endpoint;
        try(ServerSocket serverSocket = new ServerSocket()) {
            serverSocket.setSoTimeout(connectTimeoutInMillis + 30000);
            serverSocket.bind(new InetSocketAddress(0));
            route.sendRequest(createForwardSocket(serverSocket.getLocalPort(), socketAddress, connectTimeoutInMillis));
            wrapped = serverSocket.accept();
            if (readTimeoutInMillis > 0) {
                wrapped.setSoTimeout(readTimeoutInMillis);
            }
        }
    }

    @Override
    public void bind(SocketAddress bindpoint) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public InetAddress getInetAddress() {
        if (wrapped == null) {
            throw new IllegalStateException();
        } else {
            return wrapped.getInetAddress();
        }
    }

    @Override
    public InetAddress getLocalAddress() {
        if (wrapped == null) {
            throw new IllegalStateException();
        } else {
            return wrapped.getLocalAddress();
        }
    }

    @Override
    public int getPort() {
        if (wrapped == null) {
            throw new IllegalStateException();
        } else {
            return wrapped.getPort();
        }
    }

    @Override
    public int getLocalPort() {
        if (wrapped == null) {
            throw new IllegalStateException();
        } else {
            return wrapped.getLocalPort();
        }
    }

    @Override
    public SocketAddress getRemoteSocketAddress() {
        if (wrapped == null) {
            throw new IllegalStateException();
        } else {
            return wrapped.getRemoteSocketAddress();
        }
    }

    @Override
    public SocketAddress getLocalSocketAddress() {
        if (wrapped == null) {
            throw new IllegalStateException();
        } else {
            return wrapped.getLocalSocketAddress();
        }
    }

    @Override
    public SocketChannel getChannel() {
        throw new UnsupportedOperationException();
    }

    @Override
    public InputStream getInputStream() throws IOException {
        if (wrapped == null) {
            throw new IllegalStateException();
        } else {
            return wrapped.getInputStream();
        }
    }

    @Override
    public OutputStream getOutputStream() throws IOException {
        if (wrapped == null) {
            throw new IllegalStateException();
        } else {
            return wrapped.getOutputStream();
        }
    }

    @Override
    public void setTcpNoDelay(boolean on) {
        log.debug("setTcpNoDelay: {}", on);
    }

    @Override
    public boolean getTcpNoDelay() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setSoLinger(boolean on, int linger) {
        log.debug("setSoLinger: on={}, linger={}", on, linger);
    }

    @Override
    public int getSoLinger() throws SocketException {
        if (wrapped == null) {
            throw new UnsupportedOperationException();
        } else {
            return wrapped.getSoLinger();
        }
    }

    @Override
    public void sendUrgentData(int data) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setOOBInline(boolean on) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean getOOBInline() {
        throw new UnsupportedOperationException();
    }

    private int readTimeoutInMillis;

    @Override
    public void setSoTimeout(int timeout) throws SocketException {
        if (wrapped == null) {
            readTimeoutInMillis = timeout;
        } else {
            wrapped.setSoTimeout(timeout);
        }
    }

    @Override
    public int getSoTimeout() throws SocketException {
        if (wrapped == null) {
            return readTimeoutInMillis;
        } else {
            return wrapped.getSoTimeout();
        }
    }

    @Override
    public void setSendBufferSize(int size) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getSendBufferSize() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setReceiveBufferSize(int size) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getReceiveBufferSize() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setKeepAlive(boolean on) {
        log.debug("setKeepAlive: {}", on);
    }

    @Override
    public boolean getKeepAlive() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setTrafficClass(int tc) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getTrafficClass() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setReuseAddress(boolean on) {
        log.debug("setReuseAddress: {}", on);
    }

    @Override
    public boolean getReuseAddress() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void close() throws IOException {
        super.close();
        if (wrapped != null) {
            wrapped.close();
            wrapped = null;
        }
    }

    @Override
    public void shutdownInput() throws IOException {
        if (wrapped == null) {
            throw new IllegalStateException();
        } else {
            wrapped.shutdownInput();
        }
    }

    @Override
    public void shutdownOutput() throws IOException {
        if (wrapped == null) {
            throw new IllegalStateException();
        } else {
            wrapped.shutdownOutput();
        }
    }

    @Override
    public String toString() {
        if (wrapped == null) {
            return super.toString();
        } else {
            return wrapped.toString();
        }
    }

    @Override
    public boolean isConnected() {
        if (wrapped == null) {
            return false;
        } else {
            return wrapped.isConnected();
        }
    }

    @Override
    public boolean isBound() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isClosed() {
        if (wrapped == null) {
            return true;
        } else {
            return wrapped.isClosed();
        }
    }

    @Override
    public boolean isInputShutdown() {
        if (wrapped == null) {
            return true;
        } else {
            return wrapped.isInputShutdown();
        }
    }

    @Override
    public boolean isOutputShutdown() {
        if (wrapped == null) {
            return true;
        } else {
            return wrapped.isOutputShutdown();
        }
    }

    @Override
    public void setPerformancePreferences(int connectionTime, int latency, int bandwidth) {
        throw new UnsupportedOperationException();
    }

}
