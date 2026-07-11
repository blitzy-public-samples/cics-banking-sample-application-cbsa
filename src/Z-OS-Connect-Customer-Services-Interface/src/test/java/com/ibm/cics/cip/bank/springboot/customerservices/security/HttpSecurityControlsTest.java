/*                                                                        */
/* Copyright IBM Corp. 2025                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.springboot.customerservices.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/*
 * V6 Missing HTTP Security Controls - response-level enforcement verification for the
 * Customer Services module (closes QA test-coverage findings #1 and #4).
 *
 * These tests assert the ACTUAL runtime behavior of the controls declared in
 * {@code SecurityConfig}, complementing the existing RoleBasedAccessControlTest (which only
 * exercises the CSRF *success* path via .with(csrf())):
 *
 *   Finding #1 - CSRF enforcement (CWE-352 / OWASP A01):
 *     an authenticated TELLER POST to a state-changing endpoint WITHOUT a CSRF token must be
 *     rejected with HTTP 403. This proves CsrfFilter is active (CookieCsrfTokenRepository is
 *     NOT disabled); if CSRF were disabled the request would pass the CsrfFilter and this test
 *     would fail. A valid TELLER principal is used so the 403 is attributable to the missing
 *     token, not to authentication/authorization.
 *
 *   Finding #4 - security response headers (CWE-693) and restrictive CORS (CWE-942 / OWASP A05):
 *     every response carries X-Content-Type-Options: nosniff, X-Frame-Options: DENY and the
 *     explicit Content-Security-Policy; a secure request also carries Strict-Transport-Security
 *     (HSTS). A CORS pre-flight from the configured allowlisted origin succeeds and echoes that
 *     origin, whereas a pre-flight from a non-allowlisted origin is rejected (no wildcard).
 *
 * All tests are offline and deterministic: the header/CORS assertions target the GET "/"
 * services screen (no downstream z/OS Connect call), and the CSRF-negative assertion
 * short-circuits at the security filter chain. The CBSA_ZOSCONN_* stubs mirror the other
 * security tests so the application context boots consistently.
 */
@SpringBootTest
@AutoConfigureMockMvc
class HttpSecurityControlsTest
{

	@Autowired
	private MockMvc mvc;

	// The configured CORS origin allowlist (application.properties:
	// cbsa.security.cors.allowed-origins). A byte-for-byte cross-file contract.
	private static final String ALLOWED_ORIGIN = "http://localhost:3000";

	private static final String DISALLOWED_ORIGIN = "http://evil.example.com";


	@BeforeAll
	static void stubZosConnectEndpoint()
	{
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


	// ---- Finding #1: CSRF enforcement (tokenless state-change -> 403) ----

	@Test
	@WithMockUser(username = "teller", roles = "TELLER")
	void authenticatedTellerPostWithoutCsrfTokenIsForbidden() throws Exception
	{
		// NOTE: intentionally NO .with(csrf()). An authorized TELLER is still rejected with 403
		// because the CsrfFilter denies the state-changing request that carries no token. This
		// is the negative (enforcement) path the success-only RBAC tests do not cover.
		mvc.perform(post("/createacc"))
				.andExpect(status().isForbidden());
	}


	@Test
	@WithMockUser(username = "teller", roles = "TELLER")
	void authenticatedTellerDeletePostWithoutCsrfTokenIsForbidden()
			throws Exception
	{
		// A second state-changing endpoint (delete) confirms enforcement is chain-wide, not
		// specific to one handler.
		mvc.perform(post("/delcust"))
				.andExpect(status().isForbidden());
	}


	// ---- Finding #4: security response headers ----

	@Test
	@WithMockUser(username = "teller", roles = "TELLER")
	void responseCarriesSecurityHeaders() throws Exception
	{
		mvc.perform(get("/"))
				.andExpect(status().isOk())
				// CWE-693: MIME-sniffing protection (Spring Security default).
				.andExpect(header().string("X-Content-Type-Options", "nosniff"))
				// CWE-1021 clickjacking protection (Spring Security default).
				.andExpect(header().string("X-Frame-Options", "DENY"))
				// CWE-693: the CSP is the only header not added by defaults; it is set
				// explicitly in SecurityConfig. Presentation-only relaxations (style/font/img)
				// permit the Carbon Design System CDN stylesheet + inline style attributes on
				// the Thymeleaf admin forms; script-src is NOT relaxed (inherits default-src
				// 'self'), so the primary XSS control stays strict.
				.andExpect(header().string("Content-Security-Policy",
						"default-src 'self'; "
								+ "style-src 'self' 'unsafe-inline' https://unpkg.com; "
								+ "font-src 'self' https://unpkg.com https://1.www.s81c.com data:; "
								+ "img-src 'self' data:"));
	}


	@Test
	@WithMockUser(username = "teller", roles = "TELLER")
	void secureResponseCarriesHstsHeader() throws Exception
	{
		// HSTS (Strict-Transport-Security) is emitted by Spring Security ONLY on secure
		// requests, so drive the request over (simulated) HTTPS.
		mvc.perform(get("/").secure(true))
				.andExpect(status().isOk())
				.andExpect(header().exists("Strict-Transport-Security"));
	}


	// ---- Finding #4: restrictive CORS allowlist (CWE-942) ----

	@Test
	void corsPreflightFromAllowlistedOriginIsPermitted() throws Exception
	{
		// A CORS pre-flight (OPTIONS) is handled by the CORS filter BEFORE authentication, so no
		// principal is supplied. An allowlisted origin must be accepted and echoed back.
		mvc.perform(options("/enqacct")
				.header("Origin", ALLOWED_ORIGIN)
				.header("Access-Control-Request-Method", "POST"))
				.andExpect(status().isOk())
				.andExpect(header().string("Access-Control-Allow-Origin",
						ALLOWED_ORIGIN));
	}


	@Test
	void corsPreflightFromDisallowedOriginIsRejected() throws Exception
	{
		// A non-allowlisted origin must be rejected by the restrictive allowlist (never a
		// wildcard), so the pre-flight is forbidden and no allow-origin is echoed.
		mvc.perform(options("/enqacct")
				.header("Origin", DISALLOWED_ORIGIN)
				.header("Access-Control-Request-Method", "POST"))
				.andExpect(status().isForbidden())
				.andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
	}

}
