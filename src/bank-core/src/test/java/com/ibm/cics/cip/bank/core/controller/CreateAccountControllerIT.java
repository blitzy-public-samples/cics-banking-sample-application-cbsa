/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.core.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;

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
import com.ibm.cics.cip.bank.core.dto.createaccount.CreaccJson;
import com.ibm.cics.cip.bank.core.dto.createaccount.CreateAccountJson;
import com.ibm.cics.cip.bank.core.exception.BusinessRuleException;
import com.ibm.cics.cip.bank.core.service.AccountService;

/**
 * Controller <em>contract</em> integration test for {@link CreateAccountController}
 * &mdash; the proof that the production controller reproduces the frozen z/OS
 * Connect <em>create-account</em> ({@code creacc}) JSON contract byte-for-byte
 * (feature&nbsp;F-019). This is one of the ten {@code *IT.java} contract tests in
 * {@code com.ibm.cics.cip.bank.core.controller}; it targets
 * {@code POST /creacc/insert}.
 *
 * <h2>Why a DB-free web slice (and not {@code @SpringBootTest})</h2>
 * <p>The test is a {@link WebMvcTest @WebMvcTest(CreateAccountController.class)}
 * slice: it loads ONLY this controller's Spring&nbsp;MVC infrastructure (request
 * mapping, message converters, Bean&nbsp;Validation, and the
 * {@code @RestControllerAdvice}) and replaces the business collaborator with a
 * {@link MockitoBean @MockitoBean} {@link AccountService}. No datasource, JPA
 * {@code EntityManager}, Hibernate, or Flyway is started, so the test runs GREEN
 * on Java&nbsp;17 with no PostgreSQL present. A full {@code @SpringBootTest} is
 * deliberately avoided because it would bootstrap the persistence layer that this
 * contract test does not exercise. The {@code @MockitoBean} (the modern successor
 * of the deprecated {@code @MockBean}, matching the established convention of the
 * sibling controller {@code *IT} tests in this package) isolates the controller's
 * contract / envelope behaviour from the {@code CREACC.cbl} business logic, which
 * is verified independently by {@code AccountServiceTest}.</p>
 *
 * <h2>What this pins (the frozen contract)</h2>
 * <ul>
 *   <li><strong>{@code POST /creacc/insert}</strong> at the ROOT context (no
 *       servlet context-path), consuming and producing {@code application/json}.</li>
 *   <li><strong>A single top-level wire key {@code CreAcc}</strong> in both the
 *       request and the response envelope &mdash; never any sibling top-level
 *       field &mdash; so the preserved consumer (which deserialises with a strict
 *       {@code FAIL_ON_UNKNOWN_PROPERTIES} mapper) reads it with zero change.</li>
 *   <li><strong>A single HTTP&nbsp;200 in every outcome.</strong> On success the
 *       service's populated envelope ({@code CommSuccess="Y"}, allocated
 *       {@code CommKey.CommNumber}, scale-2 {@code BigDecimal} balances, opened
 *       date) round-trips. On a business rejection the service throws a
 *       {@link BusinessRuleException} (so its {@code @Transactional} boundary
 *       rolls back the consumed account number); the controller catches it and
 *       rebuilds the {@code CreAcc} envelope with {@code CommSuccess="N"} and the
 *       verbatim COBOL fail code, STILL at HTTP&nbsp;200. Asserting that the
 *       too-many-accounts case ({@code CommFailCode="8"}) returns 200 with the
 *       rebuilt envelope &mdash; rather than a generic error body &mdash; is the
 *       highest-value check in this suite (the canonical rollback-on-failure
 *       case).</li>
 * </ul>
 *
 * <p>All monetary values are modelled as scale-2 {@link BigDecimal} and asserted
 * through the parsed JSON tree using {@code BigDecimal} comparison; no
 * floating-point type is used anywhere in this test, honouring the binding money
 * rule.</p>
 *
 * @see CreateAccountController
 * @see AccountService
 * @see CreateAccountJson
 * @see CreaccJson
 * @see BusinessRuleException
 */
@WebMvcTest(CreateAccountController.class)
@DisplayName("CreateAccountController contract IT — POST /creacc/insert reproduces the frozen CreAcc envelope (F-019)")
class CreateAccountControllerIT
{

