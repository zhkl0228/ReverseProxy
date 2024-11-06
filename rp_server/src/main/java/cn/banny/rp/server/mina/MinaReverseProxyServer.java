package cn.banny.rp.server.mina;

import cn.banny.rp.server.ReverseProxyServer;
import cn.banny.rp.server.ServerHandler;
import org.apache.mina.core.filterchain.DefaultIoFilterChainBuilder;
import org.apache.mina.core.service.IoAcceptor;
import org.apache.mina.core.service.IoHandler;
import org.apache.mina.filter.codec.ProtocolCodecFilter;
import org.apache.mina.filter.codec.ProtocolDecoder;
import org.apache.mina.filter.codec.ProtocolEncoder;
import org.apache.mina.filter.ssl.SslFilter;
import org.apache.mina.transport.socket.nio.NioSocketAcceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Arrays;

/**
 * @author zhkl0228
 *
 */
public class MinaReverseProxyServer implements ReverseProxyServer {

	private final Logger logger = LoggerFactory.getLogger(MinaReverseProxyServer.class);
	
	private int listenPort;
	
	private IoAcceptor ioAcceptor;
	
	private ServerHandler handler;
	
	/* (non-Javadoc)
	 * @see cn.banny.rp.server.mina.ReverseProxyServer#setHandler(cn.banny.rp.server.mina.ServerHandler)
	 */
	@Override
	public void setHandler(ServerHandler handler) {
		assert handler instanceof IoHandler;
		
		this.handler = handler;
	}

	/* (non-Javadoc)
	 * @see cn.banny.rp.server.mina.ReverseProxyServer#setListenPort(int)
	 */
	@Override
	public void setListenPort(int listenPort) {
		this.listenPort = listenPort;
	}
	
	private int processorCount;

	public void setProcessorCount(int processorCount) {
		this.processorCount = processorCount;
	}

	private boolean useSSL;

	public boolean isUseSSL() {
		return useSSL;
	}

	public void setUseSSL(boolean useSSL) {
		this.useSSL = useSSL;
	}

	/* (non-Javadoc)
	 * @see cn.banny.rp.server.mina.ReverseProxyServer#initialize()
	 */
	@Override
	public void initialize() throws Exception {
		if(listenPort < 1024) {
			throw new IllegalArgumentException("listen port must less than 1024");
		}

		if(ioAcceptor == null) {
			if(processorCount < 1) {
				processorCount = Runtime.getRuntime().availableProcessors() + 1;
			}
			NioSocketAcceptor nioSocketAcceptor = new NioSocketAcceptor(processorCount);
			nioSocketAcceptor.setReuseAddress(true);
			ioAcceptor = nioSocketAcceptor;
		}
		
		ProtocolEncoder encoder = new LengthArrayMessageEncoder();
		ProtocolDecoder decoder = new LengthArrayMessageDecoder();
		DefaultIoFilterChainBuilder chain = ioAcceptor.getFilterChain();
		if (useSSL) {
			try (InputStream inputStream = getClass().getResourceAsStream("/server.jks")) {
				final String STORE_PASSWORD = "rp_pass";
				final String KEY_PASSWORD = "rp_pass";

				KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
				ks.load(inputStream, STORE_PASSWORD.toCharArray());

				KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
				kmf.init(ks, KEY_PASSWORD.toCharArray());

				X509TrustManager tm = new X509TrustManager() {
					public void checkClientTrusted(X509Certificate[] certs, String authType) throws CertificateException {
						if (logger.isDebugEnabled()) {
							logger.debug("checkClientTrusted certs={}, authType={}", Arrays.toString(certs), authType);
						}

						if (certs == null || certs.length == 0 || authType == null || authType.isEmpty()) {
							throw new IllegalArgumentException("null or zero-length parameter");
						}

						try (InputStream inputStream = MinaReverseProxyServer.class.getResourceAsStream("/client.crt")) {
							CertificateFactory cf = CertificateFactory.getInstance("X.509");
							X509Certificate clientCert = (X509Certificate) cf.generateCertificate(inputStream);

							for (X509Certificate cert : certs) {
								cert.verify(clientCert.getPublicKey());
							}
						} catch (Exception e) {
							throw new CertificateException("error in validating certificate", e);
						}
					}

					public void checkServerTrusted(X509Certificate[] certs, String authType) {
						if (logger.isDebugEnabled()) {
							logger.debug("checkServerTrusted certs={}, authType={}", Arrays.toString(certs), authType);
						}
					}

					public X509Certificate[] getAcceptedIssuers() {
						return new X509Certificate[0];
					}
				};

				SSLContext context = SSLContext.getInstance("TLS");
				context.init(kmf.getKeyManagers(), new TrustManager[]{tm}, null);

				SslFilter sslFilter = new SslFilter(context);
				chain.addFirst("sslFilter", sslFilter);
			} catch (IOException | KeyStoreException | NoSuchAlgorithmException | CertificateException | UnrecoverableKeyException | KeyManagementException e) {
				throw new IllegalStateException(e);
			}
		}
		chain.addLast("codec", new ProtocolCodecFilter(encoder, decoder));
		
		ioAcceptor.setHandler((IoHandler) this.handler);
		ioAcceptor.bind(new InetSocketAddress(listenPort));
		ioAcceptor.getSessionConfig().setReaderIdleTime(60);
	}
	
	/* (non-Javadoc)
	 * @see cn.banny.rp.server.mina.ReverseProxyServer#destroy()
	 */
	@Override
	public void destroy() {
		if(ioAcceptor != null) {
			ioAcceptor.unbind();
			ioAcceptor.dispose(true);
			ioAcceptor = null;
		}
	}

}
