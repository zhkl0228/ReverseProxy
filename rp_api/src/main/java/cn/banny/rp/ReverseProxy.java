package cn.banny.rp;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;
import java.util.Date;

/**
 * @author zhkl0228
 *
 */
public class ReverseProxy {

	/**
	 * Represents the end-of-file (or stream) value {@value}.
	 * @since 2.5 (made public)
	 */
	public static final int EOF = -1;

	public static String toString(InputStream inputStream, Charset charset) throws IOException {
		try(ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
			byte[] buffer = new byte[1024];
			int read;
			while((read = inputStream.read(buffer)) != ReverseProxy.EOF) {
				baos.write(buffer, 0, read);
			}
			return baos.toString(charset.name());
		}
	}
	
	public static void closeQuietly(Closeable closeable) {
		if(closeable == null) {
			return;
		}
		try {
			closeable.close();
		} catch(Throwable ignored) {}
	}

	public static void inspect(byte[] data, String label) {
		inspect(label, data, 0x10);
	}
	
	public static void inspect(String label, byte[] data, int mode) {
		inspect(null, label, data, mode);
	}
	
	public static void inspect(Date date, String label, byte[] data, int mode) {
		StringBuffer buffer = new StringBuffer();
		buffer.append("\n>-----------------------------------------------------------------------------<\n");
		
		Calendar calendar = Calendar.getInstance();
		if(date != null) {
			calendar.setTime(date);
		}
		buffer.append('[').append(calendar.get(Calendar.HOUR_OF_DAY));
		buffer.append(':').append(calendar.get(Calendar.MINUTE));
		buffer.append(':').append(calendar.get(Calendar.SECOND));
		buffer.append(' ').append(calendar.get(Calendar.MILLISECOND)).append("]");
		
		buffer.append(label);
		
		buffer.append("\nsize: ");
		if(data != null) {
			buffer.append(data.length);
		} else {
			buffer.append("null");
		}
		buffer.append('\n');
		
		if(data != null) {
			int i = 0;
			for(; i < data.length; i++) {
				if(i % mode == 0) {
					String hex = Integer.toHexString(i % 0x10000).toUpperCase();
					for(int k = 0, fill = 4 - hex.length(); k < fill; k++) {
						buffer.append('0');
					}
					buffer.append(hex).append(": ");
				}
				
				int di = data[i] & 0xFF;
				String hex = Integer.toString(di, 16).toUpperCase();
				if(hex.length() < 2) {
					buffer.append('0');
				}
				buffer.append(hex);
				buffer.append(' ');
				
				if((i + 1) % mode == 0) {
					buffer.append("   ");
					for(int k = i - 15; k < i+1; k++) {
						buffer.append(toChar(data[k]));
					}
					buffer.append('\n');
				}
			}
			
			int redex = mode - i % mode;
			for(byte k = 0; k < redex && redex < mode; k++) {
				buffer.append("  ");
				buffer.append(' ');
			}
			int count = i % mode;
			int start = i - count;
			if(start < i) {
				buffer.append("   ");
			}
			for(int k = start; k < i; k++) {
				buffer.append(toChar(data[k]));
			}
			
			if(redex < mode) {
				buffer.append('\n');
			}
		}
		
		buffer.append("^-----------------------------------------------------------------------------^");
		
		System.out.println(buffer);
	}
	
	private static char toChar(byte in) {
		if(in == ' ')
			return ' ';
		
		if(in > 0x7E || in < 0x21)
			return '.';
		else
			return (char) in;
	}
	
	public static boolean isEmpty(String str) {
		return str == null || str.trim().isEmpty();
	}
	
	public static String readUTF(ByteBuffer buffer) {
		int len = buffer.getShort() & 0xFFFF;
		String str = new String(buffer.array(), buffer.position(), len, StandardCharsets.UTF_8);
		buffer.position(buffer.position() + len);
		return str;
	}
	
	public static void writeUTF(ByteBuffer buffer, String str) {
		byte[] bs;
        bs = str.getBytes(StandardCharsets.UTF_8);
        buffer.putShort((short) bs.length);
		buffer.put(bs);
	}

}
