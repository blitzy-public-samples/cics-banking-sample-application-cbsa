/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.core.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

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
import com.ibm.cics.cip.bank.core.dto.listaccounts.AccountDetails;
import com.ibm.cics.cip.bank.core.dto.listaccounts.InqAccczJson;
import com.ibm.cics.cip.bank.core.dto.listaccounts.ListAccountsJson;
import com.ibm.cics.cip.bank.core.exception.BusinessRuleException;
import com.ibm.cics.cip.bank.core.service.AccountService;

/**
 * Controller <em>contract</em> integration test for
 * {@link InquireCustomerAccountsController} &mdash; the proof that the production
 * controller reproduces the frozen z/OS&nbsp;Connect <em>list-customer-accounts</em>
 * ({@code inqacccz}) JSON contract byte-for-byte (feature&nbsp;<strong>F-019</strong>),
 * so the preserved React/Carbon UI and the Customer-Services interface module
 * re-point by base&nbsp;URL only and are never rewritten. This is one of the ten
 * {@code *IT.java} contract tests in {@code com.ibm.cics.cip.bank.core.controller};
 * it targets {@code GET /inqacccz/list/{custno}} (operationId {@code getCScustacc},
 * service {@code CScustacc}, mapping {@code INQACCCU.cbl}).
 *
 * <h2>What this pins (verified against the frozen swagger / consumer)</h2>
 * <ul>
 *   <li><strong>{@code GET /inqacccz/list/{custno}}</strong> at the ROOT context
 *       &mdash; no servlet context-path prefix (basePath {@code /inqacccz},
 *       relativePath {@code /list/{custno}}). The customer number travels in the
 *       <em>path variable</em>; the authoritative consumer
 *       ({@code WebController.@PostMapping("/listacc")}) issues
 *       {@code client.get().retrieve()} with <strong>no request body</strong>, so
 *       every exchange here uses {@code get(...)} only.</li>
 *   <li><strong>A single top-level wire key {@code InqAccZ}</strong> on the
 *       response (the frozen {@code getCScustacc_response_200} schema is an object
 *       whose only property is {@code InqAccZ}), so the preserved consumer reads
 *       it with zero change.</li>
 *   <li><strong>A single HTTP&nbsp;200 in every outcome.</strong> On a found
 *       customer the populated envelope round-trips ({@code CommSuccess="Y"},
 *       {@code CustomerFound="Y"}, and the {@code AccountDetails} array of up to
 *       twenty rows). On a not-found / business rejection the controller's
 *       safeguard {@code catch (BusinessRuleException)} rebuilds the
 *       {@code InqAccZ} envelope with {@code CommSuccess="N"},
 *       {@code CustomerFound="N"} and {@code CommFailCode} = the carried fail code
 *       &mdash; STILL at HTTP&nbsp;200, and STILL a single top-level
 *       {@code InqAccZ} key (NOT the generic {@code GlobalExceptionHandler}
 *       {@code {success,failCode,message}} body).</li>
 *   <li><strong>This envelope HAS both a {@code CustomerFound} boolean-style flag
 *       and a {@code CommFailCode} field.</strong> Unlike the sibling
 *       {@code InqAcc} contract (success flag only), the not-found path here
 *       asserts both {@code CommSuccess="N"} and {@code CommFailCode="1"}.</li>
 *   <li><strong>A nested {@code AccountDetails} ARRAY.</strong> Each element is
 *       probed for its wire fields (e.g. {@code CommAccno}, {@code CommCustno})
 *       and a scale-2 {@code BigDecimal} money value ({@code CommAvailBal}); the
 *       account dates are the COBOL {@code DDMMYYYY} integers
 *       ({@code CommOpened}), matching the frozen schema (which types the dates
 *       and account number as JSON {@code integer}). The twenty-account cap is
 *       owned by {@code AccountService} ({@code INQACCCU}, F-010), NOT the
 *       controller, so truncation is deliberately NOT exercised here &mdash; only
 *       the wire shape.</li>
 * </ul>
 *
 * <h2>Test strategy &mdash; the DB-free web slice</h2>
 * <p>This is a {@link WebMvcTest @WebMvcTest(InquireCustomerAccountsController.class)}
 * slice: it bootstraps ONLY this controller's Spring&nbsp;MVC infrastructure
 * (request mapping, message converters, Bean&nbsp;Validation, the auto-configured
 * Jackson {@link ObjectMapper}, {@link MockMvc}, and the
 * {@code @RestControllerAdvice}) and replaces the business collaborator with a
 * Mockito mock ({@link MockitoBean @MockitoBean} {@link AccountService}). It
 * starts <strong>no datasource, no JPA/Hibernate, and no Flyway</strong>, so it
 * is GREEN on Java&nbsp;17 with no PostgreSQL running. A full
 * {@code @SpringBootTest} is deliberately avoided because it would bootstrap the
 * persistence layer this contract test does not exercise; the
 * {@code INQACCCU.cbl} business logic (incl. the twenty-account cap) is verified
 * independently by {@code AccountServiceTest}.</p>
 *
 * <p>All monetary values are modelled as scale-2 {@link BigDecimal} and asserted
 * on the raw response body; no {@code double}/{@code float} appears anywhere in
 * this test, honouring the binding money rule, and the available and actual
 * balances are kept distinct to prove they are never collapsed (&sect;0.6).</p>
 *
 * @see InquireCustomerAccountsController
 * @see AccountService#listAccountsByCustomer(long)
 * @see ListAccountsJson
 * @see InqAccczJson
 * @see AccountDetails
 * @see BusinessRuleException
 */
