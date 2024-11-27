package cn.banny.rp.server;

import org.apache.commons.io.IOUtils;
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

    private final BlockingQueue<Integer> queue = new LinkedBlockingQueue<>();
	
	public ProxyPipedInputStream(int soTimeout) {
		super();
		
		this.soTimeout = soTimeout;
	}

    final void writeData(byte[] data, int offset, int length) {
        if (data == null) {
            throw new NullPointerException();
        }
        if(offset < 0 || length < 0 || length > data.length - offset) {
            throw new IndexOutOfBoundsException("offset=" + offset + ", length=" + length + ", data.length=" + data.length);
        }
        try {
            for (int i = 0; i < length; i++) {
                queue.put(data[i + offset] & 0xff);
            }
        } catch(Exception e) {
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
        try {
            closeRequested = true;
            queue.put(IOUtils.EOF);
        } catch (InterruptedException e) {
            throw new IOException("close", e);
        }
    }

    @Override
    public int available() {
        int size = queue.size();
        if (size == 0) {
            return 0;
        }
        return size - (closeRequested ? 1 : 0);
    }

    private boolean eof;

    @Override
	public int read() throws IOException {
        if (eof) {
            throw new EOFException();
        }
        long start = System.currentTimeMillis();
        while (true) {
            try {
                Integer b = queue.poll(soTimeout < 1 ? 1000 : soTimeout, TimeUnit.MILLISECONDS);
                if (b != null) {
                    if (b == IOUtils.EOF) {
                        eof = true;
                    }
                    return b;
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
