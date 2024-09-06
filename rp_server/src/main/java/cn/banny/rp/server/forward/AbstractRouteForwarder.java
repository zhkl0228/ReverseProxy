package cn.banny.rp.server.forward;

import cn.banny.rp.ReverseProxy;
import cn.banny.rp.ReverseProxyReceiver;
import cn.banny.rp.forward.ForwarderListener;
import cn.banny.rp.forward.RouteForwarder;
import cn.banny.rp.server.AbstractRoute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.TimeUnit;

/**
 * @author zhkl0228
 *
 */
public abstract class AbstractRouteForwarder implements ReverseProxyReceiver, RouteForwarder {
	
	private static final Logger log = LoggerFactory.getLogger(AbstractRouteForwarder.class);
	
	final ForwarderListener forwarderListener;
	protected final AbstractRoute route;
	
	private final InetSocketAddress address;
	
	AbstractRouteForwarder(ForwarderListener forwarderListener,
			AbstractRoute route, String host, int port) {
		super();
		this.forwarderListener = forwarderListener;
		this.route = route;
		this.address = new InetSocketAddress(host, port);
		
		route.registerReceiver(this);
		route.sendRequest(createRequestConnect(host, port));
	}

	@Override
	public InetSocketAddress getDestAddress() {
		return address;
	}

	private ByteBuffer createRequestConnect(String host, int port) {
		ByteBuffer buffer = ByteBuffer.allocate(36 + host.length());
		buffer.order(ByteOrder.BIG_ENDIAN);
		buffer.position(4);
		buffer.put((byte) 0x1);
		buffer.putInt(this.hashCode());
		ReverseProxy.writeUTF(buffer, host);
		buffer.putShort((short) port);
		buffer.putInt((int) TimeUnit.MINUTES.toMillis(1)); // timeout
		buffer.put((byte) 1); // keepAlive
		buffer.put((byte) 0); // oobInline
		buffer.putInt(1024 * 5); // receiveBufferSize
		buffer.put((byte) 1); // reuseAddress
		buffer.putInt(1024 * 5); // sendBufferSize
		buffer.put((byte) 1); // tcpNoDelay
		buffer.put((byte) 0); // trafficClass

		buffer.put((byte) 0); // disable remote peer dns resolve

		buffer.flip();
		return buffer;
	}

	/* (non-Javadoc)
	 * @see cn.banny.rp.ReverseProxyReceiver#parseRequestCloseResponse()
	 */
	@Override
	public final void parseRequestCloseResponse() {
		ReverseProxy.closeQuietly(this);
	}

	/* (non-Javadoc)
	 * @see cn.banny.rp.ReverseProxyReceiver#parseReadData(byte[])
	 */
	@Override
	public final void parseReadData(byte[] data, int offset, int length) {
		writeData(ByteBuffer.wrap(data, offset, length));
	}

	/* (non-Javadoc)
	 * @see cn.banny.rp.ReverseProxyReceiver#parseClosed()
	 */
	@Override
	public final void parseClosed() {
		ReverseProxy.closeQuietly(this);
	}

	/* (non-Javadoc)
	 * @see cn.banny.rp.ReverseProxyReceiver#parseException(java.io.IOException)
	 */
	@Override
	public final void parseException(IOException ioe) {
		if(log.isDebugEnabled()) {
			log.debug(ioe.getMessage(), ioe);
		}
		ReverseProxy.closeQuietly(this);
	}

	/**
	 * 请求关闭远程连接
	 */
	final void requestClosePeer() {
		try {
			ByteBuffer buffer = ByteBuffer.allocate(12);
			buffer.order(ByteOrder.BIG_ENDIAN);
			buffer.position(4);
			buffer.put((byte) 0x3);
			buffer.putInt(this.hashCode());
			buffer.flip();
			route.sendRequest(buffer);
		} finally {
			ReverseProxy.closeQuietly(this);
		}
	}

	final void requestShutdownHalf(boolean flag) {
		/*try {
			ByteBuffer buffer = ByteBuffer.allocate(12);
			buffer.order(ByteOrder.BIG_ENDIAN);
			buffer.position(4);
			buffer.put((byte) 0xC);
			buffer.putInt(this.hashCode());
			buffer.put(flag ? 1 : (byte) 0);
			buffer.flip();
			route.sendRequest(buffer);
		} finally {
			ReverseProxy.closeQuietly(this);
		}*/
	}

	/**
	 * 读数据
	 * @param readData the data buffer
	 */
	final void requestReadData(ByteBuffer readData) {
		ByteBuffer data = ByteBuffer.allocate(readData.remaining() + 16);
		data.order(ByteOrder.BIG_ENDIAN);
		data.position(4);
		data.put((byte) 0x2);
		data.putInt(this.hashCode());
		int length = readData.remaining();
		data.putInt(length);
		data.put(readData);
		data.flip();

		route.addSendTraffic(getDestAddress(), length);
		route.sendRequest(data);
	}

	@Override
	public void checkForwarder() {
	}

	@Override
	public void shutdownHalf(boolean flag) {
		// ReverseProxy.closeQuietly(this);
	}

}
