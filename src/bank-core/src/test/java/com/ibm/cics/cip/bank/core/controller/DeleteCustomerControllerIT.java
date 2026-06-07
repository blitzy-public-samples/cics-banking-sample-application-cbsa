/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.core.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibm.cics.cip.bank.core.dto.deletecustomer.DelcusJson;
import com.ibm.cics.cip.bank.core.dto.deletecustomer.DeleteCustomerJson;
import com.ibm.cics.cip.bank.core.exception.BusinessRuleException;
import com.ibm.cics.cip.bank.core.service.CustomerService;

/**
 * Controller <em>contract</em> integration test for
 * {@link DeleteCustomerController} &mdash; proof that the standalone
 * {@code bank-core} module reproduces the frozen z/OS Connect
 * <em>delete-customer</em> ({@code delcus}) JSON contract byte-for-byte
 * (feature&nbsp;F-019). It is one of the ten {@code *IT.java} controller
 * contract tests that pin the preserved REST surface so the existing
 * React/Carbon front end and the Spring Boot interface modules integrate with
 * only a base-URL re-point rather than a rewrite.
 *
 * <h2>Frozen contract pinned here (verified against {@code delcus/api-docs/swagger.json}
 * and {@code delcus/package.xml})</h2>
 * <ul>
 *   <li><strong>Method&nbsp;+&nbsp;path:</strong>
 *       {@code DELETE /delcus/remove/{custno}} at the ROOT context (no servlet
 *       context-path; operationId {@code deleteCScustdel}, basePath
 *       {@code /delcus}, relativePath {@code /remove/{custno}}). The customer
 *       number is carried as the {@code {custno}} path variable &mdash; the real
 *       consumer ({@code WebController.deleteCust}) invokes
 *       {@code client.delete().retrieve()} with <strong>no request body</strong>,
 *       zero-padding the number to width&nbsp;10
 *       ({@code String.format("%10s", ...).replace(" ", "0")}), so these tests
 *       issue {@code delete(...)} without content and use a width-10 padded
 *       value.</li>
 *   <li><strong>Single HTTP&nbsp;200:</strong> the endpoint returns
 *       {@code application/json} with HTTP&nbsp;{@code 200} on success
 *       <em>and</em> on a business failure &mdash; the legacy z/OS Connect
 *       contract delivers the business outcome in the body, never via the HTTP
 *       status, so the consumer inspects the envelope rather than the status
 *       code.</li>
 *   <li><strong>Top-level envelope:</strong> exactly one wire key,
 *       {@code DelCus} (the {@link DeleteCustomerJson} wrapper over the inner
 *       {@link DelcusJson} payload).</li>
 *   <li><strong>Fail-code field present:</strong> unlike a bare success/fail
 *       flag, this envelope carries BOTH a {@code CommDelSuccess} flag and a
 *       dedicated {@code CommDelFailCd} code (the {@code CommDel...} prefix is
 *       specific to delete-customer). On a rejected delete the production
 *       controller rebuilds the {@code DelCus} envelope with
 *       {@code CommDelSuccess = "N"} and the verbatim COBOL fail code in
 *       {@code CommDelFailCd} (the COBOL {@code DELCUS} fail code {@code "1"}
 *       means the customer was not found), so the failure assertion below targets
 *       exactly those two fields.</li>
 *   <li><strong>Fixed-width path parse:</strong> the controller parses
 *       {@code {custno}} through {@code BankFormat.parseCustomerNumber}, which
 *       enforces the {@code PIC 9(10)} ten-digit contract width. An eleven-digit
 *       value (inside {@code long} range, so a naive {@code Long.parseLong} would
 *       have accepted it) is rejected as HTTP&nbsp;400 by the module's
 *       {@code @RestControllerAdvice} and never reaches the service.</li>
 * </ul>
 *
 * <h2>Test strategy &mdash; DB-free web slice</h2>
 * <p>This is a {@link WebMvcTest @WebMvcTest(DeleteCustomerController.class)}
 * slice: it loads only the {@code DeleteCustomerController} MVC web layer (plus
 * the module's {@code @RestControllerAdvice}) and replaces the business
 * {@link CustomerService} with a {@link MockitoBean @MockitoBean}. No datasource,
 * JPA, or Flyway is started, so the test is green on Java&nbsp;17 without a
 * running PostgreSQL instance. {@code @SpringBootTest} is deliberately
 * <em>not</em> used. The {@code @MockitoBean} is the modern successor of the
 * {@code @MockBean} deprecated since Spring&nbsp;Boot&nbsp;3.4, matching the
 * established convention of the sibling controller contract tests. The module
 * {@code @SpringBootApplication} ({@code BankCoreApplication}) at the package
 * root is auto-discovered as the {@code @SpringBootConfiguration} that bootstraps
 * the sliced context.</p>
 *
 * <p>The class never references {@code com.ibm.cics.server} (JCICS),
 * {@code com.ibm.jzos}, or {@code com.ibm.websphere}; those legacy mainframe
 * runtimes are decommissioned in the target. No {@code double}/{@code float}
 * appears anywhere &mdash; the {@code DelCus} payload carries only {@code String}
 * and {@code Integer} fields (no monetary value), so the money-fidelity rule is
 * satisfied trivially.</p>
 *
 * @see DeleteCustomerController
 * @see DeleteCustomerJson
 * @see DelcusJson
 * @see CustomerService#deleteCustomer(long)
 */
