/**
 * 
 */
package cn.banny.rp.auth;

import cn.banny.rp.ReverseProxy;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * @author zhkl0228
 *
 */
public class AuthResult {
	
	private final int status;
	private final String msg;
	
	private final Long expire;
	private final String nick;
	
	public static AuthResult authOk(Long expire, String nick) {
		return new AuthResult(0, null, expire, nick);
	}
	
	public static AuthResult authFailed(int status, String msg) {
		return new AuthResult(status, msg, null, null);
	}
	
	public static AuthResult readAuthResult(ByteBuffer in) throws IOException {
		int status = in.get() & 0xFF;
		String msg = in.get() == 1 ? ReverseProxy.readUTF(in) : null;
		Long expire = in.get() == 1 ? in.getLong() : null;
		String nick = in.get() == 1 ? ReverseProxy.readUTF(in) : null;
		return new AuthResult(status, msg, expire, nick);
	}

	private String clientIp;

	public String getClientIp() {
		return clientIp;
	}

	public void setClientIp(String clientIp) {
		this.clientIp = clientIp;
	}

	private AuthResult(int status, String msg, Long expire, String nick) {
		super();
		this.status = status;
		this.msg = msg;
		this.expire = expire;
		this.nick = nick;
	}

	public int getStatus() {
		return status;
	}

	public String getMsg() {
		return msg;
	}

	public Long getExpire() {
		return expire;
	}

	public String getNick() {
		return nick;
	}

	@Override
	public String toString() {
		return "AuthResult{" +
				"status=" + status +
				", msg='" + msg + '\'' +
				", expire=" + expire +
				", nick='" + nick + '\'' +
				", clientIp='" + clientIp + '\'' +
				'}';
	}
}
