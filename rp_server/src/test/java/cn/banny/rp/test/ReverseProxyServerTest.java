package cn.banny.rp.test;

import cn.banny.rp.Attribute;
import cn.banny.rp.ReverseProxy;
import cn.banny.rp.Route;
import cn.banny.rp.server.AbstractServerHandler;
import cn.banny.rp.server.ServerHandler;
import cn.banny.rp.server.mina.MinaReverseProxyServer;
import cn.banny.rp.server.mina.MinaServerHandler;
import cn.banny.rp.server.socks.NIOProxyServer;
import cn.banny.rp.server.socks.ProxyServer;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.protocol.HttpContext;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.io.InputStream;
import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.net.Proxy.Type;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

/**
 * @author zhkl0228
 *
 */
public class ReverseProxyServerTest {

	public static void main(String[] args) throws Exception {
		System.setProperty("jsse.enableSNIExtension", "false");

		MinaReverseProxyServer server = new MinaReverseProxyServer();
		server.setListenPort(2016);
		server.setUseSSL(true);
		AbstractServerHandler<?> handler = new MinaServerHandler();
		handler.setReconnect(true);
		server.setHandler(handler);
		
		Authenticator.setDefault(new Authenticator() {
			@Override
			protected PasswordAuthentication getPasswordAuthentication() {
				return new PasswordAuthentication("zhkl0228", "123456".toCharArray());
			}
		});
		
		ProxyServer proxyServer = new NIOProxyServer(2017);
		proxyServer.setHandler(handler);
		proxyServer.setSupportV4(true);
		try {
			server.initialize();
			proxyServer.initialize();
			
			Scanner scanner = new Scanner(System.in);
			String line;
			while((line = scanner.nextLine()) != null) {
				if ("exit".equalsIgnoreCase(line) ||
						"quit".equalsIgnoreCase(line) ||
						"q".equals(line)) {
					break;
				}

				final Route route = select(handler, new InetSocketAddress("any", 0));
				if(route != null) {
					long start = System.currentTimeMillis();
					Attribute attribute = route.getRemoteAddressContext().createAttribute("remoteAddressContext");
					attribute.add();
					System.err.println(attribute + ", offset=" + (System.currentTimeMillis() - start));
				}
				
				if("test".equalsIgnoreCase(line)) {
					doTest(handler, route);
				}
				if("https".equalsIgnoreCase(line)) {
					ReverseProxyServerTest.doHttps(handler, route);
				}
				if("http".equalsIgnoreCase(line)) {
					ReverseProxyServerTest.doProxyHttp(new HttpHost("mm.gzmtx.cn", 8089));
				}
				if("proxy".equalsIgnoreCase(line)) {
					Proxy proxy = new Proxy(Type.SOCKS, new InetSocketAddress("localhost", 2017));
					ReverseProxyServerTest.doProxyHttp(handler, proxy, "https://www.gzmtx.cn/ip.php");
				}
				if("taobao".equalsIgnoreCase(line)) {
					Proxy proxy = new Proxy(Type.SOCKS, new InetSocketAddress("localhost", 2017));
					ReverseProxyServerTest.doProxyHttp(handler, proxy, "https://www.taobao.com/help/getip.php");
				}
				if("pip".equalsIgnoreCase(line)) {
					Proxy proxy = new Proxy(Type.SOCKS, new InetSocketAddress("mm.gzmtx.cn", 8889));
					ReverseProxyServerTest.doProxyHttp(handler, proxy, "http://www.whatismyip.com.tw");
				}
				if("change".equalsIgnoreCase(line)) {
					proxyClient = null;
					httpProxyClient = null;
					cachedClient = null;
					if(manager != null) {
						manager.shutdown();
						manager = null;
					}
					System.out.println("requested change ip");
				}
				if("baidu".equalsIgnoreCase(line)) {
					ReverseProxyServerTest.doHttp(handler, route, "https://www.baidu.com");
				}
				if("ip138".equalsIgnoreCase(line)) {
					ReverseProxyServerTest.doHttp(handler, route, "https://2024.ip138.com/");
				}
				if("bind".equalsIgnoreCase(line) &&
						route != null) {
					try {
						route.startForward(8888, "scp66.3322.org", 31000);
					} catch(IOException e) {
						e.printStackTrace(System.err);
					}
				}
				if ("bb".equalsIgnoreCase(line)) {
					handler.sendBroadcast("broadcast test".getBytes());
				}

				TimeUnit.SECONDS.sleep(1);
			}
			ReverseProxy.closeQuietly(scanner);
		} finally {
			proxyServer.destroy();

			server.destroy();
		}
	}

	private static Route select(ServerHandler serverHandler, InetSocketAddress connectAddress) {
		Route[] routes = serverHandler.getRoutes();
		if(routes.length == 1) {
			return routes[0];
		}
		
		for(Route route : routes) {
			if(canSelect(route, connectAddress)) {
				return route;
			}
		}
		return null;
	}

