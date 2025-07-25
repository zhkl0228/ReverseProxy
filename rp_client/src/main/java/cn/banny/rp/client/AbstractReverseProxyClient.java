package cn.banny.rp.client;

import cn.banny.rp.RequestConnect;
import cn.banny.rp.ReverseProxy;
import cn.banny.rp.auth.AuthResult;
import cn.banny.rp.forward.ForwarderType;
import cn.banny.rp.handler.ExtDataHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SocketChannel;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;


/**
 * @author zhkl0228
 *
 */
public abstract class AbstractReverseProxyClient implements ReverseProxyClient {
	
	private static final Logger log = LoggerFactory.getLogger(AbstractReverseProxyClient.class);

	protected final String host;
	protected final int port;

	private final String extraData;

	protected final ByteBuffer packetBuffer;
	protected final ByteBuffer writeBuffer;
	
	public AbstractReverseProxyClient(String host, int port, String extraData, int packetBufferSize, int writeBufferSize) {
		super();
		this.host = host;
		this.port = port;
		this.extraData = extraData;
		
		this.packetBuffer = ByteBuffer.allocate(packetBufferSize);
		this.packetBuffer.order(ByteOrder.BIG_ENDIAN);
		
		this.writeBuffer = ByteBuffer.allocateDirect(writeBufferSize);
		this.writeBuffer.order(ByteOrder.BIG_ENDIAN);
	}

	private boolean socksOverTls;

	@Override
	public void enableSocksOverTls() {
		this.socksOverTls = true;
	}

	private String lbs, lastSyncLbs;

	@Override
	public void setLbs(String lbs) {
		this.lbs = lbs;
	}

	@Override
	public String getHost() {
		return host;
	}

	@Override
	public int getPort() {
		return port;
	}

	protected String username;
	protected String password;
	
	protected AuthResult authResult;
	
	protected int aliveTimeMillis;
	
	protected long lastAliveTime;
	
	protected boolean requestedLogin;

	/* (non-Javadoc)
	 * @see cn.banny.rp.client.ReverseProxyClient#getAuthResult()
	 */
	@Override
	public final AuthResult getAuthResult() {
		return authResult;
	}
	
	protected AuthListener authListener;

	/* (non-Javadoc)
	 * @see cn.banny.rp.client.IReverseProxyClient#setAuthListener(cn.banny.rp.client.AuthListener)
	 */
	@Override
	public final void setAuthListener(AuthListener authListener) {
		this.authListener = authListener;
	}

	private ExtDataHandler dataHandler;
	
	/* (non-Javadoc)
	 * @see cn.banny.rp.client.IReverseProxyClient#setDataHandler(cn.banny.rp.handler.ExtDataHandler)
	 */
	@Override
	public final void setDataHandler(ExtDataHandler dataHandler) {
		this.dataHandler = dataHandler;
	}

	@Override
	public final String getAuthUser() {
		return username;
	}
	
	/* (non-Javadoc)
	 * @see cn.banny.rp.client.IReverseProxyClient#isAuthOK()
	 */
	@Override
	public final boolean isAuthOK() {
		return username != null && password != null && authResult != null && authResult.getStatus() == 0;
	}

	private int networkDelay;
	private int averageNetworkDelay;

	private void addNetworkDelay(long networkDelay) {
		this.networkDelay = (int) networkDelay;
		if (this.averageNetworkDelay > 0) {
			this.averageNetworkDelay = (this.averageNetworkDelay + this.networkDelay) / 2;
		} else {
			this.averageNetworkDelay = this.networkDelay;
		}
	}
	
	/* (non-Javadoc)
	 * @see cn.banny.rp.client.IReverseProxyClient#getNetworkDelay()
	 */
	@Override
	public final int getNetworkDelay() {
		return networkDelay;
	}

	@Override
	public int getAverageNetworkDelay() {
		return averageNetworkDelay;
	}

	/* (non-Javadoc)
	 * @see cn.banny.rp.client.ReverseProxyClient#login(java.lang.String, java.lang.String)
	 */
	@Override
	public final void login(String username, String password) {
		login(username, password, 0);
	}
	
	protected long lastSync;

	protected final boolean canConnectReverseProxyServer() {
		return reconnect && (this.authResult == null && requestedLogin || isAuthOK());

	}

	@Override
	public final int getVersion() {
		return VERSION;
	}
	
	private IpChangeProcessor ipChangeProcessor;
	
