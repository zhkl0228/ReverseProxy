package cn.banny.rp.server.socks;

import cn.banny.rp.auth.AuthHandler;
import cn.banny.rp.server.ServerHandler;

import java.util.concurrent.ThreadFactory;

/**
 * @author zhkl0228
 *
 */
public interface ProxyServer extends ThreadFactory, SocketFactory {
	
	void setHandler(ServerHandler handler);
	
	void setAuthHandler(AuthHandler authHandler);
	
	void initialize() throws Exception;
	
	void destroy();
	
	void setSupportV4(boolean supportV4);

}
