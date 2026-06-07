/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.core.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import com.ibm.cics.cip.bank.core.dto.updatecustomer.UpdateCustomerJson;
import com.ibm.cics.cip.bank.core.dto.updatecustomer.UpdcustJson;
import com.ibm.cics.cip.bank.core.exception.BusinessRuleException;
import com.ibm.cics.cip.bank.core.service.CustomerService;

/**
 * Controller <em>contract</em> integration test for
 * {@link UpdateCustomerController} &mdash; the proof that the production
 * controller reproduces the frozen z/OS Connect <em>update-customer</em>
 * ({@code updcust}) JSON contract byte-for-byte (feature&nbsp;F-019). This is one
 * of the ten {@code *IT.java} contract tests in
 * {@code com.ibm.cics.cip.bank.core.controller}; it targets
 * {@code PUT /updcust/update}.
 *
 * <h2>Why a DB-free web slice (and not {@code @SpringBootTest})</h2>
 * <p>The test is a {@link WebMvcTest @WebMvcTest(UpdateCustomerController.class)}
 * slice: it loads ONLY this controller's Spring&nbsp;MVC infrastructure (request
 * mapping, the Jackson message converters that apply the frozen envelope naming,
 * Bean&nbsp;Validation, and the {@code @RestControllerAdvice}) and replaces the
 * business collaborator with a {@link MockitoBean @MockitoBean}
 * {@link CustomerService}. No datasource, JPA {@code EntityManager}, Hibernate,
 * or Flyway is started, so the test runs GREEN on Java&nbsp;17 with no PostgreSQL
 * present. A full {@code @SpringBootTest} is deliberately avoided because it
 * would bootstrap the persistence layer this contract test does not exercise.
 * {@code @MockitoBean} (the modern successor of the deprecated {@code @MockBean},
 * matching the established convention of the sibling controller {@code *IT} tests
 * in this package) isolates the controller's contract / envelope behaviour from
 * the {@code UPDCUST.cbl} business logic, which is verified independently by
 * {@code CustomerServiceTest}.</p>
 *
 * <h2>What this pins (the frozen contract)</h2>
 * <ul>
 *   <li><strong>{@code PUT /updcust/update}</strong> at the ROOT context (no
 *       servlet context-path), consuming and producing {@code application/json}
 *       (verified against {@code updcust/api-docs/swagger.json}, operationId
 *       {@code putCScustupd}, and {@code package.xml}: basePath {@code /updcust},
 *       relativePath {@code /update}).</li>
 *   <li><strong>A single top-level wire key {@code UpdCust}</strong> on both the
 *       request and the response envelope &mdash; never any sibling top-level
 *       field &mdash; so the preserved customer-services consumer reads it with
 *       zero change.</li>
 *   <li><strong>A single HTTP&nbsp;200 in every outcome.</strong> On success the
 *       service's populated envelope ({@code CommUpdSuccess="Y"}, the echoed name
 *       and address, the zero-padded customer number) round-trips. On a business
 *       rejection the service throws a {@link BusinessRuleException} (so its
 *       {@code @Transactional} boundary unwinds), the controller catches it and
 *       rebuilds the frozen {@code UpdCust} envelope &mdash; STILL at
 *       HTTP&nbsp;200.</li>
 * </ul>
 *
 * <h2>Key contrast with {@code UpdateAccountControllerIT}</h2>
 * <p>This test is symmetric with the sibling {@code UpdateAccountControllerIT}
 * with one decisive difference: the {@code UpdCust} envelope carries a dedicated
 * fail-code field {@code CommUpdFailCd}, whereas the {@code UpdAcc} envelope has
 * only {@code CommSuccess}. The frozen customer-services consumer
 * ({@code WebController.checkIfResponseValidUpdateCust}) reads
 * {@code UpdCust.CommUpdSuccess == "N"} and THEN inspects {@code CommUpdFailCd}
 * to distinguish {@code "4"} (no name and no address supplied) from {@code "T"}
 * (invalid title). The failure assertion therefore checks BOTH
 * {@code CommUpdSuccess="N"} AND {@code CommUpdFailCd="T"}.</p>
 *
 * <h2>Behavioural note (parity, not enhancement)</h2>
 * <p>{@code UPDCUST} changes the customer name and/or address only and writes no
 * {@code PROCTRAN} audit record (feature&nbsp;F-011). All of that logic lives in
 * {@link CustomerService#updateCustomer}; this slice asserts only the wire
 * contract, not persistence, so the service is mocked and matched with Mockito
 * {@code any()} (the controller adapts the inbound {@code UpdCust} wrapper into
 * an {@code UpdateCustomerForm} before delegating).</p>
 *
 * <p><strong>No mainframe coupling.</strong> This test imports only JUnit&nbsp;5,
 * Spring&nbsp;Test/MVC, Mockito, Jackson, and {@code bank-core} types; it never
 * references {@code com.ibm.cics.server} (JCICS), {@code com.ibm.jzos}, or
 * {@code com.ibm.websphere}, and it uses no {@code double}/{@code float}.</p>
 *
 * @see UpdateCustomerController
 * @see CustomerService#updateCustomer
 * @see UpdateCustomerJson
 * @see UpdcustJson
 */
