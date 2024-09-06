package cn.banny.rp.client.config;

import cn.banny.rp.auth.AuthResult;
import cn.banny.rp.client.*;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * @author zhkl0228
 *
 */
public class RemoteServer implements AuthListener, IpChangeProcessor {

	private static final Log log = LogFactory.getLog(RemoteServer.class);
	
	private final String host;
	private final int port;
	private final String username;
	private final String password;
	private final String extraData;
	private final String changeIp;
	private final boolean useSSL;
	
	private final List<Forward> forwards = new ArrayList<>();
	
	RemoteServer(String host, int port, String username, String password, String extraData,
                 String changeIp, boolean useSSL) {
		super();
		this.host = host;
		this.port = port;
		this.username = username;
		this.password = password;
		this.extraData = extraData;
		this.changeIp = changeIp;
		this.useSSL = useSSL;
	}
	
	void addForward(Forward forward) {
		this.forwards.add(forward);
	}
	
	public ReverseProxyClient createAndLogin() throws Exception {
		ReverseProxyClientFactory factory = new DefaultReverseProxyClientFactory();
		ReverseProxyClient client = factory.createClient(host, port, useSSL, extraData);
		if (changeIp != null) {
			client.setRequestChangeIp(this);
		}
		client.setAuthListener(this);
		executeLogin(client);
		return client;
	}

	@Override
	public void requestChangeIp(ReverseProxyClient client) {
		try {
			String[] ret = new LocalCommandExecutor(changeIp).execute();
			if(ret != null &&
					log.isDebugEnabled()) {
				for(String str : ret) {
					log.debug("requestChangeIp str=" + str);
				}
			}
		} catch (IOException e) {
			log.warn(e.getMessage(), e);
		}
	}

	private void executeLogin(ReverseProxyClient client) throws Exception {
		if (log.isDebugEnabled()) {
			client.setLbs(UUID.randomUUID().toString());
		}
		client.initialize();
		client.login(username, password);
	}

	@Override
	public boolean onAuthResponse(ReverseProxyClient client,
			AuthResult authResult) {
		if(authResult.getStatus() != 0) {
			System.err.println("Auth to " + host + ':' + port + "failed: " + authResult.getMsg());
			return true;
		}
		for(Forward forward : forwards) {
			forward.bind(client);
		}
		return false;
	}

	@Override
	public void onPortForward(ReverseProxyClient client, int remotePort, String host, int port) {
		if (log.isDebugEnabled()) {
			log.debug("onPortForward remotePort=" + remotePort + ", host=" + host + ", port=" + port);
		}
	}

	@Override
	public void onDisconnect(ReverseProxyClient client, AuthResult authResult) {
		for(Forward forward : forwards) {
			forward.onDisconnect();
		}
	}

}