	/**
	 * @return 是否能选择此路由
	 */
	private static boolean canSelect(Route route, InetSocketAddress address) {
		if(!route.isAlive()) {
			return false;
		}

		if(address == null || "any".equals(address.getHostString())) {
			return true;
		}

		InetSocketAddress remote = route.getRemoteAddress();
		if(address.getPort() > 0) {
			return address.equals(remote);
		}

		return address.getAddress().equals(remote.getAddress());
	}

	private static void doTest(ServerHandler ignoredHandler, Route route) {
		try {
			Socket socket = route.createRemoteSocket();
			socket.setSoTimeout(1000);
			socket.connect(new InetSocketAddress("www.baidu.com", 80), 1000);
			socket.close();
		} catch(Throwable t) {
			t.printStackTrace(System.err);
		}
	}
	
	private static HttpClient cachedClient;
	private static Route cachedRoute;
	
	private static HttpClient createHttpClient(final ServerHandler handler, final Route route) throws NoSuchAlgorithmException, KeyManagementException {
		if(cachedRoute == route && cachedClient != null) {
			return cachedClient;
		}
		
		SSLContext ctx = SSLContext.getInstance("SSL");
		X509TrustManager tm = new X509TrustManager() {
			public void checkClientTrusted(X509Certificate[] xcs,
					String string) {
			}
			public void checkServerTrusted(X509Certificate[] xcs,
					String string) {
			}
			public X509Certificate[] getAcceptedIssuers() {
				return null;
			}
		};
		ctx.init(null, new TrustManager[] { tm }, null);
		
		Registry<ConnectionSocketFactory> registry = RegistryBuilder.<ConnectionSocketFactory>create().register("http", new ConnectionSocketFactory() {
			@Override
			public Socket createSocket(HttpContext context) {
				return route.waitingConnectSocket();
			}
			@Override
			public Socket connectSocket(int connectTimeout, Socket socket, HttpHost host,
					InetSocketAddress remoteAddress, InetSocketAddress localAddress, HttpContext context)
					throws IOException {
				long start = System.currentTimeMillis();
				socket.connect(remoteAddress, connectTimeout);
				System.err.println("connect time=" + (System.currentTimeMillis() - start));
				return socket;
			}
		}).register("https", new SSLConnectionSocketFactory(ctx) {
			@Override
			public Socket createSocket(HttpContext context) {
				return route.waitingConnectSocket();
			}
		}).build();
		PoolingHttpClientConnectionManager manager = new PoolingHttpClientConnectionManager(registry);
		manager.setMaxTotal(10);
		manager.setDefaultMaxPerRoute(1);
		RequestConfig config = RequestConfig.custom().setConnectionRequestTimeout(5000).setConnectTimeout(5000).setSocketTimeout(10000).build();
		HttpClientBuilder builder = HttpClientBuilder.create().disableAuthCaching();
		builder.disableCookieManagement().disableRedirectHandling();
		builder.setRetryHandler(new DefaultHttpRequestRetryHandler(3, true));
		cachedClient = builder.setDefaultRequestConfig(config).setConnectionManager(manager).build();
		cachedRoute = route;
		return cachedClient;
	}
	
	private static HttpClient httpProxyClient;
	
	private static HttpClient createProxyHttpClient(final HttpHost proxy) {
		if(httpProxyClient != null) {
			return httpProxyClient;
		}
		
		RequestConfig config = RequestConfig.custom().setConnectionRequestTimeout(5000).setConnectTimeout(5000).setSocketTimeout(10000).build();
		HttpClientBuilder builder = HttpClientBuilder.create().disableAuthCaching();
		builder.disableCookieManagement().disableRedirectHandling();
		
		Credentials credentials = new UsernamePasswordCredentials("zhkl0228","123456");
		AuthScope authScope = new AuthScope("mm.gzmtx.cn", 8089);
		CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
		credentialsProvider.setCredentials(authScope, credentials);
		
		builder.setRetryHandler(new DefaultHttpRequestRetryHandler(3, true)).setProxy(proxy).setDefaultCredentialsProvider(credentialsProvider);
		httpProxyClient = builder.setDefaultRequestConfig(config).build();
		return httpProxyClient;
	}
	
	private static HttpClient proxyClient;
	private static PoolingHttpClientConnectionManager manager;
	
