package cn.banny.rp.client;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * @author zhkl0228
 *
 */
public class PortForwardRequest {

	private final int remotePort;
	private final String host;
	private final int port;
	
	PortForwardRequest(int remotePort, String host, int port) {
		super();
		this.remotePort = remotePort;
		this.host = host;
		this.port = port;
	}
	
	private byte tryCount;

	ByteBuffer createBuffer() throws UnsupportedEncodingException {
		tryCount++;
		
		byte[] hostBytes = host.getBytes(StandardCharsets.UTF_8);
		ByteBuffer buffer = ByteBuffer.allocate(hostBytes.length + 12);
		buffer.position(4);
		buffer.put((byte) 0xA);
		buffer.putShort((short) remotePort);
		buffer.putShort((short) hostBytes.length);
		buffer.put(hostBytes);
		buffer.putShort((short) port);
		buffer.flip();
		return buffer;
	}
	
	boolean isValid() {
		return tryCount < 25;
	}

	public int getRemotePort() {
		return remotePort;
	}
}
