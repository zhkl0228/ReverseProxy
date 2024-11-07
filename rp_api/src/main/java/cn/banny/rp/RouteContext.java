package cn.banny.rp;

import java.util.Map;

/**
 * @author zhkl0228
 *
 */
public interface RouteContext {
	
	Attribute createAttribute(String name);

	Map<String, Object> getAttributes();

}
