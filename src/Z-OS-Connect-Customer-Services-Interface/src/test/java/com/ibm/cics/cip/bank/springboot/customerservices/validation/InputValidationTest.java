/*                                                                        */
/* Copyright IBM Corp. 2025                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.springboot.customerservices.validation;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/*
 * Runtime regression verification for the Customer-Services input-validation fix.
 *
 * F-QA2 (MINOR - V3 CWE-20 Improper Input Validation / OWASP A03; reject-by-default): an
 *   empty required identifier on AccountEnquiryForm.acctNumber must be rejected at the
 *   controller boundary (a field binding error) rather than forwarded to the downstream
 *   z/OS Connect call, on BOTH the read-only /enqacct enquiry and the state-changing
 *   /delacct delete. @NotNull alone accepted "" (length 0 also satisfies @Size(max=8)); the
 *   fix adds @Size(min = 1, ...) to close the empty-string gap, matching CustomerEnquiryForm.
 *
 * Requests authenticate as a TELLER with a valid CSRF token so the V2 (authn/z) and V6
 * (CSRF) controls pass and the INPUT-VALIDATION behavior is isolated.
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
		// ConnectionInfo resolves these at request time; provide safe stubs.
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


	@Test
	@WithMockUser(username = "teller", roles = "TELLER")
	void enqacctEmptyAccountNumberIsRejectedAtBoundary() throws Exception
	{
		mvc.perform(post("/enqacct").with(csrf()).param("acctNumber", ""))
				.andExpect(model().attributeHasFieldErrors("accountEnquiryForm",
						"acctNumber"));
	}


	@Test
	@WithMockUser(username = "teller", roles = "TELLER")
	void delacctEmptyAccountNumberIsRejectedAtBoundary() throws Exception
	{
		// State-changing delete: an empty key must NOT be forwarded downstream.
		mvc.perform(post("/delacct").with(csrf()).param("acctNumber", ""))
				.andExpect(model().attributeHasFieldErrors("accountEnquiryForm",
						"acctNumber"));
	}


	@Test
	@WithMockUser(username = "teller", roles = "TELLER")
	void enqacctValidAccountNumberHasNoFieldErrors() throws Exception
	{
		// A well-formed 8-char account number must NOT be blocked by the new min-length rule.
		mvc.perform(post("/enqacct").with(csrf()).param("acctNumber", "12345678"))
				.andExpect(model().attributeHasNoErrors("accountEnquiryForm"));
	}


	/*
	 * V3 (CWE-20 Improper Input Validation / OWASP A03; reject-by-default): a negative
	 * overdraft limit on CreateAccountForm must be rejected at the /createacc controller
	 * boundary (a field binding error) rather than serialised into a CreateAccountJson and
	 * forwarded to the downstream z/OS Connect /creacc/insert call. Before the fix the
	 * overdraftLimit field carried no constraint and overdraftLimit=-999 was accepted.
	 */
	@Test
	@WithMockUser(username = "teller", roles = "TELLER")
	void createaccNegativeOverdraftIsRejectedAtBoundary() throws Exception
	{
		mvc.perform(post("/createacc").with(csrf())
				.param("custNumber", "12345678").param("accountType", "ISA")
				.param("overdraftLimit", "-999").param("interestRate", "1.5"))
				.andExpect(model().attributeHasFieldErrors("createAccountForm",
						"overdraftLimit"));
	}


	/*
	 * V3 (CWE-20): a negative interest rate on CreateAccountForm must likewise be rejected at
	 * the /createacc boundary. Before the fix the interestRate field carried no constraint.
	 */
	@Test
	@WithMockUser(username = "teller", roles = "TELLER")
	void createaccNegativeInterestRateIsRejectedAtBoundary() throws Exception
	{
		mvc.perform(post("/createacc").with(csrf())
				.param("custNumber", "12345678").param("accountType", "ISA")
				.param("overdraftLimit", "0").param("interestRate", "-1.0"))
				.andExpect(model().attributeHasFieldErrors("createAccountForm",
						"interestRate"));
	}


	/*
	 * V3 (no over-constraint): a well-formed create-account form with a zero overdraft and a
	 * non-negative interest rate must NOT raise field binding errors, so authorised, valid
	 * account creation continues to behave identically for the teller (AAP zero-functional-
	 * change requirement). The downstream z/OS Connect call is expected to be unavailable in
	 * this test scope and is handled gracefully by the controller without a binding error.
	 */
	@Test
	@WithMockUser(username = "teller", roles = "TELLER")
	void createaccValidValuesHaveNoFieldErrors() throws Exception
	{
		mvc.perform(post("/createacc").with(csrf())
				.param("custNumber", "12345678").param("accountType", "ISA")
				.param("overdraftLimit", "0").param("interestRate", "1.5"))
				.andExpect(model().attributeHasNoErrors("createAccountForm"));
	}

}
