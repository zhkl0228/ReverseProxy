package cn.banny.rp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

/**
 * @author zhkl0228
 *
 */
public class RequestConnect {

	private static final Logger log = LoggerFactory.getLogger(RequestConnect.class);
	
	private final String host;
	private final int port;
	private final int timeout;
	private final boolean keepAlive;
	private final boolean oobInline;
	private final int receiveBufferSize;
	private final boolean reuseAddress;
	private final int sendBufferSize;
	private final boolean tcpNoDelay;
	private final int trafficClass;

	private RequestConnect(String host, int port, int timeout,
			boolean keepAlive, boolean oobInline, int receiveBufferSize,
			boolean reuseAddress, int sendBufferSize, boolean tcpNoDelay,
			int trafficClass) {
		super();
		this.host = host;
		this.port = port;
		this.timeout = timeout;
		this.keepAlive = keepAlive;
		this.oobInline = oobInline;
		this.receiveBufferSize = receiveBufferSize;
		this.reuseAddress = reuseAddress;
		this.sendBufferSize = sendBufferSize;
		this.tcpNoDelay = tcpNoDelay;
		this.trafficClass = trafficClass;
	}

	private InetAddress address;
	
	public static RequestConnect parseRequestConnect(ByteBuffer in) throws IOException {
		RequestConnect connect = new RequestConnect(ReverseProxy.readUTF(in), in.getShort() & 0xFFFF, in.getInt(), in.get() == 1, in.get() == 1, in.getInt(), in.get() == 1, in.getInt(), in.get() == 1, in.get() & 0xFF);
		int size;
		if(in.hasRemaining() &&
				(size = (in.get() & 0xFF)) > 0) {
			byte[] address = new byte[size];
			in.get(address);
			connect.address = InetAddress.getByAddress(address);
		}
		return connect;
	}
	
	public InetSocketAddress createInetSocketAddress() {
		if (address != null) {
			log.debug("createInetSocketAddress address=" + address + ", port=" + port);
			return new InetSocketAddress(address, port);
		}

		log.debug("createInetSocketAddress host=" + host + ", port=" + port);
		return new InetSocketAddress(host, port);
	}

	public int getTimeout() {
		return timeout;
	}

	public boolean isKeepAlive() {
		return keepAlive;
	}

	public boolean isOobInline() {
		return oobInline;
	}

	public int getReceiveBufferSize() {
		return receiveBufferSize;
	}

	public boolean isReuseAddress() {
		return reuseAddress;
	}

	public int getSendBufferSize() {
		return sendBufferSize;
	}

	public boolean isTcpNoDelay() {
		return tcpNoDelay;
	}

	public int getTrafficClass() {
		return trafficClass;
	}

	public String getHost() {
		return host;
	}

	public int getPort() {
		return port;
	}
}
