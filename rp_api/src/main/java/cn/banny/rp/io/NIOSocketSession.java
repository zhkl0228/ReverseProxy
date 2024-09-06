package cn.banny.rp.io;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.*;

/**
 * @author zhkl0228
 *
 */
public interface NIOSocketSession {
	
	void processRead(SocketChannel session, ByteBuffer buffer, SelectionKey key) throws IOException;
	
	void processWrite(SocketChannel session, SelectionKey key) throws IOException;
	
	void processConnect(SocketChannel session);
	
	void processAccept(ServerSocketChannel server);
	
	/**
	 * 如果cause是EOFException则表示读到末尾了，有可能是半关闭状态
	 * @param session 会话
	 * @param cause 原因
	 */
	void processException(SelectableChannel session, Throwable cause);
	
	void notifyClosed(SelectableChannel session);

}
