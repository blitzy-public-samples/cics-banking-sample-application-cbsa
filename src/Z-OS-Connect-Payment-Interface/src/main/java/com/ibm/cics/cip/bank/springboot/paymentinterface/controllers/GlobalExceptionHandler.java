/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.springboot.paymentinterface.controllers;

import jakarta.validation.ConstraintViolationException;

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
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/*
 * Centralized, application-wide exception handling for the Payment Interface
 * Spring Boot module. This module mixes a Thymeleaf @Controller (WebController)
 * and a @RestController (ParamsController), so this advice returns a ResponseEntity
 * body (never a view name) that works for both without needing an error template.
 *
 * Auto-detected by component scanning: PaymentInterface.java declares
 * @SpringBootApplication(scanBasePackages = { ...controllers, ...config }), which
 * already includes this controllers package, so no scan change is required.
 *
 * Security fix (V4 Sensitive Data Exposure - CWE-209 Error Message Containing
 * Sensitive Information / CWE-532 Insertion of Sensitive Information into Log File;
 * OWASP A09 Security Logging & Monitoring Failures, A02): the broad
 * handleUnexpectedException(...) fallback catches any exception that escapes a
 * controller's own try/catch - most notably a JsonProcessingException raised by
 * ObjectMapper.writeValueAsString(...), which executes BEFORE the try block in both
 * controllers - and returns a single, generic, sanitized message. No stack trace,
 * exception message, internal class name, Db2 SQLCODE, or PII is ever returned to the
 * client; the full detail is written only to the trusted server-side log.
 *
 * Security fix (V3 Improper Input Validation - CWE-20; OWASP A03): ParamsController is
 * @Validated at class level, so a violated @RequestParam constraint on /submit
 * (@NotBlank / @Size / @Positive) surfaces as a ConstraintViolationException (or, on
 * the Spring MVC native method-validation path, a HandlerMethodValidationException),
 * while a @Valid bind without an adjacent BindingResult surfaces as a
 * MethodArgumentNotValidException. Spring MVC does not map ConstraintViolationException
 * / HandlerMethodValidationException to 400 by default, so handleValidation(...) maps
 * every method-validation failure shape to a reject-by-default HTTP 400 with a generic
 * body BEFORE any downstream money-movement WebClient call, restoring the API contract.
 *
 * Security fix (V2 Missing Authentication/Authorization - CWE-306 / CWE-862; OWASP A01
 * Broken Access Control, A07): the broad Exception fallback must NEVER swallow Spring
 * Security's access-control outcome. handleAccessDenied(...) and handleAuthentication(...)
 * are dedicated, MORE SPECIFIC handlers that simply re-throw, so the exception propagates
 * to Spring Security's ExceptionTranslationFilter, which renders 403 (authenticated, wrong
 * role - e.g. AuthorizationDeniedException from @PreAuthorize("hasRole('TELLER')"), a
 * subclass of AccessDeniedException) or invokes the AuthenticationEntryPoint (401). Spring
 * always selects the most specific @ExceptionHandler, so these win over the broad Exception
 * handler, preserving the V2 authorization contract.
 *
 * It likewise preserves the native client-error status of Spring MVC's own framework
 * exceptions (a missing resource stays 404, an unsupported method stays 405, an unsupported
 * or unacceptable media type stays 415/406) rather than remapping them to 500 - see
 * handleFrameworkClientError(ErrorResponse).
 *
 * Scope note: purely additive. No existing REST/MVC path, verb, or response schema
 * changes; the only new response behavior is the standard 400/401/403/404/405/406/415/500
 * handling above.
 */
@ControllerAdvice
public class GlobalExceptionHandler
{


	// SLF4J logger records the FULL exception server-side ONLY (a trusted diagnostic
	// surface distinct from the client response) - never in the HTTP response body.
	private static final Logger log = LoggerFactory
			.getLogger(GlobalExceptionHandler.class);

	// Generic, sanitized message returned for any otherwise-uncaught exception. Reuses the
	// Payment WebController generic wording so the client vocabulary stays consistent
	// module-wide; it reveals nothing about the internal failure (V4 CWE-209).
	private static final String GENERIC_ERROR_MSG = "There was an error processing the request; Please try again later or check logs for more info.";

	// Generic, sanitized message returned for rejected input - never echoes the offending
	// value or field name (V3 CWE-20 / V4 CWE-209).
	private static final String VALIDATION_ERROR_MSG = "Invalid request; please check your input and try again.";


	// Security fix (V2 - Missing Authentication/Authorization; OWASP A01 Broken Access
	// Control / A07): NEVER swallow Spring Security's authorization exception. The broad
	// @ExceptionHandler(Exception.class) below would otherwise catch AccessDeniedException
	// (and its subclass AuthorizationDeniedException, raised by @PreAuthorize("hasRole('TELLER')")
	// on /submit and /paydbcr) and convert a proper 403 into a 500. This dedicated, MORE
	// SPECIFIC handler simply re-throws, so ExceptionHandlerExceptionResolver leaves it
	// unresolved and the exception propagates to Spring Security's ExceptionTranslationFilter,
	// which renders 403 (authenticated, wrong role). Spring always selects the most specific
	// handler, so this wins over the broad Exception fallback for AccessDeniedException and
	// its subclasses.
	@ExceptionHandler(AccessDeniedException.class)
	public void handleAccessDenied(AccessDeniedException ex)
			throws AccessDeniedException
	{
		throw ex;
	}


