/*                                                                        */
/* Copyright IBM Corp. 2025                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.springboot.customerservices.errorhandling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ibm.cics.cip.bank.springboot.customerservices.controllers.GlobalExceptionHandler;

/*
 * Verification for QA finding F-1 (MINOR - Observability / Error Hygiene; a
 * remediation-introduced regression in the V4 control).
 *
 * Before the fix, the module's centralized GlobalExceptionHandler declared ONLY a broad
 * @ExceptionHandler(Exception.class) catch-all. Because Spring's
 * ExceptionHandlerExceptionResolver is consulted before DefaultHandlerExceptionResolver,
 * that catch-all intercepted Spring MVC's OWN framework client-error exceptions - a missing
 * static resource (NoResourceFoundException, 404), an unsupported HTTP method
 * (HttpRequestMethodNotSupportedException, 405) and an unsupported media type
 * (HttpMediaType*, 415/406) - and remapped every one to HTTP 500 while logging benign 404s
 * at ERROR with a full framework stack trace.
 *
 * The fix adds a dedicated, MORE SPECIFIC handler (handleFrameworkClientError) that restores
 * each exception's native client status, keeps the client body generic (no data exposure,
 * V4/CWE-209) and logs at DEBUG without a stack trace (V4/CWE-532, OWASP A09). These tests
 * assert:
 *   - authenticated caller, non-existent path        -> 404 (NOT 500)         [exact F-1 repro]
 *   - authenticated caller, unsupported HTTP method   -> 405 (NOT 500)
 *   - UNauthenticated caller, non-existent path       -> 401 (deny-by-default preserved: the
 *                                                        fix did not weaken access control, V2)
 *   - a genuinely uncaught exception                  -> 500 with a generic body and NO leak of
 *                                                        the exception message (catch-all/V4 kept)
 */
@SpringBootTest
@AutoConfigureMockMvc
class ErrorHandlingTest
{

	@Autowired
	private MockMvc mvc;


	@BeforeAll
	static void stubZosConnectEndpoint()
	{
		// Match RoleBasedAccessControlTest: ConnectionInfo resolves these at request time, so
		// provide safe stubs. None of the F-1 assertions actually reach a downstream call
		// (404/405 never dispatch to a handler; 401 never authenticates), but this keeps the
		// full application context deterministic and offline.
		System.setProperty("CBSA_ZOSCONN_HOST", "localhost");
		System.setProperty("CBSA_ZOSCONN_PORT", "38417");
		System.setProperty("CBSA_ZOSCONN_SCHEME", "http");
	}


	@AfterAll
	static void clearZosConnectEndpoint()
	{
		System.clearProperty("CBSA_ZOSCONN_HOST");
		System.clearProperty("CBSA_ZOSCONN_PORT");
		System.clearProperty("CBSA_ZOSCONN_SCHEME");
	}


	// ---- F-1: non-existent path must surface its native 404, not a remapped 500 ----

	@Test
	@WithMockUser(username = "teller", roles = "TELLER")
	void nonExistentPathAuthenticatedReturnsNotFound() throws Exception
	{
		mvc.perform(get("/nonexistent-xyz"))
			.andExpect(status().isNotFound());
	}


	// ---- F-1: unsupported HTTP method must surface its native 405, not a remapped 500 ----
	// /enqcust is mapped for GET and POST only (WebController); PUT is unmapped -> 405.
	// csrf() is supplied so the CsrfFilter (which runs before dispatch for state-changing
	// verbs) does not mask the method-not-supported outcome under test.

	@Test
	@WithMockUser(username = "teller", roles = "TELLER")
	void unsupportedMethodAuthenticatedReturnsMethodNotAllowed() throws Exception
	{
		mvc.perform(put("/enqcust").with(csrf()))
			.andExpect(status().isMethodNotAllowed());
	}


	// ---- Regression guard: the fix must NOT weaken deny-by-default authentication (V2). An
	// unauthenticated request (even to a non-existent path) is still challenged with 401. ----

	@Test
	void nonExistentPathUnauthenticatedReturnsUnauthorized() throws Exception
	{
		mvc.perform(get("/nonexistent-xyz"))
			.andExpect(status().isUnauthorized());
	}


	// ---- The centralized V4 control is preserved: a genuinely uncaught exception is still
	// mapped to a generic HTTP 500 by the retained catch-all, and the exception's own message
	// is NEVER echoed to the client (CWE-209). Verified in isolation with a standalone MockMvc
	// wired to the real GlobalExceptionHandler advice and a stub controller that throws. ----

	@Test
	void genuineUncaughtExceptionStillReturnsGenericInternalServerError()
			throws Exception
	{
		String secretDetail = "boom-internal-sqlcode-and-pii";
		MockMvc standalone = MockMvcBuilders
				.standaloneSetup(new ThrowingStubController(secretDetail))
				.setControllerAdvice(new GlobalExceptionHandler())
				.build();

		String body = standalone.perform(get("/blitzy-adhoc-throw"))
				.andExpect(status().isInternalServerError())
				.andReturn().getResponse().getContentAsString();

		assertThat(body)
			.as("uncaught exceptions must return the generic sanitized 500 body")
			.contains("There was an error processing the request");
		assertThat(body)
			.as("the exception message/detail must never be leaked to the client (V4 CWE-209)")
			.doesNotContain(secretDetail);
	}


	// Minimal stub controller used ONLY by the standalone catch-all assertion above. It throws a
	// non-framework RuntimeException carrying a would-be-sensitive message so the test can prove
	// that detail does not reach the client.
	@RestController
	static class ThrowingStubController
	{

		private final String detail;


		ThrowingStubController(String detail)
		{
			this.detail = detail;
		}


		@GetMapping("/blitzy-adhoc-throw")
		String boom()
		{
			throw new RuntimeException(detail);
		}
	}

}
