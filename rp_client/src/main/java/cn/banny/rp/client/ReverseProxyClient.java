package cn.banny.rp.client;

import cn.banny.rp.MessageDeliver;
import cn.banny.rp.auth.AuthResult;
import cn.banny.rp.handler.ExtDataHandler;

public interface ReverseProxyClient extends MessageDeliver {
	
	/**
	 * 最高位1表示客户端种类
	 */
	int VERSION = 0x10012;
	
	int READ_BUFFER_SIZE = 1024 * 64;//64K cache

	int getSocksCount();

	void setRequestChangeIp(IpChangeProcessor ipChangeProcessor);

	void login(String username, String password);

	void login(String username, String password, int aliveTimeMillis);

	void logout();

	boolean isConnected();

	boolean isAuthOK();

	/**
	 * @return 是否活跃
	 */
	boolean isAlive();
	
	boolean isDead();

	void initialize() throws Exception;

	void destroy() throws Exception;

	/**
	 * 请求端口转向，远端服务器随机绑定可用端口
	 * @param host 远端主机
	 * @param port 端口
	 */
	void requestForward(String host, int port);

	/**
	 * 请求端口转向
	 * @param remotePort 远程绑定端口
	 * @param host 远端主机
	 * @param port 远端端口
	 */
	void requestForward(int remotePort, String host, int port);

	/**
	 * 网络延时，单位毫秒
	 * @return 0表示没有初始化网络延时
	 */
	int getNetworkDelay();

	void setAuthListener(AuthListener authListener);

	AuthResult getAuthResult();

	void setDataHandler(ExtDataHandler dataHandler);
	
	int getVersion();

	String getHost();
	int getPort();

	void setLbs(String lbs);

	/**
	 * 发送广播
	 * @param data 广播内容
	 */
	void sendBroadcast(byte[] data);

	/**
	 * 广播监听
	 * @param broadcastListener 监听者
	 */
	void setBroadcastListener(BroadcastListener broadcastListener);

}