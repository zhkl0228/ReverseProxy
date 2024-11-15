package cn.banny.rp.server;

import cn.banny.rp.ReverseProxy;
import cn.banny.rp.ReverseProxyReceiver;
import cn.banny.rp.Route;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.*;
import java.nio.channels.SocketChannel;
import java.util.concurrent.TimeUnit;

/**
 * @author zhkl0228
 *
 */
public class RemoteSocket extends Socket implements ReverseProxyReceiver {

	private static final Logger log = LoggerFactory.getLogger(RemoteSocket.class);
	
	private ProxyPipedInputStream localIn;
	private OutputStream localOut;

	RemoteSocket(AbstractRoute route) {
		super();
		
		this.route = route;
	}
	
	@Override
	public synchronized void close() throws IOException {
		try {
			if(route == null ||
					isClosed()) {
				return;
			}
			
			ByteArrayOutputStream baos = null;
			DataOutputStream dos = null;
			try {
				baos = new ByteArrayOutputStream(8);
				dos = new DataOutputStream(baos);
				
				dos.writeByte(0x3);
				dos.writeInt(this.hashCode());
				sendRequest(baos.toByteArray());
			} finally {
				ReverseProxy.closeQuietly(baos);
				ReverseProxy.closeQuietly(dos);
			}
		} finally {
			closeAll();
		}
	}

	private final AbstractRoute route;

	public Route getRoute() {
		return route;
	}
	
	private InetSocketAddress endpoint;

	@Override
	public InetSocketAddress getDestAddress() {
		return endpoint;
	}

	@Override
	public void connect(SocketAddress endpoint, int timeout) throws IOException {
		this.endpoint = (InetSocketAddress) endpoint;
		
		if(route == null) {
			throw new IOException("Connect failed: route is null");
		}

		localIn = new ProxyPipedInputStream(getSoTimeout());
		
		localOut = new ProxyOutputStream(this);

		route.registerReceiver(this);
		byte[] requestConnect = createRequestConnect(this.endpoint, timeout);
		sendRequest(requestConnect);
		synchronized (this) {
			try {
				this.wait((timeout < 1 ? TimeUnit.SECONDS.toMillis(60) : timeout) + getSoTimeout() + TimeUnit.SECONDS.toMillis(3));
			} catch (InterruptedException e) {
				route.unregisterReceiver(this);
				closeAll();
				throw new IOException(e);
			}
		}
		
		if(ioException != null) {
			route.unregisterReceiver(this);
			closeAll();
			throw ioException;
		}
		if(localAddr == null) {
			route.unregisterReceiver(this);
			closeAll();
			throw new IOException("Connect failed: timeout = " + timeout);
		}
		
		connected = true;
	}
	
	private boolean connected;

	@Override
	public boolean isConnected() {
		return connected;
	}

	@Override
	public synchronized void setSoTimeout(int timeout) throws SocketException {
		super.setSoTimeout(timeout);
		
		if(localIn != null) {
			localIn.setSoTimeout(timeout);
		}
	}

	@Override
	public boolean isBound() {
		return localAddr != null;
	}

	@Override
	public void bind(SocketAddress bindpoint) {
		InetSocketAddress address = (InetSocketAddress) bindpoint;
		this.localAddr = address.getAddress();
		this.localPort = address.getPort();
	}

	@Override
	public SocketChannel getChannel() {
		throw new UnsupportedOperationException("getChannel");
	}

	@Override
	public void sendUrgentData(int data) {
		throw new UnsupportedOperationException("sendUrgentData");
	}

	@Override
	public void shutdownInput() throws IOException {
		ByteArrayOutputStream baos = null;
		DataOutputStream dos = null;
		try {
			baos = new ByteArrayOutputStream(8);
			dos = new DataOutputStream(baos);
			
			dos.writeByte(0xC);
			dos.writeInt(this.hashCode());
			dos.writeBoolean(true);
			sendRequest(baos.toByteArray());
		} finally {
			ReverseProxy.closeQuietly(baos);
			ReverseProxy.closeQuietly(dos);
		}
	}

	@Override
	public void shutdownOutput() throws IOException {
		ByteArrayOutputStream baos = null;
		DataOutputStream dos = null;
		try {
			baos = new ByteArrayOutputStream(8);
			dos = new DataOutputStream(baos);
			
			dos.writeByte(0xC);
			dos.writeInt(this.hashCode());
			dos.writeBoolean(false);
			sendRequest(baos.toByteArray());
		} finally {
			ReverseProxy.closeQuietly(baos);
			ReverseProxy.closeQuietly(dos);
		}
	}

