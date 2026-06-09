/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.core.config;

import java.io.IOException;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Servlet filter that adds defense-in-depth HTTP security response headers to
 * every {@code bank-core} response.
 *
 * <p><strong>Why this exists.</strong> {@code bank-core} is an intentionally
 * unauthenticated JSON REST API that reproduces the frozen z/OS Connect contract
 * (feature F-019); by design it does <em>not</em> depend on Spring Security, and
 * Spring Security is the component that would normally contribute hardening
 * headers. Without it, responses carried none of the standard security headers.
 * This filter restores a minimal, framework-light set of hardening headers
 * without introducing Spring Security and without altering the frozen contract:
 * it changes no JSON envelope, field name, HTTP method, path, or status code, and
 * only ever <em>adds</em> response headers.</p>
 *
 * <p><strong>Headers written.</strong></p>
 * <ul>
 *   <li>{@code X-Content-Type-Options: nosniff} &mdash; instructs browsers not to
 *       MIME-sniff a response away from the declared {@code Content-Type}
 *       ({@code application/json}), mitigating content-type confusion attacks.</li>
 *   <li>{@code X-Frame-Options: DENY} &mdash; forbids the response from being
 *       embedded in a frame/iframe, a belt-and-braces clickjacking control (of
 *       limited applicability to a JSON API, but harmless and expected by
 *       security scanners).</li>
 *   <li>{@code Cache-Control: no-store} &mdash; prevents shared and browser
 *       caches from storing potentially sensitive banking data returned by the
 *       API.</li>
 * </ul>
 *
 * <p><strong>What is deliberately omitted.</strong> {@code Strict-Transport-Security}
 * (HSTS) is intentionally <em>not</em> sent. {@code bank-core} is reached over
 * plain local HTTP in this deployment, where HSTS is meaningless and can pin a
 * non-TLS origin incorrectly; TLS termination and HSTS belong at the reverse
 * proxy / re-point layer in front of the service when the application is exposed
 * for production. Likewise no Content-Security-Policy is emitted, as the module
 * serves only {@code application/json} (no HTML/script) for which a CSP carries
 * negligible benefit.</p>
 *
 * <p><strong>Registration.</strong> Annotated {@link Component @Component}, the
 * filter is discovered by the application's package-root component scan and
 * auto-registered by Spring Boot into the servlet filter chain for every request.
 * It extends {@link OncePerRequestFilter} so that the headers are written exactly
 * once per request even across internal dispatches (forwards/errors). It is
 * ordered with {@link Ordered#HIGHEST_PRECEDENCE} so the headers are present on
 * every response, including error responses produced later in the chain.</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SecurityHeadersFilter extends OncePerRequestFilter
{

	/** HTTP header name: disables browser MIME-type sniffing. */
	private static final String X_CONTENT_TYPE_OPTIONS = "X-Content-Type-Options";

	/** Value for {@link #X_CONTENT_TYPE_OPTIONS}. */
	private static final String NOSNIFF = "nosniff";

	/** HTTP header name: controls whether the response may be framed. */
	private static final String X_FRAME_OPTIONS = "X-Frame-Options";

	/** Value for {@link #X_FRAME_OPTIONS}: never allow framing. */
	private static final String DENY = "DENY";

	/** HTTP header name: response cacheability directive. */
	private static final String CACHE_CONTROL = "Cache-Control";

	/** Value for {@link #CACHE_CONTROL}: never store the response. */
	private static final String NO_STORE = "no-store";

	/**
	 * Adds the defense-in-depth security headers to the response, then continues
	 * the filter chain. The headers are set <em>before</em> the chain proceeds so
	 * they apply uniformly to controller responses, validation/business error
	 * envelopes produced by the {@code GlobalExceptionHandler}, and container
	 * error responses alike.
	 *
	 * @param request     the current HTTP request (never {@code null})
	 * @param response    the current HTTP response to decorate (never {@code null})
	 * @param filterChain the remaining filter chain to invoke (never {@code null})
	 * @throws ServletException if the downstream chain raises a servlet error
	 * @throws IOException      if the downstream chain raises an I/O error
	 */
	@Override
	protected void doFilterInternal(@NonNull HttpServletRequest request,
			@NonNull HttpServletResponse response,
			@NonNull FilterChain filterChain) throws ServletException, IOException
	{
		response.setHeader(X_CONTENT_TYPE_OPTIONS, NOSNIFF);
		response.setHeader(X_FRAME_OPTIONS, DENY);
		response.setHeader(CACHE_CONTROL, NO_STORE);

		filterChain.doFilter(request, response);
	}

}