@WebMvcTest(InquireCustomerAccountsController.class)
@DisplayName("InquireCustomerAccountsController contract IT — GET /inqacccz/list/{custno} reproduces the frozen InqAccZ envelope with AccountDetails array (F-019)")
class InquireCustomerAccountsControllerIT
{

	/** Frozen endpoint path template: ROOT context, no context-path prefix. */
	private static final String ENDPOINT_TEMPLATE = "/inqacccz/list/{custno}";

	/** Top-level wire envelope key on the response ({@code InqAccZ}). */
	private static final String ENVELOPE_KEY = "InqAccZ";

	/**
	 * A within-width (ten-digit, {@code PIC 9(10)}) customer number used on the
	 * happy path. Sent left-zero-padded exactly as the preserved consumer would.
	 */
	private static final String SAMPLE_CUSTNO = "0000000123";

	/** Numeric value of {@link #SAMPLE_CUSTNO}, echoed back as a JSON integer. */
	private static final long SAMPLE_CUSTNO_VALUE = 123L;

	/** A different within-width customer number used on the not-found path. */
	private static final String MISSING_CUSTNO = "0000000456";

	/** Success flag value (COBOL {@code COMM-SUCCESS = 'Y'}). */
	private static final String FLAG_SUCCESS = "Y";

	/** Failure flag value (COBOL {@code COMM-SUCCESS = 'N'} / {@code CUSTOMER-FOUND = 'N'}). */
	private static final String FLAG_FAILURE = "N";

	/** Fail-code value denoting "no failure" ({@code "0"}); the {@code INQACCCU} initial value. */
	private static final String FAIL_NONE = "0";

	/** Fail code {@code "1"} = customer not found ({@code INQACCCU} L215-217). */
	private static final String FAIL_CUSTOMER_NOT_FOUND = "1";

	/** Number of accounts placed on the success envelope ({@code <=} the twenty cap). */
	private static final int SAMPLE_ACCOUNT_COUNT = 2;

	/** Sample eye-catcher carried (for wire parity) on each account row. */
	private static final String SAMPLE_EYE = "ACCT";

	/** First account number on the success envelope (8-digit, wire integer). */
	private static final int FIRST_ACCNO = 12345678;

	/** Second account number on the success envelope (8-digit, wire integer). */
	private static final int SECOND_ACCNO = 12345679;

	/** Sample account type echoed on the first account row. */
	private static final String SAMPLE_ACC_TYPE = "CURRENT";

	/** Sample overdraft limit (whole pounds, an {@link Integer} &mdash; NOT money). */
	private static final int SAMPLE_OVERDRAFT = 500;

	/**
	 * Available (cleared) balance on the first account (scale-2). Deliberately
	 * <em>distinct</em> from {@link #ACTUAL_BALANCE} so the test proves the two
	 * balances round-trip as independent fields and are never collapsed (&sect;0.6).
	 */
	private static final BigDecimal AVAILABLE_BALANCE = new BigDecimal("1234.56");

