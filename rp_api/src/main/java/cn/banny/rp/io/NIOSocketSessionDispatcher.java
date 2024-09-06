package cn.banny.rp.io;

import cn.banny.rp.ReverseProxy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.Iterator;
import java.util.Set;

/**
 * @author zhkl0228
 *
 */
public class NIOSocketSessionDispatcher {
	
	private static final Logger log = LoggerFactory.getLogger(NIOSocketSessionDispatcher.class);
	
	private final ByteBuffer readBuffer;

	public NIOSocketSessionDispatcher(ByteBuffer readBuffer) {
		super();
		this.readBuffer = readBuffer;
	}

	public void dispatch(Set<SelectionKey> keys) {
		try {
			processSelectionKeys(keys.iterator());
		} finally {
			keys.clear();
		}
	}

	private void processSelectionKeys(Iterator<SelectionKey> selectionKeys) {
		while(selectionKeys.hasNext()) {
			SelectionKey key = selectionKeys.next();
			SelectableChannel channel = key.channel();
			NIOSocketSession session = (NIOSocketSession) key.attachment();
			
			try {
				if(!key.isValid()) {
					continue;
				}

				if(key.isConnectable()) {
					session.processConnect((SocketChannel) channel);
				}
				if(key.isWritable()) {
					session.processWrite((SocketChannel) channel, key);
				}
				if(key.isReadable()) {
					// System.out.println("key=" + key + ", channel=" + channel);
					processRead((SocketChannel) channel, session, key);
				}
				if(channel instanceof ServerSocketChannel && key.isAcceptable()) {
					session.processAccept((ServerSocketChannel) channel);
				}
			} catch(EOFException e) {
				key.cancel();
				session.processException(channel, e);
			} catch(IOException e) {
				key.cancel();
				ReverseProxy.closeQuietly(channel);
				session.processException(channel, e);
				session.notifyClosed(channel);
			} catch(Throwable e) {
				if(log.isDebugEnabled()) {
					log.debug(e.getMessage(), e);
				}
				key.cancel();
				ReverseProxy.closeQuietly(channel);
				session.notifyClosed(channel);
			} finally {
				selectionKeys.remove();
			}
		}
	}

	private void processRead(SocketChannel channel,
			NIOSocketSession session, SelectionKey key) throws IOException {
		int read;
		do {
			readBuffer.clear();
			read = channel.read(readBuffer);
			if(read == -1) {
				throw new EOFException();
			}
			if(read < 1) {
				break;
			}
			
			readBuffer.flip();
			session.processRead(channel, readBuffer, key);
		} while(true);
	}

}
