/*                                                                        */
/* Copyright IBM Corp. 2025                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.springboot.paymentinterface.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.Collections;

import jakarta.validation.ConstraintViolationException;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;

/*
 * Direct unit verification for the Payment Interface {@code GlobalExceptionHandler}. Mirrors
 * the sibling Customer Services GlobalExceptionHandlerTest so both Spring Boot modules lock the
 * same error-handling contract, and adds coverage for the Payment-specific handleValidation and
 * for handleFrameworkClientError.
 *
 *   V4 Sensitive Data Exposure (CWE-209 / OWASP A09,A02): when an unexpected exception carries
 *     sensitive detail (a Db2 SQLCODE, PII), the client-facing body is the fixed generic message
 *     ONLY - no exception message, SQLCODE, PII or internal class name. Full detail stays in the
 *     server-side log.
 *
 *   V4 / QA finding F-1 error-hygiene regression: Spring MVC's framework client-error exceptions
 *     (missing resource/handler, unsupported method, unsupported/unacceptable media type) must
 *     keep their NATIVE 4xx status (404/405/415/406) - NOT be remapped to 500 by the broad
 *     Exception fallback. handleFrameworkClientError restores the native status via
 *     ErrorResponse.getStatusCode(); these tests lock that behavior for the Payment module (the
 *     regression that existed here before this fix turned a 404/405/415/406 into a 500).
 *
 *   V3 Improper Input Validation (CWE-20 / OWASP A03): a method-validation failure is rejected
 *     with a generic HTTP 400 (never a 500), before any downstream money-movement call.
 *
 *   V2 preservation (CWE-306/CWE-862 / OWASP A01,A07): the dedicated AccessDeniedException /
 *     AuthenticationException handlers RE-THROW (never swallow) so Spring Security's
 *     ExceptionTranslationFilter still renders 401/403.
 *
 * Implemented as a focused unit test (no Spring context): the handler methods are invoked
 * directly, which is deterministic and needs no contrived failing controller path.
 */
class GlobalExceptionHandlerTest
{

	// The exact generic client messages (must stay in lock-step with the production constants).
	private static final String GENERIC_ERROR_MSG =
			"There was an error processing the request; Please try again later or check logs for more info.";

	private static final String VALIDATION_ERROR_MSG =
			"Invalid request; please check your input and try again.";

	// Sensitive tokens that MUST NOT appear in the client-facing response body.
	private static final String SENSITIVE_SQLCODE = "SQLCODE=-803";

	private static final String SENSITIVE_PII = "SSN=123-45-6789";

	private final GlobalExceptionHandler handler = new GlobalExceptionHandler();


	@Test
	void unexpectedExceptionBodyIsGenericAndLeaksNothing()
	{
		Exception sensitive = new IllegalStateException(
				"Db2 failure " + SENSITIVE_SQLCODE + " for customer "
						+ SENSITIVE_PII);

		ResponseEntity<String> response =
				handler.handleUnexpectedException(sensitive);

		// A sanitized 500 with the fixed generic body.
		assertThat(response.getStatusCode())
				.isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
		assertThat(response.getBody())
				.as("client body must be exactly the generic message")
				.isEqualTo(GENERIC_ERROR_MSG);

		// The body must not leak the exception message, SQLCODE, PII or the internal class name.
		assertThat(response.getBody())
				.doesNotContain(SENSITIVE_SQLCODE)
				.doesNotContain(SENSITIVE_PII)
				.doesNotContain("Db2 failure")
				.doesNotContain("IllegalStateException")
				.doesNotContain("java.lang");
	}


	@Test
	void validationFailureReturns400Generic()
	{
		// A method-validation failure must be a reject-by-default HTTP 400 with a generic body -
		// never a 500, and never echoing the offending value or field name (V3 / V4).
		ResponseEntity<String> response = handler.handleValidation(
				new ConstraintViolationException(Collections.emptySet()));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody()).isEqualTo(VALIDATION_ERROR_MSG);
	}


	@Test
	void frameworkMethodNotSupportedPreserves405()
	{
		// V4 / QA F-1: an unsupported HTTP method stays 405 (must NOT become 500). The status is
		// taken from ErrorResponse.getStatusCode() and the body is the generic sanitized message.
		ResponseEntity<String> response = handler.handleFrameworkClientError(
				new HttpRequestMethodNotSupportedException("PATCH"));

		assertThat(response.getStatusCode())
				.isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
		assertThat(response.getBody()).isEqualTo(GENERIC_ERROR_MSG);
	}


	@Test
	void frameworkUnsupportedMediaTypePreserves415()
	{
		// V4 / QA F-1: an unsupported media type stays 415 (must NOT become 500).
		ResponseEntity<String> response = handler.handleFrameworkClientError(
				new HttpMediaTypeNotSupportedException(
						"Content-Type application/xml is not supported"));

		assertThat(response.getStatusCode())
				.isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
		assertThat(response.getBody()).isEqualTo(GENERIC_ERROR_MSG);
	}


	@Test
	void accessDeniedIsRethrownNotSwallowed()
	{
		// Re-throwing preserves the 403 rendered by Spring Security (V2). A regression that
		// swallowed it here would turn 403 into 500.
		AccessDeniedException denied =
				new AccessDeniedException("Access is denied");
		assertThatExceptionOfType(AccessDeniedException.class)
				.isThrownBy(() -> handler.handleAccessDenied(denied))
				.isSameAs(denied);
	}


	@Test
	void authenticationExceptionIsRethrownNotSwallowed()
	{
		// Re-throwing preserves the 401 challenge rendered by Spring Security (V2).
		AuthenticationException authEx =
				new BadCredentialsException("Bad credentials");
		assertThatExceptionOfType(AuthenticationException.class)
				.isThrownBy(() -> handler.handleAuthentication(authEx))
				.isSameAs(authEx);
	}

}
