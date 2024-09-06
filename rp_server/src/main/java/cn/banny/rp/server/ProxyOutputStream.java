/**
 * 
 */
package cn.banny.rp.server;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * @author zhkl0228
 *
 */
public class ProxyOutputStream extends OutputStream {
	
	private final ByteArrayOutputStream baos = new ByteArrayOutputStream();
	
	private final RemoteSocket socket;

	public ProxyOutputStream(RemoteSocket socket) {
		super();
		this.socket = socket;
	}

	/* (non-Javadoc)
	 * @see java.io.OutputStream#write(int)
	 */
	@Override
	public synchronized void write(int b) throws IOException {
		baos.write(b);
	}

	@Override
	public synchronized void flush() throws IOException {
		super.flush();
		
		if(baos.size() < 1) {
			return;
		}
		
		byte[] data = baos.toByteArray();
		baos.reset();
		
		socket.sendData(data);
	}

}
