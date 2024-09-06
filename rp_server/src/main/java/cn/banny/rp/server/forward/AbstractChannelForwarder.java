package cn.banny.rp.server.forward;

import cn.banny.rp.ReverseProxy;
import cn.banny.rp.forward.RouteForwarder;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

abstract class AbstractChannelForwarder implements RouteForwarder {

    ByteBuffer createStartProxy(int listenPort, String host, int port) {
        ByteBuffer buffer = ByteBuffer.allocate(16 + host.length());
        buffer.order(ByteOrder.BIG_ENDIAN);
        buffer.position(4);
        buffer.put((byte) 0x1a);
        buffer.putShort((short) listenPort);
        ReverseProxy.writeUTF(buffer, host);
        buffer.putShort((short) port);
        buffer.flip();
        return buffer;
    }

}