@WebMvcTest(UpdateCustomerController.class)
@DisplayName("UpdateCustomerController contract IT — PUT /updcust/update, frozen UpdCust envelope (F-019)")
class UpdateCustomerControllerIT
{

	/** Frozen endpoint path: ROOT context, no context-path prefix. */
	private static final String ENDPOINT = "/updcust/update";

	/** Top-level wire envelope key shared by the request and the response. */
	private static final String ENVELOPE_KEY = "UpdCust";

	/** Success flag value on the response envelope (COBOL {@code COMM-UPD-SUCCESS='Y'}). */
	private static final String FLAG_SUCCESS = "Y";

	/** Failure flag value on the response envelope (COBOL {@code COMM-UPD-SUCCESS='N'}). */
	private static final String FLAG_FAILURE = "N";

	/** UPDCUST fail code for an invalid honorific title (COBOL {@code COMM-UPD-FAIL-CD='T'}). */
	private static final String FAIL_CODE_INVALID_TITLE = "T";

	/** Sample eye-catcher (copybook {@code COMM-EYE}, width 4); echoed for wire parity. */
	private static final String SAMPLE_EYE = "CUST";

	/** Sample sort code (zero-padded width 6); the bank's single sort code. */
	private static final String SAMPLE_SORTCODE = "987654";

	/** Sample customer number, zero-padded to the fixed COBOL width of 10. */
	private static final String SAMPLE_CUSTNO = "0000000123";

	/** Sample updated customer name (within the {@code PIC X(60)} contract width). */
	private static final String SAMPLE_NAME = "Mr Alan Turing";

	/** Sample updated customer address (within the {@code PIC X(160)} contract width). */
	private static final String SAMPLE_ADDRESS = "1 Maths Lane, Bletchley, MK3 6EB";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@MockitoBean
	private CustomerService customerService;

	/**
	 * Success path: a contract-valid {@code UpdCust} request is delegated to the
	 * service, and the populated success envelope round-trips at HTTP&nbsp;200
	 * with the single {@code UpdCust} key, {@code CommUpdSuccess="Y"}, the echoed
	 * (updated) name and address, and the zero-padded customer number. The
	 * controller adapts the {@code UpdCust} wrapper into an
	 * {@code UpdateCustomerForm}, so the stub matches with Mockito {@code any()}.
	 *
	 * @throws Exception if the MockMvc exchange or JSON (de)serialisation fails
	 */
	@Test
	@DisplayName("PUT /updcust/update — success returns the UpdCust envelope (200, CommUpdSuccess=Y, echoed name/address, zero-padded CommCustno)")
	void putUpdate_success_returnsUpdCustEnvelope() throws Exception
	{
		// Stub the (mocked) service: any adapted form yields the success envelope.
		when(customerService.updateCustomer(any())).thenReturn(successEnvelope());

		mockMvc.perform(put(ENDPOINT)
				.contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.APPLICATION_JSON)
				.content(requestBody()))
				.andExpect(status().isOk())
				.andExpect(content()
						.contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$." + ENVELOPE_KEY).exists())
				.andExpect(jsonPath("$.UpdCust.CommUpdSuccess").value(FLAG_SUCCESS))
				.andExpect(jsonPath("$.UpdCust.CommName").value(SAMPLE_NAME))
				.andExpect(jsonPath("$.UpdCust.CommAddress").value(SAMPLE_ADDRESS))
				.andExpect(jsonPath("$.UpdCust.CommCustno").exists())
				.andExpect(jsonPath("$.UpdCust.CommCustno").value(SAMPLE_CUSTNO));

		// The controller must delegate exactly once to the business service.
		verify(customerService).updateCustomer(any());
	}

