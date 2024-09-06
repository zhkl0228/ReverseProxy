/**
 * 
 */
package cn.banny.rp.test;

import junit.framework.TestCase;

import java.util.concurrent.TimeUnit;

/**
 * @author zhkl0228
 *
 */
public class TimeUnitTest extends TestCase {
	
	public void testTime() throws Exception {
		assertEquals(1000, TimeUnit.SECONDS.toMillis(1));
		assertEquals(1000 * 60 * 60 * 24, TimeUnit.DAYS.toMillis(1));
	}

}