	/** Actual (pending) balance on the first account (scale-2, distinct from available). */
	private static final BigDecimal ACTUAL_BALANCE = new BigDecimal("1100.00");

	/** Interest rate on the first account (scale-2 {@code BigDecimal}, never a primitive). */
	private static final BigDecimal INTEREST_RATE = new BigDecimal("1.50");

	/** Fixed opened date used to derive the {@code DDMMYYYY}-packed wire integer. */
	private static final LocalDate SAMPLE_OPENED_DATE = LocalDate.of(2023, 6, 15);

	/**
	 * Account opened date packed as the COBOL {@code DDMMYYYY} integer
	 * ({@code 15/06/2023} &rarr; {@code 15062023}), the exact wire form of
	 * {@code CommOpened} (the frozen schema types the account dates as JSON
	 * {@code integer}, not as a {@code DD/MM/YYYY} string).
	 */
	private static final int SAMPLE_OPENED_DDMMYYYY = SAMPLE_OPENED_DATE
			.getDayOfMonth() * 1_000_000
			+ SAMPLE_OPENED_DATE.getMonthValue() * 10_000
			+ SAMPLE_OPENED_DATE.getYear();

	/** Auto-configured MockMvc that drives the {@code InquireCustomerAccountsController} slice. */
	@Autowired
	private MockMvc mockMvc;

	/** Auto-configured Jackson mapper (reads the response tree for the shape guards). */
	@Autowired
	private ObjectMapper objectMapper;

	/**
	 * The single business collaborator, replaced by a Mockito mock. Stubbed per
	 * test to return a populated {@link ListAccountsJson} envelope or to throw a
	 * {@link BusinessRuleException}.
	 */
	@MockitoBean
	private AccountService accountService;

