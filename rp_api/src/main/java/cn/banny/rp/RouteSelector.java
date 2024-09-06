package cn.banny.rp;

import java.net.InetSocketAddress;

/**
 * @author zhkl0228
 *
 */
public interface RouteSelector {
	
	Route select(InetSocketAddress clientAddress, InetSocketAddress connectAddress, String user, String pass);

}
