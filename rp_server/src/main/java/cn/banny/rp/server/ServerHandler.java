package cn.banny.rp.server;

import cn.banny.rp.Route;
import cn.banny.rp.auth.AuthHandler;
import cn.banny.rp.handler.ExtDataHandler;

/**
 * @author zhkl0228
 *
 */
public interface ServerHandler {
	
	void setAuthHandler(AuthHandler authHandler);
	
	/**
	 * @return 不会返回null
	 */
	Route[] getRoutes();

	void setDataHandler(ExtDataHandler dataHandler);

	/**
	 * 发送广播
	 * @param data 广播内容
	 */
	void sendBroadcast(byte[] data);

}
