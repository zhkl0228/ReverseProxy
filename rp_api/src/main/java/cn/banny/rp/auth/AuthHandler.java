package cn.banny.rp.auth;

import cn.banny.rp.Route;
import cn.banny.rp.forward.PortForwarder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author zhkl0228
 *
 */
public interface AuthHandler {

	Logger log = LoggerFactory.getLogger(AuthHandler.class);
	
	/**
	 * 登录
	 * @param username the login username
	 * @param password the login password
	 * @return 返回null表示成功
	 */
	AuthResult auth(String username, String password);
	
	void onAuth(Route route, String username);

	void onRouteDisconnect(Route route);

	default void onRouteSync(Route route) {
		log.debug("onRouteSync: {}", route);
	}

	/**
	 * 当一个端口转向成功连接时通知
	 * @param route the route
	 * @param bindPort 绑定的本地端口
	 * @param forwarder the port forwarder
	 */
	void onPortForward(Route route, int bindPort, PortForwarder forwarder);

}
