package cn.banny.rp.server.socks;

import cn.banny.rp.auth.AuthHandler;
import cn.banny.rp.server.ServerHandler;

/**
 * @author zhkl0228
 *
 */
public interface ProxyServer extends SocketFactory {
	
	void setHandler(ServerHandler handler);
	
	void setAuthHandler(AuthHandler authHandler);
	
	void initialize() throws Exception;
	
	void destroy();
	
	void setSupportV4(boolean supportV4);

}