	/* (non-Javadoc)
	 * @see cn.banny.rp.client.IReverseProxyClient#setRequestChangeIp(java.lang.String)
	 */
	@Override
	public final void setRequestChangeIp(IpChangeProcessor ipChangeProcessor) {
		this.ipChangeProcessor = ipChangeProcessor;
	}
	
	public abstract void sendResponse(SocketChannel session, ByteBuffer packet) throws IOException;

	protected final void requestAuth(SocketChannel session) {
		try {
			writeBuffer.mark();
			writeBuffer.position(writeBuffer.position() + 4);
			writeBuffer.put((byte) 0x7);
			ReverseProxy.writeUTF(writeBuffer, username);
			ReverseProxy.writeUTF(writeBuffer, password);
			writeBuffer.putInt(VERSION);
			String extraData = this.extraData;
			if(extraData == null &&
					this.exposeSystemInfo) {
				try {
					OperatingSystemMXBean operatingSystemMXBean = ManagementFactory.getOperatingSystemMXBean();
					RuntimeMXBean runtimeMXBean = ManagementFactory.getRuntimeMXBean();
					extraData = operatingSystemMXBean.getName() + ' ' + operatingSystemMXBean.getVersion() + " (" + operatingSystemMXBean.getArch() + ')' +
							' ' + runtimeMXBean.getVmVendor() + ' ' + System.getProperty("java.version");
				} catch(Exception ignored) {}
			}
			writeBuffer.put(extraData != null ? (byte) 1 : 0);
			if(extraData != null) {
				ReverseProxy.writeUTF(writeBuffer, extraData);
			}
			writeBuffer.put(ipChangeProcessor != null ? (byte) 1 : 0);
			writeBuffer.putLong(System.currentTimeMillis());
			writeBuffer.put((byte) 0);//no device info
			writeBuffer.limit(writeBuffer.position()).reset();
			sendResponse(session, writeBuffer);
		} catch(IOException e) {
			e.printStackTrace(System.err);
		}
	}
	
	private boolean exposeSystemInfo;
	
	/**
	 * 如果extraData为null的话，这个为true表示获取系统信息
	 * @param exposeSystemInfo 是否获取系统信息
	 */
	@SuppressWarnings("unused")
	public void setExposeSystemInfo(boolean exposeSystemInfo) {
		this.exposeSystemInfo = exposeSystemInfo;
	}

	private void receivedPacket(SocketChannel session, ByteBuffer packet) {
		if(log.isDebugEnabled()) {
			packet.mark();
			byte[] data = new byte[packet.remaining()];
			packet.get(data);
			packet.reset();
			ReverseProxy.inspect(data, "receivedPacket connSize=" + this.socketMap.size() + ", session=" + session);
		}
		
		messageReceived(session, packet);
	}
	
	/**
	 * 客户端是否需要断线重连
	 */
	private boolean reconnect = true;

	private void messageReceived(SocketChannel session, ByteBuffer packet) {
		long currentTimeMillis = System.currentTimeMillis();
		try {
			int type = packet.get() & 0xff;
			switch (type) {
			case 0x1:
				parseConnect(session, packet.getInt(), packet);
				break;
			case 0x2:
				lastAliveTime = currentTimeMillis;
				parseWriteData(session, packet.getInt(), packet);
				break;
			case 0x3:
				parseClose(session, packet.getInt());
				break;
			case 0x7:
				authResult = AuthResult.readAuthResult(packet);
				if(packet.hasRemaining()) {
					this.reconnect = packet.get() == 1;
				}
				if(packet.remaining() >= 8) {
					lastSync = currentTimeMillis;
					addNetworkDelay(lastSync - packet.getLong());
				}
				if (packet.remaining() >= 2) {
					String clientIp = ReverseProxy.readUTF(packet);
					authResult.setClientIp(clientIp);
				}
				if(authListener != null &&
						authListener.onAuthResponse(this, authResult)) {
					closeSession();
				}
				lastSyncLbs = null;
				break;
			case 0x8:
				lastAliveTime = currentTimeMillis;
				lastSync = currentTimeMillis;
				addNetworkDelay(lastSync - packet.getLong());
				break;
			case 0x9:
				if(dataHandler != null) {
					byte[] data = new byte[packet.getInt()];
					packet.get(data);
					dataHandler.handle(data, this);
				}
				break;
			case 0xa:
				parseRequestForward(packet);
				break;
			case 0xb:
				requestChangeIp();
				break;
			case 0xc:
				shutdownHalf(packet.getInt(), packet);
				break;
			case 0xf:
				parseBroadcast(packet);
				break;
			case 0x1a:
				parseStartProxy(packet);
				break;
			case 0x1c:
				parseStartNewProxy(packet);
				break;
			case 0x1d:
				parseStartKwikProxy(packet);
				break;
			case 0x1b:
				parseForwardSocket(packet);
				break;
			default:
				throw new IOException("messageReceived unsupported type: 0x" + Integer.toHexString(type));
			}
		} catch (IOException e) {
			log.info("process messageReceived failed", e);
		}
	}

