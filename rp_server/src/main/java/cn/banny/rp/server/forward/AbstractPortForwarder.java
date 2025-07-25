package cn.banny.rp.server.forward;

import cn.banny.rp.forward.PortForwarder;
import cn.banny.rp.forward.RouteForwarder;
import cn.banny.rp.server.AbstractRoute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author zhkl0228
 *
 */
public abstract class AbstractPortForwarder extends ForwarderAware implements PortForwarder {

	private static final Logger log = LoggerFactory.getLogger(AbstractPortForwarder.class);

	private final boolean bindLocal;
	protected final int inPort;
	final String outHost;
	final int outPort;
	protected final AbstractRoute route;
	
	final Map<Integer, RouteForwarder> forwards = new ConcurrentHashMap<>();

	final InetSocketAddress createBindAddress() {
		return bindLocal ? new InetSocketAddress(InetAddress.getLoopbackAddress(), inPort) : new InetSocketAddress(inPort);
	}

	AbstractPortForwarder(boolean bindLocal, int inPort, String outHost, int outPort, AbstractRoute route) {
		super();
		this.bindLocal = bindLocal;
		this.inPort = inPort;
		this.outHost = outHost;
		this.outPort = outPort;
		this.route = route;
	}

	int listenPort;

	@Override
	public int getListenPort() {
		return listenPort > 0 ? listenPort : inPort;
	}

	/* (non-Javadoc)
	 * @see cn.banny.rp.server.PortForwarder#getOutHost()
	 */
	@Override
	public String getOutHost() {
		return outHost;
	}

	/* (non-Javadoc)
	 * @see cn.banny.rp.server.PortForwarder#getOutPort()
	 */
	@Override
	public int getOutPort() {
		return outPort;
	}
	
	@Override
	protected final RouteForwarder[] getForwarders() {
		return forwards.values().toArray(new RouteForwarder[0]);
	}

	/* (non-Javadoc)
	 * @see cn.banny.rp.server.forward.ForwarderListener#notifyForwarderClosed(cn.banny.rp.server.forward.RouteForwarder)
	 */
	@Override
	public final void notifyForwarderClosed(RouteForwarder forwarder) {
		try (RouteForwarder removed = forwards.remove(forwarder.hashCode())) {
			log.debug("notifyForwarderClosed: removed={}", removed);
		} catch(IOException ignored) {}
	}

	/* (non-Javadoc)
	 * @see cn.banny.rp.server.forward.ForwarderListener#notifyConnectSuccess(cn.banny.rp.server.forward.RouteForwarder, java.net.InetAddress, int)
	 */
	@Override
	public void notifyConnectSuccess(RouteForwarder forwarder,
			InetAddress localAddr, int localPort) {
	}

	@Override
	public final Thread newThread(Runnable r) {
		Thread thread = new Thread(r, getClass().getSimpleName() + ", in=" + inPort + ", host=" + outHost + ", out=" + outPort);
		thread.setDaemon(true);
		return thread;
	}

}
