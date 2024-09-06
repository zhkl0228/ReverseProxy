package cn.banny.rp.client.config;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.Executor;

import java.io.IOException;

/**
 * @author Banny
 *
 */
public class LocalCommandExecutor implements CommandExecutor {
	
	private final CommandLine command;

	LocalCommandExecutor(String command) {
		this(CommandLine.parse(command));
	}

	private LocalCommandExecutor(CommandLine command) {
		super();
		this.command = command;
	}
	
	public String[] execute() throws IOException {
		StreamExtractHandler handler = new StreamExtractHandler();
		Executor executor = new DefaultExecutor();
		executor.setStreamHandler(handler);
		int exitValue = executor.execute(command);
		if(executor.isFailure(exitValue)) {
			return null;
		}

		return handler.extractAll();
	}

}
