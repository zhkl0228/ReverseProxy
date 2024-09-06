package cn.banny.rp.server.socks;

import cn.banny.rp.Route;
import cn.banny.rp.RouteSelector;
import cn.banny.rp.auth.AuthHandler;
import cn.banny.rp.forward.ForwarderListener;
import cn.banny.rp.forward.RouteForwarder;
import cn.banny.rp.server.AbstractRoute;
import cn.banny.rp.server.ServerHandler;
import cn.banny.rp.server.forward.ForwarderAware;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * @author zhkl0228
 *
 */
public abstract class AbstractProxyServer<T> extends ForwarderAware implements ProxyServer {
	
	private final Map<Integer, RouteForwarder> forwards = new ConcurrentHashMap<>();
	
	protected final int port;

	AbstractProxyServer(int port) {
		super();
		
		this.port = port;
	}

	final void notifyForwarderClosed(RouteForwarder forwarder) {
		forwards.remove(forwarder.hashCode());
	}
	
	@Override
	protected final RouteForwarder[] getForwarders() {
		return forwards.values().toArray(new RouteForwarder[0]);
	}

	@Override
	public Socket createSocket(InetSocketAddress clientAddress, InetAddress address, int port, String user, String pass)
			throws IOException {
		Route route = selectRoute(clientAddress, new InetSocketAddress(address, port), user, pass);
		if(route == null) {
			return new Socket(address, port);
		}
		
		Socket socket = route.createRemoteSocket();
		socket.setSoTimeout((int) TimeUnit.SECONDS.toMillis(60));
		socket.connect(new InetSocketAddress(address, port), (int) TimeUnit.SECONDS.toMillis(15));
		return socket;
	}
	
	private RouteSelector routeSelector;

	public void setRouteSelector(RouteSelector routeSelector) {
		this.routeSelector = routeSelector;
	}

	private Route selectRoute(InetSocketAddress clientAddress, InetSocketAddress connectAddress, String user, String pass) {
		if(routeSelector != null) {
			return routeSelector.select(clientAddress, connectAddress, user, pass);
		}

		if (handler == null) {
			return null;
		}
		
		Route[] routes = handler.getRoutes();
		List<RouteWrapper> list = new ArrayList<>();
		for(Route route : routes) {
			if(route.getAuth() != null && route.isAlive() && acceptRoute(route)) {
				list.add(new RouteWrapper(route));
			}
		}
		RouteWrapper route = selectRoute(list);
		return route == null ? null : route.getRoute();
	}
	
	private boolean acceptRoute(Route route) {
		return route.getNetworkDelay() != -1 && route.getNetworkDelay() < 1000;
	}

	private RouteWrapper selectRoute(List<RouteWrapper> list) {
		Collections.shuffle(list);
		return list.isEmpty() ? null : list.get(0);
	}
	
	private ServerHandler handler;
	
	protected AuthHandler authHandler;

	/* (non-Javadoc)
	 * @see cn.banny.rp.server.socks.ProxyServer#setHandler(cn.banny.rp.server.ServerHandler)
	 */
	@Override
	public final void setHandler(ServerHandler handler) {
		this.handler = handler;
	}

	/* (non-Javadoc)
	 * @see cn.banny.rp.server.socks.ProxyServer#setAuthHandler(cn.banny.rp.auth.AuthHandler)
	 */
	@Override
	public final void setAuthHandler(AuthHandler authHandler) {
		this.authHandler = authHandler;
	}

	@Override
	public Thread newThread(Runnable r) {
		return new Thread(r, getClass().getSimpleName() + ", port=" + port);
	}

	final InetAddress addForward(T socket, String host, int port, ForwarderListener listener, String user, String pass) throws IOException {
		InetSocketAddress address = new InetSocketAddress(host, port);
		AbstractRoute route = (AbstractRoute) selectRoute(createClientAddress(socket), address, user, pass);
		if(route == null) {
			throw new IOException("Route is null, proxy failed.");
		}

		RouteForwarder forwarder = createForward(socket, host, port, route, listener);
		forwards.put(forwarder.hashCode(), forwarder);
		return address.getAddress();
	}

	final void addForward(T socket, InetAddress address, int port, ForwarderListener listener, String user, String pass) throws IOException {
		AbstractRoute route = (AbstractRoute) selectRoute(createClientAddress(socket), new InetSocketAddress(address, port), user, pass);
		if(route == null) {
			throw new IOException("Route is null, proxy failed.");
		}
		
		RouteForwarder forwarder = createForward(socket, address.getHostAddress(), port, route, listener);
		forwards.put(forwarder.hashCode(), forwarder);
	}

	protected abstract RouteForwarder createForward(T socket, String host, int port, AbstractRoute route, ForwarderListener listener) throws IOException;
	
	protected abstract InetSocketAddress createClientAddress(T socket) throws IOException;
	
	boolean supportV4;

	@Override
	public void setSupportV4(boolean supportV4) {
		this.supportV4 = supportV4;
	}

}
