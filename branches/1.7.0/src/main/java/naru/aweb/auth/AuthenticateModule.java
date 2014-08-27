package naru.aweb.auth;

import naru.aweb.util.HeaderParser;
import naru.aweb.util.ParameterParser;

public interface AuthenticateModule {
	public boolean authenticate(HeaderParser requestHeader,ParameterParser parameter);
	public boolean cleanupAuthHeader(HeaderParser requestHeader);
	public boolean fowardAuthenticate(AuthHandler authHandler);
}
