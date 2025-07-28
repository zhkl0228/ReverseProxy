package cn.banny.rp.server.forward;

import cn.banny.rp.ReverseProxy;
import cn.banny.rp.forward.ForwarderListener;
import cn.banny.rp.forward.RouteForwarder;
import cn.banny.rp.server.AbstractRoute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.EOFException;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author zhkl0228
 *
 */
public class AIORouteForwarder extends AbstractRouteForwarder implements RouteForwarder, CompletionHandler<Integer, ByteBuffer> {
	
	private static final Logger log = LoggerFactory.getLogger(AIORouteForwarder.class);
	
	private final AsynchronousSocketChannel socket;

	public AIORouteForwarder(AsynchronousSocketChannel socket, ForwarderListener forwarderListener, AbstractRoute route,
							 String host, int port) {
		super(forwarderListener, route, host, port);
		
		this.writeBuffer = ByteBuffer.allocateDirect(1024 * 32); // 32K buffer
		this.socket = socket;
	}

	/* (non-Javadoc)
	 * @see cn.banny.rp.ReverseProxyReceiver#parseRequestConnectResponse(java.net.InetAddress, int)
	 */
	@Override
	public void parseRequestConnectResponse(InetAddress localAddr, int localPort) {
		ByteBuffer readBuffer = ByteBuffer.allocateDirect(1024 * 10);
		socket.read(readBuffer, readBuffer, this);
		
		forwarderListener.notifyConnectSuccess(this, localAddr, localPort);
	}
	
	private final Queue<ByteBuffer> bufferQueue = new LinkedBlockingQueue<>();
	private final ReentrantLock writeLock = new ReentrantLock();
	private final ByteBuffer writeBuffer;
	
	@Override
	public void writeData(ByteBuffer buffer) {
		try {
			writeLock.lock();
			bufferQueue.offer(buffer);

			try {
				processWrite(true);
			} catch(BufferOverflowException e) {
				failed(new IOException("writeBuffer=" + writeBuffer + ", buffer=" + buffer, e), null);
			}
		} finally {
			writeLock.unlock();
		}
	}
	
	private boolean writing;

	private void processWrite(boolean checkWrite) {
		try {
			writeLock.lock();
			if(checkWrite && writing) {
				return;
			}

			processWrite(bufferQueue, writeBuffer);
			writing = true;
			writeBuffer.flip();
			socket.write(writeBuffer, writeBuffer, new CompletionHandler<Integer, ByteBuffer>() {
				@Override
				public void completed(Integer result, ByteBuffer attachment) {
					if(writeBuffer.hasRemaining() || !bufferQueue.isEmpty()) {
						writeBuffer.compact();
						processWrite(false);
						return;
					}

					writeBuffer.compact();
					writing = false;
				}
				@Override
				public void failed(Throwable exc, ByteBuffer attachment) {
					AIORouteForwarder.this.failed(exc, attachment);
				}
			});
		} finally {
			writeLock.unlock();
		}
	}

	static void processWrite(Queue<ByteBuffer> bufferQueue, ByteBuffer writeBuffer) {
		ByteBuffer bb;
		while((bb = bufferQueue.peek()) != null) {
			if(writeBuffer.remaining() < bb.remaining()) {
				break;
			}

			writeBuffer.put(bb);
			bufferQueue.poll();
		}

		log.debug("processWrite writeBuffer={}, queueSize={}", writeBuffer, bufferQueue.size());
	}

	/* (non-Javadoc)
	 * @see cn.banny.rp.ReverseProxyReceiver#close()
	 */
	@Override
	public void close() {
		ReverseProxy.closeQuietly(socket);
		
		route.unregisterReceiver(this);
		
		forwarderListener.notifyForwarderClosed(this);
	}

	@Override
	public void completed(Integer result, ByteBuffer attachment) {
		if(result == -1) {
			failed(new EOFException(), attachment);
			return;
		}
		
		if(result < 1) {
			socket.read(attachment, attachment, this);
			return;
		}
		
		attachment.flip();
		try {
			requestReadData(attachment);
		} finally {
			attachment.clear();
			socket.read(attachment, attachment, this);
		}
	}

	@Override
	public void failed(Throwable exc, ByteBuffer attachment) {
		if(log.isDebugEnabled()) {
			log.debug(exc.getMessage(), exc);
		}
		
		requestClosePeer();
	}

}
