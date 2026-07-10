/*                                                                        */
/* Copyright IBM Corp. 2025                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.springboot.paymentinterface.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
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
 * Runtime regression verification for the Payment-module input-validation fixes.
 *
 * F-QA1 (MAJOR - V3 CWE-20 / OWASP A03; API contract): a @RequestParam Bean-Validation
 *   failure on /submit (@NotBlank / @Size / @Positive) must be rejected with HTTP 400 - NOT
 *   the previous misleading HTTP 500 - by the new GlobalExceptionHandler
 *   (@ControllerAdvice mapping ConstraintViolationException -> 400).
 *
 * F-QA2 (MINOR - V3 CWE-20 / OWASP A03; reject-by-default): an empty required identifier on
 *   the /paydbcr TransferForm (acctNumber, organisation) must be rejected at the controller
 *   boundary (a field binding error) rather than forwarded to the downstream money-movement
 *   call, now that @Size(min = 1, ...) has closed the empty-string gap.
 *
 * All tests authenticate as a TELLER (the authorized role) with a valid CSRF token so the
 * V2 (authn/z) and V6 (CSRF) controls pass and the INPUT-VALIDATION behavior is isolated.
 * One test intentionally omits authentication to prove that Spring Security still front-runs
 * the handler (401), i.e. the GlobalExceptionHandler does NOT swallow the auth/authorization
 * outcome.
 */
@SpringBootTest
@AutoConfigureMockMvc
class InputValidationTest
{

	@Autowired
	private MockMvc mvc;


	@BeforeAll
	static void stubZosConnectEndpoint()
	{
		// ConnectionInfo resolves these at request time. Provide safe stubs so an authorized,
		// VALID /submit request exercises the handler deterministically (the downstream
		// connect fails fast and is caught inside the controller).
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


	// ---- F-QA1: /submit constraint violations -> HTTP 400 (was 500) ----

	@Test
	@WithMockUser(username = "teller", roles = "TELLER")
	void submitBlankAccountNumberReturns400() throws Exception
	{
		mvc.perform(post("/submit").with(csrf()).param("acctnum", "")
				.param("amount", "10").param("organisation", "ACME"))
				.andExpect(status().isBadRequest());
	}


	@Test
	@WithMockUser(username = "teller", roles = "TELLER")
	void submitOversizedAccountNumberReturns400() throws Exception
	{
		mvc.perform(post("/submit").with(csrf()).param("acctnum", "123456789")
				.param("amount", "10").param("organisation", "ACME"))
				.andExpect(status().isBadRequest());
	}


	@Test
	@WithMockUser(username = "teller", roles = "TELLER")
	void submitZeroAmountReturns400() throws Exception
	{
		mvc.perform(post("/submit").with(csrf()).param("acctnum", "12345678")
				.param("amount", "0").param("organisation", "ACME"))
				.andExpect(status().isBadRequest());
	}


	@Test
	@WithMockUser(username = "teller", roles = "TELLER")
	void submitNegativeAmountReturns400() throws Exception
	{
		mvc.perform(post("/submit").with(csrf()).param("acctnum", "12345678")
				.param("amount", "-1").param("organisation", "ACME"))
				.andExpect(status().isBadRequest());
	}


	@Test
	@WithMockUser(username = "teller", roles = "TELLER")
	void submitOversizedOrganisationReturns400() throws Exception
	{
		mvc.perform(post("/submit").with(csrf()).param("acctnum", "12345678")
				.param("amount", "10")
				.param("organisation", "ABCDEFGHIJKLMNOPQ"))
				.andExpect(status().isBadRequest());
	}


	@Test
	@WithMockUser(username = "teller", roles = "TELLER")
	void submitValidParamsIsNeither400Nor500() throws Exception
	{
		// A valid, authorized request must pass validation; the downstream z/OS Connect
		// connect is refused and caught inside the controller, so the response is a benign
		// 200 - crucially NEITHER 400 (validation) NOR 500 (unmapped exception).
		int statusCode = mvc
				.perform(post("/submit").with(csrf())
						.param("acctnum", "12345678").param("amount", "10")
						.param("organisation", "ACME"))
				.andReturn().getResponse().getStatus();
		assertThat(statusCode)
				.as("valid, authorized /submit must not be rejected as 400 or 500")
				.isNotIn(400, 500);
	}


	@Test
	void submitUnauthenticatedBadParamsReturns401NotSwallowed() throws Exception
	{
		// Proves the GlobalExceptionHandler does NOT swallow the security outcome: an
		// unauthenticated request (even with invalid params) is still 401, because the
		// security filter chain front-runs the validation interceptor.
		mvc.perform(post("/submit").with(csrf()).param("acctnum", "")
				.param("amount", "0").param("organisation", ""))
				.andExpect(status().isUnauthorized());
	}


	// ---- F-QA2: /paydbcr empty required identifier -> boundary rejection ----

	@Test
	@WithMockUser(username = "teller", roles = "TELLER")
	void paydbcrEmptyAccountNumberIsRejectedAtBoundary() throws Exception
	{
		// Empty acctNumber must now produce a field binding error (reject-by-default) instead
		// of being forwarded downstream.
		mvc.perform(post("/paydbcr").with(csrf()).param("acctNumber", "")
				.param("amount", "10").param("organisation", "ACME"))
				.andExpect(model().attributeHasFieldErrors("transferForm",
						"acctNumber"));
	}


	@Test
	@WithMockUser(username = "teller", roles = "TELLER")
	void paydbcrEmptyOrganisationIsRejectedAtBoundary() throws Exception
	{
		mvc.perform(post("/paydbcr").with(csrf()).param("acctNumber", "12345678")
				.param("amount", "10").param("organisation", ""))
				.andExpect(model().attributeHasFieldErrors("transferForm",
						"organisation"));
	}


	@Test
	@WithMockUser(username = "teller", roles = "TELLER")
	void paydbcrValidIdentifiersHaveNoFieldErrors() throws Exception
	{
		// A well-formed request must NOT be blocked by the new min-length constraint.
		mvc.perform(post("/paydbcr").with(csrf()).param("acctNumber", "12345678")
				.param("amount", "10").param("organisation", "ACME"))
				.andExpect(model().attributeHasNoErrors("transferForm"));
	}

}
