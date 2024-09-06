package cn.banny.rp.server.forward;

import cn.banny.rp.ReverseProxy;
import cn.banny.rp.forward.PortForwarder;
import cn.banny.rp.forward.RouteForwarder;
import cn.banny.rp.server.AbstractRoute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.channels.AsynchronousChannelGroup;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.concurrent.Executors;

/**
 * @author zhkl0228
 *
 */
@Deprecated
public class AIOPortForwarder extends AbstractPortForwarder implements PortForwarder {
	
	private static final Logger log = LoggerFactory.getLogger(AIOPortForwarder.class);

	public AIOPortForwarder(int inPort, String outHost, int outPort, AbstractRoute route) {
		super(inPort, outHost, outPort, route);
	}
	
	private AsynchronousChannelGroup channelGroup;
	private AsynchronousServerSocketChannel server;

	/* (non-Javadoc)
	 * @see cn.banny.rp.server.PortForwarder#start()
	 */
	@Override
	public int start() throws IOException {
		channelGroup = AsynchronousChannelGroup.withThreadPool(Executors.newCachedThreadPool(this));
		server = AsynchronousServerSocketChannel.open(channelGroup);
		server.setOption(StandardSocketOptions.SO_REUSEADDR, true);
		server.bind(new InetSocketAddress(inPort));
		server.accept(null, new CompletionHandler<AsynchronousSocketChannel, Void>() {
			@Override
			public void completed(AsynchronousSocketChannel result,
					Void attachment) {
				server.accept(null, this);
				AIORouteForwarder forwarder = createForward(result);
				forwards.put(forwarder.hashCode(), forwarder);
			}
			@Override
			public void failed(Throwable exc, Void attachment) {
				if(log.isDebugEnabled()) {
					log.debug(exc.getMessage(), exc);
				}
				stop();
			}
		});
		InetSocketAddress inetSocketAddress = (InetSocketAddress) server.getLocalAddress();
		listenPort = inetSocketAddress.getPort();
		return listenPort;
	}

	private AIORouteForwarder createForward(AsynchronousSocketChannel socket) {
		return new AIORouteForwarder(socket, this, route, outHost, outPort);
	}

	/* (non-Javadoc)
	 * @see cn.banny.rp.server.PortForwarder#stop()
	 */
	@Override
	public void stop() {
		ReverseProxy.closeQuietly(server);
		for(RouteForwarder forwarder : getForwarders()) {
			ReverseProxy.closeQuietly(forwarder);
		}
		if(channelGroup != null) {
			try { channelGroup.shutdownNow(); } catch(IOException ignored) {}
		}
	}

}
