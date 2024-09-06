package cn.banny.rp;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author zhkl0228
 *
 */
public abstract class AbstractRouteContext implements RouteContext {
	
	private final Map<String, Object> attributes = new ConcurrentHashMap<>();

	protected final void setAttribute(String key, Object attr) {
		if(attr == null) {
			attributes.remove(key);
			return;
		}

		attributes.put(key, attr);
	}

	protected final <T> T getAttribute(String key, Class<T> clazz) {
		return clazz.cast(attributes.get(key));
	}

	private void addIntAttribute(String key) {
		Integer val = getAttribute(key, Integer.class);
		if(val == null) {
			val = 0;
		}
		setAttribute(key, val + 1);
	}
	
	private final class AttributeImpl implements Attribute {
		private final String key;

		private AttributeImpl(String key) {
			super();
			this.key = key;
		}
		
		@Override
		public boolean hasValue() {
			return attributes.containsKey(key);
		}

		@Override
		public void remove() {
			attributes.remove(key);
		}

		@Override
		public void set(Object obj) {
			setAttribute(key, obj);
		}
		
		@Override
		public String getString() {
			return getAttribute(key, String.class);
		}
		
		@Override
		public short getShort() {
			Short val = getAttribute(key, Short.class);
			return val == null ? 0 : val;
		}
		
		@Override
		public long getLong() {
			Long val = getAttribute(key, Long.class);
			return val == null ? 0 : val;
		}
		
		@Override
		public int getInt() {
			Integer val = getAttribute(key, Integer.class);
			return val == null ? 0 : val;
		}
		
		@Override
		public float getFloat() {
			Float val = getAttribute(key, Float.class);
			return val == null ? 0f : val;
		}
		
		@Override
		public double getDouble() {
			Double val = getAttribute(key, Double.class);
			return val == null ? 0d : val;
		}
		
		@Override
		public byte getByte() {
			Byte val = getAttribute(key, Byte.class);
			return val == null ? 0 : val;
		}
		
		@Override
		public boolean getBoolean() {
			Boolean val = getAttribute(key, Boolean.class);
			return val != null && val;
		}
		
		@Override
		public <T> T get(Class<T> clazz) {
			return getAttribute(key, clazz);
		}

		@Override
		public void add() {
			addIntAttribute(key);
		}

		@Override
		public String toString() {
			return "AttributeImpl [name=" + key + ", value=" + get(Object.class) + "]";
		}
	}

	@Override
	public final Attribute createAttribute(String name) {
		return new AttributeImpl(name);
	}

}