@WebMvcTest(DeleteCustomerController.class)
@DisplayName("DeleteCustomerController contract IT — DELETE /delcus/remove/{custno}, frozen DelCus envelope (F-019)")
class DeleteCustomerControllerIT
{

	/**
	 * Frozen route template: ROOT context, no context-path prefix. Used with
	 * {@link org.springframework.test.web.servlet.request.MockMvcRequestBuilders#delete(String, Object...)}
	 * so the {@code {custno}} URI variable is expanded per request.
	 */
	private static final String ENDPOINT = "/delcus/remove/{custno}";

	/**
	 * A within-width, ten-digit, left-zero-padded customer number used on the
	 * happy path &mdash; exactly the shape the real consumer
	 * ({@code WebController}) sends after zero-padding to width&nbsp;10. The
	 * controller parses it to {@code 123L}; the mocked service is indifferent to
	 * the parsed value and always yields the fixture.
	 */
	private static final String VALID_CUSTNO_PATH = "0000000123";

	/**
	 * An eleven-digit value: inside the {@code long} range (so the legacy
	 * {@code Long.parseLong} would have accepted it) but over the ten-digit
	 * contract width, so {@code BankFormat.parseCustomerNumber} rejects it.
	 */
	private static final String OVER_WIDTH_CUSTNO = "10000000000";

	/** The single top-level wire key the frozen contract mandates. */
	private static final String TOP_LEVEL_KEY = "DelCus";

	/** Eye-catcher echoed in the delete-customer payload (copybook {@code COMM-EYE}). */
	private static final String CUSTOMER_EYECATCHER = "CUST";

	/** Sort code constant (COBOL {@code SORTCODE} 987654), width 6. */
	private static final String SORT_CODE = "987654";

	/** Echoed customer name on the success fixture. */
	private static final String CUSTOMER_NAME = "Alice Example";

	/** Echoed customer address on the success fixture. */
	private static final String CUSTOMER_ADDRESS = "1 High Street, Townsville";

	/** Date of birth on the success fixture, in the customer {@code DD/MM/YYYY} format. */
	private static final String DATE_OF_BIRTH = "01/02/1990";

	/** Credit-score review date on the success fixture, {@code DD/MM/YYYY}. */
	private static final String REVIEW_DATE = "15/03/2024";

	/** Credit score on the success fixture (0&ndash;999); a small integer, never money. */
	private static final Integer CREDIT_SCORE = 700;

	/** Delete-success flag value on success (COBOL {@code 'Y'}). */
	private static final String FLAG_SUCCESS = "Y";

	/** Delete-success flag value on a rejected delete (COBOL {@code 'N'}). */
	private static final String FLAG_FAILURE = "N";

	/**
	 * Fail code carried on the success fixture: {@code DELCUS}'s
	 * {@code buildDeleteResponse} sets {@code CommDelFailCd} to {@code "0"} on a
	 * successful delete (no failure), so the fixture mirrors it verbatim.
	 */
	private static final String SUCCESS_FAIL_CODE = "0";

	/**
	 * COBOL {@code DELCUS} fail code {@code "1"} &mdash; the customer to delete
	 * was not found. Thrown by the mocked service in the failure test.
	 */
	private static final String FAIL_NOT_FOUND = "1";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@MockitoBean
	private CustomerService customerService;

