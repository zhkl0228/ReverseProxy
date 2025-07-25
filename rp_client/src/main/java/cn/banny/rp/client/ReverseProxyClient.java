package cn.banny.rp.client;

import cn.banny.rp.MessageDeliver;
import cn.banny.rp.auth.AuthResult;
import cn.banny.rp.forward.ForwarderType;
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

	void enableSocksOverTls();

	void logout();

	void disconnect();

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
	 * @param remotePort 远程绑定端口，0 表示随机分配可用端口
	 * @param host 远端主机 空字符串表示 localhost，并且远端绑定端口在 127.0.0.1，如果是数字，则 "0" 表示 BIO，"1" 表示 NIO，"2" 表示 AIO，"3" 表示 NewBIO
	 * @param port 远端端口
	 */
	void requestForward(int remotePort, String host, int port);

	void requestForward(int remotePort, ForwarderType type, int port);

	/**
	 * 网络延时，单位毫秒
	 * @return 0表示没有初始化网络延时
	 */
	int getNetworkDelay();

	/**
	 * 网络平均延时，单位毫秒
	 * @return 0表示没有初始化网络延时
	 */
	int getAverageNetworkDelay();

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