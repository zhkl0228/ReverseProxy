package cn.banny.rp.forward;

import cn.banny.rp.Route;

public interface ForwarderType {

    PortForwarder startForward(boolean bindLocal, int port, String remoteHost,
                               int remotePort, Route route);

}
