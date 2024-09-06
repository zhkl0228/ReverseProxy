package cn.banny.rp.client;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * @author zhkl0228
 *
 */
public interface SocketProxy {
	
	void close(boolean notify);
	
	void writeData(ByteBuffer data);
	
	void connect() throws IOException;

	void shutdownHalf(boolean flag);

}
