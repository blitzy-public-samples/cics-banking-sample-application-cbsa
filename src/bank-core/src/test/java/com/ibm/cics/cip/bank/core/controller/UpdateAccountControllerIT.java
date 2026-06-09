/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.core.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

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
import com.ibm.cics.cip.bank.core.dto.updateaccount.UpdaccJson;
import com.ibm.cics.cip.bank.core.dto.updateaccount.UpdateAccountJson;
import com.ibm.cics.cip.bank.core.exception.BusinessRuleException;
import com.ibm.cics.cip.bank.core.service.AccountService;

/**
 * Controller <em>contract</em> integration test for {@link UpdateAccountController}
 * &mdash; the proof that the production controller reproduces the frozen z/OS
 * Connect <em>update-account</em> ({@code updacc}) JSON contract byte-for-byte
 * (feature&nbsp;F-019). This is one of the ten {@code *IT.java} contract tests in
 * {@code com.ibm.cics.cip.bank.core.controller}; it targets
 * {@code PUT /updacc/update}.
 *
 * <h2>Why a DB-free web slice (and not {@code @SpringBootTest})</h2>
 * <p>The test is a {@link WebMvcTest @WebMvcTest(UpdateAccountController.class)}
 * slice: it loads ONLY this controller's Spring&nbsp;MVC infrastructure (request
 * mapping, the Jackson message converters that apply the frozen envelope naming,
 * Bean&nbsp;Validation, and the {@code @RestControllerAdvice}) and replaces the
 * business collaborator with a {@link MockitoBean @MockitoBean}
 * {@link AccountService}. No datasource, JPA {@code EntityManager}, Hibernate, or
 * Flyway is started, so the test runs GREEN on Java&nbsp;17 with no PostgreSQL
 * present. A full {@code @SpringBootTest} is deliberately avoided because it would
 * bootstrap the persistence layer that this contract test does not exercise. The
 * {@code @MockitoBean} (the modern successor of the deprecated {@code @MockBean},
 * matching the established convention of the sibling controller {@code *IT} tests
 * in this package) isolates the controller's contract / envelope behaviour from
 * the {@code UPDACC.cbl} business logic, which is verified independently by
 * {@code AccountServiceTest}.</p>
 *
 * <h2>What this pins (the frozen contract)</h2>
 * <ul>
 *   <li><strong>{@code PUT /updacc/update}</strong> at the ROOT context (no
 *       servlet context-path), consuming and producing {@code application/json}
 *       (verified against {@code updacc/api-docs/swagger.json}, operationId
 *       {@code putCSaccupd}, and {@code package.xml}: basePath {@code /updacc},
 *       relativePath {@code /update}).</li>
 *   <li><strong>A single top-level wire key {@code UpdAcc}</strong> on both the
 *       request and the response envelope &mdash; never any sibling top-level
 *       field &mdash; so the preserved customer-services consumer reads it with
 *       zero change.</li>
 *   <li><strong>A single HTTP&nbsp;200 in every outcome.</strong> On success the
 *       service's populated envelope ({@code CommSuccess="Y"}, the echoed account
 *       type, scale-2 {@code BigDecimal} interest rate and balances) round-trips.
 *       On a business rejection the service throws a
 *       {@link BusinessRuleException} (so its {@code @Transactional} boundary
 *       rolls back); the controller catches it and rebuilds the {@code UpdAcc}
 *       envelope with {@code CommSuccess="N"}, STILL at HTTP&nbsp;200.</li>
 * </ul>
 *
 * <h2>The defining subtlety &mdash; no fail-code field</h2>
 * <p>Unlike the create-account or update-customer envelopes, the {@code UpdAcc}
 * envelope declares a success flag ({@code CommSuccess}) but <strong>no</strong>
 * fail-code field (confirmed against the swagger request/response schemas and
 * {@code UPDACC.cpy}). A business rejection is therefore signalled SOLELY by
 * {@code CommSuccess="N"} &mdash; exactly the flag the preserved consumer tests
 * via {@code UpdAcc.CommSuccess.equals("N")}. Consequently the failure test
 * asserts only {@code CommSuccess="N"} (plus the single-key envelope shape that
 * proves the body is the rebuilt {@code UpdAcc} envelope, not a generic advice
 * error body) and deliberately does NOT reference any fail-code field, because
 * none exists in this contract.</p>
 *
 * <p>All monetary values are modelled as scale-2 {@link BigDecimal} and asserted
 * by round-tripping the response through the contract DTO and reading its
 * {@code BigDecimal} fields (a {@code BigDecimal}-typed field preserves the wire
 * scale on deserialisation); no floating-point type appears anywhere in this
 * test, honouring the binding money rule (ADR-005).</p>
 *
 * @see UpdateAccountController
 * @see AccountService#updateAccount(com.ibm.cics.cip.bank.core.dto.updateaccount.UpdateAccountForm)
 * @see UpdateAccountJson
 * @see UpdaccJson
 * @see BusinessRuleException
 */
