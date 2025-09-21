package cn.banny.rp.test;

import cn.banny.rp.socks.bio.BIOSocksServer;

import java.net.InetSocketAddress;
import java.util.Scanner;

public class SocksTest {

    public static void main(String[] args) throws Exception {
        BIOSocksServer socksServer = new BIOSocksServer(new InetSocketAddress(20250));
        socksServer.start();
        new Scanner(System.in).nextLine();
        socksServer.stopSilent();
    }

}
