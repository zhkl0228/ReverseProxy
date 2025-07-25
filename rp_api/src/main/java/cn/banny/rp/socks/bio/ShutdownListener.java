package cn.banny.rp.socks.bio;

import cn.banny.rp.forward.StreamSocket;

public interface ShutdownListener {

    void onStreamStart();
    void onShutdownInput(StreamSocket socket);
    void onShutdownOutput(StreamSocket socket);
    void onStreamEnd();
    boolean needShutdown();

}
