package cn.banny.rp.server.socks;

import cn.banny.rp.auth.AuthHandler;

import java.io.EOFException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;

/**
 * @author zhkl0228
 *
 */
class AIOSocksHandler extends AbstractSocksHandler<AsynchronousSocketChannel> implements CompletionHandler<Integer, AsynchronousSocketChannel> {
	
	private final ByteBuffer readBuffer;
	private final AsynchronousSocketChannel socket;
	
	AIOSocksHandler(AIOProxyServer proxyServer, ByteBuffer readBuffer, AsynchronousSocketChannel socket, AuthHandler authHandler, boolean supportV4) {
		super(proxyServer, authHandler, supportV4);
		this.readBuffer = readBuffer;
		this.socket = socket;
		
		socket.read(readBuffer, socket, this);
	}

	@Override
	public void completed(Integer result, AsynchronousSocketChannel socket) {
		if(result == -1) {
			failed(new EOFException(), socket);
			return;
		}
		
		if(result < 1) {
			socket.read(readBuffer, socket, this);
			return;
		}
		
		readBuffer.flip();
		parseReadBuffer(readBuffer, socket);
	}
	
	@Override
	protected void parseReadBufferFinish(ByteBuffer readBuffer,
			AsynchronousSocketChannel socket, boolean needRead) {
		super.parseReadBufferFinish(readBuffer, socket, needRead);
		
		if(needRead) {
			socket.read(readBuffer, socket, this);
		}
	}

	@Override
	protected void writeData(ByteBuffer buffer) {
		try {
			while (buffer.hasRemaining()) {
				socket.write(buffer).get();
			}
		} catch(Throwable t) {
			failed(t, null);
		}
	}

}