	/* (non-Javadoc)
	 * @see cn.banny.rp.client.IReverseProxyClient#logout()
	 */
	@Override
	public final void logout() {
		this.username = null;
		this.password = null;
		
		this.requestedLogin = false;
		
		closeSession();
	}

	@Override
	public void disconnect() {
		closeSession();
	}

	protected final void processPacket(SocketChannel session) {
		try {
			packetBuffer.flip();
			while(true) {
				if(packetBuffer.remaining() < 4) {
					break;
				}
				
				packetBuffer.mark();
				int packetSize = packetBuffer.getInt();
				if(packetBuffer.remaining() < packetSize) {
					packetBuffer.reset();
					break;
				}
				
				ByteBuffer packet = ByteBuffer.wrap(packetBuffer.array(), packetBuffer.position(), packetSize);
				packetBuffer.position(packetBuffer.position() + packetSize);
				receivedPacket(session, packet);
			}
		} finally {
			packetBuffer.compact();
		}
	}
	
	protected abstract void closeSession();

	private long checkSessionTime;
	
	protected final void checkSession(SocketChannel session, long currentTimeMillis, ByteBuffer writeBuffer) {
		if(currentTimeMillis - checkSessionTime < TimeUnit.SECONDS.toMillis(10)) {
			return;
		}
		checkSessionTime = currentTimeMillis;
		
		if(aliveTimeMillis > 0 &&
				lastAliveTime > 0 &&
				currentTimeMillis - lastAliveTime > aliveTimeMillis &&
				!isAlive()) {
			logout();
			return;
		}
		
		try {
			writeBuffer.mark();
			writeBuffer.position(writeBuffer.position() + 4);
			writeBuffer.put((byte) 0x8);
			writeBuffer.putInt(averageNetworkDelay > 0 ? averageNetworkDelay : networkDelay);
			writeBuffer.putLong(currentTimeMillis);
			writeBuffer.putInt(0);

			String lbs = this.lbs;
			boolean syncLbs = lbs != null && !lbs.equals(lastSyncLbs);
			writeBuffer.put((byte) (syncLbs ? 1 : 0));
			if (syncLbs) {
				ReverseProxy.writeUTF(writeBuffer, lbs);
			}
			lastSyncLbs = lbs;

			writeBuffer.limit(writeBuffer.position()).reset();
			sendResponse(session, writeBuffer);
		} catch (IOException e) {
			log.debug("send response failed", e);
			
			closeSession();
		}
		
		if(currentTimeMillis - lastPortForwardRequestTime > TimeUnit.SECONDS.toMillis(3) &&
				!portForwardMap.isEmpty()) {
			for(PortForwardRequest request : portForwardMap.values()) {
				requestForward(request);
			}
		}
	}

	protected abstract void wakeUp();

	/* (non-Javadoc)
	 * @see cn.banny.rp.client.IReverseProxyClient#deliverMessage(byte[])
	 */
	@Override
	public final void deliverMessage(byte[] msg) {
		ByteBuffer buffer = ByteBuffer.allocate(msg.length + 10);
		buffer.position(4);
		buffer.put((byte) 0x9);
		buffer.putInt(msg.length);
		buffer.put(msg);
		buffer.flip();

		packetQueue.offer(buffer);
		wakeUp();
	}

	@Override
	public void sendBroadcast(byte[] data) {
		ByteBuffer buffer = ByteBuffer.allocate(data.length + 8);
		buffer.position(4);
		buffer.put((byte) 0xf);
		buffer.putShort((short) data.length);
		buffer.put(data);
		buffer.flip();

		packetQueue.offer(buffer);
		wakeUp();
	}

	@Override
	public void requestForward(String host, int port) {
		requestForward(0, host, port);
	}

	@Override
	public final void requestForward(int remotePort, String host, int port) {
		PortForwardRequest forwardRequest = new PortForwardRequest(remotePort, host, port);
		requestForward(forwardRequest);
	}

	@Override
	public void requestForward(int remotePort, ForwarderType type, int port) {
		requestForward(remotePort, String.valueOf(type.ordinal()), port);
	}

