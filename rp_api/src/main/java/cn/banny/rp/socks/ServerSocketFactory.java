package cn.banny.rp.socks;

import java.io.IOException;
import java.net.ServerSocket;

public interface ServerSocketFactory {

    ServerSocket newServerSocket() throws IOException;

}
