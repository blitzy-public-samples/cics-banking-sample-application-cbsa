/*                                                                        */
/* Copyright IBM Corp. 2025                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.springboot.paymentinterface.controllers;

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
import org.springframework.test.web.servlet.MvcResult;

/*
 * V4 Sensitive Data Exposure - direct response-body sanitization verification for the Payment
 * {@code GlobalExceptionHandler} (closes QA test-coverage finding #3).
 *
 * The existing InputValidationTest proved only the STATUS (400) of a rejected /submit request.
 * This test asserts the response BODY content directly:
 *
 *   CWE-209 (Generation of Error Message Containing Sensitive Information) / OWASP A09,A02:
 *     a validation failure on the /submit @RestController endpoint returns exactly the fixed
 *     generic payload {status, error, message="Invalid request parameters."} and NOTHING else -
 *     no stack trace ("trace"), no exception class name ("exception"), no framework/constraint
 *     detail, and no echo of the offending submitted input value.
 *
 * The request authenticates as a TELLER with a valid CSRF token so the V2 (authn/z) and V6
 * (CSRF) controls pass and the ERROR-BODY behavior is isolated (a blank acctnum triggers the
 * ConstraintViolationException that GlobalExceptionHandler maps to the generic 400).
 */
@SpringBootTest
@AutoConfigureMockMvc
class ErrorResponseSanitizationTest
{

	@Autowired
	private MockMvc mvc;

	private static final String GENERIC_MESSAGE = "Invalid request; please check your input and try again.";


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


	@Test
	@WithMockUser(username = "teller", roles = "TELLER")
	void validationErrorBodyIsGenericAndLeaksNothing() throws Exception
	{
		// A recognisable "sensitive" organisation value; it must NOT be echoed in the body.
		String sensitiveOrg = "SECRET-ORG-CODE-XYZ";

		MvcResult result = mvc
				.perform(post("/submit").with(csrf())
						.param("acctnum", "")
						.param("amount", "10")
						.param("organisation", sensitiveOrg))
				// The generic 400 contract from GlobalExceptionHandler: handleValidation(...)
				// returns a ResponseEntity<String> generic body (module-wide String contract,
				// consistent with Customer Services) - not a JSON error object.
				.andExpect(status().isBadRequest())
				.andReturn();

		String body = result.getResponse().getContentAsString();

		// Belt-and-braces: the raw body must not contain framework/exception internals, a
		// SQLCODE, or the offending submitted value (CWE-209 / CWE-532).
		assertThat(body)
				.isEqualTo(GENERIC_MESSAGE)
				.doesNotContain("ConstraintViolation")
				.doesNotContain("jakarta")
				.doesNotContain("NotBlank")
				.doesNotContain("Exception")
				.doesNotContain("SQLCODE")
				.doesNotContain(sensitiveOrg);
	}

}
