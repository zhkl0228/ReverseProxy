package cn.banny.rp.server.socks;

import cn.banny.rp.ReverseProxy;
import cn.banny.rp.auth.AuthHandler;
import cn.banny.rp.auth.AuthResult;
import cn.banny.rp.forward.ForwarderListener;
import cn.banny.rp.forward.RouteForwarder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * @author zhkl0228
 *
 */
public abstract class AbstractSocksHandler<T extends Closeable> implements ForwarderListener {
	
	private static final Logger log = LoggerFactory.getLogger(AbstractSocksHandler.class);
	
	private final AbstractProxyServer<T> proxyServer;
	protected final AuthHandler authHandler;
	private final boolean supportV4;

	AbstractSocksHandler(AbstractProxyServer<T> proxyServer, AuthHandler authHandler, boolean supportV4) {
		super();
		this.proxyServer = proxyServer;
		this.authHandler = authHandler;
		this.supportV4 = supportV4;
	}
	
	protected byte auth = -1;
	private AuthResult authResult;
	
	private int socksVersion;
	private int socksPort;
	private byte[] socksIp;

	final void parseReadBuffer(ByteBuffer readBuffer, T socket) {
		readBuffer.order(ByteOrder.BIG_ENDIAN);
		boolean needRead = true;
		try {
			readBuffer.mark();
			byte version = readBuffer.get();
			if(version == 0x4) {
				socksVersion = version;
				if(!supportV4) {
					failed(new IOException("Not enabled socksv4"), socket);
					return;
				}
				needRead = handleConnectV4(readBuffer, socket);
				return;
			}
			socksVersion = 5;
			
			readBuffer.reset();
			if(auth == -1) {
				selectAuth(readBuffer, socket);
				return;
			}
			
			if(auth == 0 ||
					authHandler == null) {
				authResult = AuthResult.authOk(0L, null);
			}
			
			if(authResult == null) {
				handleAuth(readBuffer, socket);
				return;
			}
			
			needRead = handleConnectV5(readBuffer, socket);
		} catch(IOException | BufferUnderflowException t) {
			failed(t, socket);
		} catch(Exception e) {
            log.warn("parse read buffer failed: {}", e.getMessage(), e);
			failed(e, socket);
		} finally {
			readBuffer.clear();
			parseReadBufferFinish(readBuffer, socket, needRead);
		}
	}

	@Override
	public void notifyConnectSuccess(RouteForwarder forwarder, InetAddress localAddr, int localPort) {
        ByteBuffer buffer;
        if(socksVersion == 0x4) {
            buffer = ByteBuffer.allocate(8);
			buffer.order(ByteOrder.BIG_ENDIAN);
			buffer.put(new byte[] { 0x0, 0x5A });
			buffer.putShort((short) socksPort);
			buffer.put(socksIp, 0, 4);

        } else {
            buffer = ByteBuffer.allocate(10);
			buffer.order(ByteOrder.BIG_ENDIAN);
			buffer.putInt(0x5000001);
			buffer.put(localAddr.getAddress(), 0, 4);
			buffer.putShort((short) localPort);

        }
        buffer.flip();
        forwarder.writeData(buffer);
    }

	@Override
	public void notifyForwarderClosed(RouteForwarder forwarder) {
		proxyServer.notifyForwarderClosed(forwarder);
	}

	public final void failed(Throwable exc, T socket) {
		if(log.isDebugEnabled()) {
			log.debug(exc.getMessage(), exc);
		}
		ReverseProxy.closeQuietly(socket);
	}
	
	protected void parseReadBufferFinish(ByteBuffer readBuffer, T socket, boolean needRead) {
	}

