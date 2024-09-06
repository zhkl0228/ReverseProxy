package cn.banny.rp.client;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;


/**
 * @author zhkl0228
 *
 */
public abstract class AbstractSocketProxy implements SocketProxy {
	
	private static final Logger log = LoggerFactory.getLogger(AbstractSocketProxy.class);

	protected final SocketChannel route;
	protected final int socket;
	protected final ByteBuffer packetWriteBuffer;
	private final AbstractReverseProxyClient client;
	
	public AbstractSocketProxy(SocketChannel route, int socket, ByteBuffer writeBuffer,
			AbstractReverseProxyClient client) {
		super();
		this.route = route;
		this.socket = socket;
		this.packetWriteBuffer = writeBuffer;
		this.client = client;
	}

	private Socket connectedSocket;

	protected final void notifySocketConnected(Socket socket) {
		try {
			connectedSocket = socket;
			InetAddress localAddr = socket.getLocalAddress();
			int localPort = socket.getLocalPort();
			packetWriteBuffer.mark();
			packetWriteBuffer.position(packetWriteBuffer.position() + 4);
			packetWriteBuffer.put((byte) 0x1);
			packetWriteBuffer.putInt(this.socket);
			byte[] data = localAddr.getAddress();
			packetWriteBuffer.put((byte) data.length);
			packetWriteBuffer.put(data);
			packetWriteBuffer.putShort((short) localPort);
			packetWriteBuffer.limit(packetWriteBuffer.position()).reset();
			client.sendResponse(route, packetWriteBuffer);
		} catch (IOException e) {
			if(log.isDebugEnabled()) {
				log.debug(e.getMessage(), e);
			}
		}
	}

	protected final Socket getConnectedSocket() {
		return connectedSocket;
	}
}