@WebMvcTest(UpdateAccountController.class)
@DisplayName("UpdateAccountController contract IT — PUT /updacc/update reproduces the frozen UpdAcc envelope (F-019)")
class UpdateAccountControllerIT
{

	/** Frozen endpoint path: ROOT context, no context-path prefix. */
	private static final String ENDPOINT = "/updacc/update";

	/** Top-level wire envelope key shared by the request and the response. */
	private static final String ENVELOPE_KEY = "UpdAcc";

	/** Sample owning customer number used in request bodies (zero-padded width 10). */
	private static final String SAMPLE_CUSTNO = "0000000001";

	/** Sample sort code (zero-padded width 6); the bank's single sort code. */
	private static final String SAMPLE_SORTCODE = "987654";

	/** Sample account number (JSON integer per the frozen {@code updacc} schema). */
	private static final int SAMPLE_ACCNO = 12345678;

	/** Sample account type echoed on the success envelope (one of the five valid types). */
	private static final String SAMPLE_ACC_TYPE = "CURRENT";

	/** Sample overdraft limit (whole pounds, not money). */
	private static final int SAMPLE_OVERDRAFT = 500;

	/** Sample {@code DDMMYYYY} date-opened integer (25/12/2023). */
	private static final int SAMPLE_OPENED = 25122023;

	/** Sample {@code DDMMYYYY} last-statement-date integer (25/12/2023). */
	private static final int SAMPLE_LAST_STMT = 25122023;

	/** Sample {@code DDMMYYYY} next-statement-date integer (25/01/2024). */
	private static final int SAMPLE_NEXT_STMT = 25012024;

	/** Sample interest rate, scale 2 ({@code RoundingMode.HALF_UP}). */
	private static final BigDecimal SAMPLE_RATE = new BigDecimal("1.50");

	/** Sample available balance, scale 2 (echoed unchanged by {@code UPDACC}). */
	private static final BigDecimal SAMPLE_AVAIL_BAL = new BigDecimal("1000.00");

	/** Sample actual balance, scale 2 (echoed unchanged, independent of available). */
	private static final BigDecimal SAMPLE_ACTUAL_BAL = new BigDecimal("1234.56");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@MockitoBean
	private AccountService accountService;

