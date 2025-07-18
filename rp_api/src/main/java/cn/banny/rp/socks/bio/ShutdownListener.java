package cn.banny.rp.socks.bio;

import java.net.Socket;

public interface ShutdownListener {

    void onStreamStart();
    void onShutdownInput(Socket socket);
    void onShutdownOutput(Socket socket);
    void onStreamEnd();
    boolean needShutdown();

}