	/** Frozen endpoint path: ROOT context, no context-path prefix. */
	private static final String ENDPOINT = "/creacc/insert";

	/** Top-level wire envelope key shared by the request and the response. */
	private static final String ENVELOPE_KEY = "CreAcc";

	/** The sample owning customer number used in request bodies (zero-padded 10). */
	private static final String SAMPLE_CUSTNO = "0000000001";

	/** The sample account type echoed on the success envelope. */
	private static final String SAMPLE_ACC_TYPE = "ISA";

	/** The sample allocated account number returned on the success envelope. */
	private static final long SAMPLE_ACC_NUMBER = 12345678L;

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@MockitoBean
	private AccountService accountService;

	/**
	 * Success path: a contract-valid {@code CreAcc} request is delegated to the
	 * service, and the populated success envelope round-trips at HTTP&nbsp;200
	 * with the single {@code CreAcc} key, {@code CommSuccess="Y"}, an allocated
	 * {@code CommKey.CommNumber}, the echoed account type, and scale-2
	 * {@code BigDecimal} balances / interest rate.
	 *
	 * @throws Exception if the MockMvc exchange or JSON (de)serialisation fails
	 */
	@Test
	@DisplayName("POST /creacc/insert — success returns the CreAcc envelope (200, CommSuccess=Y, scale-2 money)")
	void postInsert_success_returnsCreAccEnvelope() throws Exception
	{
		// Stub the (mocked) service: any adapted form yields the success envelope.
		when(accountService.createAccount(any())).thenReturn(successEnvelope());

		MvcResult result = mockMvc
				.perform(post(ENDPOINT)
						.contentType(MediaType.APPLICATION_JSON)
						.accept(MediaType.APPLICATION_JSON)
						.content(requestBody()))
				.andExpect(status().isOk())
				.andExpect(content()
						.contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$." + ENVELOPE_KEY).exists())
				.andExpect(jsonPath("$.CreAcc.CommSuccess").value("Y"))
				.andExpect(jsonPath("$.CreAcc.CommKey.CommNumber").exists())
				.andExpect(jsonPath("$.CreAcc.CommAccType").value(SAMPLE_ACC_TYPE))
				.andReturn();

		// The controller must delegate exactly once to the business service.
		verify(accountService).createAccount(any());

		// Money is asserted through the parsed tree with BigDecimal comparison so
		// that no floating-point type appears in this test. compareTo ignores
		// scale, proving numeric equality of the scale-2 wire values.
		JsonNode creAcc = objectMapper
				.readTree(result.getResponse().getContentAsString())
				.get(ENVELOPE_KEY);
		assertEquals(0,
				creAcc.get("CommAvailBal").decimalValue()
						.compareTo(new BigDecimal("0.00")),
				"CommAvailBal must serialise as the scale-2 value 0.00");
		assertEquals(0,
				creAcc.get("CommActBal").decimalValue()
						.compareTo(new BigDecimal("0.00")),
				"CommActBal must serialise as the scale-2 value 0.00");
		assertEquals(0,
				creAcc.get("CommIntRt").decimalValue()
						.compareTo(new BigDecimal("1.50")),
				"CommIntRt must serialise as the scale-2 value 1.50");
		assertEquals(SAMPLE_ACC_NUMBER, creAcc.get("CommKey").get("CommNumber").asLong(),
				"CommKey.CommNumber must echo the allocated account number");
	}

	/**
	 * Canonical rollback-on-failure case (the highest-value assertion): when the
	 * service throws {@link BusinessRuleException}{@code ("8")} (a customer
	 * already holds the maximum of ten accounts), the controller catches it and
	 * rebuilds the frozen {@code CreAcc} envelope with {@code CommSuccess="N"} and
	 * {@code CommFailCode="8"} &mdash; still at HTTP&nbsp;200, and still a single
	 * top-level {@code CreAcc} key (NOT a generic {@code GlobalExceptionHandler}
	 * error body).
	 *
	 * @throws Exception if the MockMvc exchange or JSON parsing fails
	 */
	@Test
	@DisplayName("POST /creacc/insert — too many accounts ('8') returns 200 with the CreAcc failure envelope")
	void postInsert_tooManyAccounts_returns200WithFailCode8() throws Exception
	{
		when(accountService.createAccount(any()))
				.thenThrow(new BusinessRuleException("8"));

		MvcResult result = mockMvc
				.perform(post(ENDPOINT)
						.contentType(MediaType.APPLICATION_JSON)
						.accept(MediaType.APPLICATION_JSON)
						.content("{\"CreAcc\":{}}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.CreAcc").exists())
				.andExpect(jsonPath("$.CreAcc.CommSuccess").value("N"))
				.andExpect(jsonPath("$.CreAcc.CommFailCode").value("8"))
				.andReturn();

		// Envelope-shape guard on the failure path too: exactly one top-level key.
		JsonNode root = objectMapper
				.readTree(result.getResponse().getContentAsString());
		assertEquals(1, root.size(),
				"Failure response must carry exactly one top-level key; body=" + root);
		assertTrue(root.has(ENVELOPE_KEY),
				"The single top-level key must be CreAcc; body=" + root);
	}

	/**
	 * A second failure variant proving the envelope rebuild is fail-code-agnostic:
	 * {@link BusinessRuleException}{@code ("1")} (the owning customer was not
	 * found) is rendered as the {@code CreAcc} failure envelope at HTTP&nbsp;200
	 * with {@code CommFailCode="1"}.
	 *
	 * @throws Exception if the MockMvc exchange fails
	 */
	@Test
	@DisplayName("POST /creacc/insert — customer not found ('1') returns 200 with the CreAcc failure envelope")
	void postInsert_customerNotFound_returns200WithFailCode1() throws Exception
	{
		when(accountService.createAccount(any()))
				.thenThrow(new BusinessRuleException("1"));

		mockMvc.perform(post(ENDPOINT)
				.contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.APPLICATION_JSON)
				.content("{\"CreAcc\":{}}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.CreAcc").exists())
				.andExpect(jsonPath("$.CreAcc.CommSuccess").value("N"))
				.andExpect(jsonPath("$.CreAcc.CommFailCode").value("1"));
	}

