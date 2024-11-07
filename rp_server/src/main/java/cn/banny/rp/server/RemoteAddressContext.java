package cn.banny.rp.server;

import cn.banny.rp.AbstractRouteContext;
import cn.banny.rp.RouteContext;
import org.apache.commons.collections4.map.PassiveExpiringMap;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * @author zhkl0228
 *
 */
public class RemoteAddressContext extends AbstractRouteContext implements RouteContext {
	
	private static final Map<String, RouteContext> contextMap = new PassiveExpiringMap<>(TimeUnit.HOURS.toMillis(24));
	
	public static RouteContext obtain(String ip, RouteContext copy) {
		RouteContext context = contextMap.get(ip);
		if(context == null) {
			context = new RemoteAddressContext(copy);
		}
		contextMap.put(ip, context); // 确保旧的不会24小时过期
		return context;
	}

	public static void keepRemoteAddressContextAlive(String ...keys) {
		for(String key : keys) {
			if (key != null) {
				obtain(key, null);
			}
		}
	}

	private RemoteAddressContext(RouteContext copy) {
		super();
		if (copy != null) {
			attributes.putAll(copy.getAttributes());
		}
	}

}
