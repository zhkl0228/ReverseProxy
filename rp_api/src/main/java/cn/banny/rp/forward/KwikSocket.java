package cn.banny.rp.forward;

import tech.kwik.core.QuicClientConnection;
import tech.kwik.core.QuicConnection;
import tech.kwik.core.QuicConstants;
import tech.kwik.core.QuicStream;
import tech.kwik.core.server.ServerConnection;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.SocketAddress;

public class KwikSocket extends StreamSocket implements Closeable {

    private final QuicConnection connection;
    private final QuicStream stream;

    public KwikSocket(QuicConnection connection, QuicStream stream) {
        this.connection = connection;
        this.stream = stream;
    }

    @Override
    public String toString() {
        return connection.toString();
    }

    @Override
    public void shutdownInput() {
        stream.abortReading(QuicConstants.TransportErrorCode.NO_ERROR.value);
    }

    @Override
    public void shutdownOutput() {
        stream.resetStream(QuicConstants.TransportErrorCode.NO_ERROR.value);
    }

    @Override
    public int getReceiveBufferSize() {
        return 0x4000;
    }

    public InputStream getInputStream() {
        return stream.getInputStream();
    }

    public OutputStream getOutputStream() {
        return stream.getOutputStream();
    }

    @Override
    public SocketAddress getRemoteSocketAddress() {
        if (connection instanceof ServerConnection) {
            ServerConnection serverConnection = (ServerConnection) connection;
            return new InetSocketAddress(serverConnection.getInitialClientAddress(), 0);
        } else if (connection instanceof QuicClientConnection) {
            QuicClientConnection quicClientConnection = (QuicClientConnection) connection;
            return quicClientConnection.getServerAddress();
        } else {
            throw new IllegalStateException("connection=" + connection);
        }
    }

    @Override
    public void close() throws IOException {
        connection.close();
    }
}
