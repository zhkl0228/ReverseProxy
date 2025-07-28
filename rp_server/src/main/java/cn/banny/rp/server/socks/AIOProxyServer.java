package cn.banny.rp.server.socks;

import cn.banny.rp.ReverseProxy;
import cn.banny.rp.forward.ForwarderListener;
import cn.banny.rp.forward.RouteForwarder;
import cn.banny.rp.server.AbstractRoute;
import cn.banny.rp.server.forward.AIORouteForwarder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousChannelGroup;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.concurrent.Executors;

/**
 * @author zhkl0228
 *
 */
public class AIOProxyServer extends AbstractProxyServer<AsynchronousSocketChannel> implements ProxyServer {
	
	private static final Logger log = LoggerFactory.getLogger(AIOProxyServer.class);

	public AIOProxyServer(int port) {
		super(port);
	}
	
	private AsynchronousChannelGroup channelGroup;
	private AsynchronousServerSocketChannel server;
	
	@Override
	public void initialize() throws Exception {
		channelGroup = AsynchronousChannelGroup.withThreadPool(Executors.newCachedThreadPool(this));
		server = AsynchronousServerSocketChannel.open(channelGroup);
		server.setOption(StandardSocketOptions.SO_REUSEADDR, true);
		server.bind(new InetSocketAddress(port));
		server.accept(null, new CompletionHandler<AsynchronousSocketChannel, Void>() {
			@Override
			public void completed(AsynchronousSocketChannel result,
					Void attachment) {
				server.accept(attachment, this);

				new AIOSocksHandler(AIOProxyServer.this, ByteBuffer.allocate(128), result, authHandler, supportV4);
			}
			@Override
			public void failed(Throwable exc, Void attachment) {
				if(log.isDebugEnabled()) {
					log.debug(exc.getMessage(), exc);
				}
				destroy();
			}
		});
	}
	
	@Override
	public void destroy() {
		ReverseProxy.closeQuietly(server);
		for(RouteForwarder forwarder : getForwarders()) {
			ReverseProxy.closeQuietly(forwarder);
		}
		if(channelGroup != null) {
			try { channelGroup.shutdownNow(); } catch(IOException ignored) {}
		}
	}

	@Override
	protected RouteForwarder createForward(AsynchronousSocketChannel socket, String outHost, int outPort, AbstractRoute route, ForwarderListener listener) {
		return new AIORouteForwarder(socket, listener, route, outHost, outPort);
	}

	@Override
	protected InetSocketAddress createClientAddress(AsynchronousSocketChannel socket) throws IOException {
		return (InetSocketAddress) socket.getRemoteAddress();
	}

}
