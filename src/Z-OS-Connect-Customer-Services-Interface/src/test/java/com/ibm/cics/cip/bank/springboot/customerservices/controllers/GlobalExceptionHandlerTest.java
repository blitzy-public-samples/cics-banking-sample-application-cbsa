/*                                                                        */
/* Copyright IBM Corp. 2025                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.springboot.customerservices.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;

/*
 * V4 Sensitive Data Exposure - direct response-body sanitization verification for the
 * Customer Services {@code GlobalExceptionHandler} (closes QA test-coverage finding #3).
 *
 * The existing suite proved the handler is active only INDIRECTLY (via status codes). This
 * test asserts the sanitization contract DIRECTLY and deterministically:
 *
 *   CWE-209 (Generation of Error Message Containing Sensitive Information) / OWASP A09,A02:
 *     when an unexpected exception carries sensitive detail (a Db2 SQLCODE, PII), the response
 *     body returned to the client is the fixed generic message ONLY - it contains no exception
 *     message, no SQLCODE, no PII and no internal class name. Full detail is confined to the
 *     server-side log.
 *
 *   V2 preservation (CWE-306/CWE-862 / OWASP A01,A07):
 *     the dedicated AccessDeniedException / AuthenticationException handlers must RE-THROW (never
 *     swallow) so Spring Security's ExceptionTranslationFilter still renders 401/403. If these
 *     carve-outs regressed to being caught by the broad Exception handler, 403/401 would become
 *     a rendered 500 - these tests lock that behavior.
 *
 * Implemented as a focused unit test (no Spring context) because the CS handler is a fallback
 * for genuinely uncaught exceptions, which cannot be triggered deterministically through
 * MockMvc without contriving a failing controller path.
 */
class GlobalExceptionHandlerTest
{

	// The exact generic client message (must stay in lock-step with the production constant).
	private static final String GENERIC_ERROR_MESSAGE =
			"There was an error processing the request; Please try again later or check logs for more info.";

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
				.isEqualTo(GENERIC_ERROR_MESSAGE);

		// The body must not leak the exception message, SQLCODE, PII or the internal class name.
		assertThat(response.getBody())
				.doesNotContain(SENSITIVE_SQLCODE)
				.doesNotContain(SENSITIVE_PII)
				.doesNotContain("Db2 failure")
				.doesNotContain("IllegalStateException")
				.doesNotContain("java.lang");
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
