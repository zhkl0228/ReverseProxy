package cn.banny.rp;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;

/**
 * @author zhkl0228
 *
 */
public interface ReverseProxyReceiver extends Closeable {
	
	/**
	 * 连接成功
	 * @param localAddr local address
	 * @param localPort local port
	 */
	void parseRequestConnectResponse(InetAddress localAddr, int localPort);

	void parseRequestCloseResponse();
	
	/**
	 * 从客户端发来的读取数据
	 * @param data 数据
	 * @param offset 偏移
	 * @param length 长度
	 */
	void parseReadData(byte[] data, int offset, int length);
	
	/**
	 * 客户端请求关闭
	 */
	void parseClosed();
	
	/**
	 * 客户端发生IO异常
	 * @param ioe 异常信息
	 */
	void parseException(IOException ioe);

	/**
	 * 半开关
	 * @param flag 状态
	 */
	void shutdownHalf(boolean flag);
	
	InetSocketAddress getDestAddress();

}