	/**
	 * Success path: a contract-valid {@code UpdAcc} request is delegated to the
	 * service, and the populated success envelope round-trips at HTTP&nbsp;200
	 * with the single {@code UpdAcc} key, {@code CommSuccess="Y"}, the echoed
	 * account type, and scale-2 {@code BigDecimal} interest rate / balances. The
	 * controller adapts the {@code UpdAcc} wrapper into an
	 * {@code UpdateAccountForm}, so the stub matches with Mockito {@code any()}.
	 *
	 * @throws Exception if the MockMvc exchange or JSON (de)serialisation fails
	 */
	@Test
	@DisplayName("PUT /updacc/update — success returns the UpdAcc envelope (200, CommSuccess=Y, echoed type, scale-2 money)")
	void putUpdate_success_returnsUpdAccEnvelope() throws Exception
	{
		// Stub the (mocked) service: any adapted form yields the success envelope.
		when(accountService.updateAccount(any())).thenReturn(successEnvelope());

		MvcResult result = mockMvc
				.perform(put(ENDPOINT)
						.contentType(MediaType.APPLICATION_JSON)
						.accept(MediaType.APPLICATION_JSON)
						.content(requestBody()))
				.andExpect(status().isOk())
				.andExpect(content()
						.contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$." + ENVELOPE_KEY).exists())
				.andExpect(jsonPath("$.UpdAcc.CommSuccess").value("Y"))
				.andExpect(jsonPath("$.UpdAcc.CommAccType").value(SAMPLE_ACC_TYPE))
				.andReturn();

		// The controller must delegate exactly once to the business service.
		verify(accountService).updateAccount(any());

		// Money fidelity (never double): round-trip the response through the
		// contract DTO. A BigDecimal-typed field preserves the wire scale on
		// deserialisation, so this proves the money fields serialised at scale 2.
		UpdateAccountJson response = objectMapper.readValue(
				result.getResponse().getContentAsString(),
				UpdateAccountJson.class);
		UpdaccJson out = response.getUpdAcc();
		assertNotNull(out, "Response must carry a nested UpdAcc payload");
		assertScale2(out.getCommInterestRate(), SAMPLE_RATE, "CommIntRate");
		assertScale2(out.getCommAvailableBalance(), SAMPLE_AVAIL_BAL,
				"CommAvailBal");
		assertScale2(out.getCommActualBalance(), SAMPLE_ACTUAL_BAL,
				"CommActualBal");
	}

	/**
	 * Failure path (the highest-value, defining case): when the service throws
	 * {@link BusinessRuleException}{@code ("1")} (the {@code UPDACC} not-found /
	 * invalid-type channel), the controller catches it &mdash; the throw is what
	 * unwinds the service's {@code @Transactional} unit of work &mdash; and
	 * rebuilds the frozen {@code UpdAcc} envelope with {@code CommSuccess="N"},
	 * STILL at HTTP&nbsp;200.
	 *
	 * <p>The body is the REBUILT {@code UpdAcc} envelope, NOT a generic
	 * {@code GlobalExceptionHandler} advice error body; the single-top-level-key
	 * guard proves that. Because the {@code UpdAcc} envelope has NO fail-code
	 * field, the failure surfaces ONLY through {@code CommSuccess="N"}, so this
	 * test deliberately asserts nothing about a fail code.</p>
	 *
	 * @throws Exception if the MockMvc exchange or JSON parsing fails
	 */
	@Test
	@DisplayName("PUT /updacc/update — business rejection returns 200 with CommSuccess=N (no fail-code field)")
	void putUpdate_notFound_returns200WithCommSuccessN() throws Exception
	{
		when(accountService.updateAccount(any()))
				.thenThrow(new BusinessRuleException("1"));

		MvcResult result = mockMvc
				.perform(put(ENDPOINT)
						.contentType(MediaType.APPLICATION_JSON)
						.accept(MediaType.APPLICATION_JSON)
						.content(requestBody()))
				.andExpect(status().isOk())
				.andExpect(content()
						.contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$." + ENVELOPE_KEY).exists())
				.andExpect(jsonPath("$.UpdAcc.CommSuccess").value("N"))
				.andReturn();

		// The controller still delegated once; the rejection came from the service.
		verify(accountService).updateAccount(any());

		// Envelope-shape guard on the failure path: exactly one top-level key, and
		// it is UpdAcc. This proves the body is the rebuilt UpdAcc envelope, not a
		// generic advice error body (which would have a different top-level shape,
		// e.g. timestamp/status/message). This envelope has no fail-code field, so
		// CommSuccess="N" is the sole failure signal and nothing else is asserted.
		JsonNode root = objectMapper
				.readTree(result.getResponse().getContentAsString());
		assertEquals(1, root.size(),
				"Failure response must carry exactly one top-level key; body="
						+ root);
		assertTrue(root.has(ENVELOPE_KEY),
				"The single top-level key must be UpdAcc; body=" + root);
	}

	/**
	 * Envelope-shape guard: a successful response tree carries EXACTLY one
	 * top-level field, and that field is {@code UpdAcc}. This protects the
	 * preserved consumer from any accidental sibling top-level field.
	 *
	 * @throws Exception if the MockMvc exchange or JSON parsing fails
	 */
	@Test
	@DisplayName("PUT /updacc/update — response has exactly one top-level key: UpdAcc")
	void putUpdate_singleTopLevelKeyIsUpdAcc() throws Exception
	{
		when(accountService.updateAccount(any())).thenReturn(successEnvelope());

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
				"The single top-level key must be UpdAcc; body=" + root);
	}

	/**
	 * Cascaded-validation guard (F-021): an empty body {@code {}} leaves the
	 * {@code @NotNull} nested envelope unset (the wrapper does not eager-init it),
	 * so binding fails with HTTP&nbsp;400 and the service is NEVER invoked &mdash;
	 * eliminating the {@code NullPointerException}&rarr;500 path that a missing
	 * payload would otherwise hit in the controller.
	 *
	 * @throws Exception if the MockMvc exchange fails
	 */
	@Test
	@DisplayName("PUT /updacc/update — an empty body ({}) returns HTTP 400 and never reaches the service")
	void putUpdate_emptyBody_returns400() throws Exception
	{
		mockMvc.perform(put(ENDPOINT)
				.contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.APPLICATION_JSON)
				.content("{}"))
				.andExpect(status().isBadRequest());

		verify(accountService, never()).updateAccount(any());
	}

