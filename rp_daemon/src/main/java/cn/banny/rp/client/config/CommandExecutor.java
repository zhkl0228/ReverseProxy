package cn.banny.rp.client.config;

import java.io.IOException;

/**
 * @author Banny
 *
 */
public interface CommandExecutor {
	
	/**
	 * 执行返回输出
	 * @return null表示执行失败
	 * @throws IOException 执行异常
	 */
	String[] execute() throws IOException;

}
