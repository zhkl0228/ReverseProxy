package cn.banny.rp.socks.bio;

import cn.banny.rp.ReverseProxy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

public class StreamPipe implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(StreamPipe.class);

    private final Socket in;
    private final InputStream inputStream;
    private final Socket out;
    private final OutputStream outputStream;
    private final ShutdownListener shutdownListener;

    public StreamPipe(Socket in, InputStream inputStream, Socket out, OutputStream outputStream, ShutdownListener shutdownListener) {
        this.in = in;
        this.inputStream = inputStream;
        this.out = out;
        this.outputStream = outputStream;
        this.shutdownListener = shutdownListener;
    }

    @Override
    public void run() {
        try {
            shutdownListener.onStreamStart();
            byte[] buf = new byte[in.getReceiveBufferSize()];
            int read;
            while ((read = inputStream.read(buf)) != ReverseProxy.EOF) {
                outputStream.write(buf, 0, read);
            }
        } catch (IOException e) {
            log.debug("stream failed: in={}, out={}", in, out, e);
        } finally {
            if (shutdownListener.needShutdown()) {
                ReverseProxy.closeQuietly(inputStream);
                ReverseProxy.closeQuietly(outputStream);
            }
            try { in.shutdownInput(); } catch(IOException ignored) {}
            shutdownListener.onShutdownInput(in);
            try { out.shutdownOutput(); } catch(IOException ignored) {}
            shutdownListener.onShutdownOutput(out);
            shutdownListener.onStreamEnd();
        }
    }

}
