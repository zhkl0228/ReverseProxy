package cn.banny.rp.server.mina;

import cn.banny.rp.auth.Auth;
import cn.banny.rp.server.AbstractRoute;
import cn.banny.rp.server.AbstractServerHandler;
import cn.banny.rp.server.ServerHandler;
import org.apache.mina.core.service.IoHandler;
import org.apache.mina.core.session.IdleStatus;
import org.apache.mina.core.session.IoSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;

/**
 * @author zhkl0228
 *
 */
public class MinaServerHandler extends AbstractServerHandler<IoSession> implements ServerHandler, IoHandler {
	
	private static final Logger log = LoggerFactory.getLogger(MinaServerHandler.class);

	@Override
	public void sessionOpened(IoSession session) {
		sessions.put(session.getId(), new MinaRemoteRoute(session, authHandler));
	}

	@Override
	public void sessionClosed(IoSession session) {
		AbstractRoute route = sessions.remove(session.getId());
		if (route == null) {
            log.warn("sessionClosed routes is null: {}", session);
			return;
		}
		route.notifyRouteClosed();
	}

	@Override
	public void sessionIdle(IoSession session, IdleStatus status) {
		if(status == IdleStatus.READER_IDLE) {
			session.closeOnFlush();
		}
	}

	@Override
	public void exceptionCaught(IoSession session, Throwable cause) {
		log.debug("exceptionCaught", cause);
	}

	@Override
	public void inputClosed(IoSession session) {
		session.closeNow();
	}

	@Override
	public void messageReceived(IoSession session, Object message)
			throws Exception {
		AbstractRoute route = this.sessions.get(session.getId());
		if (route == null) {
            log.warn("messageReceived route is null: {}", session);
			return;
		}
		ByteBuffer msg = (ByteBuffer) message;
		messageReceived(route, msg, session);
	}

	@Override
	public void sessionCreated(IoSession session) {
	}

	@Override
	public void messageSent(IoSession session, Object message) {
	}

	@Override
	protected void setSessionAuth(Auth auth, IoSession session) {
		session.setAttribute(Auth.class.getCanonicalName(), auth);
	}

	@Override
	protected void closeSession(IoSession session) {
		session.closeOnFlush();
	}

}