	/**
	 * Failure path (the defining case): when the service throws
	 * {@link BusinessRuleException}{@code ("T")} (the {@code UPDCUST} invalid-title
	 * rejection), the controller catches it &mdash; the throw is what unwinds the
	 * service's {@code @Transactional} unit of work &mdash; and rebuilds the
	 * frozen {@code UpdCust} envelope with {@code CommUpdSuccess="N"} AND
	 * {@code CommUpdFailCd="T"}, STILL at HTTP&nbsp;200 (never a 4xx/5xx).
	 *
	 * <p>Unlike the sibling {@code UpdateAccountController} (whose {@code UpdAcc}
	 * envelope has no fail-code field), the {@code UpdCust} envelope declares
	 * {@code CommUpdFailCd}, so BOTH the flag and the verbatim COBOL fail code are
	 * surfaced. The body is the REBUILT {@code UpdCust} envelope, NOT a generic
	 * {@code GlobalExceptionHandler} advice error body; the single-top-level-key
	 * guard at the end proves that.</p>
	 *
	 * @throws Exception if the MockMvc exchange or JSON parsing fails
	 */
	@Test
	@DisplayName("PUT /updcust/update — invalid title (BusinessRuleException 'T') returns 200 with CommUpdSuccess=N and CommUpdFailCd=T")
	void putUpdate_invalidTitle_returns200WithFailCodeT() throws Exception
	{
		when(customerService.updateCustomer(any()))
				.thenThrow(new BusinessRuleException(FAIL_CODE_INVALID_TITLE));

		MvcResult result = mockMvc
				.perform(put(ENDPOINT)
						.contentType(MediaType.APPLICATION_JSON)
						.accept(MediaType.APPLICATION_JSON)
						.content(requestBody()))
				.andExpect(status().isOk())
				.andExpect(content()
						.contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$." + ENVELOPE_KEY).exists())
				.andExpect(jsonPath("$.UpdCust.CommUpdSuccess").value(FLAG_FAILURE))
				.andExpect(jsonPath("$.UpdCust.CommUpdFailCd")
						.value(FAIL_CODE_INVALID_TITLE))
				.andReturn();

		// The controller still delegated once; the rejection came from the service.
		verify(customerService).updateCustomer(any());

		// Envelope-shape guard on the failure path: exactly one top-level key, and
		// it is UpdCust. This proves the body is the rebuilt UpdCust envelope, not a
		// generic advice error body (which would carry a different top-level shape,
		// e.g. timestamp/status/message).
		JsonNode root = objectMapper
				.readTree(result.getResponse().getContentAsString());
		assertEquals(1, root.size(),
				"Failure response must carry exactly one top-level key; body="
						+ root);
		assertTrue(root.has(ENVELOPE_KEY),
				"The single top-level key must be UpdCust; body=" + root);
	}

	/**
	 * Envelope-shape guard: a successful response tree carries EXACTLY one
	 * top-level field, and that field is {@code UpdCust}. This protects the
	 * preserved consumer from any accidental sibling top-level field.
	 *
	 * @throws Exception if the MockMvc exchange or JSON parsing fails
	 */
	@Test
	@DisplayName("PUT /updcust/update — response has exactly one top-level key: UpdCust")
	void putUpdate_singleTopLevelKeyIsUpdCust() throws Exception
	{
		when(customerService.updateCustomer(any())).thenReturn(successEnvelope());

		MvcResult result = mockMvc
				.perform(put(ENDPOINT)
						.contentType(MediaType.APPLICATION_JSON)
						.accept(MediaType.APPLICATION_JSON)
						.content(requestBody()))
				.andExpect(status().isOk())
				.andReturn();

		JsonNode root = objectMapper
				.readTree(result.getResponse().getContentAsString());
		assertEquals(1, root.size(),
				"Response must carry exactly one top-level key; body=" + root);
		assertTrue(root.has(ENVELOPE_KEY),
				"The single top-level key must be UpdCust; body=" + root);
	}

	/**
	 * Builds the populated success envelope the mocked service returns. Mirrors
	 * the {@code UPDCUST} success outcome: the updated name and address echoed
	 * back over the located customer number, with {@code CommUpdSuccess="Y"} and
	 * the fail code left blank. No money or floating-point types are involved
	 * (the {@code UpdCust} envelope carries no monetary field).
	 *
	 * @return a populated {@link UpdateCustomerJson} success envelope
	 */
	private UpdateCustomerJson successEnvelope()
	{
		UpdcustJson out = new UpdcustJson();
		out.setCommEye(SAMPLE_EYE);
		out.setCommSortcode(SAMPLE_SORTCODE);
		out.setCommCustno(SAMPLE_CUSTNO);
		out.setCommName(SAMPLE_NAME);
		out.setCommAddress(SAMPLE_ADDRESS);
		out.setCommUpdateSuccess(FLAG_SUCCESS);
		return new UpdateCustomerJson(out);
	}

	/**
	 * Serialises a contract-valid {@code {"UpdCust":{...}}} request body using the
	 * production {@link ObjectMapper}, guaranteeing the exact frozen wire names
	 * (via {@code @JsonProperty}) and values within the DTO's Bean-Validation
	 * bounds, so binding succeeds and the controller reaches the (mocked) service.
	 * The success flag is intentionally left unset on the request; the controller
	 * sets it on the response (Y on success, N on a caught rejection).
	 *
	 * @return the serialised JSON request body
	 * @throws Exception if serialisation fails
	 */
	private String requestBody() throws Exception
	{
		UpdcustJson in = new UpdcustJson();
		in.setCommEye(SAMPLE_EYE);
		in.setCommSortcode(SAMPLE_SORTCODE);
		in.setCommCustno(SAMPLE_CUSTNO);
		in.setCommName(SAMPLE_NAME);
		in.setCommAddress(SAMPLE_ADDRESS);
		return objectMapper.writeValueAsString(new UpdateCustomerJson(in));
	}

}
