package cn.banny.rp.server;

import cn.banny.rp.ReverseProxy;
import cn.banny.rp.Route;
import cn.banny.rp.auth.Auth;
import cn.banny.rp.auth.AuthHandler;
import cn.banny.rp.auth.AuthResult;
import cn.banny.rp.handler.ExtDataHandler;
import cn.banny.utils.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author zhkl0228
 *
 */
public abstract class AbstractServerHandler<T> implements ServerHandler {
	
	private static final Logger log = LoggerFactory.getLogger(AbstractServerHandler.class);
	
	protected final Map<Long, AbstractRoute> sessions = new ConcurrentHashMap<>();
	
	private Route[] getRemoteRoutes() {
		return sessions.values().toArray(new Route[0]);
	}

	@Override
	public final Route[] getRoutes() {
		return getRemoteRoutes();
	}
	
	protected void messageReceived(AbstractRoute route, ByteBuffer msg, T session) throws Exception {
		if(log.isDebugEnabled()) {
			msg.mark();
			byte[] temp = new byte[msg.remaining()];
			msg.get(temp);
			msg.reset();
			ReverseProxy.inspect(temp, "messageReceived delay=" + route.getNetworkDelay() + ", connSize=" + route.getConnectionSize() + ", session=" + session);
		}

		route.setLastReceivedTime(System.currentTimeMillis());
		route.addSendTraffic(null, msg.remaining() + 4);
		
		int type = msg.get() & 0xff;
		switch (type) {
		case 0x1:
		case 0x3:
		case 0x4:
		case 0x5:
		case 0x6:
		case 0xc:
			route.receivedMessage(type, msg);
			break;
		case 0x7:
			parseAuth(msg, route, session);
			break;
		case 0x8:
			parseSync(msg, route, session);
			break;
		case 0x9:
			if(dataHandler != null) {
				byte[] data = new byte[msg.getInt()];
				msg.get(data);
				dataHandler.handle(data, route);
			}
			break;
		case 0xa:
			parseRequestForward(msg, route);
			break;
		case 0xf:
			parseBroadcast(msg, route);
			break;
		default:
			break;
		}
	}

	private void parseBroadcast(ByteBuffer msg, AbstractRoute route) {
		byte[] data = new byte[msg.getShort() & 0xffff];
		msg.get(data);

		sendBroadcast(false, data, route);
	}

	@Override
	public void sendBroadcast(byte[] data) {
		sendBroadcast(true, data, null);
	}

	private void sendBroadcast(boolean fromServer, byte[] data, AbstractRoute route) {
		ByteArrayOutputStream baos = null;
		DataOutputStream dos = null;
		try {
			baos = new ByteArrayOutputStream();
			dos = new DataOutputStream(baos);

			dos.writeByte(0xf);
			dos.writeBoolean(fromServer);
			dos.writeShort(data.length);
			dos.write(data);

			for (AbstractRoute r : sessions.values()) {
				if (route != r) {
					r.sendRequest(baos.toByteArray());
				}
			}
		} catch (IOException e) {
			log.warn("sendBroadcast failed.", e);
		} finally {
			ReverseProxy.closeQuietly(baos);
			ReverseProxy.closeQuietly(dos);
		}
	}

	private boolean forwardEnabled = true;
	
	public void setForwardEnabled(boolean forwardEnabled) {
		this.forwardEnabled = forwardEnabled;
	}

	private void parseRequestForward(ByteBuffer in, AbstractRoute route) throws IOException {
		int port = in.getShort() & 0xffff;
		String remoteHost = ReverseProxy.readUTF(in);
		int remotePort = in.getShort() & 0xffff;
		IOException exception = null;
		try {
			if(!forwardEnabled &&
					(route.getAuth() == null || route.getAuth().getResult() == null)) {
				throw new IOException("Port forward NOT enabled!");
			}
			
			port = route.startForward(port, remoteHost, remotePort);
		} catch(IOException e) {
			exception = e;
		}
		
		ByteArrayOutputStream baos = null;
		DataOutputStream dos = null;
		try {
			baos = new ByteArrayOutputStream();
			dos = new DataOutputStream(baos);
			
			dos.writeByte(0xa);
			dos.writeShort(port);
			dos.writeBoolean(exception != null);
			if(exception != null) {
				dos.writeUTF(exception.getMessage());
			} else if(route.getVersion() >= 0x10012) {
				dos.writeUTF(remoteHost);
				dos.writeShort(remotePort);
			}
			route.sendRequest(baos.toByteArray());
		} finally {
			ReverseProxy.closeQuietly(baos);
			ReverseProxy.closeQuietly(dos);
		}
	}