	private void requestForward(PortForwardRequest forwardRequest) {
		portForwardMap.remove(forwardRequest.getRemotePort());
		if(!forwardRequest.isValid()) {
			return;
		}
		
		try {
			lastPortForwardRequestTime = System.currentTimeMillis();
			if (forwardRequest.getRemotePort() > 0) {
				portForwardMap.put(forwardRequest.getRemotePort(), forwardRequest);
			}

			packetQueue.offer(forwardRequest.createBuffer());
			wakeUp();
		} catch (IOException e) {
			log.warn(e.getMessage(), e);
			
			closeSession();
		}
	}

	private void parseForwardSocket(ByteBuffer in) {
		int listenPort = in.getShort() & 0xffff;
		String host = ReverseProxy.readUTF(in);
		int port = in.getShort() & 0xffff;
		int readTimeoutInMillis = in.getInt();
		int connectTimeoutInMillis = in.getInt();

		ProxyStarter proxyStarter = new ProxyStarter(this.host, listenPort, host, port, null, socksOverTls);
		proxyStarter.readTimeoutInMillis = readTimeoutInMillis;
		proxyStarter.connectTimeoutInMillis = connectTimeoutInMillis;
		new Thread(proxyStarter).start();
	}

	private void parseStartProxy(ByteBuffer in) throws IOException {
		int listenPort = in.getShort() & 0xffff;
		String host = ReverseProxy.readUTF(in);
		int port = in.getShort() & 0xffff;

		new Thread(new ProxyStarter(this.host, listenPort, host, port, null, socksOverTls)).start();
	}

	private void parseStartNewProxy(ByteBuffer in) throws IOException {
		int listenPort = in.getShort() & 0xffff;
		String host = ReverseProxy.readUTF(in);
		int port = in.getShort() & 0xffff;
		byte[] uuid=  new byte[16];
		in.get(uuid);

		new Thread(new ProxyStarter(this.host, listenPort, host, port, uuid, socksOverTls)).start();
	}

	private void parseStartKwikProxy(ByteBuffer in) throws IOException {
		int listenPort = in.getShort() & 0xffff;
		String host = ReverseProxy.readUTF(in);
		int port = in.getShort() & 0xffff;
		byte[] uuid=  new byte[16];
		in.get(uuid);

		new Thread(new KwikStarter(this.host, listenPort, host, port, uuid)).start();
	}

	private final Map<Integer, PortForwardRequest> portForwardMap = new ConcurrentHashMap<>();
	private long lastPortForwardRequestTime;

	private void parseRequestForward(ByteBuffer in) throws IOException {
		String label = "";
		if(this.extraData != null) {
			label = '[' + extraData + ']';
		}
		
		lastPortForwardRequestTime = System.currentTimeMillis();
		int remotePort = in.getShort() & 0xffff;
		if(in.get() == 0) { // no exception
			DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
			portForwardMap.remove(remotePort);
            log.debug("{}[{}]parseRequestForward bind remote port#{} successfully!", label, dateFormat.format(new Date()), remotePort);

			String host = ReverseProxy.readUTF(in);
			int port = in.getShort() & 0xffff;
			if (authListener != null) {
				authListener.onPortForward(this, remotePort, host, port);
			}
		} else {
            log.info("{}[{}]parseRequestForward bind remote port#{} failed: {}", label, host, remotePort, ReverseProxy.readUTF(in));
		}
	}

	private void parseBroadcast(ByteBuffer in) {
		boolean fromServer = in.get() == 1;
		byte[] data = new byte[in.getShort() & 0xffff];
		in.get(data);
		log.debug("parseBroadcast fromServer={}, data={}", fromServer, data);
		if (broadcastListener != null) {
			broadcastListener.onBroadcast(fromServer, data);
		}
	}

	private final Map<Integer, SocketProxy> socketMap = new ConcurrentHashMap<>();

	@Override
	public int getSocksCount() {
		return socketMap.size();
	}

	protected final void closeAllSocketProxies() {
		portForwardMap.clear();
		
		for(SocketProxy proxy : socketMap.values()) {
			proxy.close(false);
		}

		packetQueue.clear();
	}
	
	/* (non-Javadoc)
	 * @see cn.banny.rp.client.ReverseProxyClient#isAlive()
	 */
	@Override
	public final boolean isAlive() {
		long currentTimeMillis = System.currentTimeMillis();
		return currentTimeMillis > lastAliveTime && currentTimeMillis - lastAliveTime < TimeUnit.MINUTES.toMillis(1);
	}

