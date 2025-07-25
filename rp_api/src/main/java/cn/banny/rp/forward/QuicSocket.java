package cn.banny.rp.forward;

import tech.kwik.core.QuicConnection;
import tech.kwik.core.QuicConstants;
import tech.kwik.core.QuicStream;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class QuicSocket extends StreamSocket implements Closeable {
    final QuicConnection connection;
    final QuicStream stream;

    public QuicSocket(QuicConnection connection, QuicStream stream) {
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

    public int getStreamId() {
        return stream.getStreamId();
    }

    @Override
    public void close() throws IOException {
        connection.close();
    }
}
