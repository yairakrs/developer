package org.openmhealth.reference.filter;

import java.io.IOException;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;

import org.openmhealth.reference.data.AuthenticationTokenBin;
import org.openmhealth.reference.data.AuthorizationTokenBin;
import org.openmhealth.reference.domain.AuthenticationToken;
import org.openmhealth.reference.exception.InvalidAuthenticationException;
import org.openmhealth.reference.servlet.Version1;

/**
 * <p>
 * This filter is responsible for retrieving the authentication and
 * authorization information, validating them, and associating them with the
 * request.
 * </p>
 *
 * @author John Jenkins
 */
public class AuthFilter implements Filter {
	/**
	 * The Authorization header from HTTP requests.
	 */
	public static final String HEADER_AUTHORIZATION = "Authorization";
	/**
	 * The OAuth Authorization type.
	 */
	public static final String HEADER_AUTHORIZATION_BEARER_UPPER =
		"Bearer".toUpperCase();
	
	/**
	 * The attribute for an authenticated authentication token from the
	 * request. 
	 */
	public static final String ATTRIBUTE_AUTHENTICATION_TOKEN =
		"omh_authentication_token";
	/**
	 * The attribute that indicates whether or not the authentication came from
	 * the parameter list, as opposed to from a cookie.
	 */
	public static final String ATTRIBUTE_AUTHENTICATION_TOKEN_IS_PARAM =
		"omh_user_is_param";
	/**
	 * The attribute for an authorization token from a third party.
	 */
	public static final String ATTRIBUTE_AUTHORIZATION_TOKEN =
		"omh_authenticated_authorization_token";
	
	/*
	 * (non-Javadoc)
	 * @see javax.servlet.Filter#init(javax.servlet.FilterConfig)
	 */
	@Override
	public void init(final FilterConfig config) throws ServletException {
		// Do nothing.
	}

	/*
	 * (non-Javadoc)
	 * @see javax.servlet.Filter#doFilter(javax.servlet.ServletRequest, javax.servlet.ServletResponse, javax.servlet.FilterChain)
	 */
	@Override
	public void doFilter(
		final ServletRequest request,
		final ServletResponse response,
		final FilterChain chain)
		throws IOException, ServletException {
		
		// Get the set of authentication tokens and make sure they match.
		Set<String> authTokens = new HashSet<String>();
		
		// Get the authentication tokens from the cookies.
		if(request instanceof HttpServletRequest) {
			// Cast the HTTP request.
			HttpServletRequest httpRequest = (HttpServletRequest) request;
			
			// Get the array of cookies.
			Cookie[] cookies = httpRequest.getCookies();
			
			// If any cookies were given, cycle through them to find any that
			// match the authentication cookie name.
			if(cookies != null) {
				for(Cookie cookie : cookies) {
					if(Version1
						.PARAM_AUTHENTICATION_AUTH_TOKEN
						.equals(cookie.getName())) {
						
						// Add the token now. We will validate the case where
						// there are multiple, different tokens later.
						authTokens.add(cookie.getValue());
					}
				}
			}
		}
		
		// Get the authentication tokens from the parameters.
		boolean authTokenIsFromParameters = false;
		String[] authTokenParameters =
			request
				.getParameterValues(Version1.PARAM_AUTHENTICATION_AUTH_TOKEN);
		// If any authentication token parameters exist, validate that there is
		// only one and then remember it.
		if(authTokenParameters != null) {
			// If multiple authentication tokens were given, throw an
			// exception.
			if(authTokenParameters.length > 1) {
				throw 
					new InvalidAuthenticationException(
						"Multiple authentication tokens were given.");
			}
			// If exactly one was given, save it to compare it to the
			if(authTokenParameters.length == 1) {
				authTokens.add(authTokenParameters[0]);
				authTokenIsFromParameters = true;
			}
		}
		
		// Validate that if a token was given, only one token was given.
		if(authTokens.size() == 1) {
			// Attempt to get the authentication token.
			AuthenticationToken authTokenObject =
				AuthenticationTokenBin
					.getInstance().getToken(authTokens.iterator().next());
			if(authTokenObject == null) {
				throw
					new InvalidAuthenticationException(
						"The authentication token is unknown or has expired.");
			}
			
			// Associate the authentication token with the request.
			request
				.setAttribute(ATTRIBUTE_AUTHENTICATION_TOKEN, authTokenObject);
		}
		// Make sure multiple, different authentication token were not given.
		else if(authTokens.size() > 1) {
			throw
				new InvalidAuthenticationException(
					"Multiple, different authentication tokens were given.");
		}

		// Indicate if the token used to find this user was a parameter or not.
		request
			.setAttribute(
				ATTRIBUTE_AUTHENTICATION_TOKEN_IS_PARAM,
				authTokenIsFromParameters);
		
		// Attempt to get the authentication from the third-party.
		if(request instanceof HttpServletRequest) {
			// Cast the request.
			HttpServletRequest httpRequest = (HttpServletRequest) request;
			
			// The OAuth Authorization header uses the "Bearer" token type. If
			// some other Authorization type is given, ignore it.
			if(HEADER_AUTHORIZATION_BEARER_UPPER
				.equals(httpRequest.getAuthType())) {
				
				// Get a placeholder for the third-party token.
				String thirdPartyToken = null;
				
				// Get the authorization headers, of which there may be
				// multiple.
				Enumeration<String> authorizations =
					httpRequest.getHeaders(HEADER_AUTHORIZATION);
				
				// Cycle through each element and process the "Bearer" type.
				String currElement;
				while((currElement = authorizations.nextElement()) != null) {
					// Split it to find the type and value.
					String[] currElementParts = currElement.split(" ");
					
					// If there aren't exactly two parts, then we cannot
					// process it.
					if(currElementParts.length != 2) {
						continue;
					}
					
					// Get the current element part.
					String typeUpper = currElementParts[0].toUpperCase();
					
					// If the type is the Bearer, then process it.
					if(HEADER_AUTHORIZATION_BEARER_UPPER.equals(typeUpper)) {
						if(thirdPartyToken == null) {
							thirdPartyToken = currElementParts[1];
						}
						else {
							throw
								new InvalidAuthenticationException(
									"Multiple third-party credentials were " +
										"provided as '" + 
										HEADER_AUTHORIZATION_BEARER_UPPER +
										"' Authorization headers.");
						}
					}
				}
				
				// If the third-party token was given, attempt to add the
				// third-party to the request.
				if(thirdPartyToken != null) {
					request
						.setAttribute(
							ATTRIBUTE_AUTHORIZATION_TOKEN, 
							AuthorizationTokenBin
								.getInstance()
								.getTokenFromAccessToken(thirdPartyToken));
				}
			}
		}
		
		// Continue along the filter chain.
		chain.doFilter(request, response);
	}

	/*
	 * (non-Javadoc)
	 * @see javax.servlet.Filter#destroy()
	 */
	@Override
	public void destroy() {
		// Do nothing.
	}
}