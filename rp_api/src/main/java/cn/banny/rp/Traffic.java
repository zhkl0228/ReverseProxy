package cn.banny.rp;

import java.net.InetSocketAddress;

/**
 * @author zhkl0228
 *
 */
public class Traffic {
	
	private final InetSocketAddress address;
	private boolean online = true;
	
	public Traffic(InetSocketAddress address) {
		super();
		this.address = address;
	}

	public InetSocketAddress getAddress() {
		return address;
	}

	private long sendTraffic, receiveTraffic;

	public void addSendTraffic(long traffic) {
		sendTraffic += traffic;
	}
	public void addReceiveTraffic(long traffic) {
		receiveTraffic += traffic;
	}

	public long getSendTraffic() {
		return sendTraffic;
	}

	public long getReceiveTraffic() {
		return receiveTraffic;
	}

	public boolean isOnline() {
		return online;
	}

	public void setOffline() {
		this.online = false;
	}

}
