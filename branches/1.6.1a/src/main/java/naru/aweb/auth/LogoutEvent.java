package naru.aweb.auth;

public interface LogoutEvent {
	/**
	 * 処理中にsynchronizedしてはいけない
	 */
	public void onLogout();
}
