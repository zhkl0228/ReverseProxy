package cn.banny.rp;

import cn.banny.rp.auth.Auth;
import cn.banny.rp.forward.PortForwarder;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Collection;

/**
 * @author zhkl0228
 *
 */
public interface Route extends MessageDeliver, RouteContext {
	
	/**
	 * 最后一次请求更换IP的间隔
	 */
	String LAST_REQUEST_CHANGE_IP_TIME_KEY = "lastRequestChangeIp";
	
	/**
	 * @return 路由授权
	 */
	Auth getAuth();
	
	/**
	 * 断开连接
	 */
	@Deprecated
	void disconnect();
	
	/**
	 * 断开连接
	 * @param immediately 是否强制断开
	 */
	void disconnect(boolean immediately);
	
	/**
	 * @return 这个路由上的连接数
	 */
	int getConnectionSize();
	
	/**
	 * not null peer address
	 * @return 客户端地址
	 */
	InetSocketAddress getRemoteAddress();
	
	RouteContext getRemoteAddressContext();
	
	/**
	 * @return 网络延时
	 */
	int getNetworkDelay();

	/**
	 * 端口转发
	 * @param port 服务器监听端口
	 * @param remoteHost 转发的主机
	 * @param remotePort 转发的主机端口
	 * @throws IOException 异常
	 * @return 端口转发绑定的端口
	 */
	int startForward(boolean bindLocal, int port, String remoteHost, int remotePort) throws IOException;

	/**
	 * @return 客户端版本号
	 */
	int getVersion();
	
	boolean isAlive();
	
	long getLastAlive();
	
	long getStartTime();
	
	String getExtraData();

	/**
	 * @return 是否支持更换ip
	 */
	boolean canChangeIp();
	
	/**
	 * 60秒内重复请求无效
	 * @return 返回true表示请求成功
	 */
	boolean requestChangeIp();
	
	PortForwarder[] getForwarders();
	
	long getSendTraffic();
	long getReceiveTraffic();
	
	Collection<Traffic> getTrafficDetail();

	/**
	 * @return Android客户端设备信息
	 */
	String getDeviceInfo();

	Socket createRemoteSocket();

	Socket waitingConnectSocket();

	String getLbs();

}
