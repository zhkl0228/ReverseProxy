package cn.banny.rp.socks.bio;

import cn.banny.rp.ReverseProxy;
import cn.banny.rp.forward.StreamSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SocksShutdownListener implements ShutdownListener {

    private static final Logger log = LoggerFactory.getLogger(SocksShutdownListener.class);

    private final String threadName;

    public SocksShutdownListener(String threadName) {
        this.threadName = threadName;
    }

    private StreamSocket in, out;

    @Override
    public void onStreamStart() {
        if (threadName != null) {
            Thread thread = Thread.currentThread();
            thread.setName(threadName);
        }
    }

    @Override
    public boolean needShutdown() {
        return true;
    }

    @Override
    public void onStreamEnd() {
    }

    @Override
    public synchronized void onShutdownInput(StreamSocket socket) {
        if (socket == out) {
            log.debug("onShutdownInput close socket: {}", socket);
            ReverseProxy.closeQuietly(socket);
        } else {
            in = socket;
        }
    }

    @Override
    public synchronized boolean onShutdownOutput(StreamSocket socket) {
        if (socket == in) {
            log.debug("onShutdownOutput close socket: {}", socket);
            ReverseProxy.closeQuietly(socket);
            return true;
        } else {
            out = socket;
            return false;
        }
    }

}
