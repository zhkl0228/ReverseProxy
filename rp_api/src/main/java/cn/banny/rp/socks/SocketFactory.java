package cn.banny.rp.socks;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

public interface SocketFactory {

    Socket connectSocket(SocketType socketType, InetSocketAddress address) throws IOException;

}
