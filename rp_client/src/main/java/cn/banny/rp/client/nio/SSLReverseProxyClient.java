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
import cn.banny.utils.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.*;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * @author zhkl0228
 *
 */
public class SSLReverseProxyClient extends AbstractReverseProxyClient implements Runnable, MessageDeliver, NIOSocketSession, ReverseProxyClient {

	private static final Logger log = LoggerFactory.getLogger(SSLReverseProxyClient.class);

	private static final int BUFFER_SIZE = 1024 * 1024;

	private final NIOSocketSessionDispatcher dispatcher;

	private final SSLContext ctx;
	private final ByteBuffer encryptedIn;
	private final ByteBuffer encryptedOut;
	private final ByteBuffer decryptedIn;
	private final ByteBuffer decryptedOut;

	@SuppressWarnings("unused")
	public SSLReverseProxyClient(String host, int port) {
		this(host, port, null);
	}

	/**
	 * 每个封包最大10M，写数据最大1M
	 * @param host 反向服务器主机
	 * @param port 反向服务器端口
	 * @param extraData 额外携带数据
	 */
	public SSLReverseProxyClient(String host, int port, String extraData) {
		super(host, port, extraData, BUFFER_SIZE, BUFFER_SIZE);
		
		dispatcher = new NIOSocketSessionDispatcher(ByteBuffer.allocateDirect(1024 * 16));

		try (InputStream inputStream = getClass().getResourceAsStream("/client.jks")) {
			final String STORE_PASSWORD = "rp_pass";
			final String KEY_PASSWORD = "rp_pass";

			KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
			ks.load(inputStream, STORE_PASSWORD.toCharArray());

			KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
			kmf.init(ks, KEY_PASSWORD.toCharArray());

			ctx = SSLContext.getInstance("TLS");
			X509TrustManager tm = new X509TrustManager() {
				public void checkClientTrusted(X509Certificate[] certs, String authType) {
					if (log.isDebugEnabled()) {
                        log.debug("checkClientTrusted certs={}, authType={}", Arrays.toString(certs), authType);
					}
				}

				public void checkServerTrusted(X509Certificate[] certs, String authType) throws CertificateException {
					if (log.isDebugEnabled()) {
                        log.debug("checkServerTrusted certs={}, authType={}", Arrays.toString(certs), authType);
					}

					if (certs == null || certs.length == 0 || StringUtils.isEmpty(authType)) {
						throw new IllegalArgumentException("null or zero-length parameter");
					}

					try (InputStream inputStream = SSLReverseProxyClient.class.getResourceAsStream("/server.crt")) {
						CertificateFactory cf = CertificateFactory.getInstance("X.509");
						X509Certificate serverCert = (X509Certificate) cf.generateCertificate(inputStream);

						for (X509Certificate cert : certs) {
							cert.verify(serverCert.getPublicKey());
						}
					} catch (Exception e) {
						throw new CertificateException("error in validating certificate", e);
					}
				}

				public X509Certificate[] getAcceptedIssuers() {
					return new X509Certificate[0];
				}
			};
			ctx.init(kmf.getKeyManagers(), new TrustManager[]{tm}, null);

			decryptedIn = ByteBuffer.allocate(1024 * 16);
			decryptedOut = ByteBuffer.allocate(1024 * 16);
			encryptedIn = ByteBuffer.allocate(BUFFER_SIZE);
			encryptedOut = ByteBuffer.allocate(BUFFER_SIZE);
		} catch (NoSuchAlgorithmException | KeyManagementException | IOException | KeyStoreException | CertificateException | UnrecoverableKeyException e) {
			throw new IllegalStateException(e);
		}
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

	private boolean isHandshaking() {
		return engine.getHandshakeStatus() != SSLEngineResult.HandshakeStatus.FINISHED &&
				engine.getHandshakeStatus() != SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING;
	}
	
	@Override
	public void processRead(SocketChannel session, ByteBuffer buffer, SelectionKey key) throws IOException {
		if (engine.isInboundDone()) {
			return;
		}
		if (isHandshaking()) {
			doHandshake(session, key, buffer);
			return;
		}

		try {
			decryptedIn.clear();
			SSLEngineResult result = engine.unwrap(buffer, decryptedIn);
			decryptedIn.flip();
			if (log.isDebugEnabled()) {
                log.debug("processRead {} result={}, decryptedIn={}", session.socket().getRemoteSocketAddress(), result.getStatus(), decryptedIn);
			}
			switch (result.getStatus()) {
				case BUFFER_UNDERFLOW:
					return;
				case BUFFER_OVERFLOW:
					throw new BufferOverflowException();
				case CLOSED:
					session.socket().shutdownInput();
					break;
				case OK:
					break;
			}
			packetBuffer.put(decryptedIn);
		} catch (SSLException e) {
			throw new IOException(e);
		}
		
		processPacket(session);
	}

	private void doHandshake(SocketChannel sc, SelectionKey key, ByteBuffer buffer) throws IOException {
		if (buffer != null) {
			encryptedIn.put(buffer);
		}

		SSLEngineResult result;
		SSLEngineResult.HandshakeStatus status = engine.getHandshakeStatus();
		if (log.isDebugEnabled()) {
            log.debug("doHandshake status={}, key={}, buffer={}", status, key, buffer);
		}

		try {
			encryptedOut.flip();
			if (encryptedOut.hasRemaining()) {
				session.write(encryptedOut);
				return;
			}
		} finally {
			encryptedOut.compact();
		}

		if (key != null) {
			key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
		}

		// process the handshake status
		switch (status) {
			case NEED_TASK:
				if (log.isDebugEnabled()) {
                    log.debug("{} NEED_TASK", sc.socket().getRemoteSocketAddress());
				}
				// Run the delegated SSL/TLS tasks
				Runnable task;
				while ((task = engine.getDelegatedTask()) != null) {
					task.run();
				}
				return;
			case NEED_UNWRAP:
				try {
					encryptedIn.flip();
					if (!encryptedIn.hasRemaining()) {
						if(key != null) {
							key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);
						}
						return;
					}
					if(log.isDebugEnabled()) {
                        log.debug("doHandshake encryptedIn={}, decryptedIn={}", encryptedIn, decryptedIn);
					}

					result = engine.unwrap(encryptedIn, decryptedIn);
				} finally {
					encryptedIn.compact();
				}

				if(log.isDebugEnabled()) {
                    log.debug("doHandshake encryptedIn={}, decryptedIn={}", encryptedIn, decryptedIn);
				}
				break;
			case NEED_WRAP:
				if(log.isDebugEnabled()) {
                    log.debug("doHandshake encryptedOut={}, decryptedOut={}", encryptedOut, decryptedOut);
				}

				decryptedOut.flip();
				result = engine.wrap(decryptedOut, encryptedOut);
				decryptedOut.compact();

				if(log.isDebugEnabled()) {
                    log.debug("doHandshake encryptedOut={}, decryptedOut={}", encryptedOut, decryptedOut);
				}

				encryptedOut.flip();
				session.write(encryptedOut);
				encryptedOut.compact();

				if(log.isDebugEnabled()) {
                    log.debug("doHandshake encryptedOut={}, decryptedOut={}", encryptedOut, decryptedOut);
				}
				break;
			case FINISHED:
				if (log.isDebugEnabled()) {
                    log.debug("{} FINISHED", sc.socket().getRemoteSocketAddress());
				}
				return;
			case NOT_HANDSHAKING:
				if (log.isDebugEnabled()) {
                    log.debug("{} NOT_HANDSHAKING", sc.socket().getRemoteSocketAddress());
				}
				// handshake has been completed at this point, no need to
				// check the status of the SSLEngineResult;
				return;
			default:
				throw new IllegalStateException("status=" + status);
		}

		// Check the result of the preceding wrap or unwrap.
		switch (result.getStatus()) {
			case BUFFER_UNDERFLOW:
				// Return as we do not have enough data to continue processing
				// the handshake
                log.info("{} BUFFER_UNDERFLOW", sc.socket().getRemoteSocketAddress());
				return;
			case BUFFER_OVERFLOW:
                log.info("{} BUFFER_OVERFLOW", sc.socket().getRemoteSocketAddress());
				// Return as the encrypted buffer has not been cleared yet
				return;
			case CLOSED:
				if (log.isDebugEnabled()) {
                    log.debug("{} CLOSED", sc.socket().getRemoteSocketAddress());
				}
				if (engine.isOutboundDone()) {
					sc.socket().shutdownOutput();// stop sending
				}
				return;
			case OK:
				if (log.isDebugEnabled()) {
                    log.debug("{} OK", sc.socket().getRemoteSocketAddress());
				}
				// handshaking can continue.
				break;
		}
	}

