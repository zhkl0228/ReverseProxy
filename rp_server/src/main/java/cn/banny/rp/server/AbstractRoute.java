package cn.banny.rp.server;

import cn.banny.rp.AbstractRouteContext;
import cn.banny.rp.ReverseProxy;
import cn.banny.rp.ReverseProxyReceiver;
import cn.banny.rp.Route;
import cn.banny.rp.Traffic;
import cn.banny.rp.auth.Auth;
import cn.banny.rp.auth.AuthHandler;
import cn.banny.rp.forward.PortForwarder;
import cn.banny.rp.server.forward.BIOPortForwarder;
import cn.banny.utils.IOUtils;
import com.alibaba.fastjson.JSON;
import com.fuzhu8.device.AndroidDevice;
import com.fuzhu8.device.android.Device;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;

/**
 * @author zhkl0228
 *
 */
public abstract class AbstractRoute extends AbstractRouteContext implements Route {
	
	private static final Logger log = LoggerFactory.getLogger(AbstractRoute.class);
	
	private final long startTime;
	private final AuthHandler authHandler;

	public AbstractRoute(AuthHandler authHandler) {
		super();
		
		this.startTime = System.currentTimeMillis();
		this.authHandler = authHandler;
	}

	@Override
	public Socket waitingConnectSocket() {
		return new SocketWrap(this);
	}

	@Override
	public long getStartTime() {
		return startTime;
	}

	/**
	 * 发送数据
	 * @param request request data
	 */
	final void sendRequest(byte[] request) {
		ByteBuffer buffer = ByteBuffer.allocate(request.length + 4);
		buffer.position(4);
		buffer.put(request);
		buffer.flip();
		sendRequest(buffer);
	}

	/**
	 * 发送数据
	 * @param request request data
	 */
	public void sendRequest(ByteBuffer request) {
		addReceiveTraffic(null, request.remaining() + 4);
		writeMessage(request);
	}

	protected abstract void writeMessage(ByteBuffer message);

	/**
	 * 注册ReverseProxyReceiver
	 * @param receiver the receiver
	 */
	public final void registerReceiver(ReverseProxyReceiver receiver) {
		ReverseProxyReceiver last = receivers.put(receiver.hashCode(), receiver);
		if (last != null) {
            log.warn("registerReceiver repeated receiver: {}, last={}", receiver, last);
		}
	}
	
	private Device device;
	
	@Override
	public Device getDevice() {
		return device;
	}
	
	void setDeviceData(byte[] zipDeviceData) {
		InputStream inputStream = null;
		GZIPInputStream gzip = null;
		try {
			inputStream = new ByteArrayInputStream(zipDeviceData);
			gzip = new GZIPInputStream(inputStream);
			
			device = JSON.parseObject(gzip, StandardCharsets.UTF_8, AndroidDevice.class);
		} catch(Throwable t) {
			if(log.isDebugEnabled()) {
				log.debug(t.getMessage(), t);
			}
		} finally {
			IOUtils.close(gzip);
			IOUtils.close(inputStream);
		}
	}

	/**
	 * 此路由收到消息
	 * @param type msg type
	 * @param in buffer
	 * @throws IOException error
	 */
	final void receivedMessage(int type, ByteBuffer in) throws IOException {
		int socket = in.getInt();
		ReverseProxyReceiver receiver = receivers.get(socket);
		if(receiver == null) {
            log.debug("receivedMessage receiver is null: 0x{}, type=0x{}", Integer.toHexString(socket), Integer.toHexString(type));
			return;
		}
		
		switch (type) {
		case 0x1:
			byte[] addrData = new byte[in.get()];
			in.get(addrData);
			InetAddress localAddr = InetAddress.getByAddress(addrData);
			int localPort = in.getShort() & 0xffff;
			receiver.parseRequestConnectResponse(localAddr, localPort);
			break;
		case 0x3:
			receiver.parseRequestCloseResponse();
			break;
		case 0x4:
			int length = in.getInt();
			addReceiveTraffic(receiver.getDestAddress(), length);
			
			try {
				receiver.parseReadData(in.array(), in.position(), length);
			} finally {
				in.position(in.position() + length);
			}
			break;
		case 0x5:
			receiver.parseClosed();
			break;
		case 0x6:
			IOException ioe = new IOException(ReverseProxy.readUTF(in));
			receiver.parseException(ioe);
			break;
		case 0xC:
			receiver.shutdownHalf(in.get() == 1);
			break;
		default:
			throw new IOException("Unsupported receivedMessage type: 0x" + Integer.toHexString(type).toUpperCase());
		}
	}
	
	private final Map<Integer, ReverseProxyReceiver> receivers = new ConcurrentHashMap<>();
	
	private ReverseProxyReceiver[] getRemoteSockets() {
		return receivers.values().toArray(new ReverseProxyReceiver[0]);
	}

	/**
	 * 取消注册ReverseProxyReceiver
	 * @param receiver the receiver
	 */
	public final void unregisterReceiver(ReverseProxyReceiver receiver) {
		ReverseProxyReceiver check = receivers.remove(receiver.hashCode());
		ReverseProxy.closeQuietly(check);
		
		Traffic traffic = socketTraffic.get(receiver.getDestAddress());
		if(traffic != null) {
			traffic.setOffline();
		}
	}
	
	private final List<PortForwarder> forwarders = new ArrayList<>();

