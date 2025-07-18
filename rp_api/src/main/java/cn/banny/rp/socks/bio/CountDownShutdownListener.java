package cn.banny.rp.socks.bio;

import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class CountDownShutdownListener implements ShutdownListener {

    private final CountDownLatch countDownLatch = new CountDownLatch(2);

    private final String threadName;

    public CountDownShutdownListener(String threadName) {
        this.threadName = threadName;
    }

    private String backupThreadName;

    @Override
    public void onStreamStart() {
        if (threadName != null) {
            Thread thread = Thread.currentThread();
            backupThreadName = thread.getName();
            thread.setName(threadName);
        }
    }

    @Override
    public void onShutdownInput(Socket socket) {
    }

    @Override
    public void onShutdownOutput(Socket socket) {
    }

    public void waitCountDown() throws InterruptedException, IOException {
        if (!countDownLatch.await(1, TimeUnit.DAYS)) {
            throw new IOException("Wait failed");
        }
    }

    @Override
    public boolean needShutdown() {
        return false;
    }

    @Override
    public void onStreamEnd() {
        if (backupThreadName != null) {
            Thread thread = Thread.currentThread();
            thread.setName(backupThreadName);
        }
        countDownLatch.countDown();
    }

}
