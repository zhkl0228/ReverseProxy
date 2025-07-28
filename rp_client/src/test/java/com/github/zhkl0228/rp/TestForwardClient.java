package com.github.zhkl0228.rp;

import cn.banny.rp.auth.AuthResult;
import cn.banny.rp.client.AuthListener;
import cn.banny.rp.client.DefaultReverseProxyClientFactory;
import cn.banny.rp.client.ReverseProxyClient;
import cn.banny.rp.client.ReverseProxyClientFactory;
import cn.banny.rp.forward.ForwarderType;
import cn.banny.rp.server.forward.PortForwarderType;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.TimeUnit;

public class TestForwardClient {

    public static void main(String[] args) throws Exception {
        ReverseProxyClientFactory factory = new DefaultReverseProxyClientFactory();
        ReverseProxyClient reverseProxyClient = factory.createClient("localhost", 2016, false, TestForwardClient.class.getSimpleName());
        reverseProxyClient.setAuthListener(new AuthListener() {
            @Override
            public boolean onAuthResponse(ReverseProxyClient client, AuthResult authResult) {
                client.requestForward(8088, PortForwarderType.AIO, 8080);
                return false;
            }
            @Override
            public void onPortForward(ReverseProxyClient client, int remotePort, String host, int port) {
                DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                System.out.printf("[%s][%s]onPortForward remotePort=%d, host=%s, port=%d%n", dateFormat.format(new Date()), "localhost", remotePort, host, port);
            }
            @Override
            public void onDisconnect(ReverseProxyClient client, AuthResult authResult) {
            }
        });
        reverseProxyClient.initialize();
        reverseProxyClient.login("ab", "cd");
        TimeUnit.HOURS.sleep(1);
    }

}
