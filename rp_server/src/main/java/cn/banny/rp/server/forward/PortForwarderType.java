package cn.banny.rp.server.forward;

import cn.banny.rp.Route;
import cn.banny.rp.forward.ForwarderType;
import cn.banny.rp.forward.PortForwarder;
import cn.banny.rp.server.AbstractRoute;

public enum PortForwarderType implements ForwarderType {

    BIO,
    NIO,
    AIO,
    NewBIO;

    @Deprecated
    public PortForwarder startForward(boolean bindLocal, int port, String remoteHost,
                                      int remotePort, Route route) {
        switch (this) {
            case BIO:
                return new BIOPortForwarder(bindLocal, port, remoteHost, remotePort, (AbstractRoute) route);
            case NIO:
                return new NIOPortForwarder(bindLocal, port, remoteHost, remotePort, (AbstractRoute) route);
            case AIO:
                return new AIOPortForwarder(bindLocal, port, remoteHost, remotePort, (AbstractRoute) route);
            case NewBIO:
                return new NewBIOPortForwarder(bindLocal, port, remoteHost, remotePort, (AbstractRoute) route);
            default:
                throw new UnsupportedOperationException("type=" + this);
        }
    }

}