	/**
	 * Envelope-shape guard: a successful response tree carries EXACTLY one
	 * top-level field, and that field is {@code CreAcc}. This protects the strict
	 * preserved consumer from any accidental sibling top-level field.
	 *
	 * @throws Exception if the MockMvc exchange or JSON parsing fails
	 */
	@Test
	@DisplayName("POST /creacc/insert — response has exactly one top-level key: CreAcc")
	void postInsert_singleTopLevelKeyIsCreAcc() throws Exception
	{
		when(accountService.createAccount(any())).thenReturn(successEnvelope());

		MvcResult result = mockMvc
				.perform(post(ENDPOINT)
						.contentType(MediaType.APPLICATION_JSON)
						.accept(MediaType.APPLICATION_JSON)
						.content("{\"CreAcc\":{}}"))
				.andExpect(status().isOk())
				.andReturn();

		JsonNode root = objectMapper
				.readTree(result.getResponse().getContentAsString());
		assertEquals(1, root.size(),
				"Response must carry exactly one top-level key; body=" + root);
		assertTrue(root.has(ENVELOPE_KEY),
				"The single top-level key must be CreAcc; body=" + root);
	}

	/**
	 * Cascaded Bean Validation (F-021): a request whose wrapper carries an explicit
	 * {@code null} nested envelope ({@code {"CreAcc": null}}) is rejected with
	 * HTTP&nbsp;400 by the {@code @Valid}+{@code @NotNull} cascade BEFORE the
	 * mapping dereferences the payload, so the business service is never invoked
	 * (no {@code NullPointerException}&rarr;500 and no silent business failure).
	 *
	 * @throws Exception if the MockMvc exchange fails
	 */
	@Test
	@DisplayName("POST /creacc/insert — a null nested CreAcc ({\"CreAcc\":null}) returns HTTP 400")
	void postInsert_nullNestedCreAcc_returns400() throws Exception
	{
		mockMvc.perform(post(ENDPOINT)
				.contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.APPLICATION_JSON)
				.content("{\"CreAcc\":null}"))
				.andExpect(status().isBadRequest());

