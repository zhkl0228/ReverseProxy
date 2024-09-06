package cn.banny.rp.client;

/**
 * @author zhkl0228
 */
public interface BroadcastListener {

    /**
     * 接收广播
     * @param fromServer 是否服务器主动发送
     * @param data 广播内容
     */
    void onBroadcast(boolean fromServer, byte[] data);

}
