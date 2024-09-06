package cn.banny.rp;

import java.io.IOException;


/**
 * @author zhkl0228
 *
 */
public interface MessageDeliver {
	
	void deliverMessage(byte[] msg) throws IOException;
	
	String getAuthUser();

}
