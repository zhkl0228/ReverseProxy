package cn.banny.rp.forward;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;

public abstract class StreamSocket implements Closeable {

    public abstract InputStream getInputStream() throws IOException;
    public abstract OutputStream getOutputStream() throws IOException;

    public abstract void shutdownInput() throws IOException;
    public abstract void shutdownOutput() throws IOException;
    public abstract int getReceiveBufferSize() throws SocketException;
    public abstract SocketAddress getRemoteSocketAddress();

    public static StreamSocket forSocket(final Socket socket) {
        return new SocketWrapper(socket);
    }

    private static class SocketWrapper extends StreamSocket {
        private final Socket socket;

        public SocketWrapper(Socket socket) {
            this.socket = socket;
        }

        @Override
        public String toString() {
            return socket.toString();
        }

        @Override
        public InputStream getInputStream() throws IOException {
            return socket.getInputStream();
        }

        @Override
        public OutputStream getOutputStream() throws IOException {
            return socket.getOutputStream();
        }

        @Override
        public void shutdownInput() throws IOException {
            socket.shutdownInput();
        }

        @Override
        public void shutdownOutput() throws IOException {
            socket.shutdownOutput();
        }

        @Override
        public int getReceiveBufferSize() throws SocketException {
            return socket.getReceiveBufferSize();
        }

        @Override
        public SocketAddress getRemoteSocketAddress() {
            return socket.getRemoteSocketAddress();
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }
}
