package cn.banny.rp.server.forward;

import cn.banny.rp.ReverseProxy;
import cn.banny.rp.forward.ForwarderListener;
import cn.banny.rp.forward.RouteForwarder;
import cn.banny.rp.io.ByteBufferPool;
import cn.banny.rp.io.NIOSocketSession;
import cn.banny.rp.server.AbstractRoute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.EOFException;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author zhkl0228
 *
 */
public class NIORouteForwarder extends AbstractRouteForwarder implements
		RouteForwarder, NIOSocketSession {
	
	private static final Logger log = LoggerFactory.getLogger(NIORouteForwarder.class);
	
	private final Selector selector;
	private final SocketChannel socket;
	private final ByteBufferPool bufferPool;
	private final ByteBuffer writeBuffer;

	public NIORouteForwarder(Selector selector, SocketChannel socket, ForwarderListener forwarderListener, AbstractRoute route,
							 String host, int port, ByteBufferPool bufferPool) {
		super(forwarderListener, route, host, port);
		
		this.selector = selector;
		this.socket = socket;
		this.bufferPool = bufferPool;
		this.writeBuffer = bufferPool.acquire(1024 * 32, true);
		this.writeBuffer.clear();
	}
	
	private int pendingRegister = -1;

	@Override
	public void checkForwarder() {
		super.checkForwarder();
		
		if(pendingRegister == -1) {
			return;
		}
		
		try {
			socket.register(selector, pendingRegister, this);
		} catch (ClosedChannelException e) {
			processException(socket, e);
		} finally {
			pendingRegister = -1;
		}
	}

	/* (non-Javadoc)
	 * @see cn.banny.rp.ReverseProxyReceiver#parseRequestConnectResponse(java.net.InetAddress, int)
	 */
	@Override
	public void parseRequestConnectResponse(InetAddress localAddr, int localPort) {
		forwarderListener.notifyConnectSuccess(this, localAddr, localPort);
		
		pendingRegister = SelectionKey.OP_READ;
		selector.wakeup();
	}
	
	private boolean closed;

	/* (non-Javadoc)
	 * @see java.io.Closeable#close()
	 */
	@Override
	public void close() {
		try {
			ReverseProxy.closeQuietly(socket);
			
			route.unregisterReceiver(this);
			
			forwarderListener.notifyForwarderClosed(this);
		} finally {
			closed = true;
			
			bufferPool.release(writeBuffer);
		}
	}

	private final Queue<ByteBuffer> bufferQueue = new LinkedBlockingQueue<>();
	private final ReentrantLock writeLock = new ReentrantLock();

	/* (non-Javadoc)
	 * @see cn.banny.rp.server.forward.RouteForwarder#writeData(java.nio.ByteBuffer)
	 */
	@Override
	public void writeData(ByteBuffer buffer) {
		if(closed) {
			return;
		}
		
		try {
			writeLock.lock();
			bufferQueue.offer(buffer);
			
			try {
				SelectionKey key = socket.keyFor(selector);
				processWrite(socket, key);
			} catch(IOException e) {
				processException(socket, e);
			} catch(BufferOverflowException e) {
				processException(socket, new IOException("writeBuffer=" + writeBuffer + ", buffer=" + buffer, e));
			}
		} finally {
			writeLock.unlock();
		}
	}

	@Override
	public void processRead(SocketChannel session, ByteBuffer buffer, SelectionKey key) {
		requestReadData(buffer);
	}

	@Override
	public void processWrite(SocketChannel session, SelectionKey key)
			throws IOException {
		try {
			writeLock.lock();
			ByteBuffer bb;
			while((bb = bufferQueue.peek()) != null) {
				if(writeBuffer.remaining() < bb.remaining()) {
					break;
				}
				
				writeBuffer.put(bb);
				bufferQueue.poll();
			}

			log.debug("processWrite writeBuffer=" + writeBuffer + ", queueSize=" + bufferQueue.size());
			writeBuffer.flip();
			session.write(writeBuffer);
			
			if(writeBuffer.hasRemaining() || !bufferQueue.isEmpty()) {
				writeBuffer.compact();
				key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
				return;
			}
			
			writeBuffer.compact();
			if(key != null) {
				key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);
			}
		} finally {
			writeLock.unlock();
		}
	}

	@Override
	public void processConnect(SocketChannel session) {
		throw new UnsupportedOperationException("processConnect");
	}

	@Override
	public void processAccept(ServerSocketChannel server) {
		throw new UnsupportedOperationException("processAccept");
	}

	@Override
	public void processException(SelectableChannel session, Throwable cause) {
		if(log.isDebugEnabled()) {
			log.debug(cause.getMessage(), cause);
		}
		
		if(cause instanceof EOFException) {
			requestShutdownHalf(false);
		}
	}

	@Override
	public void notifyClosed(SelectableChannel session) {
		requestClosePeer();
	}

}
