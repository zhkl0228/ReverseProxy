package cn.banny.rp.forward;

import cn.banny.rp.ReverseProxyReceiver;

import java.io.Closeable;
import java.nio.ByteBuffer;

/**
 * @author zhkl0228
 *
 */
public interface RouteForwarder extends Closeable {
	
	void writeData(ByteBuffer buffer);

	void checkForwarder();

}
