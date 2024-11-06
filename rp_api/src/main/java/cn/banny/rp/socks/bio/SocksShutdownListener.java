package cn.banny.rp.socks.bio;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.Socket;

public class SocksShutdownListener implements ShutdownListener {

    private static final Logger log = LoggerFactory.getLogger(SocksShutdownListener.class);

    private final String threadName;

    public SocksShutdownListener(String threadName) {
        this.threadName = threadName;
    }

    private Socket in, out;

    @Override
    public void onStreamStart() {
        if (threadName != null) {
            Thread thread = Thread.currentThread();
            thread.setName(threadName);
        }
    }

    @Override
    public synchronized void onShutdownInput(Socket socket) {
        if (socket == out) {
            log.debug("onShutdownInput close socket: {}", socket);
            IOUtils.closeQuietly(socket);
        } else {
            in = socket;
        }
    }

    @Override
    public synchronized void onShutdownOutput(Socket socket) {
        if (socket == in) {
            log.debug("onShutdownOutput close socket: {}", socket);
            IOUtils.closeQuietly(socket);
        } else {
            out = socket;
        }
    }

}