	/**
	 * Happy path: a fully populated {@link DeleteCustomerJson} returned by the
	 * service reaches the wire verbatim under the single {@code DelCus} key at
	 * HTTP&nbsp;200, with the delete-success flag {@code CommDelSuccess = "Y"},
	 * the echoed (zero-padded) customer number, and the echoed customer name. The
	 * service is invoked exactly once with the parsed customer number.
	 *
	 * @throws Exception if the MockMvc exchange fails
	 */
	@Test
	@DisplayName("DELETE /delcus/remove/{custno} — success returns the DelCus envelope (200, CommDelSuccess=Y, echoed customer detail)")
	void deleteRemove_success_returnsDelCusEnvelope() throws Exception
	{
		// anyLong(): the path variable is a customer number; the stub is
		// indifferent to its parsed value and always yields the success fixture.
		when(customerService.deleteCustomer(anyLong()))
				.thenReturn(successEnvelope());

		// DELETE with a path variable and NO request body (the real no-body
		// consumer path). The contract delivers the outcome in the body at 200.
		mockMvc.perform(delete(ENDPOINT, VALID_CUSTNO_PATH)
				.accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(content()
						.contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.DelCus").exists())
				.andExpect(jsonPath("$.DelCus.CommDelSuccess").value(FLAG_SUCCESS))
				.andExpect(jsonPath("$.DelCus.CommCustno").value(VALID_CUSTNO_PATH))
				.andExpect(jsonPath("$.DelCus.CommName").value(CUSTOMER_NAME));

		verify(customerService).deleteCustomer(anyLong());
	}

	/**
	 * Failure path: when the service raises {@link BusinessRuleException} (COBOL
	 * {@code DELCUS} fail code {@code "1"} &mdash; customer not found), the
	 * controller does NOT defer to the generic advice body; it rebuilds the
	 * frozen {@code DelCus} envelope, flagging {@code CommDelSuccess = "N"} and
	 * carrying the verbatim fail code in {@code CommDelFailCd}, and still returns
	 * HTTP&nbsp;200 (the legacy z/OS Connect contract delivers a business outcome
	 * in the body, not via the HTTP status). The echoed customer number is the
	 * raw path value the controller received.
	 *
	 * @throws Exception if the MockMvc exchange fails
	 */
	@Test
	@DisplayName("DELETE /delcus/remove/{custno} — customer-not-found returns HTTP 200 with the rebuilt DelCus failure envelope (CommDelSuccess=N, CommDelFailCd=1)")
	void deleteRemove_notFound_returns200WithFailCode1() throws Exception
	{
		when(customerService.deleteCustomer(anyLong()))
				.thenThrow(new BusinessRuleException(FAIL_NOT_FOUND));

		mockMvc.perform(delete(ENDPOINT, VALID_CUSTNO_PATH)
				.accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(content()
						.contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.DelCus").exists())
				.andExpect(jsonPath("$.DelCus.CommDelSuccess").value(FLAG_FAILURE))
				.andExpect(jsonPath("$.DelCus.CommDelFailCd").value(FAIL_NOT_FOUND))
				.andExpect(jsonPath("$.DelCus.CommCustno").value(VALID_CUSTNO_PATH));

		verify(customerService).deleteCustomer(anyLong());
	}

	/**
	 * Envelope-shape guard: the serialised response carries exactly one
	 * top-level key, {@code DelCus}, and nothing else &mdash; the contract is a
	 * single-wrapper envelope. Reading the body into a {@link JsonNode} tree and
	 * asserting {@code root.size() == 1} catches any accidental extra top-level
	 * field that a looser DTO mapping might leak onto the wire.
	 *
	 * @throws Exception if the MockMvc exchange or JSON parsing fails
	 */
	@Test
	@DisplayName("DELETE /delcus/remove/{custno} — the response has exactly one top-level key: DelCus")
	void deleteRemove_singleTopLevelKeyIsDelCus() throws Exception
	{
		when(customerService.deleteCustomer(anyLong()))
				.thenReturn(successEnvelope());

		MvcResult result = mockMvc
				.perform(delete(ENDPOINT, VALID_CUSTNO_PATH)
						.accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andReturn();

		JsonNode root = objectMapper
				.readTree(result.getResponse().getContentAsString());
		assertEquals(1, root.size(),
				"Response must carry exactly one top-level key; body=" + root);
		assertTrue(root.has(TOP_LEVEL_KEY),
				"The single top-level key must be DelCus; body=" + root);
	}

	/**
	 * Fixed-width parse guard: an eleven-digit customer number (inside the
	 * {@code long} range, but one digit over the {@code PIC 9(10)} contract
	 * width) must be rejected as HTTP&nbsp;400 by the module's
	 * {@code @RestControllerAdvice} &mdash; the controller's
	 * {@code BankFormat.parseCustomerNumber} guard raises
	 * {@link NumberFormatException} <em>before</em> any delegation &mdash; and the
	 * service must never be invoked. This proves the parse is width-bounded
	 * rather than a naive {@code Long.parseLong} (which would have accepted the
	 * value and corrupted the lookup key).
	 *
	 * @throws Exception if the MockMvc exchange fails
	 */
	@Test
	@DisplayName("DELETE /delcus/remove/10000000000 — an over-width (11-digit) custno is rejected as HTTP 400; the service is never called")
	void deleteRemove_overWidthCustno_returns400() throws Exception
	{
		mockMvc.perform(delete(ENDPOINT, OVER_WIDTH_CUSTNO)
				.accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isBadRequest());

		verify(customerService, never()).deleteCustomer(anyLong());
	}

	/**
	 * Swagger parameter-source fidelity: the {@code delcus} swagger declares a
	 * {@code DelCus} request body in addition to the {@code {custno}} path
	 * variable, so the controller accepts an OPTIONAL
	 * ({@code @RequestBody(required = false)}) body. A request that DOES carry a
	 * well-formed {@code DelCus} body is accepted (not rejected as unsupported or
	 * malformed) and still returns the {@code DelCus} envelope at HTTP&nbsp;200;
	 * the path variable remains the authoritative customer identifier, so the
	 * service is invoked exactly once.
	 *
	 * @throws Exception if the MockMvc exchange or JSON serialisation fails
	 */
	@Test
	@DisplayName("DELETE /delcus/remove/{custno} — an optional DelCus request body is accepted (swagger parameter source) and returns 200")
	void deleteRemove_withOptionalBody_isAccepted() throws Exception
	{
		when(customerService.deleteCustomer(anyLong()))
				.thenReturn(successEnvelope());

		// A well-formed DelCus envelope serialised as the optional request body;
		// every field satisfies the DelcusJson Bean Validation constraints so the
		// @Valid body is accepted rather than rejected as HTTP 400.
		String requestBody = objectMapper.writeValueAsString(successEnvelope());

		mockMvc.perform(delete(ENDPOINT, VALID_CUSTNO_PATH)
				.contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.APPLICATION_JSON)
				.content(requestBody))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.DelCus").exists())
				.andExpect(jsonPath("$.DelCus.CommDelSuccess").value(FLAG_SUCCESS));

		verify(customerService).deleteCustomer(anyLong());
	}

	/**
	 * Builds the populated success envelope the mocked service returns: a
	 * {@code DelCus} payload echoing a deleted customer, with the delete-success
	 * flag {@code "Y"}, the success fail code {@code "0"} (exactly as the real
	 * {@code DELCUS} {@code buildDeleteResponse} sets it), the zero-padded
	 * customer number and sort code, and {@code DD/MM/YYYY} dates. Every field is
	 * a {@link String} or boxed {@link Integer} &mdash; no {@code double} /
	 * {@code float} &mdash; and every value satisfies the {@link DelcusJson} Bean
	 * Validation constraints so the same envelope doubles as a valid optional
	 * request body.
	 *
	 * @return a fully populated {@link DeleteCustomerJson} response envelope
	 */
	private DeleteCustomerJson successEnvelope()
	{
		DelcusJson payload = new DelcusJson();
		payload.setCommEye(CUSTOMER_EYECATCHER);
		payload.setCommSortcode(SORT_CODE);
		payload.setCommCustno(VALID_CUSTNO_PATH);
		payload.setCommName(CUSTOMER_NAME);
		payload.setCommAddress(CUSTOMER_ADDRESS);
		payload.setCommDateOfBirth(DATE_OF_BIRTH);
		payload.setCommCreditScore(CREDIT_SCORE);
		payload.setCommCsReviewDate(REVIEW_DATE);
		payload.setCommDelSuccess(FLAG_SUCCESS);
		payload.setCommDelFailCode(SUCCESS_FAIL_CODE);
		return new DeleteCustomerJson(payload);
	}

}

