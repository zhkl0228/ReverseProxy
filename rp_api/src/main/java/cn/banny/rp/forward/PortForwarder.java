package cn.banny.rp.forward;

import java.io.IOException;
import java.util.concurrent.ThreadFactory;

/**
 * @author zhkl0228
 *
 */
public interface PortForwarder extends ForwarderListener, ThreadFactory {

	String APPLICATION_PROTOCOL = "rp";
	int MAX_OPEN_BIDIRECTIONAL_STREAMS = Short.MAX_VALUE;

	String getOutHost();
	int getOutPort();

	int getListenPort();
	
	int start() throws IOException;
	void stop();

}