	@Override
	public void processWrite(SocketChannel session, SelectionKey key) throws IOException {
		if (engine == null) {
			engine = ctx.createSSLEngine();
			engine.setUseClientMode(true);
			engine.beginHandshake();
		}
		if (isHandshaking()) {
			doHandshake(session, key, null);
			return;
		}

		writeBuffer.flip();
		if (writeBuffer.hasRemaining()) {
			SSLEngineResult result = engine.wrap(writeBuffer, encryptedOut);
			encryptedOut.flip();
			if (log.isDebugEnabled()) {
                log.debug("processWrite {} result={}, writeBuffer={}, encryptedOut={}", session.socket().getRemoteSocketAddress(), result.getStatus(), writeBuffer, encryptedOut);
			}
			switch (result.getStatus()) {
				case BUFFER_UNDERFLOW:
					// This shouldn't happen as we only call write() when there is
					// data to be written, throw an exception that will be handled
					// in the application layer.
					throw new BufferUnderflowException();
				case BUFFER_OVERFLOW:
					// This shouldn't happen if we flush data that has been wrapped
					// as we do in this implementation, throw an exception that will
					// be handled in the application layer.
					throw new BufferOverflowException();
				case CLOSED:
					// Trying to write on a closed SSLEngine, throw an exception
					// that will be handled in the application layer.
					throw new SSLException("SSLEngine is CLOSED");
				case OK:
					// Everything is good, everything is fine.
					break;
			}
		}
		session.write(encryptedOut);
		
		try {
			if(encryptedOut.hasRemaining()) {
				key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
				return;
			}
			if(key != null) {
				key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);
			}
		} finally {
			encryptedOut.compact();
			writeBuffer.compact();

			if(log.isDebugEnabled()) {
                log.debug("processWrite encryptedOut={}, writeBuffer={}", encryptedOut, writeBuffer);
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

	private SSLEngine engine;

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

			engine = null;
			decryptedOut.clear();
			decryptedIn.clear();
			encryptedOut.clear();
			encryptedIn.clear();
			
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