	@Override
	public int startForward(int port, String remoteHost, int remotePort)
			throws IOException {
		if(remoteHost == null ||
                remoteHost.trim().isEmpty() ||
				remotePort < 1) {
			throw new IllegalArgumentException();
		}
		
		PortForwarder forwarder = startForward(port, remoteHost, remotePort, this);
		int bindPort;
		try {
			bindPort = forwarder.start();
		} catch(IOException e) {
			forwarder.stop();
			throw e;
		}
		synchronized (forwarders) {
			this.forwarders.add(forwarder);
		}
		if (authHandler != null) {
			authHandler.onPortForward(this, bindPort, forwarder);
		}
		return bindPort;
	}

	/**
	 * 启动端口转向
	 * @param port local listen port
	 * @param remoteHost remote host
	 * @param remotePort remote port
	 * @param route the route
	 * @return 端口转向
	 */
	private PortForwarder startForward(int port, String remoteHost,
			int remotePort, AbstractRoute route) {
		return new BIOPortForwarder(port, remoteHost, remotePort, route);
	}

	@Override
	public PortForwarder[] getForwarders() {
		synchronized (forwarders) {
			return forwarders.toArray(new PortForwarder[0]);
		}
	}

	public final void notifyRouteClosed() {
		synchronized (forwarders) {
			for(Iterator<PortForwarder> iterator = forwarders.iterator(); iterator.hasNext(); ) {
				iterator.next().stop();
				iterator.remove();
			}
		}
		for(ReverseProxyReceiver receiver : getRemoteSockets()) {
			ReverseProxy.closeQuietly(receiver);
		}

		if (authHandler != null) {
			authHandler.onRouteDisconnect(this);
		}
	}

	@Override
	public final Auth getAuth() {
		return getAuthInternal();
	}

	protected abstract Auth getAuthInternal();

	@Override
	public void deliverMessage(byte[] msg) throws IOException {
		ByteArrayOutputStream baos = null;
		DataOutputStream dos = null;
		try {
			baos = new ByteArrayOutputStream();
			dos = new DataOutputStream(baos);
			
			dos.writeByte(0x9);
			dos.writeInt(msg.length);
			dos.write(msg);
			sendRequest(baos.toByteArray());
		} finally {
			ReverseProxy.closeQuietly(baos);
			ReverseProxy.closeQuietly(dos);
		}
	}

	@Override
	public final String getAuthUser() {
		Auth auth = getAuth();
		return auth == null ? null : auth.getUsername();
	}

	@Override
	public int getConnectionSize() {
		return receivers.size();
	}
	
	int networkDelay = -1;

	@Override
	public int getNetworkDelay() {
		return networkDelay;
	}
	
	int version = 0x10000;

	@Override
	public int getVersion() {
		return version;
	}
	
	private long lastReceivedTime;

	void setLastReceivedTime(long lastReceivedTime) {
		this.lastReceivedTime = lastReceivedTime;
		
		RemoteAddressContext.obtain(getRemoteAddress().getHostString()); // keep remote address context alive
	}

	@Override
	public boolean isAlive() {
		return System.currentTimeMillis() - lastReceivedTime < TimeUnit.SECONDS.toMillis(11);
	}
	
	@Override
	public long getLastAlive() {
		return lastReceivedTime;
	}

	String extraData;

	@Override
	public String getExtraData() {
		return extraData;
	}

	private int lastNetworkHash;
	
	void checkNetworkChanged(RouteNetworkChangedListener listener, int networkHash) {
		boolean update = true;
		try {
			if(listener != null &&
					lastNetworkHash != networkHash) {
				update = listener.notifyNetworkChanged(this, networkHash);
			}
		} finally {
			if(update) {
				lastNetworkHash = networkHash;
			}
		}
	}

	String lbs;

	@Override
	public String getLbs() {
		return lbs;
	}

	boolean canChangeIp;

	@Override
	public boolean canChangeIp() {
		return canChangeIp;
	}

	@Override
	public boolean requestChangeIp() {
		if(!canChangeIp) {
			return false;
		}

		if (log.isDebugEnabled()) {
			log.debug("requestChangeIp", new Exception("route=" + this));
		}
		synchronized (this) {
			long currentTimeMillis = System.currentTimeMillis();
			Long last = getAttribute(LAST_REQUEST_CHANGE_IP_TIME_KEY, Long.class);
			if(last != null &&
					currentTimeMillis - last < TimeUnit.MINUTES.toMillis(1)) {
				return false;
			}
			
			setAttribute(LAST_REQUEST_CHANGE_IP_TIME_KEY, currentTimeMillis);
			sendRequest(new byte[] {
				0xB
			});
			return true;
		}
	}
	
	private long _sendTraffic, _receiveTraffic;
	private final Map<InetSocketAddress, Traffic> socketTraffic = new ConcurrentHashMap<>();

	private Traffic getSocketTraffic(InetSocketAddress address) {
        return socketTraffic.computeIfAbsent(address, Traffic::new);
	}
	public void addSendTraffic(InetSocketAddress address, long traffic) {
		_sendTraffic += traffic;
		
		if(address != null) {
			getSocketTraffic(address).addSendTraffic(traffic);
		}
	}
	private void addReceiveTraffic(InetSocketAddress address, long traffic) {
		_receiveTraffic += traffic;
		
		if(address != null) {
			getSocketTraffic(address).addReceiveTraffic(traffic);
		}
	}

	@Override
	public long getSendTraffic() {
		return _sendTraffic;
	}

	@Override
	public long getReceiveTraffic() {
		return _receiveTraffic;
	}

	@Override
	public Collection<Traffic> getTrafficDetail() {
		return socketTraffic.values();
	}

	@Override
	public Socket createRemoteSocket() {
		return new RemoteSocket(this);
	}

	protected String remoteIp;

	public void setRemoteIp(String remoteIp) {
		this.remoteIp = remoteIp;
	}

}
