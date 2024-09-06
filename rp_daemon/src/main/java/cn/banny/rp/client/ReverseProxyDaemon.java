package cn.banny.rp.client;

import cn.banny.rp.client.config.RemoteServer;
import cn.banny.utils.IOUtils;
import org.apache.commons.daemon.Daemon;
import org.apache.commons.daemon.DaemonContext;

import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * @author zhkl0228
 *
 */
public class ReverseProxyDaemon implements Daemon {
	
	private List<RemoteServer> remoteServers;
	private final List<ReverseProxyClient> clients = new ArrayList<>();

	/* (non-Javadoc)
	 * @see org.apache.commons.daemon.Daemon#destroy()
	 */
	@Override
	public void destroy() {
		if(remoteServers != null) {
			remoteServers.clear();
			remoteServers = null;
		}
	}

	/* (non-Javadoc)
	 * @see org.apache.commons.daemon.Daemon#init(org.apache.commons.daemon.DaemonContext)
	 */
	@Override
	public void init(DaemonContext context) throws Exception {
		FileInputStream inputStream = null;
		try {
			inputStream = new FileInputStream("config.xml");
			remoteServers = ReverseProxyProcrun.parseRemoteServers(inputStream);
			if(remoteServers == null || remoteServers.isEmpty()) {
				throw new Exception("config.xml error!");
			}
		} finally {
			IOUtils.close(inputStream);
		}
	}

	/* (non-Javadoc)
	 * @see org.apache.commons.daemon.Daemon#start()
	 */
	@Override
	public void start() throws Exception {
		for(RemoteServer server : remoteServers) {
			clients.add(server.createAndLogin());
		}
	}

	/* (non-Javadoc)
	 * @see org.apache.commons.daemon.Daemon#stop()
	 */
	@Override
	public void stop() {
		for(ReverseProxyClient client : clients) {
			try { client.destroy(); } catch(Exception e) {
				e.printStackTrace(System.err);
			}
		}
		clients.clear();
	}

}
