package cn.banny.rp.server;

import cn.banny.rp.AbstractRouteContext;
import cn.banny.rp.RouteContext;
import org.apache.commons.collections4.map.PassiveExpiringMap;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * @author zhkl0228
 *
 */
public class RemoteAddressContext extends AbstractRouteContext implements RouteContext {
	
	private static final Map<InetAddress, RouteContext> contextMap = new PassiveExpiringMap<>(TimeUnit.HOURS.toMillis(24));
	
	public static RouteContext obtain(InetSocketAddress address) {
		RouteContext context = contextMap.get(address.getAddress());
		if(context == null) {
			context = new RemoteAddressContext();
		}
		contextMap.put(address.getAddress(), context); // 确保旧的不会24小时过期
		return context;
	}

	private RemoteAddressContext() {
		super();
	}

}