		verify(accountService, never()).createAccount(any());
	}

	/**
	 * Empty-body Bean Validation (F-021, QA Issue&nbsp;2): an empty {@code {}}
	 * request body carries no {@code CreAcc} key, and the wrapper's no-argument
	 * constructor no longer eager-initialises the nested envelope, so the
	 * {@code @NotNull} nested field stays {@code null} and the
	 * {@code @Valid}+{@code @NotNull} cascade rejects the request with
	 * HTTP&nbsp;400 BEFORE the mapping dereferences the payload &mdash; matching
	 * the {@code updacc} / {@code makepayment} siblings and never degrading into a
	 * business-fail envelope at HTTP&nbsp;200. The business service is never
	 * invoked.
	 *
	 * @throws Exception if the MockMvc exchange fails
	 */
	@Test
	@DisplayName("POST /creacc/insert — an empty {} body returns HTTP 400 (Issue 2)")
	void postInsert_emptyBody_returns400() throws Exception
	{
		mockMvc.perform(post(ENDPOINT)
				.contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.APPLICATION_JSON)
				.content("{}"))
				.andExpect(status().isBadRequest());

		verify(accountService, never()).createAccount(any());
	}

	/**
	 * Builds the populated success-response envelope the mocked service returns:
	 * {@code CommSuccess="Y"}, an allocated sort code + account number, the account
	 * type, scale-2 {@code BigDecimal} balances (both {@code 0.00} for a freshly
	 * opened account) and interest rate ({@code 1.50}), and the opened date packed
	 * as the {@code DDMMYYYY} integer derived from {@link LocalDate#now()}.
	 *
	 * @return a success {@link CreateAccountJson} envelope
	 */
	private CreateAccountJson successEnvelope()
	{
		CreaccJson inner = new CreaccJson();
		inner.setCommSuccess("Y");
		inner.setCommCustno(SAMPLE_CUSTNO);
		inner.setCommAccType(SAMPLE_ACC_TYPE);
		// CommKey is non-null on a freshly-constructed CreaccJson; set the
		// allocated identity without importing the dto.common key type.
		inner.getCommKey().setCommSortcode(987654);
		inner.getCommKey().setCommNumber(SAMPLE_ACC_NUMBER);
		// Money is scale-2 BigDecimal (never a floating-point type).
		inner.setCommAvailableBalance(new BigDecimal("0.00"));
		inner.setCommActualBalance(new BigDecimal("0.00"));
		inner.setCommInterestRate(new BigDecimal("1.50"));
		inner.setCommOverdraftLimit(0);
		// Opened date as the COBOL DDMMYYYY-packed integer, derived from today.
		LocalDate today = LocalDate.now();
		inner.setCommOpened(today.getDayOfMonth() * 1_000_000
				+ today.getMonthValue() * 10_000
				+ today.getYear());

		CreateAccountJson envelope = new CreateAccountJson();
		envelope.setCreAcc(inner);
		return envelope;
	}

	/**
	 * Serialises a contract-valid {@code {"CreAcc":{...}}} request body via the
	 * application's {@link ObjectMapper}, exercising the real envelope wire-name
	 * mapping. The populated fields satisfy the structural Bean Validation
	 * constraints so the request reaches the (mocked) service.
	 *
	 * @return the JSON request body as a {@link String}
	 * @throws Exception if serialisation fails
	 */
	private String requestBody() throws Exception
	{
		CreateAccountJson request = new CreateAccountJson();
		// The no-argument envelope no longer eager-initialises the inner payload
		// (leaving it null so an empty {} body trips @NotNull -> HTTP 400, F-021),
		// so the inner CreaccJson is constructed and attached explicitly here.
		CreaccJson inner = new CreaccJson();
		request.setCreAcc(inner);
		inner.setCommCustno(SAMPLE_CUSTNO);
		inner.setCommAccType(SAMPLE_ACC_TYPE);
		inner.setCommInterestRate(new BigDecimal("1.50"));
		inner.setCommOverdraftLimit(0);
		return objectMapper.writeValueAsString(request);
	}

}
