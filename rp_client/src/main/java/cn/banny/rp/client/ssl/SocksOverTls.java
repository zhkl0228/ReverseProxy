package cn.banny.rp.client.ssl;

import cn.banny.rp.ReverseProxy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;

public class SocksOverTls {

    private static final Logger log = LoggerFactory.getLogger(SocksOverTls.class);

    private static final int READ_TIMEOUT = (int) Duration.ofMinutes(5).toMillis();

    private static final int PROTO_VERS         = 5;
    private static final int NO_AUTH            = 0;
    private static final int USER_PASSW         = 2;
    private static final int NO_METHODS         = -1;
    private static final int CONNECT            = 1;

    private static final int IPV4                       = 1;
    private static final int DOMAIN_NAME                = 3;
    private static final int IPV6                       = 4;

    private static final int REQUEST_OK         = 0;
    private static final int GENERAL_FAILURE    = 1;
    private static final int NOT_ALLOWED                = 2;
    private static final int NET_UNREACHABLE    = 3;
    private static final int HOST_UNREACHABLE   = 4;
    private static final int CONN_REFUSED               = 5;
    private static final int TTL_EXPIRED                = 6;
    private static final int CMD_NOT_SUPPORTED  = 7;
    private static final int ADDR_TYPE_NOT_SUP  = 8;

    private static final String[] SOCKS_OVER_SSL_ALPN = new String[]{"http/1.1", "h2", "spdy/3"};

