package com.github.zhkl0228.rp;

import cn.banny.rp.client.DefaultReverseProxyClientFactory;
import cn.banny.rp.client.ReverseProxyClient;
import cn.banny.rp.client.ReverseProxyClientFactory;

import java.util.concurrent.TimeUnit;

public class AliyunClient {

    public static void main(String[] args) throws Exception {
        ReverseProxyClientFactory factory = new DefaultReverseProxyClientFactory();
        ReverseProxyClient reverseProxyClient = factory.createClient("8.138.83.139", 2016, true, AliyunClient.class.getSimpleName());
        reverseProxyClient.initialize();
        reverseProxyClient.login("ab", "cd");
        TimeUnit.HOURS.sleep(1);
    }

}
