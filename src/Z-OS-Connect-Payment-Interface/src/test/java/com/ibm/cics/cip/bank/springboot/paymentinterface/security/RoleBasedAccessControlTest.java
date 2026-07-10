/*                                                                        */
/* Copyright IBM Corp. 2025                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.springboot.paymentinterface.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
 * Verification for finding F-001 (MAJOR - AAP Compliance / Integration / Security RBAC):
 * the @PreAuthorize("hasRole('TELLER')") guards on the Payment money-movement endpoints
 * (/submit in ParamsController line 48, /paydbcr in WebController line 62) are now backed by
 * an externalized ROLE_TELLER authority source (SecurityConfig UserDetailsService). These tests
 * prove the V2 contract (OWASP A01 Broken Access Control / A07 Auth Failures; CWE-306/CWE-862)
 * for every guarded route:
 *   - unauthenticated            -> 401 Unauthorized
 *   - authenticated, non-TELLER  -> 403 Forbidden
 *   - authenticated TELLER       -> request passes the authorization gate (NOT 401/403), so
 *                                   authorized-user behavior is preserved (resolves F-001).
 * CSRF (V6, CWE-352) is exercised via .with(csrf()) so the CsrfFilter (which runs before
 * authorization) does not mask the authentication/authorization outcome under test.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RoleBasedAccessControlTest
{

	@Autowired
	private MockMvc mvc;


	@BeforeAll
	static void stubZosConnectEndpoint()
	{
		// ConnectionInfo resolves these at request time (not at startup). Provide safe stubs so a
		// TELLER-authorized request exercises the handler deterministically; the downstream connect
		// fails fast (refused) and is caught, so the response is never 401/403 for a valid teller.
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


	// ---- POST /submit (ParamsController) - finding F-001 line 48 ----

	@Test
	void submitUnauthenticatedReturns401() throws Exception
	{
		mvc.perform(post("/submit").with(csrf())
				.param("acctnum", "12345678")
				.param("amount", "10.00")
				.param("organisation", "ACME"))
				.andExpect(status().isUnauthorized());
	}


	@Test
	@WithMockUser(username = "clerk", roles = "CUSTOMER")
	void submitAuthenticatedNonTellerReturns403() throws Exception
	{
		mvc.perform(post("/submit").with(csrf())
				.param("acctnum", "12345678")
				.param("amount", "10.00")
				.param("organisation", "ACME"))
				.andExpect(status().isForbidden());
	}


	@Test
	@WithMockUser(username = "teller", roles = "TELLER")
	void submitAuthenticatedTellerPassesAuthorization() throws Exception
	{
		int status = mvc
				.perform(post("/submit").with(csrf())
						.param("acctnum", "12345678")
						.param("amount", "10.00")
						.param("organisation", "ACME"))
				.andReturn().getResponse().getStatus();
		assertThat(status)
				.as("authorized TELLER must pass the authorization gate on /submit")
				.isNotIn(401, 403);
	}


	// ---- POST /paydbcr (WebController) - finding F-001 line 62 ----

	@Test
	void paydbcrUnauthenticatedReturns401() throws Exception
	{
		mvc.perform(post("/paydbcr").with(csrf()))
				.andExpect(status().isUnauthorized());
	}


	@Test
	@WithMockUser(username = "clerk", roles = "CUSTOMER")
	void paydbcrAuthenticatedNonTellerReturns403() throws Exception
	{
		mvc.perform(post("/paydbcr").with(csrf()))
				.andExpect(status().isForbidden());
	}


	@Test
	@WithMockUser(username = "teller", roles = "TELLER")
	void paydbcrAuthenticatedTellerPassesAuthorization() throws Exception
	{
		int status = mvc.perform(post("/paydbcr").with(csrf()))
				.andReturn().getResponse().getStatus();
		assertThat(status)
				.as("authorized TELLER must pass the authorization gate on /paydbcr")
				.isNotIn(401, 403);
	}

}
