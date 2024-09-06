package cn.banny.rp.client.nio;

import cn.banny.rp.MessageDeliver;
import cn.banny.rp.RequestConnect;
import cn.banny.rp.ReverseProxy;
import cn.banny.rp.client.AbstractReverseProxyClient;
import cn.banny.rp.client.ReverseProxyClient;
import cn.banny.rp.client.SocketProxy;
import cn.banny.rp.io.ByteBufferPool;
import cn.banny.rp.io.MappedByteBufferPool;
import cn.banny.rp.io.NIOSocketSession;
import cn.banny.rp.io.NIOSocketSessionDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * @author zhkl0228
 *
 */
public class NIOReverseProxyClient extends AbstractReverseProxyClient implements Runnable, MessageDeliver, NIOSocketSession, ReverseProxyClient {
	
	private static final Logger log = LoggerFactory.getLogger(NIOReverseProxyClient.class);
	
	private final NIOSocketSessionDispatcher dispatcher;

	@SuppressWarnings("unused")
	public NIOReverseProxyClient(String host, int port) {
		this(host, port, null);
	}
	
	/**
	 * 每个封包最大10M，写数据最大1M
	 * @param host 反向服务器主机
	 * @param port 反向服务器端口
	 * @param extraData 额外携带数据
	 */
	public NIOReverseProxyClient(String host, int port, String extraData) {
		super(host, port, extraData, 1024 * 1024, 1024 * 1024);
		
		dispatcher = new NIOSocketSessionDispatcher(ByteBuffer.allocateDirect(1024 * 16));
	}
	
	/* (non-Javadoc)
	 * @see cn.banny.rp.client.IReverseProxyClient#login(java.lang.String, java.lang.String, int)
	 */
	@Override
	public void login(String username, String password, int aliveTimeMillis) {
		this.authResult = null;
		this.username = username;
		this.password = password;
		this.aliveTimeMillis = aliveTimeMillis;
		
		this.requestedLogin = true;
	}

	/* (non-Javadoc)
	 * @see cn.banny.rp.client.IReverseProxyClient#isConnected()
	 */
	@Override
	public boolean isConnected() {
		return session != null;
	}

	private boolean canStop;
	
	private SocketChannel session;
	private Selector selector;
	
	/* (non-Javadoc)
	 * @see cn.banny.rp.client.IReverseProxyClient#initialize()
	 */
	@Override
	public void initialize() throws Exception {
		this.selector = Selector.open();
		
		canStop = false;

		Thread thread = new Thread(this);
		thread.setDaemon(true);
		thread.start();
	}

	/* (non-Javadoc)
	 * @see cn.banny.rp.client.IReverseProxyClient#destroy()
	 */
	@Override
	public void destroy() {
		canStop = true;

		wakeUp();
		if(selector != null) {
			ReverseProxy.closeQuietly(selector);
			selector = null;
		}
		closeSession();
	}
	
	@Override
	protected void closeSession() {
		if (session != null) {
			SelectionKey key = session.keyFor(selector);
			if(key != null) {
				key.cancel();
			}
			ReverseProxy.closeQuietly(session);
			notifyClosed(session);
		}
	}

	@Override
	public void run() {
		while(!canStop) {
			try {
				long currentTimeMillis;// = System.currentTimeMillis();
				// checkNetworkChange(currentTimeMillis);
				
				SocketChannel session = this.session;
				if(session == null) {
					if(canConnectReverseProxyServer()) {
						connectReverseProxyServer();
					}
					if(this.session == null) {
						try { TimeUnit.SECONDS.sleep(1); } catch(Exception ignored) {}
					} else {
						try { TimeUnit.MILLISECONDS.sleep(100); } catch(Exception ignored) {}
					}
					continue;
				}
				
				processPacketQueue(session);
				
				int count = this.selector.select(500);
				if(count > 0) {
					Set<SelectionKey> set = selector.selectedKeys();
					dispatcher.dispatch(set);
				}
				
				currentTimeMillis = System.currentTimeMillis();
				checkSession(session, currentTimeMillis, writeBuffer);
				
				if(lastSync > 0 &&
						(currentTimeMillis < lastSync || currentTimeMillis - lastSync > TimeUnit.SECONDS.toMillis(30))) {
					closeSession();
				}
			} catch(Exception t) {
				if(log.isDebugEnabled()) {
					log.debug(t.getMessage(), t);
				}
				try { TimeUnit.SECONDS.sleep(1); } catch(Exception ignored) {}
			}
		}
	}

