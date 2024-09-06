package cn.banny.rp.client.nio;

import cn.banny.rp.RequestConnect;
import cn.banny.rp.ReverseProxy;
import cn.banny.rp.client.AbstractReverseProxyClient;
import cn.banny.rp.client.AbstractSocketProxy;
import cn.banny.rp.client.SocketProxy;
import cn.banny.rp.io.ByteBufferPool;
import cn.banny.rp.io.NIOSocketSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.EOFException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
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
class NIOSocketProxy extends AbstractSocketProxy implements NIOSocketSession, SocketProxy {
	
	private static final Logger log = LoggerFactory.getLogger(NIOSocketProxy.class);
	
	private final RequestConnect connect;
	private final AbstractReverseProxyClient client;
	private final Selector selector;
	private final ByteBuffer writeBuffer;
	private final ByteBufferPool bufferPool;
	
	NIOSocketProxy(SocketChannel route, int socket, RequestConnect connect, AbstractReverseProxyClient client, Selector selector, ByteBuffer writeBuffer, ByteBufferPool bufferPool) {
		super(route, socket, writeBuffer, client);

		this.connect = connect;
		this.client = client;
		this.selector = selector;
		this.bufferPool = bufferPool;
		this.writeBuffer = bufferPool.acquire(1024 * 20, true);
	}

	@Override
	public void processRead(SocketChannel session, ByteBuffer buffer, SelectionKey key) {
		messageReceived(session, buffer);
	}

	@Override
	public void processConnect(SocketChannel session) {
		try {
			session.finishConnect();
			session.register(selector, SelectionKey.OP_READ, this);
			Socket socket = session.socket();
			
			notifySocketConnected(socket);
		} catch (IOException e) {
			if(log.isDebugEnabled()) {
				log.debug(e.getMessage(), e);
			}
			
			client.sendException(route, socket, e);
		}
	}

	private SocketChannel session;
	
	@Override
	public void connect() throws IOException {
		SocketChannel session = null;
		SelectionKey key = null;
		try {
			InetSocketAddress addr = connect.createInetSocketAddress();
			if(addr.isUnresolved()) {
				throw new IOException("Unresolved host: " + connect.getHost());
			}
			
			session = SocketChannel.open();
			session.configureBlocking(false);
			Socket socket = session.socket();
			
			socket.setKeepAlive(connect.isKeepAlive());
			socket.setReceiveBufferSize(connect.getReceiveBufferSize());
			socket.setSendBufferSize(connect.getSendBufferSize());
			socket.setOOBInline(connect.isOobInline());
			socket.setReuseAddress(connect.isReuseAddress());
			socket.setTcpNoDelay(connect.isTcpNoDelay());
			socket.setTrafficClass(connect.getTrafficClass());
			
			key = session.register(selector, SelectionKey.OP_CONNECT, this);
			
			session.connect(addr);
			
			sessionOpened(session);
		} catch(IOException e) {
			if(key != null) {
				key.cancel();
			}
			ReverseProxy.closeQuietly(session);
			throw e;
		}
	}

	private void messageReceived(SocketChannel session, ByteBuffer readBuffer) {
		if (log.isDebugEnabled()) {
			readBuffer.mark();
			byte[] data = new byte[readBuffer.remaining()];
			readBuffer.get(data);
			readBuffer.reset();
			ReverseProxy.inspect(data, "messageReceived proxy=" + this + ", session=" + route);
		}

		try {
			packetWriteBuffer.mark();
			packetWriteBuffer.position(packetWriteBuffer.position() + 4);
			packetWriteBuffer.put((byte) 0x4);
			packetWriteBuffer.putInt(socket);
			packetWriteBuffer.putInt(readBuffer.remaining());
			packetWriteBuffer.put(readBuffer);
			packetWriteBuffer.limit(packetWriteBuffer.position()).reset();
			client.sendResponse(route, packetWriteBuffer);
		} catch (IOException e) {
			processException(session, e);
			
			close(true);
		}
	}
	
