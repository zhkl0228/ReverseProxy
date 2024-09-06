package cn.banny.rp.client;

import cn.banny.rp.client.nio.NIOReverseProxyClient;
import cn.banny.rp.client.nio.SSLReverseProxyClient;

/**
 * @author zhkl0228
 *
 */
public class DefaultReverseProxyClientFactory implements
		ReverseProxyClientFactory {

	/* (non-Javadoc)
	 * @see cn.banny.rp.client.ReverseProxyClientFactory#createClient(java.lang.String, int)
	 */
	@Override
	public ReverseProxyClient createClient(String host, int port) {
		return createClient(host, port, null);
	}

	/* (non-Javadoc)
	 * @see cn.banny.rp.client.ReverseProxyClientFactory#createClient(java.lang.String, int, java.lang.String)
	 */
	@Override
	public ReverseProxyClient createClient(String host, int port,
			String extraData) {
		return createClient(host, port, false, extraData);
	}

	@Override
	public ReverseProxyClient createClient(String host, int port, boolean useSSL) {
		return createClient(host, port, useSSL, null);
	}

	@Override
	public ReverseProxyClient createClient(String host, int port, boolean useSSL, String extraData) {
		if (useSSL) {
			return new SSLReverseProxyClient(host, port, extraData);
		} else {
			return new NIOReverseProxyClient(host, port, extraData);
		}
	}

}