	@Override
	protected void wakeUp() {
		if (selector != null) {
			selector.wakeup();
		}
	}

	@Override
	public void processConnect(SocketChannel session) {
		throw new UnsupportedOperationException("processConnect");
	}
	
	@Override
	public void processRead(SocketChannel session, ByteBuffer buffer, SelectionKey key) {
		packetBuffer.put(buffer);
		
		processPacket(session);
	}

	@Override
	public void processWrite(SocketChannel session, SelectionKey key) throws IOException {
		writeBuffer.flip();
		session.write(writeBuffer);
		
		try {
			if(writeBuffer.hasRemaining()) {
				key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
				return;
			}
			if(key != null) {
				key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);
			}
		} finally {
			writeBuffer.compact();

			if(log.isDebugEnabled()) {
				log.debug("processWrite writeBuffer=" + writeBuffer);
			}
		}
	}

	@Override
	public void sendResponse(SocketChannel session, ByteBuffer writeBuffer) throws IOException {
		writeBuffer.mark();
		writeBuffer.putInt(writeBuffer.remaining() - 4);
		writeBuffer.reset();
		
		if(log.isDebugEnabled()) {
			writeBuffer.mark();
			writeBuffer.getInt(); // size
			byte[] data = new byte[writeBuffer.remaining()];
			writeBuffer.get(data);
			writeBuffer.reset();
			ReverseProxy.inspect(data, "sendResponse session=" + session);
		}
		
		writeBuffer.position(writeBuffer.limit()).limit(writeBuffer.capacity());
		
		SelectionKey key = session.keyFor(selector);
		processWrite(session, key);
	}

	@Override
	public void notifySessionClosed(SocketChannel session, int socket, Throwable throwable)
			throws IOException {
		super.notifySessionClosed(session, socket, throwable);
	}

	@Override
	public void sendException(SocketChannel session, int socket, Throwable t) {
		super.sendException(session, socket, t);
	}

	private void connectReverseProxyServer() throws IOException {
		SocketChannel socketChannel = null;
		SelectionKey key = null;
		try {
			socketChannel = SocketChannel.open();
			socketChannel.configureBlocking(true);
			Socket socket = socketChannel.socket();
			socket.setKeepAlive(true);
			
			InetSocketAddress addr = new InetSocketAddress(host, port);
			if(addr.isUnresolved()) {
				throw new IOException("Unresolved host: " + host);
			}
			socketChannel.connect(addr);
			socketChannel.configureBlocking(false);
			key = socketChannel.register(selector, SelectionKey.OP_READ, this);
			sessionOpened(socketChannel);
		} catch(IOException e) {
			ReverseProxy.closeQuietly(socketChannel);
			throw e;
		} catch(Throwable e) {
			if(key != null) {
				key.cancel();
			}
			ReverseProxy.closeQuietly(socketChannel);
			throw new IOException(e);
		}
	}

	@Override
	public void notifyClosed(SelectableChannel session) {
		if(this.session == session) {
			SelectionKey key = this.session.keyFor(selector);
			if(key != null) {
				key.cancel();
			}
			ReverseProxy.closeQuietly(session);
			this.session = null;
			
			closeAllSocketProxies();
			
			if(authListener != null) {
				authListener.onDisconnect(this, this.authResult);
			}
		}
	}

	private void sessionOpened(SocketChannel session) {
		if(this.session == null) {
			this.session = session;
			this.lastAliveTime = System.currentTimeMillis();
			this.lastSync = System.currentTimeMillis();
			writeBuffer.clear();
			
			requestAuth(session);
		}
	}

	@Override
	public void processException(SelectableChannel session, Throwable cause) {
		if(log.isDebugEnabled()) {
			log.debug(cause.getMessage(), cause);
		}
	}
	
	private final ByteBufferPool bufferPool = new MappedByteBufferPool();

	@Override
	protected SocketProxy createSocketProxy(SocketChannel route, int socket, RequestConnect connect) {
		return new NIOSocketProxy(route, socket, connect, this, this.selector, writeBuffer, bufferPool);
	}

	@Override
	public void processAccept(ServerSocketChannel server) {
		throw new UnsupportedOperationException("processAccept");
	}

	@Override
	public boolean isDead() {
		return this.session == null && !canConnectReverseProxyServer();
	}

}
