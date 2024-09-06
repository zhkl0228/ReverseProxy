package cn.banny.rp.forward;

import java.net.InetAddress;

/**
 * @author zhkl0228
 *
 */
public interface ForwarderListener {
	
	void notifyForwarderClosed(RouteForwarder forwarder);
	
	void notifyConnectSuccess(RouteForwarder forwarder, InetAddress localAddr, int localPort);

}