	private boolean handleConnectV4(ByteBuffer readBuffer, T socket) throws IOException {
		byte cd = readBuffer.get();
		if(cd != 1) {
			failed(new IOException("Unsupported socks CONNECT type: " + cd), socket);
			return true;
		}
		
		int port = readBuffer.getShort() & 0xFFFF;
		
		byte[] ipv4 = new byte[4];
		readBuffer.get(ipv4);

		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		byte b;
		while((b = readBuffer.get()) != 0) {
			baos.write(b);
		}
		String user;
		try {
			user = baos.toString("UTF-8");
		} catch(UnsupportedEncodingException e) {
			user = baos.toString();
		}
		
		if(ipv4[0] == 0 && ipv4[1] == 0 && ipv4[2] == 0 && ipv4[3] != 0) { // socksv4a
			baos.reset();
			while((b = readBuffer.get()) != 0) {
				baos.write(b);
			}
			String host = baos.toString("UTF-8");
			beforeForward(socket);
			InetAddress address = proxyServer.addForward(socket, host, port, this, user, null);
			ipv4 = address.getAddress();
		} else { // socksv4
			InetAddress address = InetAddress.getByAddress(ipv4);
			beforeForward(socket);
			proxyServer.addForward(socket, address, port, this, user, null);
		}
		socksPort = port;
		socksIp = ipv4;
		return false;
	}

	private boolean handleConnectV5(ByteBuffer readBuffer, T socket) throws IOException {
		byte v = readBuffer.get();
		if(v != 5) {
			failed(new IOException("Unsupported handleConnect version: " + v), socket);
			return true;
		}
		
		byte ip = readBuffer.get();
		if(ip != 1) {
			failed(new IOException("Unsupported ip version type: " + ip), socket);
			return true;
		}
		
		readBuffer.get();//0
		
		byte addrType = readBuffer.get();
		if(addrType == 3) {//host
			byte[] hb = new byte[readBuffer.get()];
			readBuffer.get(hb);
			String host;
            host = new String(hb, StandardCharsets.UTF_8);
            int port = readBuffer.getShort() & 0xFFFF;
			beforeForward(socket);
			proxyServer.addForward(socket, host, port, this, user, pass);
			return false;
		}
		
		if(addrType == 1) {//address
			byte[] ipv4 = new byte[4];
			readBuffer.get(ipv4);
			InetAddress address = InetAddress.getByAddress(ipv4);
			int port = readBuffer.getShort() & 0xFFFF;
			beforeForward(socket);
			proxyServer.addForward(socket, address, port, this, user, pass);
			return false;
		}
		
		failed(new IOException("Unsupported tcp address type: " + addrType), socket);
		return true;
	}

	protected void beforeForward(T socket) {
	}
	
	private String user, pass;

	private void handleAuth(ByteBuffer readBuffer, T socket) {
		byte magic = readBuffer.get();
		if(magic != 1) {
			failed(new IOException("Unsupported auth magic: " + magic), socket);
			return;
		}
		
		byte[] ub = new byte[readBuffer.get()];
		readBuffer.get(ub);
		byte[] pb = new byte[readBuffer.get()];
		readBuffer.get(pb);

        user = new String(ub, StandardCharsets.UTF_8);
        pass = new String(pb, StandardCharsets.UTF_8);
        authResult = authHandler.auth(user, pass);
		if(authResult == null) {
			authResult = AuthResult.authOk(0L, null);
		}
		if(authResult.getStatus() != 0) {
			failed(new IOException("Auth failed: user=" + user + ", pass=" + pass), socket);
			return;
		}
		
		// authHandler.onAuth(user);
		ByteBuffer authOk = ByteBuffer.wrap(new byte[] {
			0x1, 0x0
		});
		writeData(authOk);
	}

	private void selectAuth(ByteBuffer readBuffer, T socket) {
		byte v = readBuffer.get();
		if(v != 5) {
			failed(new IOException("Unsupported selectAuth version: " + v), socket);
			return;
		}
		
		byte methods = readBuffer.get();
		for(int i = 0; i < methods; i++) {
			byte b = readBuffer.get();
			
			if(b == 0 && authHandler == null) {
				auth = 0;
				break;
			}
			
			if(b == 2 && authHandler != null) {
				auth = 2;
				break;
			}
		}
		
		if(auth == -1 && authHandler == null) {
			auth = 0;
		}
		if(auth == -1) {
			auth = 2;
		}

		ByteBuffer selectAuth = ByteBuffer.wrap(new byte[] {
			0x5, auth
		});
		writeData(selectAuth);
	}
	
	protected abstract void writeData(ByteBuffer buffer);

}
