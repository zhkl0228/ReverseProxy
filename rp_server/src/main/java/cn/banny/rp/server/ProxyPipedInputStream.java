package cn.banny.rp.server;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.lang.reflect.Field;
import java.net.SocketTimeoutException;

/**
 * @author zhkl0228
 *
 */
public class ProxyPipedInputStream extends PipedInputStream {
	
	public ProxyPipedInputStream(PipedOutputStream src, int timeout) throws IOException {
		super(src);
		
		this.soTimeout = timeout;
	}

	private int soTimeout;

	public void setSoTimeout(int timeout) {
		this.soTimeout = timeout;
	}

	private boolean getBool(String name) {
		try {
			Field field = PipedInputStream.class.getDeclaredField(name);
			field.setAccessible(true);
			return field.getBoolean(this);
		} catch(Exception e) {
			e.printStackTrace();
			return false;
		}
	}
	
	private Thread getWriteSide() {
		try {
			Field field = PipedInputStream.class.getDeclaredField("writeSide");
			field.setAccessible(true);
			return (Thread) field.get(this);
		} catch(Exception e) {
			e.printStackTrace();
			return null;
		}
	}
	
	private void setReadSide(Thread thread) {
		try {
			Field field = PipedInputStream.class.getDeclaredField("readSide");
			field.setAccessible(true);
			field.set(this, thread);
		} catch(Exception e) {
			e.printStackTrace();
		}
	}
	
	private boolean closeRequested;

	@Override
	public void close() throws IOException {
		if(in == -1) {
			super.close();
			return;
		}
		
		closeRequested = true;
	}

	@Override
	public synchronized int read() throws IOException {
		Thread writeSide;
		if (!getBool("connected")) {
            throw new IOException("Pipe not connected");
        } else if (getBool("closedByReader")) {
            throw new IOException("Pipe closed");
        } else if ((writeSide = getWriteSide()) != null && !writeSide.isAlive()
                   && !getBool("closedByWriter") && (in < 0)) {
            throw new IOException("Write end dead");
        }

        setReadSide(Thread.currentThread());
        int trials = 2;
        long start = System.currentTimeMillis();
        while (in < 0) {
            if (getBool("closedByWriter")) {
                /* closed by writer, return EOF */
                return -1;
            }
            if (((writeSide = getWriteSide()) != null) && (!writeSide.isAlive()) && (--trials < 0)) {
                throw new IOException("Pipe broken");
            }
            /* might be a writer waiting */
            notifyAll();
            try {
                wait(soTimeout < 1 ? 1000 : soTimeout);
                
            	if(soTimeout > 0 &&
            			System.currentTimeMillis() - start >= soTimeout) {
            		throw new SocketTimeoutException();
            	}
            } catch (InterruptedException ex) {
                throw new java.io.InterruptedIOException();
            }
        }
        int ret = buffer[out++] & 0xFF;
        if (out >= buffer.length) {
            out = 0;
        }
        if (in == out) {
            /* now empty */
            in = -1;
        }
        
        if(in == -1 &&
        		closeRequested) {
        	super.close();
        }

        return ret;
	}

}