	@Override
	public void setPerformancePreferences(int connectionTime, int latency,
			int bandwidth) {
		throw new UnsupportedOperationException("setPerformancePreferences");
	}

	@Override
	public void setSoLinger(boolean on, int linger) {
	}

	@Override
	public boolean isInputShutdown() {
		return false;
	}

	@Override
	public boolean isOutputShutdown() {
		return false;
	}
	
	@Override
	public InetAddress getLocalAddress() {
		return localAddr;
	}

	@Override
	public int getLocalPort() {
		return localPort;
	}

	@Override
	public InputStream getInputStream() throws IOException {
		if(localIn == null) {
			return super.getInputStream();
		}
		
		return localIn;
	}

	@Override
	public OutputStream getOutputStream() throws IOException {
		if(localOut == null) {
			return super.getOutputStream();
		}
		
		return localOut;
	}

	private byte[] createRequestConnect(InetSocketAddress endpoint, int timeout) throws IOException {
		ByteArrayOutputStream baos = null;
		DataOutputStream dos = null;
		try {
			baos = new ByteArrayOutputStream(64);
			dos = new DataOutputStream(baos);
			
			dos.writeByte(0x1);
			dos.writeInt(this.hashCode());
			dos.writeUTF(endpoint.getHostString());
			dos.writeShort(endpoint.getPort());
			dos.writeInt(timeout);
			dos.writeBoolean(getKeepAlive());
			dos.writeBoolean(getOOBInline());
			dos.writeInt(getReceiveBufferSize());
			dos.writeBoolean(getReuseAddress());
			dos.writeInt(getSendBufferSize());
			dos.writeBoolean(getTcpNoDelay());
			dos.writeByte(getTrafficClass());

			dos.writeByte(0); // disable remote peer dns resolve
			
			return baos.toByteArray();
		} finally {
			ReverseProxy.closeQuietly(baos);
			ReverseProxy.closeQuietly(dos);
		}
	}
	
	@Override
	public void parseClosed() {
		closeAll();
	}

	private IOException ioException;
	
	@Override
	public void parseException(IOException ioe) {
		ioException = ioe;
		
		synchronized (this) {
			notify();
		}
	}

	private InetAddress localAddr;
	private int localPort;

	@Override
	public void parseRequestConnectResponse(InetAddress localAddr, int localPort) {
		this.localAddr = localAddr;
		this.localPort = localPort;
		
		synchronized (this) {
			notify();
		}
	}

	@Override
	public void parseRequestCloseResponse() {
		closeAll();
	}

	private void closeAll() {
		if(route != null) {
			route.unregisterReceiver(this);
		}
		
		ReverseProxy.closeQuietly(localOut);
		ReverseProxy.closeQuietly(localIn);
		
		localOut = null;
		localIn = null;
		
		try {
			super.close();
		} catch(IOException e) {
			// ignore
		} finally {
			closed = true;
		}
	}
	
	private boolean closed;

	@Override
	public boolean isClosed() {
		return closed;
	}

	@Override
	public void parseReadData(byte[] data, int offset, int length) {
		try {
			localIn.writeData(data, offset, length);
		} catch (Exception e) {
			log.warn("parseReadData", e);
			ReverseProxy.closeQuietly(localIn);
		}
	}
	
	private void sendRequest(byte[] request) {
		route.sendRequest(request);
	}
	
	void sendData(byte[] data) throws IOException {
		ByteArrayOutputStream baos = null;
		DataOutputStream dos = null;
		try {
			baos = new ByteArrayOutputStream(data.length + 16);
			dos = new DataOutputStream(baos);
			
			dos.writeByte(0x2);
			dos.writeInt(this.hashCode());
			dos.writeInt(data.length);
			dos.write(data);

			route.addSendTraffic(getDestAddress(), data.length);
			
			sendRequest(baos.toByteArray());
		} finally {
			ReverseProxy.closeQuietly(baos);
			ReverseProxy.closeQuietly(dos);
		}
	}

	@Override
	public String toString() {
		if (isConnected()) {
            return "RemoteSocket[addr=" + endpoint.getAddress() +
                ",port=" + endpoint.getPort() +
                ",localport=" + localPort + "]";
		}
        return "Socket[unconnected]";
	}

	@Override
	public void shutdownHalf(boolean flag) {
		try {
			if(flag) {
				localOut.close();
			} else {
				localIn.close();
			}
		} catch(IOException e) {
			ReverseProxy.closeQuietly(this);
		}
	}

}
