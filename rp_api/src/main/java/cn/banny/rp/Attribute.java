/**
 * 
 */
package cn.banny.rp;

/**
 * @author zhkl0228
 *
 */
public interface Attribute {
	
	void set(Object obj);
	void remove();
	
	<T> T get(Class<T> clazz);
	
	String getString();
	int getInt();
	long getLong();
	boolean getBoolean();
	short getShort();
	float getFloat();
	double getDouble();
	byte getByte();
	
	void add();
	
	boolean hasValue();

}
