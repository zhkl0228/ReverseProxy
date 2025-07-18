package cn.banny.rp.server.forward;

import cn.banny.rp.ReverseProxy;
import cn.banny.rp.forward.PortForwarder;
import cn.banny.rp.forward.RouteForwarder;
import cn.banny.rp.io.ByteBufferPool;
import cn.banny.rp.io.MappedByteBufferPool;
import cn.banny.rp.io.NIOSocketSession;
import cn.banny.rp.io.NIOSocketSessionDispatcher;
import cn.banny.rp.server.AbstractRoute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.*;

/**
 * @author zhkl0228
 *
 */
class NIOPortForwarder extends AbstractPortForwarder implements
		PortForwarder, Runnable, NIOSocketSession {
	
	private static final Logger log = LoggerFactory.getLogger(NIOPortForwarder.class);
	
	private Selector selector;
	private ServerSocketChannel server;

	NIOPortForwarder(boolean bindLocal, int inPort, String outHost, int outPort, AbstractRoute route) {
		super(bindLocal, inPort, outHost, outPort, route);
	}
	
	private NIOSocketSessionDispatcher dispatcher;

	/* (non-Javadoc)
	 * @see cn.banny.rp.server.forward.PortForwarder#start()
	 */
	@Override
	public int start() throws IOException {
		canStop = false;
		selector = Selector.open();
		server = ServerSocketChannel.open();
		server.bind(createBindAddress());
		server.socket().setReuseAddress(true);
		server.configureBlocking(false);
		server.register(selector, SelectionKey.OP_ACCEPT, this);
		
		dispatcher = new NIOSocketSessionDispatcher(ByteBuffer.allocateDirect(1024 * 5));
		
		newThread(this).start();
		listenPort = server.socket().getLocalPort();
		return listenPort;
	}

	/* (non-Javadoc)
	 * @see cn.banny.rp.server.forward.PortForwarder#stop()
	 */
	@Override
	public void stop() {
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
			
			RouteForwarder forwarder = createForward(socket);
			forwards.put(forwarder.hashCode(), forwarder);
		} catch(IOException e) {
			log.info("processAccept server={}", server, e);
		}
	}
	
	private final ByteBufferPool bufferPool = new MappedByteBufferPool();

	private RouteForwarder createForward(SocketChannel socket) {
		return new NIORouteForwarder(selector, socket, this, route, outHost, outPort, bufferPool);
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
			stop();
		}
	}

}
