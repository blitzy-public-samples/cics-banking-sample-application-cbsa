/*                                                                        */
/* Copyright IBM Corp. 2025                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.springboot.customerservices.security;

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
 * the @PreAuthorize("hasRole('TELLER')") guards on the Customer Services state-changing
 * handlers in WebController (createacc line 390, createcust line 502, updateacc line 608,
 * updatecust line 712, delacct line 818, delcust line 886) are now backed by an externalized
 * ROLE_TELLER authority source (SecurityConfig UserDetailsService). These tests prove the V2
 * contract (OWASP A01 Broken Access Control / A07 Auth Failures; CWE-306/CWE-862) for every
 * guarded route:
 *   - unauthenticated            -> 401 Unauthorized
 *   - authenticated, non-TELLER  -> 403 Forbidden
 *   - authenticated TELLER       -> request passes the authorization gate (NOT 401/403), so
 *                                   authorized-user behavior is preserved (resolves F-001).
 * CSRF (V6, CWE-352) is exercised via .with(csrf()) so the CsrfFilter (which runs before
 * authorization) does not mask the authentication/authorization outcome under test. Each POST
 * is sent with an empty form so the @Valid+BindingResult handler short-circuits to its view
 * (no downstream call), keeping the teller case deterministic and offline.
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
		// TELLER-authorized request exercises the handler deterministically; an empty form makes the
		// handler short-circuit to its view, so the response is never 401/403 for a valid teller.
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


	// ---- POST /createacc (WebController) - finding F-001 line 390 ----

	@Test
	void createAccUnauthenticatedReturns401() throws Exception
	{
		mvc.perform(post("/createacc").with(csrf()))
			.andExpect(status().isUnauthorized());
	}


	@Test
	@WithMockUser(username = "clerk", roles = "CUSTOMER")
	void createAccAuthenticatedNonTellerReturns403() throws Exception
	{
		mvc.perform(post("/createacc").with(csrf()))
			.andExpect(status().isForbidden());
	}


	@Test
	@WithMockUser(username = "teller", roles = "TELLER")
	void createAccAuthenticatedTellerPassesAuthorization() throws Exception
	{
		int status = mvc.perform(post("/createacc").with(csrf()))
			.andReturn().getResponse().getStatus();
		assertThat(status)
			.as("authorized TELLER must pass the authorization gate on /createacc")
			.isNotIn(401, 403);
	}


	// ---- POST /createcust (WebController) - finding F-001 line 502 ----

	@Test
	void createCustUnauthenticatedReturns401() throws Exception
	{
		mvc.perform(post("/createcust").with(csrf()))
			.andExpect(status().isUnauthorized());
	}


	@Test
	@WithMockUser(username = "clerk", roles = "CUSTOMER")
	void createCustAuthenticatedNonTellerReturns403() throws Exception
	{
		mvc.perform(post("/createcust").with(csrf()))
			.andExpect(status().isForbidden());
	}


	@Test
	@WithMockUser(username = "teller", roles = "TELLER")
	void createCustAuthenticatedTellerPassesAuthorization() throws Exception
	{
		int status = mvc.perform(post("/createcust").with(csrf()))
			.andReturn().getResponse().getStatus();
		assertThat(status)
			.as("authorized TELLER must pass the authorization gate on /createcust")
			.isNotIn(401, 403);
	}


	// ---- POST /updateacc (WebController) - finding F-001 line 608 ----

	@Test
	void updateAccUnauthenticatedReturns401() throws Exception
	{
		mvc.perform(post("/updateacc").with(csrf()))
			.andExpect(status().isUnauthorized());
	}


	@Test
	@WithMockUser(username = "clerk", roles = "CUSTOMER")
	void updateAccAuthenticatedNonTellerReturns403() throws Exception
	{
		mvc.perform(post("/updateacc").with(csrf()))
			.andExpect(status().isForbidden());
	}


	@Test
	@WithMockUser(username = "teller", roles = "TELLER")
	void updateAccAuthenticatedTellerPassesAuthorization() throws Exception
	{
		int status = mvc.perform(post("/updateacc").with(csrf()))
			.andReturn().getResponse().getStatus();
		assertThat(status)
			.as("authorized TELLER must pass the authorization gate on /updateacc")
			.isNotIn(401, 403);
	}


	// ---- POST /updatecust (WebController) - finding F-001 line 712 ----

	@Test
	void updateCustUnauthenticatedReturns401() throws Exception
	{
		mvc.perform(post("/updatecust").with(csrf()))
			.andExpect(status().isUnauthorized());
	}


	@Test
	@WithMockUser(username = "clerk", roles = "CUSTOMER")
	void updateCustAuthenticatedNonTellerReturns403() throws Exception
	{
		mvc.perform(post("/updatecust").with(csrf()))
			.andExpect(status().isForbidden());
	}


	@Test
	@WithMockUser(username = "teller", roles = "TELLER")
	void updateCustAuthenticatedTellerPassesAuthorization() throws Exception
	{
		int status = mvc.perform(post("/updatecust").with(csrf()))
			.andReturn().getResponse().getStatus();
		assertThat(status)
			.as("authorized TELLER must pass the authorization gate on /updatecust")
			.isNotIn(401, 403);
	}


	// ---- POST /delacct (WebController) - finding F-001 line 818 ----

	@Test
	void delAcctUnauthenticatedReturns401() throws Exception
	{
		mvc.perform(post("/delacct").with(csrf()))
			.andExpect(status().isUnauthorized());
	}


	@Test
	@WithMockUser(username = "clerk", roles = "CUSTOMER")
	void delAcctAuthenticatedNonTellerReturns403() throws Exception
	{
		mvc.perform(post("/delacct").with(csrf()))
			.andExpect(status().isForbidden());
	}


	@Test
	@WithMockUser(username = "teller", roles = "TELLER")
	void delAcctAuthenticatedTellerPassesAuthorization() throws Exception
	{
		int status = mvc.perform(post("/delacct").with(csrf()))
			.andReturn().getResponse().getStatus();
		assertThat(status)
			.as("authorized TELLER must pass the authorization gate on /delacct")
			.isNotIn(401, 403);
	}


	// ---- POST /delcust (WebController) - finding F-001 line 886 ----

	@Test
	void delCustUnauthenticatedReturns401() throws Exception
	{
		mvc.perform(post("/delcust").with(csrf()))
			.andExpect(status().isUnauthorized());
	}


	@Test
	@WithMockUser(username = "clerk", roles = "CUSTOMER")
	void delCustAuthenticatedNonTellerReturns403() throws Exception
	{
		mvc.perform(post("/delcust").with(csrf()))
			.andExpect(status().isForbidden());
	}


	@Test
	@WithMockUser(username = "teller", roles = "TELLER")
	void delCustAuthenticatedTellerPassesAuthorization() throws Exception
	{
		int status = mvc.perform(post("/delcust").with(csrf()))
			.andReturn().getResponse().getStatus();
		assertThat(status)
			.as("authorized TELLER must pass the authorization gate on /delcust")
			.isNotIn(401, 403);
	}

}
