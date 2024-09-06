package cn.banny.rp.socks;

public interface SocksServer {

    void start() throws Exception;

    void stopSilent();

    int getBindPort();

    /**
     * 设置启用状态
     * @param active 是否启用服务器
     */
    @SuppressWarnings("unused")
    void setActive(boolean active);

    @SuppressWarnings("unused")
    void setSocketFactory(SocketFactory socketFactory);

}