	private static HttpClient createProxyHttpClient(final ServerHandler handler, final Proxy proxy) throws NoSuchAlgorithmException, KeyManagementException {
		if(proxyClient != null) {
			return proxyClient;
		}
		
		final ConnectionSocketFactory socketFactory = new ConnectionSocketFactory() {
			@Override
			public Socket createSocket(HttpContext context) {
				return new Socket(proxy);
			}
			@Override
			public Socket connectSocket(int connectTimeout, Socket socket, HttpHost host,
					InetSocketAddress remoteAddress, InetSocketAddress localAddress, HttpContext context)
					throws IOException {
				long start = System.currentTimeMillis();
				socket.connect(remoteAddress, connectTimeout);
				System.err.println("connect time=" + (System.currentTimeMillis() - start) + ", addr=" + remoteAddress);
				return socket;
			}
		};
		SSLContext ctx = SSLContext.getInstance("SSL");
		X509TrustManager tm = new X509TrustManager() {
			public void checkClientTrusted(X509Certificate[] xcs,
					String string) throws CertificateException {
			}
			public void checkServerTrusted(X509Certificate[] xcs,
					String string) throws CertificateException {
			}
			public X509Certificate[] getAcceptedIssuers() {
				return null;
			}
		};
		ctx.init(null, new TrustManager[] { tm }, null);
		Registry<ConnectionSocketFactory> registry = RegistryBuilder.<ConnectionSocketFactory>create().register("http", socketFactory).register("https", new SSLConnectionSocketFactory(ctx) {
			@Override
			public Socket createSocket(HttpContext context) throws IOException {
				return socketFactory.createSocket(context);
			}
			@Override
			public Socket connectSocket(int connectTimeout, Socket socket, HttpHost host,
					InetSocketAddress remoteAddress, InetSocketAddress localAddress, HttpContext context)
					throws IOException {
				return super.connectSocket(connectTimeout, socket, host, remoteAddress, localAddress, context);
			}
		}).build();
		PoolingHttpClientConnectionManager manager = new PoolingHttpClientConnectionManager(registry);
		manager.setMaxTotal(10);
		manager.setDefaultMaxPerRoute(10);
		RequestConfig config = RequestConfig.custom().setConnectionRequestTimeout(5000).setConnectTimeout(5000).setSocketTimeout(10000).build();
		HttpClientBuilder builder = HttpClientBuilder.create().disableAuthCaching();
		builder.disableCookieManagement().disableRedirectHandling();
		builder.setRetryHandler(new DefaultHttpRequestRetryHandler(3, true));
		builder.setSSLContext(ctx);
		proxyClient = builder.setDefaultRequestConfig(config).setConnectionManager(manager).build();
		ReverseProxyServerTest.manager = manager;
		return proxyClient;
	}

	private static void doProxyHttp(final HttpHost proxy) {
		HttpClient client = createProxyHttpClient(proxy);
		HttpGet get = new HttpGet("http://1212.ip138.com/ic.asp");
		InputStream inputStream = null;
		try {
			long start = System.currentTimeMillis();
			org.apache.http.HttpResponse response = client.execute(get);
			HttpEntity entity = response.getEntity();
			inputStream = entity.getContent();
			System.out.println(IOUtils.toString(inputStream, "GBK"));
			System.out.println("offset=" + (System.currentTimeMillis() - start));
		} catch (IOException e) {
			e.printStackTrace(System.err);
		} finally {
			IOUtils.closeQuietly(inputStream);
			get.releaseConnection();
		}
	}

	private static void doProxyHttp(final ServerHandler handler, final Proxy proxy, String url) throws NoSuchAlgorithmException, KeyManagementException {
		HttpClient client = createProxyHttpClient(handler, proxy);
		HttpGet get = new HttpGet(url);
		InputStream inputStream = null;
		try {
			long start = System.currentTimeMillis();
			org.apache.http.HttpResponse response = client.execute(get);
			HttpEntity entity = response.getEntity();
			inputStream = entity.getContent();
			String str = IOUtils.toString(inputStream, StandardCharsets.UTF_8);
            System.out.println(str);
            System.out.println("offset=" + (System.currentTimeMillis() - start));
		} catch (IOException e) {
			e.printStackTrace(System.err);
		} finally {
			IOUtils.closeQuietly(inputStream);
			get.releaseConnection();
		}
	}

	private static void doHttp(final ServerHandler handler, final Route route, String url) throws KeyManagementException, NoSuchAlgorithmException {
		HttpClient client = createHttpClient(handler, route);
		HttpGet get = new HttpGet(url);
		get.setHeader(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36");
		InputStream inputStream = null;
		try {
			long start = System.currentTimeMillis();
			org.apache.http.HttpResponse response = client.execute(get);
			HttpEntity entity = response.getEntity();
			if(entity != null) {
				inputStream = entity.getContent();
				System.out.println(IOUtils.toString(inputStream, StandardCharsets.UTF_8));
			}
			System.out.println("offset=" + (System.currentTimeMillis() - start) + ", lbs=" + route.getLbs());
		} catch (IOException e) {
			e.printStackTrace(System.err);
		} finally {
			IOUtils.closeQuietly(inputStream);
			get.releaseConnection();
		}
	}

	private static void doHttps(final ServerHandler handler, final Route route) throws KeyManagementException, NoSuchAlgorithmException {
		HttpClient client = createHttpClient(handler, route);
		HttpGet get = new HttpGet("https://www.gzmtx.cn/ip.php");
		InputStream inputStream = null;
		try {
			long start = System.currentTimeMillis();
			org.apache.http.HttpResponse response = client.execute(get);
			HttpEntity entity = response.getEntity();
			inputStream = entity.getContent();
			System.out.println(IOUtils.toString(inputStream, "GBK"));
			System.out.println("offset=" + (System.currentTimeMillis() - start));
		} catch (IOException e) {
			e.printStackTrace(System.err);
		} finally {
			IOUtils.closeQuietly(inputStream);
			get.releaseConnection();
		}
	}

}
