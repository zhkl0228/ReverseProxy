/**
 * 
 */
package cn.banny.rp.client;

/**
 * @author zhkl0228
 *
 */
public interface ReverseProxyClientFactory {
	
	ReverseProxyClient createClient(String host, int port);
	
	ReverseProxyClient createClient(String host, int port, String extraData);

	ReverseProxyClient createClient(String host, int port, boolean useSSL);

	ReverseProxyClient createClient(String host, int port, boolean useSSL, String extraData);

}