	private final Queue<ByteBuffer> bufferQueue = new LinkedBlockingQueue<>();
	private final ReentrantLock queueLock = new ReentrantLock();
	
	@Override
	public void writeData(ByteBuffer buffer) {
		if(closed) {
			log.warn("writeData closed: proxy=" + this + ", session=" + route);
			return;
		}

		if (buffer.remaining() > writeBuffer.capacity()) { // fail
			throw new IllegalStateException("writeData buffer overflow: " + buffer);
		}

		ByteBuffer copy = bufferPool.acquire(buffer.remaining(), false);
		copy.clear();
		copy.put(buffer);
		copy.flip();
		try {
			queueLock.lock();
			bufferQueue.offer(copy);
		} finally {
			queueLock.unlock();
		}
		
		try {
			SelectionKey key = session.keyFor(selector);
			processWrite(session, key);
		} catch(IOException e) {
			processException(session, e);
			
			close(true);
		} catch(BufferOverflowException e) {
			log.warn("writeBuffer=" + writeBuffer + ", buffer=" + buffer, e);
			processException(session, new IOException("writeBuffer=" + writeBuffer + ", buffer=" + buffer, e));
			
			close(true);
		}
	}

	@Override
	public void processWrite(SocketChannel session, SelectionKey key)
			throws IOException {
		try {
			queueLock.lock();
			ByteBuffer bb;
			while((bb = bufferQueue.peek()) != null) {
				if(writeBuffer.remaining() < bb.remaining()) {
					break;
				}

				writeBuffer.put(bb);
				bufferQueue.poll();
				bufferPool.release(bb);
			}
		} finally {
			queueLock.unlock();
		}

		log.debug("processWrite writeBuffer=" + writeBuffer + ", queueSize=" + bufferQueue.size() + ", poolSize=" + (bufferPool.totalMemory() / 1024) + "kb" + ", proxy=" + this + ", session=" + route);
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
	}
	
	private void sessionOpened(SocketChannel session) {
		if(this.session == null) {
			this.session = session;
			
			writeBuffer.clear();
			bufferQueue.clear();
		}
	}
	
	private boolean closed;

	@Override
	public void notifyClosed(SelectableChannel session) {
		if(this.session != session) {
			return;
		}

		try {
			close(true);
		} finally {
			closed = true;
			this.bufferPool.release(writeBuffer);
		}
	}

	@Override
	public void processException(SelectableChannel session, Throwable cause) {
		if(log.isDebugEnabled()) {
			log.debug(cause.getMessage(), cause);
		}
		
		this.exception = cause;
		
		if(cause instanceof EOFException) {//shutdownOutput for peer
			notifyClosed(session);
		}
	}
	
	private Throwable exception;

	@Override
	public void close(boolean notify) {
		if(session == null)	{
			return;
		}

		SelectionKey key = session.keyFor(selector);
		if(key != null) {
			key.cancel();
		}
		ReverseProxy.closeQuietly(session);
		session = null;
		try {
            if (notify) {
                client.notifySessionClosed(route, socket, exception);
            }
		} catch (IOException e) {
			log.debug("close", e);
		}
	}

	@Override
	public void processAccept(ServerSocketChannel server) {
		throw new UnsupportedOperationException("processAccept");
	}

	@Override
	public void shutdownHalf(boolean flag) {
		try {
			if(flag) {
				session.shutdownInput();
			} else {
				session.shutdownOutput();
			}
		} catch(IOException e) {
			processException(session, e);
			
			close(true);
		}
	}

	@Override
	public String toString() {
		Socket connectedSocket = getConnectedSocket();
		if (connectedSocket != null) {
			return String.valueOf(connectedSocket.getRemoteSocketAddress());
		}

		return String.valueOf(connect.createInetSocketAddress());
	}
}
