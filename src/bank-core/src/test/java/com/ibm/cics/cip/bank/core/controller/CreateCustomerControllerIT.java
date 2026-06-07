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
import com.ibm.cics.cip.bank.core.dto.createcustomer.CreateCustomerJson;
import com.ibm.cics.cip.bank.core.dto.createcustomer.CrecustJson;
import com.ibm.cics.cip.bank.core.exception.BusinessRuleException;
import com.ibm.cics.cip.bank.core.service.CustomerService;

/**
 * Controller <em>contract</em> integration test for
 * {@link CreateCustomerController} &mdash; the proof that the production
 * controller reproduces the frozen z/OS Connect <em>create-customer</em>
 * ({@code crecust}) JSON contract byte-for-byte (feature&nbsp;F-019). This is one
 * of the ten {@code *IT.java} contract tests in
 * {@code com.ibm.cics.cip.bank.core.controller}; it targets
 * {@code POST /crecust/insert} (service {@code CScustcre}, operationId
 * {@code postCScustcre}).
 *
 * <h2>Why a DB-free web slice (and not {@code @SpringBootTest})</h2>
 * <p>This is a {@link WebMvcTest @WebMvcTest(CreateCustomerController.class)}
 * slice: it loads ONLY this controller's Spring&nbsp;MVC infrastructure (request
 * mapping, the Jackson message converters and the module {@code ObjectMapper},
 * Bean&nbsp;Validation, and the {@code @RestControllerAdvice}
 * {@code GlobalExceptionHandler}) and replaces the sole business collaborator
 * with a mock {@link CustomerService}. No JPA {@code EntityManager}, Hibernate,
 * Flyway, or PostgreSQL datasource is started, so the test runs GREEN on
 * Java&nbsp;17 with no live database present. A full {@code @SpringBootTest} is
 * deliberately avoided because it would bootstrap the persistence layer that this
 * contract test does not exercise (the {@code CRECUST.cbl} business logic &mdash;
 * the title check, the asynchronous five-way credit-agency fan-out, the date
 * validation, and the gap-free customer-number allocation &mdash; is verified
 * independently by {@code CustomerServiceTest}). {@code @WebMvcTest} auto-detects
 * {@code BankCoreApplication} as the {@code @SpringBootConfiguration} by scanning
 * up the package tree.</p>
 *
 * <p><strong>{@code @MockitoBean}, not the deprecated {@code @MockBean}.</strong>
 * The collaborator is replaced with a
 * {@link MockitoBean @MockitoBean} {@link CustomerService} &mdash; the modern
 * successor of the {@code @MockBean} deprecated since Spring&nbsp;Boot&nbsp;3.4,
 * matching the established convention of the existing stub and every sibling
 * controller {@code *IT} test in this package, so the whole module shares one
 * non-deprecated test-mocking idiom under Spring&nbsp;Boot&nbsp;3.5.11.</p>
 *
 * <h2>What this pins (the frozen contract)</h2>
 * <ul>
 *   <li><strong>{@code POST /crecust/insert}</strong> at the ROOT context (no
 *       servlet context-path), consuming and producing {@code application/json}.</li>
 *   <li><strong>A single top-level wire key {@code CreCust}</strong> in both the
 *       request and the response envelope &mdash; never any sibling top-level
 *       field &mdash; so the preserved consumer
 *       ({@code WebController.checkIfResponseValidCreateCust}, which deserialises
 *       with a strict {@code FAIL_ON_UNKNOWN_PROPERTIES} {@code ObjectMapper} and
 *       branches on {@code CreCust.CommFailCode}) reads it with zero change.</li>
 *   <li><strong>A single HTTP&nbsp;200 in every outcome.</strong> On success the
 *       service's populated envelope ({@code CommSuccess="Y"}, allocated
 *       {@code CommKey}, agency-derived {@code CommCreditScore}, the date-of-birth
 *       string) round-trips. On a business rejection the service throws a
 *       {@link BusinessRuleException} (so its {@code @Transactional} boundary
 *       rolls back the consumed customer number &mdash; ADR-003); the controller
 *       catches it and rebuilds the {@code CreCust} envelope with
 *       {@code CommSuccess="N"} and the verbatim COBOL fail code, STILL at
 *       HTTP&nbsp;200. Asserting that the invalid-title case
 *       ({@code CommFailCode="T"}) returns 200 with the rebuilt single-key
 *       envelope &mdash; rather than the generic {@code GlobalExceptionHandler}
 *       error body &mdash; is the highest-value check in this suite, because it is
 *       exactly what keeps the contract intact for the existing consumers.</li>
 * </ul>
 *
 * <h2>Date-of-birth wire format: 8-character {@code DDMMYYYY}, no slashes</h2>
 * <p>{@code CommDateOfBirth} is asserted as the compact eight-character
 * {@code DDMMYYYY} digit string (for example {@code "11021990"} for 11&nbsp;Feb
 * 1990), NOT a slashed {@code DD/MM/YYYY} display form. This is the authoritative
 * wire contract documented on the production {@code CrecustJson} DTO, whose
 * {@code commDateOfBirth} field is a {@link String} bounded by
 * {@code @Size(max = 8)}: a slashed ten-character value would both contradict the
 * documented "no slashes on the wire" contract and fail the cascaded
 * {@code @Valid} request validation (HTTP&nbsp;400) before the controller logic
 * could run. The {@code DD/MM/YYYY} form is the human-readable DISPLAY format
 * only. This test locks the 8-character wire behaviour.</p>
 *
 * <p>No floating-point {@code double}/{@code float} type appears anywhere in this
 * test, and no decommissioned mainframe library
 * ({@code com.ibm.cics.server.*}, {@code com.ibm.jzos.*},
 * {@code com.ibm.websphere.*}) is referenced, honouring the binding cross-cutting
 * rules (AAP&nbsp;&sect;0.6/&sect;0.7).</p>
 *
 * @see CreateCustomerController
 * @see CustomerService
 * @see CreateCustomerJson
 * @see CrecustJson
 * @see BusinessRuleException
 */
