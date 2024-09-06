package cn.banny.rp.server.socks;

import cn.banny.rp.ReverseProxy;
import cn.banny.rp.auth.AuthHandler;
import cn.banny.rp.io.NIOSocketSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.*;

/**
 * @author zhkl0228
 *
 */
class NIOSocksHandler extends AbstractSocksHandler<SocketChannel> implements NIOSocketSession {
	
	private static final Logger log = LoggerFactory.getLogger(NIOSocksHandler.class);
	
	private final Selector selector;
	private final SocketChannel socket;

	NIOSocksHandler(NIOProxyServer proxyServer, Selector selector, SocketChannel socket, AuthHandler authHandler, boolean supportV4) {
		super(proxyServer, authHandler, supportV4);
		this.selector = selector;
		this.socket = socket;
	}

	/* (non-Javadoc)
	 * @see cn.banny.rp.io.NIOSocketSession#processRead(java.nio.channels.SocketChannel, java.nio.ByteBuffer)
	 */
	@Override
	public void processRead(SocketChannel session, ByteBuffer readBuffer, SelectionKey key) {
		readBuffer.order(ByteOrder.BIG_ENDIAN);
		
		parseReadBuffer(readBuffer, session);
	}

	@Override
	protected void beforeForward(SocketChannel socket) {
		super.beforeForward(socket);

		SelectionKey key = socket.keyFor(selector);
		if(key != null) {
			key.interestOps(key.interestOps() & ~SelectionKey.OP_READ);
		}
	}

	/* (non-Javadoc)
	 * @see cn.banny.rp.io.NIOSocketSession#processWrite(java.nio.channels.SocketChannel, java.nio.channels.SelectionKey)
	 */
	@Override
	public void processWrite(SocketChannel session, SelectionKey key) {
		throw new UnsupportedOperationException("processWrite");
	}

	/* (non-Javadoc)
	 * @see cn.banny.rp.io.NIOSocketSession#processConnect(java.nio.channels.SocketChannel)
	 */
	@Override
	public void processConnect(SocketChannel session) {
		throw new UnsupportedOperationException("processConnect");
	}

	/* (non-Javadoc)
	 * @see cn.banny.rp.io.NIOSocketSession#processAccept(java.nio.channels.ServerSocketChannel)
	 */
	@Override
	public void processAccept(ServerSocketChannel server) {
		throw new UnsupportedOperationException("processAccept");
	}

	/* (non-Javadoc)
	 * @see cn.banny.rp.io.NIOSocketSession#processException(java.nio.channels.NetworkChannel, java.lang.Throwable)
	 */
	@Override
	public void processException(SelectableChannel session, Throwable cause) {
		if(log.isDebugEnabled()) {
			log.debug(cause.getMessage(), cause);
		}
		ReverseProxy.closeQuietly(socket);
	}

	/* (non-Javadoc)
	 * @see cn.banny.rp.io.NIOSocketSession#notifyClosed(java.nio.channels.NetworkChannel)
	 */
	@Override
	public void notifyClosed(SelectableChannel session) {
		ReverseProxy.closeQuietly(socket);
	}

	@Override
	protected void writeData(ByteBuffer buffer) {
		try {
			while (buffer.hasRemaining()) {
				socket.write(buffer);
			}
		} catch(Throwable t) {
			failed(t, null);
		}
	}

}
