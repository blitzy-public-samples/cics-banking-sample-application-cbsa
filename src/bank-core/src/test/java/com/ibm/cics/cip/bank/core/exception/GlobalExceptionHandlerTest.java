/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.core.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Unit test for {@link GlobalExceptionHandler} pinning the HTTP status mapping
 * hardened in response to QA checkpoint CP3:
 *
 * <ul>
 *   <li>An unmapped route &mdash; surfaced by Spring MVC as
 *       {@link NoResourceFoundException} (or the legacy
 *       {@link NoHandlerFoundException}) &mdash; must map to HTTP&nbsp;404,
 *       <em>not</em> the catch-all HTTP&nbsp;500 (the catch-all is declared for
 *       {@code Exception} and would otherwise swallow these "no route"
 *       exceptions and mislabel them).</li>
 *   <li>A genuinely unexpected exception still maps to a sanitised
 *       HTTP&nbsp;500.</li>
 * </ul>
 *
 * <p>The advice is exercised directly (no Spring context needed) because each
 * handler is a pure function of its exception argument.</p>
 */
@DisplayName("GlobalExceptionHandler — CP3 HTTP status mapping (404 for no route, 500 only as last resort)")
class GlobalExceptionHandlerTest
{

	private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

	@Test
	@DisplayName("NoResourceFoundException maps to HTTP 404, not 500")
	void noResourceFoundMapsTo404()
	{
		NoResourceFoundException ex = new NoResourceFoundException(HttpMethod.GET,
				"/nonexistent/path");

		ResponseEntity<?> response = handler.handleNotFound(ex);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(response.getBody()).isNotNull();
	}

	@Test
	@DisplayName("NoHandlerFoundException maps to HTTP 404, not 500")
	void noHandlerFoundMapsTo404()
	{
		NoHandlerFoundException ex = new NoHandlerFoundException("DELETE",
				"/delcus/remove/", new HttpHeaders());

		ResponseEntity<?> response = handler.handleNotFound(ex);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(response.getBody()).isNotNull();
	}

	@Test
	@DisplayName("An unexpected exception still maps to a sanitised HTTP 500")
	void unexpectedMapsTo500()
	{
		ResponseEntity<?> response = handler
				.handleUnexpected(new IllegalStateException("boom"));

		assertThat(response.getStatusCode())
				.isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
		assertThat(response.getBody()).isNotNull();
	}
}