	/**
	 * Success path: a valid customer number is delegated to the service exactly
	 * once and the populated {@code InqAccZ} envelope round-trips at
	 * HTTP&nbsp;200 &mdash; single {@code InqAccZ} key, {@code CommSuccess="Y"},
	 * {@code CustomerFound="Y"}, the echoed {@code CustomerNumber}, and the nested
	 * {@code AccountDetails} JSON ARRAY whose elements carry the expected wire
	 * fields (a present {@code CommAccno}, the zero-padded {@code CommCustno}) and
	 * scale-2 {@code BigDecimal} balances (two distinct, independent values). The
	 * opened date serialises as the {@code DDMMYYYY} integer, exactly as the
	 * frozen contract types {@code CommOpened}. The request carries NO body,
	 * matching the authoritative {@code WebController} consumer's
	 * {@code client.get().retrieve()}.
	 *
	 * @throws Exception if the MockMvc exchange or JSON (de)serialisation fails
	 */
	@Test
	@DisplayName("GET /inqacccz/list/{custno} — success returns the InqAccZ envelope with the AccountDetails array (200, scale-2 balances, DDMMYYYY opened date)")
	void getList_success_returnsInqAccZWithAccountArray() throws Exception
	{
		// Stub the (mocked) service: any customer number yields the success
		// envelope. anyLong() matches because the controller parses the path
		// variable to a long (BankFormat.parseCustomerNumber) before delegating.
		when(accountService.listAccountsByCustomer(anyLong()))
				.thenReturn(successEnvelope());

		MvcResult result = mockMvc
				.perform(get(ENDPOINT_TEMPLATE, SAMPLE_CUSTNO)
						.accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(content()
						.contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$." + ENVELOPE_KEY).exists())
				.andExpect(jsonPath("$.InqAccZ.CustomerNumber").exists())
				.andExpect(jsonPath("$.InqAccZ.CommSuccess").value(FLAG_SUCCESS))
				.andExpect(jsonPath("$.InqAccZ.CustomerFound").value(FLAG_SUCCESS))
				// AccountDetails is a JSON array with exactly the populated rows.
				.andExpect(jsonPath("$.InqAccZ.AccountDetails").isArray())
				.andExpect(jsonPath("$.InqAccZ.AccountDetails",
						hasSize(SAMPLE_ACCOUNT_COUNT)))
				// Probe element [0] field shape (per-account wire fields).
				.andExpect(jsonPath("$.InqAccZ.AccountDetails[0].CommAccno")
						.exists())
				.andExpect(jsonPath("$.InqAccZ.AccountDetails[0].CommCustno")
						.value(SAMPLE_CUSTNO))
				.andExpect(jsonPath("$.InqAccZ.AccountDetails[0].CommAccType")
						.value(SAMPLE_ACC_TYPE))
				.andReturn();

		// The controller must delegate exactly once to the business service.
		verify(accountService).listAccountsByCustomer(anyLong());

		// Scale-2 money fidelity on the raw wire (no floating-point type anywhere):
		// each balance serialises with exactly two fraction digits, and the two
		// balances remain the two distinct values supplied (never collapsed, §0.6).
		// The opened date serialises as the DDMMYYYY integer, exactly as the
		// frozen contract types CommOpened. The raw body is asserted because the
		// JSON-path number reader would strip a trailing-zero scale.
		String body = result.getResponse().getContentAsString();
		assertTrue(
				body.contains("\"CommAvailBal\":" + AVAILABLE_BALANCE.toPlainString()),
				"CommAvailBal must serialise at scale 2 (1234.56); body=" + body);
		assertTrue(
				body.contains("\"CommActualBal\":" + ACTUAL_BALANCE.toPlainString()),
				"CommActualBal must serialise at scale 2 (1100.00) and stay independent of the available balance; body="
						+ body);
		assertTrue(
				body.contains("\"CommOpened\":" + SAMPLE_OPENED_DDMMYYYY),
				"CommOpened must serialise as the DDMMYYYY integer 15062023; body="
						+ body);
	}

	/**
	 * Not-found / business-failure path: the stubbed service throws
	 * {@link BusinessRuleException}{@code ("1")} (customer not found); the
	 * controller's safeguard {@code catch} rebuilds the frozen {@code InqAccZ}
	 * envelope with {@code CommSuccess="N"} and {@code CommFailCode="1"} and
	 * returns HTTP&nbsp;<strong>200</strong> (never 4xx/5xx). The body MUST be the
	 * rebuilt single-key {@code InqAccZ} envelope, NOT the generic
	 * {@code GlobalExceptionHandler} advice body &mdash; asserting the single
	 * top-level {@code InqAccZ} key proves exactly that.
	 *
	 * <p>This envelope carries BOTH a {@code CommFailCode} field and a
	 * {@code CustomerFound} flag, so this test asserts the fail-code value here
	 * (in contrast with the {@code InqAcc} contract, which has no fail-code
	 * field).</p>
	 *
	 * @throws Exception if the MockMvc exchange or JSON parsing fails
	 */
	@Test
	@DisplayName("GET /inqacccz/list/{custno} — customer not found returns 200 with CommSuccess=N and CommFailCode=1 (rebuilt envelope)")
	void getList_customerNotFound_returns200WithFailCode1() throws Exception
	{
		// Drive the controller's safeguard catch with the INQACCCU fail code '1';
		// the controller honours the frozen contract by rebuilding the InqAccZ
		// envelope at HTTP 200 rather than surfacing a 4xx/5xx.
		when(accountService.listAccountsByCustomer(anyLong()))
				.thenThrow(new BusinessRuleException(FAIL_CUSTOMER_NOT_FOUND));

		MvcResult result = mockMvc
				.perform(get(ENDPOINT_TEMPLATE, MISSING_CUSTNO)
						.accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(content()
						.contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$." + ENVELOPE_KEY).exists())
				.andExpect(jsonPath("$.InqAccZ.CommSuccess").value(FLAG_FAILURE))
				.andExpect(jsonPath("$.InqAccZ.CommFailCode")
						.value(FAIL_CUSTOMER_NOT_FOUND))
				.andReturn();

		// Envelope rebuilt, NOT the generic {success,failCode,message} advice body:
		// exactly one top-level key, and it is InqAccZ.
		JsonNode root = objectMapper
				.readTree(result.getResponse().getContentAsString());
		assertEquals(1, root.size(),
				"Failure response must carry exactly one top-level key (the rebuilt InqAccZ envelope, not the generic advice body); body="
						+ root);
		assertTrue(root.has(ENVELOPE_KEY),
				"The single top-level key must be InqAccZ; body=" + root);
	}

	/**
	 * Envelope-shape guard: a successful response tree carries EXACTLY one
	 * top-level field, and that field is {@code InqAccZ}. This pins the single-key
	 * wrapper shape of the frozen {@code getCScustacc_response_200} schema and
	 * protects the preserved consumer from any accidental sibling top-level field.
	 *
	 * @throws Exception if the MockMvc exchange or JSON parsing fails
	 */
	@Test
	@DisplayName("GET /inqacccz/list/{custno} — response has exactly one top-level key: InqAccZ")
	void getList_singleTopLevelKeyIsInqAccZ() throws Exception
	{
		when(accountService.listAccountsByCustomer(anyLong()))
				.thenReturn(successEnvelope());

		MvcResult result = mockMvc
				.perform(get(ENDPOINT_TEMPLATE, SAMPLE_CUSTNO)
						.accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andReturn();

		JsonNode root = objectMapper
				.readTree(result.getResponse().getContentAsString());
		assertEquals(1, root.size(),
				"Response must carry exactly one top-level key; body=" + root);
		assertTrue(root.has(ENVELOPE_KEY),
				"The single top-level key must be InqAccZ; body=" + root);
	}

	/**
	 * Builds the populated success-response envelope the mocked service returns:
	 * {@code CommSuccess="Y"}, {@code CustomerFound="Y"}, {@code CommFailCode="0"},
	 * the echoed {@code CustomerNumber} (JSON integer), and a list of
	 * {@link #SAMPLE_ACCOUNT_COUNT} {@link AccountDetails} rows. The first row carries the values
	 * asserted by {@link #getList_success_returnsInqAccZWithAccountArray()}
	 * (scale-2 balances, the {@code DDMMYYYY} opened date, account type and a
	 * present account number).
	 *
	 * @return a success {@link ListAccountsJson} envelope carrying
	 *         {@link #SAMPLE_ACCOUNT_COUNT} account rows ({@code <=} the twenty
	 *         cap that the service, not this test, enforces)
	 */
	private ListAccountsJson successEnvelope()
	{
		// Two distinct rows (matching SAMPLE_ACCOUNT_COUNT); the immutable List
		// uses only the declared java.util.List import.
		List<AccountDetails> details = List.of(sampleAccount(0), sampleAccount(1));

		InqAccczJson inner = new InqAccczJson();
		// CustomerNumber is a JSON integer per the frozen inqacccz schema (F-019);
		// echo the numeric value (Long), not the zero-padded key.
		inner.setCustomerNumber(SAMPLE_CUSTNO_VALUE);
		inner.setAccountDetails(details);
		inner.setCustomerFound(FLAG_SUCCESS);
		inner.setCommSuccess(FLAG_SUCCESS);
		inner.setCommFailCode(FAIL_NONE);
		inner.setCommPcbPointer("");

		return new ListAccountsJson(inner);
	}

	/**
	 * Builds one {@link AccountDetails} row. Index {@code 0} carries the exact
	 * values the success test probes; subsequent rows vary only the account
	 * number so the list has distinct elements. Money fields are scale-2
	 * {@link BigDecimal} (never a floating-point type) and the dates are the COBOL
	 * {@code DDMMYYYY} integers, matching the frozen wire contract.
	 *
	 * @param index the zero-based row index
	 * @return a populated per-account detail element
	 */
	private AccountDetails sampleAccount(int index)
	{
		AccountDetails details = new AccountDetails();
		details.setCommEye(SAMPLE_EYE);
		// Owning customer number is the zero-padded width-10 string (the schema
		// types CommCustno as string), echoing SAMPLE_CUSTNO on every row.
		details.setCommCustno(SAMPLE_CUSTNO);
		details.setCommAccno(index == 0 ? FIRST_ACCNO : SECOND_ACCNO);
		details.setCommAccType(SAMPLE_ACC_TYPE);
		details.setCommInterestRate(INTEREST_RATE);
		details.setCommOpened(SAMPLE_OPENED_DDMMYYYY);
		details.setCommOverdraft(SAMPLE_OVERDRAFT);
		details.setCommLastStatementDate(SAMPLE_OPENED_DDMMYYYY);
		details.setCommNextStatementDate(SAMPLE_OPENED_DDMMYYYY);
		// Two independent, distinct balances (never collapsed, §0.6); scale 2.
		details.setCommAvailableBalance(AVAILABLE_BALANCE);
		details.setCommActualBalance(ACTUAL_BALANCE);
		return details;
	}

}
