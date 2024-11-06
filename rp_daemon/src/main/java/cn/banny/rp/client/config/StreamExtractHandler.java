/**
 * 
 */
package cn.banny.rp.client.config;

import org.apache.commons.exec.PumpStreamHandler;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.Vector;

/**
 * @author Banny
 *
 */
public class StreamExtractHandler extends PumpStreamHandler {

	public StreamExtractHandler() {
		super(new ByteArrayOutputStream(), new ByteArrayOutputStream());
	}
	
	public String[] extractAll() {
		return extractAll(Charset.defaultCharset());
	}
	
	public String[] extractAll(Charset charset) {
		String[] out = extractOut(charset);
		String[] err = extractErr(charset);
		
		String[] values = new String[out.length + err.length];
		System.arraycopy(out, 0, values, 0, out.length);
		System.arraycopy(err, 0, values, out.length, err.length);
		return values;
	}
	
	public String[] extractOut() {
		return extractOut(Charset.defaultCharset());
	}
	
	public String[] extractErr() {
		return extractErr(Charset.defaultCharset());
	}
	
	public String[] extractOut(Charset charset) {
		OutputStream out = getOut();
		if(out instanceof ByteArrayOutputStream) {
			return extractLines(new ByteArrayInputStream(((ByteArrayOutputStream) out).toByteArray()), charset);
		}
		
		return new String[0];
	}
	
	public String[] extractErr(Charset charset) {
		OutputStream err = getErr();
		if(err instanceof ByteArrayOutputStream) {
			return extractLines(new ByteArrayInputStream(((ByteArrayOutputStream) err).toByteArray()), charset);
		}
		
		return new String[0];
	}

	private String[] extractLines(InputStream in, Charset charset) {
		Vector<String> lines = new Vector<>();
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, charset))) {
			String line;
			while((line = reader.readLine()) != null) {
				lines.add(line);
			}
		} catch (IOException e) {
			e.printStackTrace(System.err);
		}
		
		String[] values = new String[lines.size()];
		lines.copyInto(values);
		return values;
	}

}
