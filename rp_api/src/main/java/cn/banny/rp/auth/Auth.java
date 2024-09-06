/**
 * 
 */
package cn.banny.rp.auth;

/**
 * @author zhkl0228
 *
 */
public class Auth {
	
	private final String username;
	private final String password;
	private final AuthResult result;
	
	public Auth(String username, String password, AuthResult result) {
		super();
		this.username = username;
		this.password = password;
		this.result = result;
	}

	public String getUsername() {
		return username;
	}

	public String getPassword() {
		return password;
	}

	public AuthResult getResult() {
		return result;
	}

}
