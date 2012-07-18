package naru.aweb.auth;

import naru.aweb.http.HeaderParser;
import naru.aweb.http.ParameterParser;

public interface AuthenticateModule {
	public boolean authenticate(HeaderParser requestHeader,ParameterParser parameter);
	public boolean cleanupAuthHeader(HeaderParser requestHeader);
	public boolean fowardAuthenticate(AuthHandler authHandler);
}
