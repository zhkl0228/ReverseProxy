package cn.banny.rp.socks.bio;

import cn.banny.rp.ReverseProxy;
import cn.banny.rp.forward.StreamSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class StreamPipe implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(StreamPipe.class);

    private final StreamSocket in;
    private final InputStream inputStream;
    private final StreamSocket out;
    private final OutputStream outputStream;
    private final ShutdownListener shutdownListener;

    public StreamPipe(StreamSocket in, InputStream inputStream, StreamSocket out, OutputStream outputStream, ShutdownListener shutdownListener) {
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
            outputStream.flush();
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
