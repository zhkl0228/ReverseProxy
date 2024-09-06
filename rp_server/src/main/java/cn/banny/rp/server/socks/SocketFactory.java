package cn.banny.rp.server.socks;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * @author zhkl0228
 *
 */
public interface SocketFactory {
	
	Socket createSocket(InetSocketAddress clientAddress, InetAddress address, int port, String user, String pass) throws IOException;

}
