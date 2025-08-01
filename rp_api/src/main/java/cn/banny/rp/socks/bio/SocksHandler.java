package cn.banny.rp.socks.bio;

import cn.banny.rp.ReverseProxy;
import cn.banny.rp.forward.StreamSocket;
import cn.banny.rp.socks.SocketFactory;
import cn.banny.rp.socks.SocketType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.concurrent.ExecutorService;

public class SocksHandler implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(SocksHandler.class);

    private static final int CONNECT_TIMEOUT = 30000;
    private static final int SO_TIMEOUT = 600000;

    private final ExecutorService executorService;
    private final Socket socket;
    private final SocketFactory socketFactory;

    SocksHandler(ExecutorService executorService, Socket socket, SocketFactory socketFactory) {
        this.executorService = executorService;
        this.socket = socket;
        this.socketFactory = socketFactory;
    }

    public void handle() {
        Thread thread = new Thread(this, getClass().getSimpleName() + "[" + socket + "]");
        thread.setDaemon(true);
        thread.start();
    }

    @Override
    public void run() {
        InputStream inputStream = null;
        OutputStream outputStream = null;
        try {
            socket.setSoTimeout(SO_TIMEOUT);
            inputStream = socket.getInputStream();
            outputStream = socket.getOutputStream();
            process(inputStream, outputStream);
        } catch (IOException e) {
            log.debug("handle socks failed: socket={}", socket, e);

            ReverseProxy.closeQuietly(inputStream);
            ReverseProxy.closeQuietly(outputStream);
            ReverseProxy.closeQuietly(socket);
        }
    }

    private void process(InputStream inputStream, OutputStream outputStream) throws IOException {
        DataInputStream dis = new DataInputStream(inputStream);
        byte v = dis.readByte();
        switch (v) {
            case 0x4:
                handleConnectV4(dis, inputStream, outputStream);
                break;
            case 0x5:
                handleV5(dis, inputStream, outputStream);
                break;
            default:
                throw new IOException("Not socks request");
        }
    }

    private Socket connectSocket(SocketType socketType, InetSocketAddress address) throws IOException {
        if (socketFactory != null) {
            return socketFactory.connectSocket(socketType, address);
        }
        Socket socket = new Socket();
        try {
            socket.setSoTimeout(SO_TIMEOUT);
            socket.connect(address, CONNECT_TIMEOUT);
            return socket;
        } catch (IOException e) {
            ReverseProxy.closeQuietly(socket);
            throw e;
        }
    }

    private void handleV5(DataInputStream dis, InputStream inputStream, OutputStream outputStream) throws IOException {
        DataOutputStream dos = new DataOutputStream(outputStream);

        byte methods = dis.readByte();
        for(int i = 0; i < methods; i++) {
            dis.readByte();
        }
        dos.write(new byte[] { 0x5, 0x0 }); // no auth
        dos.flush();

        byte v = dis.readByte();
        if (v != 5) {
            throw new IOException("Unsupported handleConnect version: " + v);
        }

        byte ip = dis.readByte();
        if(ip != 1) {
            throw new IOException("Unsupported ip version type: " + ip);
        }

        dis.readByte();//0

        final Socket socket;

        DateFormat dateFormat = new SimpleDateFormat("[yyyy-MM-dd HH:mm:ss]");
        StringBuilder builder = new StringBuilder(dateFormat.format(new Date())).append("PROXY => ");
        byte addrType = dis.readByte();
        if(addrType == 3) {//host
            byte[] hb = new byte[dis.readUnsignedByte()];
            dis.readFully(hb);
            String host = new String(hb, StandardCharsets.UTF_8);
            int port = dis.readUnsignedShort();

            socket = connectSocket(SocketType.V5Host, new InetSocketAddress(host, port));
            builder.append(host).append(":").append(port);
        } else if(addrType == 1) {//address
            byte[] ipv4 = new byte[4];
            dis.readFully(ipv4);
            int port = dis.readUnsignedShort();

            InetAddress address = InetAddress.getByAddress(ipv4);
            socket = connectSocket(SocketType.V5, new InetSocketAddress(address, port));
            builder.append(address.getHostAddress()).append(":").append(port);
        } else {
            throw new IOException("Unsupported tcp address type: " + addrType);
        }

        InetSocketAddress socketAddress = (InetSocketAddress) socket.getRemoteSocketAddress();
        byte[] ipv4 = socketAddress.getAddress().getAddress();
        if (ipv4.length != 4) {
            throw new IOException("ipv4 failed: " + Arrays.toString(ipv4));
        }

        dos.writeInt(0x5000001);
        dos.write(ipv4);
        dos.writeShort(socketAddress.getPort());
        dos.flush();

        ShutdownListener listener = new SocksShutdownListener(builder.toString());
        StreamSocket s1 = StreamSocket.forSocket(this.socket);
        StreamSocket s2 = StreamSocket.forSocket(socket);
        executorService.submit(new StreamPipe(s1, inputStream, s2, socket.getOutputStream(), listener));
        executorService.submit(new StreamPipe(s2, socket.getInputStream(), s1, outputStream, listener));
    }

    private void handleConnectV4(DataInputStream dis, InputStream inputStream, OutputStream outputStream) throws IOException {
        DataOutputStream dos = new DataOutputStream(outputStream);

        byte cd = dis.readByte();
        if(cd != 1) {
            throw new IOException("Unsupported socks CONNECT type: " + cd);
        }

        int port = dis.readUnsignedShort();

        byte[] ipv4 = new byte[4];
        dis.readFully(ipv4);

        ByteArrayOutputStream baos = new ByteArrayOutputStream(32);
        byte b;
        while((b = dis.readByte()) != 0) {
            baos.write(b);
        }
        String user = new String(baos.toByteArray(), StandardCharsets.UTF_8);
        log.debug("handleV4 user={}", user);

        final Socket socket;

        DateFormat dateFormat = new SimpleDateFormat("[yyyy-MM-dd HH:mm:ss]");
        StringBuilder builder = new StringBuilder(dateFormat.format(new Date())).append("PROXY => ");
        if(ipv4[0] == 0 && ipv4[1] == 0 && ipv4[2] == 0 && ipv4[3] != 0) { // socksv4a
            baos.reset();
            while((b = dis.readByte()) != 0) {
                baos.write(b);
            }
            String host = new String(baos.toByteArray(), StandardCharsets.UTF_8);
            socket = connectSocket(SocketType.V4A, new InetSocketAddress(host, port));
            builder.append(host).append(":").append(port);
        } else { // socksv4
            InetAddress address = InetAddress.getByAddress(ipv4);
            socket = connectSocket(SocketType.V4, new InetSocketAddress(address, port));
            builder.append(address.getHostAddress()).append(":").append(port);
        }

        InetSocketAddress socketAddress = (InetSocketAddress) socket.getRemoteSocketAddress();
        ipv4 = socketAddress.getAddress().getAddress();
        if (ipv4.length != 4) {
            throw new IOException("ipv4 failed: " + Arrays.toString(ipv4));
        }
        dos.writeShort(0x5a);
        dos.writeShort(port);
        dos.write(ipv4);
        dos.flush();

        ShutdownListener listener = new SocksShutdownListener(builder.toString());
        StreamSocket s1 = StreamSocket.forSocket(this.socket);
        StreamSocket s2 = StreamSocket.forSocket(socket);
        executorService.submit(new StreamPipe(s1, inputStream, s2, socket.getOutputStream(), listener));
        executorService.submit(new StreamPipe(s2, socket.getInputStream(), s1, outputStream, listener));
    }

}
