package cn.banny.rp.client;

import cn.banny.rp.auth.AuthResult;

/**
 * @author zhkl0228
 *
 */
public interface AuthListener {

	/**
	 * 通知授权结果
	 * @param client 反向客户端
	 * @param authResult 授权结果
	 * @return 返回true表示关闭远程连接
	 */
	boolean onAuthResponse(ReverseProxyClient client, AuthResult authResult);

	/**
	 * 端口转向成功启动
	 * @param client 反向客户端
	 * @param remotePort 服务器端口
	 * @param host 本地访问主机
	 * @param port 本地访问端口
	 */
	void onPortForward(ReverseProxyClient client, int remotePort, String host, int port);
	
	/**
	 * 断开连接时
	 * @param client 反向客户端
	 * @param authResult 授权结果
	 */
	void onDisconnect(ReverseProxyClient client, AuthResult authResult);

}
