/**
 * 
 */
package cn.banny.rp.server.socks;

import cn.banny.rp.Route;

/**
 * @author zhkl0228
 *
 */
public class RouteWrapper implements Comparable<RouteWrapper> {
	
	private final Route route;
	private final int networkDelay;

	public RouteWrapper(Route route) {
		super();
		this.route = route;
		this.networkDelay = route.getNetworkDelay();
	}

	@Override
	public int compareTo(RouteWrapper o) {
		return networkDelay - o.networkDelay;
	}

	public Route getRoute() {
		return route;
	}

}
