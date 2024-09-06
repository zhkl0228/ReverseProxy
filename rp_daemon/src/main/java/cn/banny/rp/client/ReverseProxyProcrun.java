package cn.banny.rp.client;

import cn.banny.rp.ReverseProxy;
import cn.banny.rp.client.config.RemoteServer;
import cn.banny.rp.client.config.RemoteServerParser;
import org.apache.commons.daemon.Daemon;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.InputStream;
import java.util.List;
import java.util.Scanner;

/**
 * @author zhkl0228
 *
 */
public class ReverseProxyProcrun {
	
	private static Daemon daemon;

	public static void main(String[] args) throws Exception {
		String action = args.length < 1 ? null : args[0];
		
		if("stop".equals(action)) {
			if(daemon != null) {
				daemon.stop();
				daemon.destroy();
				daemon = null;
			}
			return;
		}
		
		daemon = new ReverseProxyDaemon();
		try {
			daemon.init(null);
			daemon.start();
		} catch(Exception e) {
			e.printStackTrace();
			daemon.destroy();
			return;
		}
		
		if("start".equals(action)) {
			return;
		}
		
		try {
			Scanner scanner = new Scanner(System.in);
			String line;
			while((line = scanner.nextLine()) != null) {
				if("exit".equalsIgnoreCase(line) ||
						"quit".equalsIgnoreCase(line)) {
					break;
				}
				
				Thread.sleep(1000);
			}
			ReverseProxy.closeQuietly(scanner);
		} finally {
			daemon.stop();
			daemon.destroy();
		}
	}
	
	static List<RemoteServer> parseRemoteServers(InputStream inputStream) throws Exception {
		SAXParserFactory factory = SAXParserFactory.newInstance();
		SAXParser parser = factory.newSAXParser();
		RemoteServerParser serverParser = new RemoteServerParser();
		parser.parse(inputStream, serverParser);
		return serverParser.getServers();
	}

}