@WebMvcTest(CreateCustomerController.class)
@DisplayName("CreateCustomerController contract IT — POST /crecust/insert reproduces the frozen CreCust envelope (F-019)")
class CreateCustomerControllerIT
{

	/** Frozen endpoint path: ROOT context, no context-path prefix. */
	private static final String ENDPOINT = "/crecust/insert";

	/** Top-level wire envelope key shared by the request and the response. */
	private static final String ENVELOPE_KEY = "CreCust";

	/** Sample customer name (the leading token is a valid honorific title). */
	private static final String SAMPLE_NAME = "Mr Test Customer";

	/** Sample postal address (well within the {@code COMM-ADDRESS X(160)} width). */
	private static final String SAMPLE_ADDRESS = "1 Test Street, Testville TE5 7XX";

	/**
	 * Sample date of birth as the compact eight-character {@code DDMMYYYY} digit
	 * string (11&nbsp;February 1990) &mdash; the exact on-the-wire form, with no
	 * slashes, fitting the {@code CrecustJson.commDateOfBirth} {@code @Size(max=8)}
	 * constraint so the request passes cascaded {@code @Valid} validation.
	 */
	private static final String SAMPLE_DOB = "11021990";

	/** Constant bank sort code echoed on the success envelope ({@code 9(6)}). */
	private static final int SAMPLE_SORTCODE = 987654;

	/**
	 * Sample allocated customer number returned on the success envelope. A
	 * ten-digit value held as a {@code long}, mirroring the {@code 9(10)}
	 * {@code CommNumber} that exceeds {@code int} range and is modelled as a
	 * {@link Long} on the shared key.
	 */
	private static final long SAMPLE_CUSTNO = 1234567890L;

