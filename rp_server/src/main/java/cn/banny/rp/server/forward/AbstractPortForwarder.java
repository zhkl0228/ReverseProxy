package cn.banny.rp.server.forward;

import cn.banny.rp.forward.PortForwarder;
import cn.banny.rp.forward.RouteForwarder;
import cn.banny.rp.server.AbstractRoute;

import java.net.InetAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author zhkl0228
 *
 */
public abstract class AbstractPortForwarder extends ForwarderAware implements PortForwarder {

	final int inPort;
	final String outHost;
	final int outPort;
	protected final AbstractRoute route;
	
	final Map<Integer, RouteForwarder> forwards = new ConcurrentHashMap<>();

	AbstractPortForwarder(int inPort, String outHost, int outPort, AbstractRoute route) {
		super();
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
		forwards.remove(forwarder.hashCode());
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
		return new Thread(r, getClass().getSimpleName() + ", in=" + inPort + ", host=" + outHost + ", out=" + outPort);
	}

}
