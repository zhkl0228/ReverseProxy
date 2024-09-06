package cn.banny.rp.handler;

import cn.banny.rp.MessageDeliver;

/**
 * @author zhkl0228
 *
 */
public interface ExtDataHandler {
	
	void handle(byte[] data, MessageDeliver deliver);

}