	private void shutdownHalf(int socket, ByteBuffer in) {
		SocketProxy socketProxy = socketMap.get(socket);
		if(socketProxy == null) {
			return;
		}

		socketProxy.shutdownHalf(in.get() == 1);
	}

	private void parseClose(SocketChannel session, int socket) throws IOException {
		SocketProxy socketProxy = socketMap.get(socket);
		if(socketProxy == null) {
            log.debug("parseClose proxy is null: session={}", session);
			return;
		}
		
		socketProxy.close(true);

		writeBuffer.mark();
		writeBuffer.position(writeBuffer.position() + 4);
		writeBuffer.put((byte) 0x3);
		writeBuffer.putInt(socket);
		writeBuffer.limit(writeBuffer.position()).reset();
		sendResponse(session, writeBuffer);
	}

	private void parseWriteData(SocketChannel session, int socket, ByteBuffer in) {
		SocketProxy proxy = socketMap.get(socket);
		if(proxy == null) {
			sendException(session, socket, new IOException("parseWriteData socket is null: socket=0x" + Integer.toHexString(socket) + ", session=" + session));
			return;
		}

		if(log.isDebugEnabled()) {
			in.mark();
			in.getInt(); // length
			byte[] data = new byte[in.remaining()];
			in.get(data);
			in.reset();
			ReverseProxy.inspect(data, "parseWriteData proxy=" + proxy + ", session=" + session);
		}
		
		int length = in.getInt();
		proxy.writeData(ByteBuffer.wrap(in.array(), in.position(), length));
	}

	public void notifySessionClosed(SocketChannel session, int socket, Throwable throwable) throws IOException {
		socketMap.remove(socket);
		
		writeBuffer.mark();
		writeBuffer.position(writeBuffer.position() + 4);
		writeBuffer.put((byte) 0x5);
		writeBuffer.putInt(socket);
		String msg = null;
		if (throwable != null) {
			StringWriter writer = new StringWriter();
			throwable.printStackTrace(new PrintWriter(writer));
			msg = writer.toString();
		}
		writeBuffer.put(msg != null ? (byte) 1 : 0);
		if(msg != null) {
			ReverseProxy.writeUTF(writeBuffer, msg);
		}
		writeBuffer.limit(writeBuffer.position()).reset();
		sendResponse(session, writeBuffer);
	}
	
	private void requestChangeIp() {
		if(ipChangeProcessor != null) {
			ipChangeProcessor.requestChangeIp(this);
		}
	}

	public void sendException(SocketChannel session, int socket, Throwable t) {
		try {
			writeBuffer.mark();
			writeBuffer.position(writeBuffer.position() + 4);
			writeBuffer.put((byte) 0x6);
			writeBuffer.putInt(socket);

			StringWriter writer = new StringWriter();
			t.printStackTrace(new PrintWriter(writer));
			String msg = writer.toString();
			ReverseProxy.writeUTF(writeBuffer, msg);

			writeBuffer.limit(writeBuffer.position()).reset();
			sendResponse(session, writeBuffer);
		} catch (IOException e) {
			e.printStackTrace(System.err);
		}
	}

	private void parseConnect(SocketChannel session, int socket, ByteBuffer buffer) {
		try {
			RequestConnect connect = RequestConnect.parseRequestConnect(buffer);
			SocketProxy proxy = createSocketProxy(session, socket, connect);
			proxy.connect();
			socketMap.put(socket, proxy);
		} catch (Throwable e) {
			if(log.isDebugEnabled()) {
				log.debug(e.getMessage(), e);
			}
			
			sendException(session, socket, e);
		} finally {
			lastSync = System.currentTimeMillis();
		}
	}

	protected abstract SocketProxy createSocketProxy(SocketChannel route, int socket, RequestConnect connect);
	
	private final Queue<ByteBuffer> packetQueue = new ConcurrentLinkedQueue<>();

	protected final void processPacketQueue(SocketChannel session) throws IOException {
		ByteBuffer buffer;
		while((buffer = packetQueue.poll()) != null) {
			writeBuffer.mark();
			writeBuffer.put(buffer);
			writeBuffer.limit(writeBuffer.position()).reset();
			sendResponse(session, writeBuffer);
		}
	}

	private BroadcastListener broadcastListener;

	@Override
	public void setBroadcastListener(BroadcastListener broadcastListener) {
		this.broadcastListener = broadcastListener;
	}
}