	private void parseSync(ByteBuffer in, AbstractRoute route, T session) throws IOException {
		int networkDelay = in.getInt();
		long time = in.getLong();

		if(networkDelay > 0) {
			route.networkDelay = networkDelay;
		} else if(networkDelay == 0) {
			route.networkDelay = 1;
		}

		if(route.getVersion() >= 0x10002) {
			route.checkNetworkChanged(routeNetworkChangedListener, in.getInt());
		}

		if (in.remaining() > 0 && in.get() == 1) { // sync lbs
			String lbs = ReverseProxy.readUTF(in);
			try {
				if (routeNetworkChangedListener != null) {
					String remoteIp = routeNetworkChangedListener.notifyLbsUpdate(route, lbs);
					if (StringUtils.hasText(remoteIp)) {
						route.setRemoteIp(remoteIp);
					}
				}
			} finally {
				route.lbs = lbs;
			}
		}
		
		ByteArrayOutputStream baos = null;
		DataOutputStream dos = null;
		try {
			baos = new ByteArrayOutputStream();
			dos = new DataOutputStream(baos);
			
			dos.writeByte(0x8);
			dos.writeLong(time);
			route.sendRequest(baos.toByteArray());
		} finally {
			ReverseProxy.closeQuietly(baos);
			ReverseProxy.closeQuietly(dos);
		}
		
		if(route.getAuthUser() == null) {
			if(route.getVersion() == 0x10000) {
				setSessionAuth(new Auth("460000000000000", "00000000000000000000000000000000", null), session);
			} else {
				closeSession(session);
			}
		}
	}

	private ExtDataHandler dataHandler;

	@Override
	public void setDataHandler(ExtDataHandler dataHandler) {
		this.dataHandler = dataHandler;
	}
	
	private RouteNetworkChangedListener routeNetworkChangedListener;
	
	public void setRouteNetworkChangedListener(
			RouteNetworkChangedListener routeNetworkChangedListener) {
		this.routeNetworkChangedListener = routeNetworkChangedListener;
	}
	
	private boolean reconnect = true;

	/**
	 * 是否需要客户端重连
	 * @param reconnect can reconnect
	 */
	public void setReconnect(boolean reconnect) {
		this.reconnect = reconnect;
	}

	protected abstract void setSessionAuth(Auth auth, T session);

	private void parseAuth(ByteBuffer in, AbstractRoute route, T session) throws IOException {
		String username = ReverseProxy.readUTF(in);
		String password = ReverseProxy.readUTF(in);
		long requestTime = System.currentTimeMillis();
		if(in.remaining() >= 4) {//客户端版本号
			int version = in.getInt();
            route.version = version;
			
			if(version >= 0x10001 && in.get() == 1) {
				route.extraData = ReverseProxy.readUTF(in);
			}
			
			if(version >= 0x10003 && in.get() == 1) {
				route.canChangeIp = true;
			}
			
			int mask = version & 0xFFFF;
			if(mask >= 0x10) {
				requestTime = in.getLong();
			}
			
			if(mask >= 0x11 && in.hasRemaining() && in.get() == 1) {//have device info
				byte[] zipDeviceData = new byte[in.getInt()];
				in.get(zipDeviceData);
				route.setDeviceData(zipDeviceData);
			}
		}
		AuthResult result = authHandler == null ? null : authHandler.auth(username, password);
		
		int status;
		if(result == null) {
			setSessionAuth(new Auth(username, password, null), session);
			status = 0;
		} else {
			status = result.getStatus();
			if(status == 0) {
				setSessionAuth(new Auth(username, password, result), session);
			}
		}
		
		ByteArrayOutputStream baos = null;
		DataOutputStream dos = null;
		try {
			baos = new ByteArrayOutputStream();
			dos = new DataOutputStream(baos);
			
			dos.writeByte(0x7);
			dos.writeByte(status);
			dos.writeBoolean(result != null && result.getMsg() != null);
			if(result != null && result.getMsg() != null) {
				dos.writeUTF(result.getMsg());
			}
			dos.writeBoolean(result != null && result.getExpire() != null);
			if(result != null && result.getExpire() != null) {
				dos.writeLong(result.getExpire());
			}
			dos.writeBoolean(result != null && result.getNick() != null);
			if(result != null && result.getNick() != null) {
				dos.writeUTF(result.getNick());
			}
			dos.writeBoolean(reconnect);
			dos.writeLong(requestTime);
			dos.writeUTF(route.getRemoteAddress().getHostString());
			
			route.sendRequest(baos.toByteArray());
		} finally {
			ReverseProxy.closeQuietly(baos);
			ReverseProxy.closeQuietly(dos);
		}
		if(authHandler != null &&
				status == 0) {
			authHandler.onAuth(route, username);
		}
		
		if(status != 0) {
			closeSession(session);
		}
	}
	
	protected abstract void closeSession(T session);
	
	protected AuthHandler authHandler;

	@Override
	public void setAuthHandler(AuthHandler authHandler) {
		this.authHandler = authHandler;
	}

}
