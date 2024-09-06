package cn.banny.rp.server.socks;

import cn.banny.rp.ReverseProxy;
import cn.banny.rp.forward.ForwarderListener;
import cn.banny.rp.forward.RouteForwarder;
import cn.banny.rp.io.ByteBufferPool;
import cn.banny.rp.io.MappedByteBufferPool;
import cn.banny.rp.io.NIOSocketSession;
import cn.banny.rp.io.NIOSocketSessionDispatcher;
import cn.banny.rp.server.AbstractRoute;
import cn.banny.rp.server.forward.NIORouteForwarder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;

/**
 * @author zhkl0228
 *
 */
public class NIOProxyServer extends AbstractProxyServer<SocketChannel> implements ProxyServer, Runnable, NIOSocketSession {
	
	private static final Logger log = LoggerFactory.getLogger(NIOProxyServer.class);
	
	private Selector selector;
	private ServerSocketChannel server;

	public NIOProxyServer(int port) {
		super(port);
	}
	
	private NIOSocketSessionDispatcher dispatcher;

	/* (non-Javadoc)
	 * @see cn.banny.rp.server.socks.ProxyServer#initialize()
	 */
	@Override
	public void initialize() throws Exception {
		canStop = false;
		selector = Selector.open();
		server = ServerSocketChannel.open();
		server.bind(new InetSocketAddress(port));
		server.socket().setReuseAddress(true);
		server.configureBlocking(false);
		server.register(selector, SelectionKey.OP_ACCEPT, this);
		
		dispatcher = new NIOSocketSessionDispatcher(ByteBuffer.allocateDirect(1024 * 5));
		
		newThread(this).start();
	}

	/* (non-Javadoc)
	 * @see cn.banny.rp.server.socks.ProxyServer#destroy()
	 */
	@Override
	public void destroy() {
		ReverseProxy.closeQuietly(server);
		for(RouteForwarder forwarder : getForwarders()) {
			ReverseProxy.closeQuietly(forwarder);
		}
		selector.wakeup();
		ReverseProxy.closeQuietly(selector);
		canStop = true;
	}
	
	private boolean canStop;

	@Override
	public void run() {
		while(!canStop) {
			checkSelector(selector, dispatcher);
		}
	}

	@Override
	public void processRead(SocketChannel session, ByteBuffer buffer, SelectionKey key) {
		throw new UnsupportedOperationException("processRead");
	}

	@Override
	public void processWrite(SocketChannel session, SelectionKey key) {
		throw new UnsupportedOperationException("processWrite");
	}

	@Override
	public void processConnect(SocketChannel session) {
		throw new UnsupportedOperationException("processConnect");
	}

	@Override
	public void processAccept(ServerSocketChannel server) {
		try {
			SocketChannel socket = server.accept();
			socket.configureBlocking(false);
			
			NIOSocksHandler handler = new NIOSocksHandler(this, selector, socket, authHandler, supportV4);
			socket.register(selector, SelectionKey.OP_READ, handler);
		} catch(IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void processException(SelectableChannel session, Throwable cause) {
		if(log.isDebugEnabled()) {
			log.debug(cause.getMessage(), cause);
		}
	}

	@Override
	public void notifyClosed(SelectableChannel session) {
		if(session == server) {
			destroy();
		}
	}
	
	private final ByteBufferPool bufferPool = new MappedByteBufferPool();

	@Override
	protected RouteForwarder createForward(SocketChannel socket, String host, int port, AbstractRoute route, ForwarderListener listener) {
		return new NIORouteForwarder(selector, socket, listener, route, host, port, bufferPool);
	}

	@Override
	protected InetSocketAddress createClientAddress(SocketChannel socket) throws IOException {
		return (InetSocketAddress) socket.getRemoteAddress();
	}

}