	// Security fix (V2 - defense-in-depth for the 401 path): likewise never swallow an
	// AuthenticationException. Re-throwing lets ExceptionTranslationFilter invoke the
	// configured AuthenticationEntryPoint (401) instead of the broad handler turning it into
	// a 500, keeping the authentication challenge intact. As with handleAccessDenied, this
	// more specific handler is selected ahead of the broad Exception fallback.
	@ExceptionHandler(AuthenticationException.class)
	public void handleAuthentication(AuthenticationException ex)
			throws AuthenticationException
	{
		throw ex;
	}


	// Security fix (V3 CWE-20 Improper Input Validation - OWASP A03; V4 CWE-209 Sensitive
	// Data Exposure): map every method-validation AND request-binding failure shape to a
	// reject-by-default HTTP 400 with a generic body. ParamsController's @Validated @RequestParam
	// constraints throw ConstraintViolationException (or HandlerMethodValidationException on the
	// Spring MVC native method-validation path); a @Valid bind without an adjacent BindingResult
	// throws MethodArgumentNotValidException.
	//
	// Security fix (QA finding F1 - V3 CWE-20 / OWASP A03 API contract; V4 CWE-532 / OWASP A09
	// Security Logging & Monitoring Failures): two REQUEST-BINDING failures on /submit were
	// previously downgraded to a misleading HTTP 500 - a missing required @RequestParam
	// (MissingServletRequestParameterException, e.g. an absent "acctnum") and a non-numeric value
	// bound to a typed param (MethodArgumentTypeMismatchException, e.g. amount="abc"). Both are
	// benign CLIENT input errors that belong at 400, but because ExceptionHandlerExceptionResolver
	// runs BEFORE Spring's DefaultHandlerExceptionResolver (which would otherwise map them to their
	// native 400), and handleFrameworkClientError below enumerates only the 5 framework client
	// errors it lists (so neither of these two is matched there), they fell through to
	// handleUnexpectedException(Exception) -> HTTP 500 AND were logged at ERROR with a full stack
	// trace (benign client input polluting the error signal used for on-call alerting - OWASP A09).
	// Handling them here restores the correct 400, reuses the same generic sanitized body, and logs
	// at INFO (not ERROR).
	//
	// Spring MVC does not map ConstraintViolationException / HandlerMethodValidationException to
	// 400 by default, so without this they would surface as a misleading 500. The 400 is returned
	// BEFORE any downstream money-movement call. Explicitly listing each type here is an exact-type
	// match, so these win over the broad @ExceptionHandler(Exception.class) fallback and never
	// collide with handleFrameworkClientError's disjoint explicit list.
	@ExceptionHandler({ HandlerMethodValidationException.class,
			ConstraintViolationException.class,
			MethodArgumentNotValidException.class,
			MissingServletRequestParameterException.class,
			MethodArgumentTypeMismatchException.class })
	public ResponseEntity<String> handleValidation(Exception ex)
	{
		// Log only the exception TYPE (never the message or the offending value) so submitted
		// input and any PII stay out of the log (V4 CWE-532). INFO level (not ERROR): these are
		// benign client-input rejections, not server faults, so they must not pollute the ERROR
		// signal used for on-call alerting (OWASP A09).
		log.info("Rejected request: input validation failure ({})",
				ex.getClass().getSimpleName());
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body(VALIDATION_ERROR_MSG);
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
	// dedicated re-throwing handlers above (401/403 preserved). Mirrors the sibling Customer
	// Services GlobalExceptionHandler so both Spring Boot modules behave identically.
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
		return ResponseEntity.status(status).body(GENERIC_ERROR_MSG);
	}


	// Security fix (V4 - CWE-209 Sensitive Data Exposure; OWASP A09 Security Logging &
	// Monitoring Failures, A02): catch ANY otherwise-uncaught exception and return a generic,
	// sanitized 500. The full detail (type, message, stack trace) is logged server-side ONLY;
	// no stack trace, SQLCODE, internal class name, or PII is ever sent to the client. A
	// ResponseEntity<String> is returned (not a view name) so the body is written directly for
	// both the @Controller (WebController) and @RestController (ParamsController) without an
	// error template. Spring Security's AccessDeniedException / AuthenticationException are
	// intercepted by the dedicated re-throwing handlers above and never reach this fallback,
	// so the V2 401/403 contract is preserved.
	@ExceptionHandler(Exception.class)
	public ResponseEntity<String> handleUnexpectedException(Exception ex)
	{
		// Log a static context string plus the exception object (server-side, trusted surface);
		// do NOT log request PII (account/customer numbers, amounts) - keeps the log free of
		// sensitive identifiers (OWASP A09, V4 CWE-532).
		log.error("Unhandled exception while processing request", ex);
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(GENERIC_ERROR_MSG);
	}

}
