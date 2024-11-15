package cn.banny.rp.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * @author zhkl0228
 *
 */
public class ProxyPipedInputStream extends InputStream {

    private static final Logger log = LoggerFactory.getLogger(ProxyPipedInputStream.class);

    private final BlockingQueue<Byte> queue = new LinkedBlockingQueue<>();
	
	public ProxyPipedInputStream(int soTimeout) {
		super();
		
		this.soTimeout = soTimeout;
	}

    final void writeData(byte[] data, int offset, int length) {
        try {
            for (int i = 0; i < length; i++) {
                queue.put(data[i + offset]);
            }
        } catch(InterruptedException e) {
            log.warn("writeData", e);
        }
    }

	private int soTimeout;

	public void setSoTimeout(int timeout) {
		this.soTimeout = timeout;
	}
	
	private boolean closeRequested;

	@Override
	public void close() throws IOException {
        log.debug("close");
		closeRequested = true;
        try {
            queue.put((byte) -1);
        } catch (InterruptedException e) {
            throw new IOException("close", e);
        }
    }

    @Override
    public int available() throws IOException {
        return super.available();
    }

    @Override
	public int read() throws IOException {
        if (closeRequested) {
            throw new IOException("Stream closed");
        }

        long start = System.currentTimeMillis();
        while (true) {
            try {
                Byte b = queue.poll(soTimeout < 1 ? 1000 : soTimeout, TimeUnit.MILLISECONDS);
                if (closeRequested) {
                    throw new EOFException();
                }
                if (b != null) {
                    return b & 0xff;
                }
            	if(soTimeout > 0 &&
            			System.currentTimeMillis() - start >= soTimeout) {
            		throw new SocketTimeoutException();
            	}
            } catch (InterruptedException ex) {
                throw new java.io.InterruptedIOException(ex.getMessage());
            }
        }
	}

}
