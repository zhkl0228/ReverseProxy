package cn.banny.rp.client.config;

import cn.banny.rp.client.ReverseProxyClient;
import cn.banny.rp.socks.SocksServer;
import cn.banny.rp.socks.bio.BIOSocksServer;

import java.net.InetAddress;
import java.net.InetSocketAddress;

/**
 * @author zhkl0228
 *
 */
class Forward {
	
	private final int remotePort;
	private final String toHost;
	private final int toPort;
	
	Forward(int remotePort, String toHost, int toPort) {
		super();
		this.remotePort = remotePort;
		this.toHost = toHost;
		this.toPort = toPort;
	}
	
	void bind(ReverseProxyClient client) {
		boolean socks = "socks".equals(toHost);
		try {
			if (socksServer == null && socks) {
				socksServer = new BIOSocksServer(new InetSocketAddress(InetAddress.getLoopbackAddress(), toPort));
				socksServer.start();
			}
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}

		if (socks) {
			client.requestForward(remotePort, "localhost", socksServer.getBindPort());
		} else {
			client.requestForward(remotePort, toHost, toPort);
		}
	}

	private SocksServer socksServer;

	void onDisconnect() {
		if (socksServer != null) {
			socksServer.stopSilent();
			socksServer = null;
		}
	}

}