	/** Sample agency-derived credit score returned on the success envelope. */
	private static final int SAMPLE_CREDIT_SCORE = 750;

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@MockitoBean
	private CustomerService customerService;

	/**
	 * Success path: a contract-valid {@code CreCust} request is delegated to the
	 * service, and the populated success envelope round-trips at HTTP&nbsp;200
	 * with the single {@code CreCust} key, {@code CommSuccess="Y"}, the echoed
	 * name, the allocated identity ({@code CommKey.CommSortcode} +
	 * {@code CommNumber}), the agency-derived {@code CommCreditScore}, and the
	 * eight-character {@code DDMMYYYY} {@code CommDateOfBirth}.
	 *
	 * <p>The stub matches with {@code any()} because the controller adapts the
	 * inbound {@code CreCust} wrapper into a {@code CreateCustomerForm} before
	 * calling {@code createCustomer}.</p>
	 *
	 * @throws Exception if the MockMvc exchange or JSON (de)serialisation fails
	 */
	@Test
	@DisplayName("POST /crecust/insert — success returns the CreCust envelope (200, CommSuccess=Y, 8-char DDMMYYYY DOB)")
	void postInsert_success_returnsCreCustEnvelope() throws Exception
	{
		// Stub the (mocked) service: any adapted form yields the success envelope.
		when(customerService.createCustomer(any())).thenReturn(successEnvelope());

		MvcResult result = mockMvc
				.perform(post(ENDPOINT)
						.contentType(MediaType.APPLICATION_JSON)
						.accept(MediaType.APPLICATION_JSON)
						.content(requestBody()))
				.andExpect(status().isOk())
				.andExpect(content()
						.contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$." + ENVELOPE_KEY).exists())
				.andExpect(jsonPath("$.CreCust.CommSuccess").value("Y"))
				.andExpect(jsonPath("$.CreCust.CommName").value(SAMPLE_NAME))
				.andExpect(jsonPath("$.CreCust.CommKey.CommSortcode").exists())
				.andExpect(jsonPath("$.CreCust.CommKey.CommNumber").exists())
				.andExpect(jsonPath("$.CreCust.CommCreditScore")
						.value(SAMPLE_CREDIT_SCORE))
				// The date of birth is the compact 8-character DDMMYYYY wire
				// string (no slashes) — locked exactly as the CrecustJson DTO
				// (@Size(max=8)) and the frozen contract require.
				.andExpect(jsonPath("$.CreCust.CommDateOfBirth").value(SAMPLE_DOB))
				.andReturn();

		// The controller must delegate exactly once to the business service.
		verify(customerService).createCustomer(any());

		// Identity numbers are asserted through the parsed tree (asInt/asLong) so
		// the 10-digit CommNumber is compared without a JsonPath Integer-vs-Long
		// type mismatch.
		JsonNode creCust = objectMapper
				.readTree(result.getResponse().getContentAsString())
				.get(ENVELOPE_KEY);
		assertEquals(SAMPLE_SORTCODE,
				creCust.get("CommKey").get("CommSortcode").asInt(),
				"CommKey.CommSortcode must echo the constant bank sort code");
		assertEquals(SAMPLE_CUSTNO,
				creCust.get("CommKey").get("CommNumber").asLong(),
				"CommKey.CommNumber must echo the allocated customer number");
	}

	/**
	 * Canonical rollback-on-failure case (the highest-value assertion): when the
	 * service throws {@link BusinessRuleException}{@code ("T")} (an invalid
	 * honorific title), the controller catches it and rebuilds the frozen
	 * {@code CreCust} envelope with {@code CommSuccess="N"} and
	 * {@code CommFailCode="T"} &mdash; still at HTTP&nbsp;200, and still a single
	 * top-level {@code CreCust} key.
	 *
	 * <p>This proves the controller rebuilds the {@code CreCust} envelope rather
	 * than emitting the generic {@code GlobalExceptionHandler} body (which would
	 * carry multiple sibling keys and no {@code CreCust}). That byte-for-byte
	 * envelope fidelity is exactly what lets the preserved
	 * {@code WebController.checkIfResponseValidCreateCust} consumer read
	 * {@code CreCust.CommFailCode} unchanged after a base-URL re-point.</p>
	 *
	 * @throws Exception if the MockMvc exchange or JSON parsing fails
	 */
	@Test
	@DisplayName("POST /crecust/insert — invalid title ('T') returns 200 with the CreCust failure envelope")
	void postInsert_invalidTitle_returns200WithFailCodeT() throws Exception
	{
		when(customerService.createCustomer(any()))
				.thenThrow(new BusinessRuleException("T"));

		MvcResult result = mockMvc
				.perform(post(ENDPOINT)
						.contentType(MediaType.APPLICATION_JSON)
						.accept(MediaType.APPLICATION_JSON)
						.content(requestBody()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.CreCust").exists())
				.andExpect(jsonPath("$.CreCust.CommSuccess").value("N"))
				.andExpect(jsonPath("$.CreCust.CommFailCode").value("T"))
				.andReturn();

		verify(customerService).createCustomer(any());

		// Envelope-shape guard on the failure path: exactly one top-level key,
		// and it is CreCust — NOT the generic advice body.
		JsonNode root = objectMapper
				.readTree(result.getResponse().getContentAsString());
		assertEquals(1, root.size(),
				"Failure response must carry exactly one top-level key; body=" + root);
		assertTrue(root.has(ENVELOPE_KEY),
				"The single top-level key must be CreCust; body=" + root);
	}

	/**
	 * A second failure variant proving the envelope rebuild is fail-code-agnostic:
	 * {@link BusinessRuleException}{@code ("C")} (no credit agency replied inside
	 * the three-second deadline) is rendered as the {@code CreCust} failure
	 * envelope at HTTP&nbsp;200 with {@code CommFailCode="C"}.
	 *
	 * @throws Exception if the MockMvc exchange fails
	 */
	@Test
	@DisplayName("POST /crecust/insert — no credit-agency reply ('C') returns 200 with the CreCust failure envelope")
	void postInsert_noAgency_returns200WithFailCodeC() throws Exception
	{
		when(customerService.createCustomer(any()))
				.thenThrow(new BusinessRuleException("C"));

		mockMvc.perform(post(ENDPOINT)
				.contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.APPLICATION_JSON)
				.content(requestBody()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.CreCust").exists())
				.andExpect(jsonPath("$.CreCust.CommSuccess").value("N"))
				.andExpect(jsonPath("$.CreCust.CommFailCode").value("C"));

		verify(customerService).createCustomer(any());
	}

	/**
	 * Envelope-shape guard: a successful response tree carries EXACTLY one
	 * top-level field, and that field is {@code CreCust}. This protects the strict
	 * preserved consumer from any accidental sibling top-level field.
	 *
	 * @throws Exception if the MockMvc exchange or JSON parsing fails
	 */
	@Test
	@DisplayName("POST /crecust/insert — response has exactly one top-level key: CreCust")
	void postInsert_singleTopLevelKeyIsCreCust() throws Exception
	{
		when(customerService.createCustomer(any())).thenReturn(successEnvelope());

		MvcResult result = mockMvc
				.perform(post(ENDPOINT)
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
				"The single top-level key must be CreCust; body=" + root);
	}

	/**
	 * Cascaded Bean Validation (F-021): a request whose wrapper carries an explicit
	 * {@code null} nested envelope ({@code {"CreCust": null}}) is rejected with
	 * HTTP&nbsp;400 by the {@code @Valid}+{@code @NotNull} cascade BEFORE the
	 * mapping dereferences the payload, so the business service is never invoked
	 * (no {@code NullPointerException}&rarr;500 and no silent business failure).
	 *
	 * @throws Exception if the MockMvc exchange fails
	 */
	@Test
	@DisplayName("POST /crecust/insert — a null nested CreCust ({\"CreCust\":null}) returns HTTP 400")
	void postInsert_nullNestedCreCust_returns400() throws Exception
	{
		mockMvc.perform(post(ENDPOINT)
				.contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.APPLICATION_JSON)
				.content("{\"CreCust\":null}"))
				.andExpect(status().isBadRequest());

		verify(customerService, never()).createCustomer(any());
	}

	/**
	 * Empty-body Bean Validation (F-021, QA Issue&nbsp;2): an empty {@code {}}
	 * request body carries no {@code CreCust} key, and the wrapper's no-argument
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
	@DisplayName("POST /crecust/insert — an empty {} body returns HTTP 400 (Issue 2)")
	void postInsert_emptyBody_returns400() throws Exception
	{
		mockMvc.perform(post(ENDPOINT)
				.contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.APPLICATION_JSON)
				.content("{}"))
				.andExpect(status().isBadRequest());

		verify(customerService, never()).createCustomer(any());
	}

	/**
	 * Builds the populated success-response envelope the mocked service returns:
	 * {@code CommSuccess="Y"}, the echoed name and address, the eight-character
	 * {@code DDMMYYYY} date of birth, the agency-derived credit score, the
	 * allocated sort code + customer number, and an empty {@code CommFailCode}
	 * (the success convention the preserved consumer treats as "no failure").
	 *
	 * @return a success {@link CreateCustomerJson} envelope
	 */
	private CreateCustomerJson successEnvelope()
	{
		CrecustJson inner = new CrecustJson();
		inner.setCommEyecatcher("CUST");
		inner.setCommSuccess("Y");
		// Empty fail code is the success marker; the consumer treats a non-empty
		// CommFailCode as a business failure.
		inner.setCommFailCode("");
		inner.setCommName(SAMPLE_NAME);
		inner.setCommAddress(SAMPLE_ADDRESS);
		// 8-character DDMMYYYY wire string (no slashes), per the CrecustJson DTO.
		inner.setCommDateOfBirth(SAMPLE_DOB);
		inner.setCommCreditScore(SAMPLE_CREDIT_SCORE);
		// CommKey is non-null on a freshly-constructed CrecustJson; set the
		// allocated identity without importing the dto.common key type.
		inner.getCommKey().setCommSortcode(SAMPLE_SORTCODE);
		inner.getCommKey().setCommNumber(SAMPLE_CUSTNO);

		CreateCustomerJson envelope = new CreateCustomerJson();
		envelope.setCreCust(inner);
		return envelope;
	}

	/**
	 * Serialises a contract-valid {@code {"CreCust":{...}}} request body via the
	 * application's {@link ObjectMapper}, exercising the real envelope wire-name
	 * mapping. The populated fields satisfy the structural Bean Validation
	 * constraints (name &le; 60, address &le; 160, the eight-character all-digit
	 * date of birth &le; 8) so the request reaches the (mocked) service rather
	 * than being rejected as HTTP&nbsp;400.
	 *
	 * @return the JSON request body as a {@link String}
	 * @throws Exception if serialisation fails
	 */
	private String requestBody() throws Exception
	{
		CreateCustomerJson request = new CreateCustomerJson();
		// The no-argument envelope no longer eager-initialises the inner payload
		// (leaving it null so an empty {} body trips @NotNull -> HTTP 400, F-021),
		// so the inner CrecustJson is constructed and attached explicitly here.
		CrecustJson inner = new CrecustJson();
		request.setCreCust(inner);
		inner.setCommName(SAMPLE_NAME);
		inner.setCommAddress(SAMPLE_ADDRESS);
		inner.setCommDateOfBirth(SAMPLE_DOB);
		return objectMapper.writeValueAsString(request);
	}

}
