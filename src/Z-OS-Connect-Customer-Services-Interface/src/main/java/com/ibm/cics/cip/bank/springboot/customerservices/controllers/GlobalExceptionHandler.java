/*
 *
 *    Copyright IBM Corp. 2023
 *
 */
package com.ibm.cics.cip.bank.springboot.customerservices.controllers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Centralized, application-wide exception handler for the Customer Services
 * Spring Boot module.
 *
 * <p><b>Security fix - V4 Sensitive Data Exposure.</b></p>
 *
 * <p>Threat addressed: <b>CWE-209 (Generation of Error Message Containing
 * Sensitive Information)</b>, categorized under <b>OWASP Top 10 (2021) A09
 * (Security Logging and Monitoring Failures)</b> and <b>A02 (Cryptographic
 * Failures - formerly "Sensitive Data Exposure")</b>.</p>
 *
 * <p>The per-request handlers in {@code WebController} each wrap their remote
 * calls in {@code try/catch} blocks, but several code paths can still raise an
 * exception that escapes those blocks - most notably the
 * {@code ObjectMapper.writeValueAsString(...)} serialization calls (declared to
 * throw {@code JsonProcessingException}) that execute BEFORE the {@code try}
 * block, as well as any unforeseen runtime error. Without a global handler such
 * an exception would be rendered by the servlet container's default error path,
 * which can surface a stack trace, an internal class name, a Db2
 * {@code SQLCODE}, or other sensitive detail to the browser. This
 * {@code @ControllerAdvice} intercepts those genuinely uncaught exceptions and
 * returns a single, generic, sanitized message instead, while the full
 * diagnostic detail is written only to the trusted server-side log.</p>
 *
 * <p>This advice is auto-detected by component scanning: the module's
 * {@code @SpringBootApplication(scanBasePackages = { ...controllers, ...config })}
 * already includes this {@code controllers} package, so no scan configuration
 * change is required.</p>
 *
 * <p>Scope note: this handler is purely additive. It does not alter any existing
 * REST/MVC path, verb, or response schema; it only governs the response produced
 * for otherwise-unhandled exceptions, and it deliberately preserves Spring
 * Security's 401/403 semantics - see {@link #handleAccessDenied(AccessDeniedException)}
 * and {@link #handleAuthentication(AuthenticationException)}. It likewise preserves
 * the native client-error status of Spring MVC's own framework exceptions (a missing
 * resource stays 404, an unsupported method stays 405, an unsupported media type stays
 * 415/406) rather than remapping them to 500 - see
 * {@link #handleFrameworkClientError(org.springframework.web.ErrorResponse)}.</p>
 */
@ControllerAdvice
public class GlobalExceptionHandler
{

	static final String COPYRIGHT = "Copyright IBM Corp. 2023";

	/**
	 * Generic, sanitized message returned to the client for any uncaught
	 * exception. Intentionally identical to {@code WebController.ERROR_MSG} so
	 * the user-facing vocabulary stays consistent across the module. It reveals
	 * nothing about the internal failure - no exception type, message, stack
	 * trace, {@code SQLCODE}, or PII.
	 */
	private static final String GENERIC_ERROR_MESSAGE = "There was an error processing the request; Please try again later or check logs for more info.";

	/**
	 * SLF4J logger used to record the FULL exception server-side only. The
	 * server log is a trusted diagnostic surface, distinct from the client
	 * response, so it is the correct (and only) place for stack traces.
	 */
	private static final Logger log = LoggerFactory
			.getLogger(GlobalExceptionHandler.class);


	// Security fix (V2 - Missing Authentication/Authorization; OWASP A01 Broken
	// Access Control / A07 Identification and Authentication Failures): NEVER
	// swallow Spring Security's access-control exception. A broad
	// @ExceptionHandler(Exception.class) would otherwise catch AccessDeniedException
	// (raised by @PreAuthorize("hasRole('TELLER')") method security) and convert a
	// proper 403 into a rendered 500. By declaring a dedicated, MORE SPECIFIC handler
	// that simply re-throws the same exception, Spring's ExceptionHandlerExceptionResolver
	// stops resolving (it returns null for the re-thrown original) and the exception
	// propagates back up the filter chain to Spring Security's ExceptionTranslationFilter,
	// which correctly renders 401 (anonymous caller) or 403 (authenticated, wrong role).
	// Spring always selects the most specific @ExceptionHandler, so this handler wins
	// over the broad Exception handler below for AccessDeniedException and its subclasses
	// (e.g. AuthorizationDeniedException).
	@ExceptionHandler(AccessDeniedException.class)
	public void handleAccessDenied(AccessDeniedException ex)
			throws AccessDeniedException
	{
		throw ex;
	}


	// Security fix (V2 - defense-in-depth for the 401 path): likewise never swallow
	// an AuthenticationException. Re-throwing it lets the framework's
	// ExceptionTranslationFilter invoke the configured AuthenticationEntryPoint (401)
	// instead of letting the broad handler below turn it into a 500. This keeps the
	// authentication challenge intact for any authentication failure that reaches the
	// dispatcher layer. As with the AccessDeniedException handler, this more specific
	// handler is selected ahead of the broad Exception fallback.
	@ExceptionHandler(AuthenticationException.class)
	public void handleAuthentication(AuthenticationException ex)
			throws AuthenticationException
	{
		throw ex;
	}


	// Security fix (V4 / QA finding F-1 - remediation-introduced error-hygiene regression;
	// OWASP A09 Security Logging and Monitoring Failures): Spring MVC's OWN framework
	// client-error exceptions are HANDLED outcomes carrying a well-defined 4xx client status -
	// a request for a missing static resource (NoResourceFoundException -> 404) or an unmapped
	// handler (NoHandlerFoundException -> 404), an unsupported HTTP method
	// (HttpRequestMethodNotSupportedException -> 405), or an unsupported / unacceptable media
	// type (HttpMediaTypeNotSupportedException -> 415, HttpMediaTypeNotAcceptableException -> 406).
	// They are NOT genuinely uncaught server errors. Because ExceptionHandlerExceptionResolver
	// (which drives @ExceptionHandler methods) is consulted BEFORE Spring's
	// DefaultHandlerExceptionResolver, the broad @ExceptionHandler(Exception.class) fallback
	// below would otherwise intercept these framework exceptions first and remap every one to
	// HTTP 500 - corrupting client-error monitoring/alerting (a 500 pages on-call; a 404 does
	// not) - while logging benign 404s (scanners, favicon/actuator probes, mistyped URLs) at
	// ERROR with full framework stack traces that pollute the error log and can mask real
	// failures. This dedicated, MORE SPECIFIC handler restores each exception's native status.
	// All of the listed exceptions implement org.springframework.web.ErrorResponse, whose
	// getStatusCode() yields the correct status, so nothing is hardcoded. Spring always selects
	// the most specific @ExceptionHandler, so these client-errors resolve here while genuinely
	// uncaught exceptions still fall through to handleUnexpectedException (HTTP 500) below, and
	// Spring Security's AccessDeniedException / AuthenticationException remain handled by the
	// dedicated re-throwing handlers above (401/403 preserved).
	@ExceptionHandler({ NoResourceFoundException.class,
			NoHandlerFoundException.class,
			HttpRequestMethodNotSupportedException.class,
			HttpMediaTypeNotSupportedException.class,
			HttpMediaTypeNotAcceptableException.class })
	public ResponseEntity<String> handleFrameworkClientError(ErrorResponse ex)
	{
		HttpStatusCode status = ex.getStatusCode();
		// V4 (CWE-532 log hygiene / OWASP A09): benign, often high-volume client errors -
		// log at DEBUG with ONLY the exception TYPE and the resolved status. NEVER log the
		// exception object (no stack trace), its message, or request PII. The client body is
		// the same generic, sanitized message used elsewhere (no internal detail leaks, V4
		// CWE-209), while the response carries the correct client status (404/405/415/406).
		log.debug("Client request error ({}) -> HTTP {}",
				ex.getClass().getSimpleName(), status.value());
		return ResponseEntity.status(status).body(GENERIC_ERROR_MESSAGE);
	}


	// Security fix (V4 - CWE-209 Sensitive Data Exposure; OWASP A09 Security Logging
	// and Monitoring Failures, A02 Cryptographic Failures): catch ANY otherwise
	// uncaught exception and return a generic, sanitized message to the client. The
	// full detail (type, message, stack trace) is logged server-side ONLY via SLF4J;
	// no stack trace, SQLCODE, internal class name, or PII is ever sent to the browser.
	// A ResponseEntity<String> is returned (not a view name) because this @Controller
	// module has no generic error template, and a ResponseEntity is always written as
	// the HTTP response body rather than resolved as a Thymeleaf view. Spring Security's
	// AccessDeniedException / AuthenticationException are intercepted by the dedicated
	// handlers above and therefore never reach this fallback.
	@ExceptionHandler(Exception.class)
	public ResponseEntity<String> handleUnexpectedException(Exception ex)
	{
		// Log only a static context string plus the exception object; do NOT log
		// request PII (e.g. customer/account numbers) so the server log stays free
		// of sensitive identifiers (OWASP A09).
		log.error("Unhandled exception while processing request", ex);
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(GENERIC_ERROR_MESSAGE);
	}

}