    public static Socket openSocksSocket(String domain, int localPort, int connectTimeMillis) throws IOException {
        SSLSocket socket = null;
        InputStream inputStream = null;
        OutputStream outputStream = null;
        try {
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, null, new SecureRandom());
            socket = (SSLSocket) context.getSocketFactory().createSocket();
            SSLParameters parameters = socket.getSSLParameters();
            parameters.setApplicationProtocols(SOCKS_OVER_SSL_ALPN);
            socket.setSSLParameters(parameters);
            socket.setSoTimeout(READ_TIMEOUT);
            socket.connect(new InetSocketAddress(domain, 443), connectTimeMillis);
            inputStream = socket.getInputStream();
            outputStream = socket.getOutputStream();
            BufferedOutputStream out =  new BufferedOutputStream(outputStream);
            DataInput dataInput = new DataInputStream(inputStream);
            out.write(PROTO_VERS);
            byte[] auth = new byte[]{NO_AUTH, USER_PASSW};
            out.write(auth.length);
            out.write(auth);
            out.flush();
            byte[] data = new byte[2];
            dataInput.readFully(data);
            if ((int) data[0] != PROTO_VERS) {
                // Maybe it's not a V5 sever after all
                // Let's try V4 before we give up
                throw new SocketException("SOCKS : it's not a V5 sever");
            }
            if ((int) data[1] == NO_METHODS) {
                throw new SocketException("SOCKS : No acceptable methods");
            }
            if (!authenticate(data[1], dataInput, out, domain)) {
                throw new SocketException("SOCKS : authentication failed");
            }
            out.write(PROTO_VERS);
            out.write(CONNECT);
            out.write(0);
            final InetSocketAddress epoint = InetSocketAddress.createUnresolved("localhost", localPort);
            /* Test for IPV4/IPV6/Unresolved */
            if (epoint.isUnresolved()) {
                out.write(DOMAIN_NAME);
                out.write(epoint.getHostName().length());
                out.write(epoint.getHostName().getBytes(StandardCharsets.ISO_8859_1));
                out.write((epoint.getPort() >> 8) & 0xff);
                out.write(epoint.getPort() & 0xff);
            } else if (epoint.getAddress() instanceof Inet6Address) {
                out.write(IPV6);
                out.write(epoint.getAddress().getAddress());
                out.write((epoint.getPort() >> 8) & 0xff);
                out.write(epoint.getPort() & 0xff);
            } else {
                out.write(IPV4);
                out.write(epoint.getAddress().getAddress());
                out.write((epoint.getPort() >> 8) & 0xff);
                out.write(epoint.getPort() & 0xff);
            }
            out.flush();
            data = new byte[4];
            dataInput.readFully(data);
            int len;
            byte[] addr;
            switch (data[1]) {
                case REQUEST_OK: {
                    // success!
                    switch (data[3]) {
                        case IPV4:
                            addr = new byte[4];
                            dataInput.readFully(addr);
                            data = new byte[2];
                            dataInput.readFully(data);
                            break;
                        case DOMAIN_NAME:
                            byte[] lenByte = new byte[1];
                            dataInput.readFully(lenByte);
                            len = lenByte[0] & 0xFF;
                            byte[] host = new byte[len];
                            dataInput.readFully(host);
                            data = new byte[2];
                            dataInput.readFully(data);
                            break;
                        case IPV6:
                            len = 16;
                            addr = new byte[len];
                            dataInput.readFully(addr);
                            data = new byte[2];
                            dataInput.readFully(data);
                            break;
                        default:
                            throw new SocketException("Reply from SOCKS server contains wrong code");
                    }
                    break;
                }
                case GENERAL_FAILURE:
                    throw new SocketException("SOCKS server general failure");
                case NOT_ALLOWED:
                    throw new SocketException("SOCKS: Connection not allowed by ruleset");
                case NET_UNREACHABLE:
                    throw new SocketException("SOCKS: Network unreachable");
                case HOST_UNREACHABLE:
                    throw new SocketException("SOCKS: Host unreachable");
                case CONN_REFUSED:
                    throw new SocketException("SOCKS: Connection refused");
                case TTL_EXPIRED:
                    throw  new SocketException("SOCKS: TTL expired");
                case CMD_NOT_SUPPORTED:
                    throw new SocketException("SOCKS: Command not supported");
                case ADDR_TYPE_NOT_SUP:
                    throw new SocketException("SOCKS: address type not supported");
                default:
                    throw new SocketException("SOCKS: unknown protocol: 0x" + Integer.toHexString(data[1]));
            }
            return socket;
        } catch (IOException e) {
            ReverseProxy.closeQuietly(inputStream);
            ReverseProxy.closeQuietly(outputStream);
            ReverseProxy.closeQuietly(socket);
            log.debug("newSocksOverTlsSocket domain={}, localPort={}", domain, localPort, e);
            throw e;
        } catch (KeyManagementException | NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean authenticate(byte method, DataInput in,
                                 BufferedOutputStream out, String domain) throws IOException {
        // No Authentication required. We're done then!
        if (method == NO_AUTH) {
            return true;
        }
        /*
         * User/Password authentication. Try, in that order :
         * - The application provided Authenticator, if any
         * - the user.name & no password (backward compatibility behavior).
         */
        if (method == USER_PASSW) {
            String userName;
            String password = null;
            final InetAddress addr = InetAddress.getByName(domain);
            PasswordAuthentication pw = Authenticator.requestPasswordAuthentication(domain, addr, 443, "SOCKS5", "SOCKS authentication", "https");
            if (pw != null) {
                userName = pw.getUserName();
                password = new String(pw.getPassword());
            } else {
                userName = null;
            }
            log.debug("authenticate: userName={}, password={}", userName, password);
            if (userName == null) {
                return false;
            }
            out.write(1);
            out.write(userName.length());
            out.write(userName.getBytes(StandardCharsets.ISO_8859_1));
            out.write(password.length());
            out.write(password.getBytes(StandardCharsets.ISO_8859_1));
            out.flush();
            byte[] data = new byte[2];
            in.readFully(data);
            if (data[1] == 0) {
                /* Authentication succeeded */
                return true;
            } else {
                throw new SocketException("SOCKS : authentication failed");
            }
        }
        return false;
    }

}
