package cn.banny.rp.server.mina;

import cn.banny.rp.Route;
import cn.banny.rp.RouteContext;
import cn.banny.rp.auth.Auth;
import cn.banny.rp.auth.AuthHandler;
import cn.banny.rp.server.AbstractRoute;
import cn.banny.rp.server.RemoteAddressContext;
import org.apache.mina.core.session.IoSession;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

/**
 * @author zhkl0228
 *
 */
public class MinaRemoteRoute extends AbstractRoute implements Route {
	
	private final IoSession session;
	private final RouteContext remoteAddressContext;

	MinaRemoteRoute(IoSession session, AuthHandler authHandler) {
		super(authHandler);
		this.session = session;
		
		this.remoteAddressContext = RemoteAddressContext.obtain(getRemoteAddress());
	}

	@Override
	protected void writeMessage(ByteBuffer message) {
		session.write(message);
	}

	@Override
	public InetSocketAddress getRemoteAddress() {
		return (InetSocketAddress) session.getRemoteAddress();
	}

	@Override
	protected Auth getAuthInternal() {
		return (Auth) session.getAttribute(Auth.class.getCanonicalName());
	}

	@Override
	@Deprecated
	public void disconnect() {
		disconnect(false);
	}

	@Override
	public void disconnect(boolean immediately) {
		if (immediately) {
			session.closeNow();
		} else {
			session.closeOnFlush();
		}
	}

	@Override
	public RouteContext getRemoteAddressContext() {
		return remoteAddressContext;
	}

}