	/**
	 * Cascaded-validation guard (F-021): an explicit {@code {"UpdAcc":null}} body
	 * is rejected by the same {@code @NotNull} as HTTP&nbsp;400, and the service
	 * is NEVER invoked.
	 *
	 * @throws Exception if the MockMvc exchange fails
	 */
	@Test
	@DisplayName("PUT /updacc/update — a null nested UpdAcc ({\"UpdAcc\":null}) returns HTTP 400")
	void putUpdate_nullNestedUpdAcc_returns400() throws Exception
	{
		mockMvc.perform(put(ENDPOINT)
				.contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.APPLICATION_JSON)
				.content("{\"UpdAcc\":null}"))
				.andExpect(status().isBadRequest());

		verify(accountService, never()).updateAccount(any());
	}

	/**
	 * Builds the populated success envelope the mocked service returns. Mirrors
	 * the service's {@code buildUpdateResponse}: the updated account type,
	 * interest rate, and overdraft limit, with both (unchanged) balances echoed
	 * and {@code CommSuccess="Y"}. All monetary fields are scale-2
	 * {@link BigDecimal}; no floating-point type is used.
	 *
	 * @return a populated {@link UpdateAccountJson} success envelope
	 */
	private UpdateAccountJson successEnvelope()
	{
		UpdaccJson out = new UpdaccJson();
		out.setCommEye("ACCT");
		out.setCommCustno(SAMPLE_CUSTNO);
		out.setCommSortcode(SAMPLE_SORTCODE);
		out.setCommAccno(SAMPLE_ACCNO);
		out.setCommAccountType(SAMPLE_ACC_TYPE);
		out.setCommInterestRate(SAMPLE_RATE);
		out.setCommOpened(SAMPLE_OPENED);
		out.setCommOverdraft(SAMPLE_OVERDRAFT);
		out.setCommLastStatementDate(SAMPLE_LAST_STMT);
		out.setCommNextStatementDate(SAMPLE_NEXT_STMT);
		out.setCommAvailableBalance(SAMPLE_AVAIL_BAL);
		out.setCommActualBalance(SAMPLE_ACTUAL_BAL);
		out.setCommSuccess("Y");
		return new UpdateAccountJson(out);
	}

	/**
	 * Serialises a contract-valid {@code {"UpdAcc":{...}}} request body using the
	 * production {@link ObjectMapper}, guaranteeing the exact frozen wire names
	 * (via {@code @JsonProperty}) and values within the DTO's Bean-Validation
	 * bounds, so binding succeeds and the controller reaches the (mocked) service.
	 *
	 * @return the serialised JSON request body
	 * @throws Exception if serialisation fails
	 */
	private String requestBody() throws Exception
	{
		UpdaccJson in = new UpdaccJson();
		in.setCommEye("ACCT");
		in.setCommCustno(SAMPLE_CUSTNO);
		in.setCommSortcode(SAMPLE_SORTCODE);
		in.setCommAccno(SAMPLE_ACCNO);
		in.setCommAccountType(SAMPLE_ACC_TYPE);
		in.setCommInterestRate(SAMPLE_RATE);
		in.setCommOpened(SAMPLE_OPENED);
		in.setCommOverdraft(SAMPLE_OVERDRAFT);
		in.setCommLastStatementDate(SAMPLE_LAST_STMT);
		in.setCommNextStatementDate(SAMPLE_NEXT_STMT);
		in.setCommAvailableBalance(SAMPLE_AVAIL_BAL);
		in.setCommActualBalance(SAMPLE_ACTUAL_BAL);
		// CommSuccess intentionally left unset on the request; the controller
		// sets it on the response (Y on success, N on a caught rejection).
		return objectMapper.writeValueAsString(new UpdateAccountJson(in));
	}

	/**
	 * Asserts a monetary wire value is present, serialised at scale&nbsp;2 (never
	 * a floating-point type), and numerically equal to the expected value
	 * ({@code compareTo} confirms numeric equality independently of scale).
	 *
	 * @param actual   the {@link BigDecimal} read back from the response DTO
	 * @param expected the expected scale-2 value
	 * @param field    the wire field name, for diagnostic messages
	 */
	private void assertScale2(BigDecimal actual, BigDecimal expected,
			String field)
	{
		assertNotNull(actual, field + " must be present on the wire");
		assertEquals(2, actual.scale(),
				field + " must serialise at scale 2 (never double); was "
						+ actual);
		assertEquals(0, actual.compareTo(expected),
				field + " numeric value mismatch; expected " + expected
						+ " but was " + actual);
	}

}
