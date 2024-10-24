package cn.banny.rp.server;

import cn.banny.rp.Route;

/**
 * @author zhkl0228
 *
 */
public interface RouteNetworkChangedListener {
	
	/**
	 * 通知网络变化
	 * @param route 路由
	 * @param hash 网络哈希
	 * @return 是否需要更新服务器的networkHash
	 */
	boolean notifyNetworkChanged(Route route, int hash);

	String notifyLbsUpdate(Route route, String lbs);

}
