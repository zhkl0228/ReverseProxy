package cn.banny.rp.server;


public interface ReverseProxyServer {

	void setHandler(ServerHandler handler);

	void setListenPort(int listenPort);

	void initialize() throws Exception;

	void destroy() throws Exception;

}