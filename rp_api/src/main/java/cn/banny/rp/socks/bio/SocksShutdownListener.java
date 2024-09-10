package cn.banny.rp.socks.bio;

import cn.banny.utils.IOUtils;
import cn.banny.utils.StringUtils;
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
        if (StringUtils.hasText(threadName)) {
            Thread thread = Thread.currentThread();
            thread.setName(threadName);
        }
    }

    @Override
    public synchronized void onShutdownInput(Socket socket) {
        if (socket == out) {
            log.debug("onShutdownInput close socket: {}", socket);
            IOUtils.close(socket);
        } else {
            in = socket;
        }
    }

    @Override
    public synchronized void onShutdownOutput(Socket socket) {
        if (socket == in) {
            log.debug("onShutdownOutput close socket: {}", socket);
            IOUtils.close(socket);
        } else {
            out = socket;
        }
    }

}
