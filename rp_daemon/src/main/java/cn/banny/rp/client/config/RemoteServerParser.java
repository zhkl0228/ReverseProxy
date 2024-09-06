package cn.banny.rp.client.config;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

/**
 * @author zhkl0228
 *
 */
public class RemoteServerParser extends DefaultHandler {
	
	private List<RemoteServer> servers;

	public List<RemoteServer> getServers() {
		return servers;
	}
	
	private RemoteServer currentServer;

	@Override
	public void startDocument() throws SAXException {
		super.startDocument();
		
		servers = new ArrayList<>();
	}

	@Override
	public void startElement(String uri, String localName, String qName,
			Attributes attributes) throws SAXException {
		super.startElement(uri, localName, qName, attributes);
		
		if("rp".equals(qName)) {
			boolean debug = Boolean.parseBoolean(attributes.getValue("debug"));
			if (debug) {
				Logger.getRootLogger().addAppender(new ConsoleAppender(new PatternLayout("%5p [%t] (%F:%L) - %m%n")));
				Logger.getRootLogger().setLevel(Level.DEBUG);
			}
			return;
		}
		
		if("server".equals(qName)) {
			String host = attributes.getValue("host");
			int port = Integer.parseInt(attributes.getValue("port"));
			String username = attributes.getValue("username");
			String password = attributes.getValue("password");
			String extraData = attributes.getValue("extra");
			String changeIp = attributes.getValue("change");
			boolean useSSL = Boolean.parseBoolean(attributes.getValue("ssl"));
			currentServer = new RemoteServer(host, port, username, encodePassword(password), extraData, changeIp, useSSL);
			return;
		}
		
		if("forward".equals(qName)) {
			String rp = attributes.getValue("remotePort");
			int remotePort = rp == null ? 0 : Integer.parseInt(rp);
			String toHost = attributes.getValue("toHost");
			String tp = attributes.getValue("toPort");
			int toPort = tp == null ? 0 : Integer.parseInt(tp);
			currentServer.addForward(new Forward(remotePort, toHost, toPort));
		}
	}

	private String encodePassword(String password) {
		Stack<String> stack = new Stack<>();
		StringBuilder buffer = new StringBuilder();
		for(int i = 0, len = password.length(); i < len; i++) {
			char c = password.charAt(i);
			switch (c) {
			case '(':
				stack.push(buffer.toString());
				buffer.setLength(0);
				break;
			case ')':
				StringBuilder val = new StringBuilder(buffer.toString());
				buffer.setLength(0);
				String op = stack.pop();
				while(!op.contains("MD5_")) {
					val.insert(0, op);
					op = stack.pop();
				}
				
				String result = calcResult(op, val.toString());
				stack.push(result);
				break;
			default:
				buffer.append(c);
				break;
			}
		}
		
		if(stack.isEmpty()) {
			return buffer.toString();
		}
		
		return stack.pop();
	}

	private String calcResult(String op, String val) {
		if("MD5_16".equalsIgnoreCase(op)) {
			return DigestUtils.md5Hex(val).substring(8, 24).toLowerCase();
		}
		if("MD5_32".equalsIgnoreCase(op)) {
			return DigestUtils.md5Hex(val).toLowerCase();
		}
		throw new UnsupportedOperationException("op=" + op + ", val=" + val);
	}

	@Override
	public void endElement(String uri, String localName, String qName)
			throws SAXException {
		super.endElement(uri, localName, qName);
		
		if("server".equals(qName)) {
			servers.add(currentServer);
			currentServer = null;
		}
	}

}
