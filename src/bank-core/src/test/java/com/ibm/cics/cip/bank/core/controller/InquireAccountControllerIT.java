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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import com.ibm.cics.cip.bank.core.dto.accountenquiry.AccountEnquiryJson;
import com.ibm.cics.cip.bank.core.dto.accountenquiry.InqaccJson;
import com.ibm.cics.cip.bank.core.exception.BusinessRuleException;
import com.ibm.cics.cip.bank.core.service.AccountService;

/**
 * Controller <em>contract</em> integration test for
 * {@link InquireAccountController} &mdash; the proof that the production
 * controller reproduces the frozen z/OS&nbsp;Connect <em>inquire-account</em>
 * ({@code inqaccz}) JSON contract byte-for-byte (feature&nbsp;<strong>F-019</strong>),
 * so the preserved React/Carbon UI and the Customer-Services interface module
 * re-point by base&nbsp;URL only and are never rewritten. This is one of the ten
 * {@code *IT.java} contract tests in {@code com.ibm.cics.cip.bank.core.controller};
 * it targets {@code GET /inqaccz/enquiry/{accno}}.
 *
 * <h2>What this pins (verified against the frozen swagger / consumer)</h2>
 * <ul>
 *   <li><strong>{@code GET /inqaccz/enquiry/{accno}}</strong> at the ROOT context
 *       &mdash; no servlet context-path prefix (basePath {@code /inqaccz},
 *       relativePath {@code /enquiry/{accno}}, operationId {@code getCSaccenq},
 *       service {@code CSaccenq}). The account number travels in the
 *       <em>path variable</em>; the authoritative consumer
 *       ({@code WebController.@GetMapping("/enqacct")}) issues
 *       {@code client.get().retrieve()} with <strong>no request body</strong>.</li>
 *   <li><strong>A single top-level wire key {@code InqAcc}</strong> on the
 *       response (the frozen {@code getCSaccenq_response_200} schema is an object
 *       whose only property is {@code InqAcc}), so the preserved consumer &mdash;
 *       which deserialises with a strict {@code FAIL_ON_UNKNOWN_PROPERTIES}
 *       mapper &mdash; reads it with zero change.</li>
 *   <li><strong>A single HTTP&nbsp;200 in every outcome.</strong> On a found
 *       account the populated envelope round-trips ({@code InqAccSuccess="Y"},
 *       account type, scale-2 {@code BigDecimal} balances, the {@code DDMMYYYY}
 *       opened date). On a not-found / business rejection the controller's
 *       safeguard {@code catch (BusinessRuleException)} rebuilds the {@code InqAcc}
 *       envelope with {@code InqAccSuccess="N"} &mdash; STILL at HTTP&nbsp;200, and
 *       STILL a single top-level {@code InqAcc} key (NOT the generic
 *       {@code GlobalExceptionHandler} {@code {success,failCode,message}} body).</li>
 *   <li><strong>This envelope has a success flag but NO fail-code field.</strong>
 *       Unlike the sibling {@code InqCustZ} contract (which carries
 *       {@code InqCustInqFailCd}), the {@code InqAcc} payload signals failure with
 *       ONLY {@code InqAccSuccess="N"}; this test therefore never asserts a
 *       fail-code field for this endpoint.</li>
 * </ul>
 *
 * <h2>Test strategy &mdash; the DB-free web slice</h2>
 * <p>This is a {@link WebMvcTest @WebMvcTest(InquireAccountController.class)}
 * slice: it bootstraps ONLY this controller's Spring&nbsp;MVC infrastructure
 * (request mapping, message converters, Bean&nbsp;Validation, the auto-configured
 * Jackson {@link ObjectMapper}, {@link MockMvc}, and the
 * {@code @RestControllerAdvice}) and replaces the business collaborator with a
 * {@link MockitoBean @MockitoBean} {@link AccountService}. It starts <strong>no
 * datasource, no JPA/Hibernate, and no Flyway</strong>, so it is GREEN on
 * Java&nbsp;17 with no PostgreSQL running. A full {@code @SpringBootTest} is
 * deliberately avoided because it would bootstrap the persistence layer this
 * contract test does not exercise; the {@code INQACC.cbl} business logic (incl.
 * the {@code 99999999} highest-account sentinel) is verified independently by
 * {@code AccountServiceTest}.</p>
 *
 * <p>All monetary values are modelled as scale-2 {@link BigDecimal} and asserted
 * on the raw response body; no {@code double}/{@code float} appears anywhere in
 * this test, honouring the binding money rule, and the two balances are kept
 * distinct to prove they are never collapsed (&sect;0.6).</p>
 *
 * @see InquireAccountController
 * @see AccountService#inquireAccount(long)
 * @see AccountEnquiryJson
 * @see InqaccJson
 * @see BusinessRuleException
 */
@WebMvcTest(InquireAccountController.class)
@DisplayName("InquireAccountController contract IT — GET /inqaccz/enquiry/{accno} reproduces the frozen InqAcc envelope (F-019)")
class InquireAccountControllerIT
{

	/** Frozen endpoint path template: ROOT context, no context-path prefix. */
	private static final String ENDPOINT_TEMPLATE = "/inqaccz/enquiry/{accno}";

	/** Top-level wire envelope key on the response ({@code InqAcc}). */
	private static final String ENVELOPE_KEY = "InqAcc";

	/** Within-width account number (8 digits, {@code PIC 9(8)}) for the happy path. */
	private static final String SAMPLE_ACCNO = "00000123";

	/** Nine-digit value: over the eight-digit contract width (rejected as 400). */
	private static final String OVER_WIDTH_ACCNO = "123456789";

	/** Success flag value ({@code INQACC-SUCCESS = 'Y'}). */
	private static final String FLAG_SUCCESS = "Y";

	/** Failure flag value ({@code INQACC-SUCCESS = 'N'}); the only failure signal. */
	private static final String FLAG_FAILURE = "N";

	/** Sample account type echoed on the success envelope. */
	private static final String SAMPLE_ACC_TYPE = "ISA";

	/** Sample account number carried on the success envelope (wire integer). */
	private static final int SAMPLE_ACC_NUMBER = 123;

	/** The bank's single sort code (987654). */
	private static final int SAMPLE_SORT_CODE = 987654;

	/**
	 * Available (cleared) balance on the success path (scale-2). Deliberately
	 * <em>distinct</em> from {@link #ACTUAL_BALANCE} so the test proves the two
	 * balances round-trip as independent fields and are never collapsed (&sect;0.6).
	 */
	private static final BigDecimal AVAILABLE_BALANCE = new BigDecimal("1234.56");

	/** Actual (pending) balance on the success path (scale-2, distinct from available). */
	private static final BigDecimal ACTUAL_BALANCE = new BigDecimal("1100.00");

	/** Interest rate on the success path (scale-2 {@code BigDecimal}, never a primitive). */
	private static final BigDecimal INTEREST_RATE = new BigDecimal("1.50");

	/** Fixed opened date used to derive the {@code DDMMYYYY}-packed wire integer. */
	private static final LocalDate SAMPLE_OPENED_DATE = LocalDate.of(2023, 6, 15);

	/**
	 * Account opened date packed as the COBOL {@code DDMMYYYY} integer
	 * ({@code 15/06/2023} &rarr; {@code 15062023}), the exact wire form of
	 * {@code InqAccOpened}.
	 */
	private static final int SAMPLE_OPENED_DDMMYYYY = SAMPLE_OPENED_DATE
			.getDayOfMonth() * 1_000_000
			+ SAMPLE_OPENED_DATE.getMonthValue() * 10_000
			+ SAMPLE_OPENED_DATE.getYear();

	/** Auto-configured MockMvc that drives the {@code InquireAccountController} slice. */
	@Autowired
	private MockMvc mockMvc;

	/** Auto-configured Jackson mapper (reads the response tree for the shape guards). */
	@Autowired
	private ObjectMapper objectMapper;

	/**
	 * The single business collaborator, replaced by a Mockito mock. Stubbed per
	 * test to return a populated {@link AccountEnquiryJson} envelope or to throw a
	 * {@link BusinessRuleException}.
	 */
	@MockitoBean
	private AccountService accountService;

	/**
	 * Success path: a valid account number is delegated to the service exactly
	 * once and the populated {@code InqAcc} envelope round-trips at HTTP&nbsp;200
	 * &mdash; single {@code InqAcc} key, {@code InqAccSuccess="Y"}, the echoed
	 * account type, a present account number, the {@code DDMMYYYY} opened date,
	 * and scale-2 {@code BigDecimal} balances (two distinct, independent values).
	 * The request carries NO body, matching the authoritative {@code WebController}
	 * consumer's {@code client.get().retrieve()}.
	 *
	 * @throws Exception if the MockMvc exchange or JSON (de)serialisation fails
	 */
	@Test
	@DisplayName("GET /inqaccz/enquiry/{accno} — success returns the InqAcc envelope (200, InqAccSuccess=Y, scale-2 balances, DDMMYYYY opened date)")
	void getEnquiry_success_returnsInqAccEnvelope() throws Exception
	{
		// Stub the (mocked) service: any account number yields the success
		// envelope. anyLong() matches because the controller parses the path
		// variable to a long before delegating.
		when(accountService.inquireAccount(anyLong()))
				.thenReturn(successEnvelope());

		MvcResult result = mockMvc
				.perform(get(ENDPOINT_TEMPLATE, SAMPLE_ACCNO)
						.accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(content()
						.contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$." + ENVELOPE_KEY).exists())
				.andExpect(jsonPath("$.InqAcc.InqAccSuccess").value(FLAG_SUCCESS))
				.andExpect(jsonPath("$.InqAcc.InqAccAccType").value(SAMPLE_ACC_TYPE))
				.andExpect(jsonPath("$.InqAcc.InqAccAccno").exists())
				.andReturn();

		// The controller must delegate exactly once to the business service.
		verify(accountService).inquireAccount(anyLong());

		// Scale-2 money fidelity on the raw wire (no floating-point type anywhere):
		// each balance serialises with exactly two fraction digits, and the two
		// balances remain the two distinct values supplied (never collapsed, §0.6).
		// The opened date serialises as the DDMMYYYY integer, exactly as the
		// frozen contract types InqAccOpened.
		String body = result.getResponse().getContentAsString();
		assertTrue(
				body.contains(
						"\"InqAccAvailBal\":" + AVAILABLE_BALANCE.toPlainString()),
				"InqAccAvailBal must serialise at scale 2 (1234.56); body=" + body);
		assertTrue(
				body.contains(
						"\"InqAccActualBal\":" + ACTUAL_BALANCE.toPlainString()),
				"InqAccActualBal must serialise at scale 2 (1100.00) and stay independent of the available balance; body="
						+ body);
		assertTrue(
				body.contains("\"InqAccOpened\":" + SAMPLE_OPENED_DDMMYYYY),
				"InqAccOpened must serialise as the DDMMYYYY integer 15062023; body="
						+ body);
	}

	/**
	 * Not-found / business-failure path: the stubbed service throws
	 * {@link BusinessRuleException}{@code ("1")}; the controller's safeguard
	 * {@code catch} rebuilds the frozen {@code InqAcc} envelope with
	 * {@code InqAccSuccess="N"} and returns HTTP&nbsp;<strong>200</strong> (never
	 * 4xx/5xx). The body MUST be the rebuilt single-key {@code InqAcc} envelope,
	 * NOT the generic {@code GlobalExceptionHandler} advice body &mdash; asserting
	 * the single top-level {@code InqAcc} key proves exactly that.
	 *
	 * <p>The {@code InqAcc} envelope has NO fail-code field, so this test asserts
	 * ONLY {@code InqAccSuccess="N"} and never references a (non-existent)
	 * fail-code field.</p>
	 *
	 * @throws Exception if the MockMvc exchange or JSON parsing fails
	 */
	@Test
	@DisplayName("GET /inqaccz/enquiry/{accno} — not found returns 200 with InqAccSuccess=N (rebuilt envelope, no fail-code field)")
	void getEnquiry_notFound_returns200WithInqAccSuccessN() throws Exception
	{
		// Drive the controller's safeguard catch with a business-rule exception;
		// the controller honours the frozen contract by rebuilding the InqAcc
		// envelope (InqAccSuccess="N") at HTTP 200 rather than surfacing a 4xx/5xx.
		when(accountService.inquireAccount(anyLong()))
				.thenThrow(new BusinessRuleException("1"));

		MvcResult result = mockMvc
				.perform(get(ENDPOINT_TEMPLATE, SAMPLE_ACCNO)
						.accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(content()
						.contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$." + ENVELOPE_KEY).exists())
				.andExpect(jsonPath("$.InqAcc.InqAccSuccess").value(FLAG_FAILURE))
				.andReturn();

		// Envelope rebuilt, NOT the generic {success,failCode,message} advice body:
		// exactly one top-level key, and it is InqAcc.
		JsonNode root = objectMapper
				.readTree(result.getResponse().getContentAsString());
		assertEquals(1, root.size(),
				"Failure response must carry exactly one top-level key (the rebuilt InqAcc envelope, not the generic advice body); body="
						+ root);
		assertTrue(root.has(ENVELOPE_KEY),
				"The single top-level key must be InqAcc; body=" + root);
	}

	/**
	 * Envelope-shape guard: a successful response tree carries EXACTLY one
	 * top-level field, and that field is {@code InqAcc}. This pins the single-key
	 * wrapper shape of the frozen {@code getCSaccenq_response_200} schema and
	 * protects the strict ({@code FAIL_ON_UNKNOWN_PROPERTIES}) preserved consumer
	 * from any accidental sibling top-level field.
	 *
	 * @throws Exception if the MockMvc exchange or JSON parsing fails
	 */
	@Test
	@DisplayName("GET /inqaccz/enquiry/{accno} — response has exactly one top-level key: InqAcc")
	void getEnquiry_singleTopLevelKeyIsInqAcc() throws Exception
	{
		when(accountService.inquireAccount(anyLong()))
				.thenReturn(successEnvelope());

		MvcResult result = mockMvc
				.perform(get(ENDPOINT_TEMPLATE, SAMPLE_ACCNO)
						.accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andReturn();

		JsonNode root = objectMapper
				.readTree(result.getResponse().getContentAsString());
		assertEquals(1, root.size(),
				"Response must carry exactly one top-level key; body=" + root);
		assertTrue(root.has(ENVELOPE_KEY),
				"The single top-level key must be InqAcc; body=" + root);
	}

	/**
	 * Frozen-contract parameter source: the swagger declares BOTH the
	 * {@code {accno}} path variable and an optional {@code InqAcc} request body
	 * ({@code getCSaccenq_request}). The controller accepts the body as
	 * {@code @RequestBody(required = false)}, so a swagger-shaped caller that
	 * sends the {@code InqAcc} envelope alongside the path variable is NOT
	 * rejected and still receives the {@code InqAcc} response at HTTP&nbsp;200. The
	 * path variable stays authoritative, so the service is still invoked exactly
	 * once.
	 *
	 * @throws Exception if the MockMvc exchange or JSON serialisation fails
	 */
	@Test
	@DisplayName("GET /inqaccz/enquiry/{accno} — an optional InqAcc request body is accepted (swagger parameter source)")
	void getEnquiry_withOptionalBody_isAccepted() throws Exception
	{
		when(accountService.inquireAccount(anyLong()))
				.thenReturn(successEnvelope());

		String requestBody = objectMapper.writeValueAsString(successEnvelope());

		mockMvc.perform(get(ENDPOINT_TEMPLATE, SAMPLE_ACCNO)
				.contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.APPLICATION_JSON)
				.content(requestBody))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$." + ENVELOPE_KEY).exists())
				.andExpect(jsonPath("$.InqAcc.InqAccSuccess").value(FLAG_SUCCESS));

		verify(accountService).inquireAccount(anyLong());
	}

	/**
	 * Fixed-width path enforcement: a nine-digit account number exceeds the
	 * eight-digit ({@code PIC 9(8)}) contract width. {@code BankFormat
	 * .parseAccountNumber} raises {@link NumberFormatException}, which the
	 * controller deliberately does NOT catch; it propagates to
	 * {@code GlobalExceptionHandler} and is rendered as HTTP&nbsp;400. The business
	 * service is never invoked, so a malformed identifier never reaches
	 * {@code INQACC} logic.
	 *
	 * @throws Exception if the MockMvc exchange fails
	 */
	@Test
	@DisplayName("GET /inqaccz/enquiry/123456789 — an over-width (9-digit) accno returns HTTP 400, service not invoked")
	void getEnquiry_overWidthAccno_returns400() throws Exception
	{
		mockMvc.perform(get(ENDPOINT_TEMPLATE, OVER_WIDTH_ACCNO)
				.accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isBadRequest());

		verify(accountService, never()).inquireAccount(anyLong());
	}

	/**
	 * Builds the populated success-response envelope the mocked service returns:
	 * {@code InqAccSuccess="Y"}, the bank sort code and account number, the
	 * account type, scale-2 {@code BigDecimal} balances (two distinct, independent
	 * values) and interest rate, and the opened date packed as the {@code DDMMYYYY}
	 * integer derived from {@link #SAMPLE_OPENED_DATE}.
	 *
	 * @return a success {@link AccountEnquiryJson} envelope
	 */
	private AccountEnquiryJson successEnvelope()
	{
		InqaccJson inner = new InqaccJson();
		inner.setInqaccSuccess(FLAG_SUCCESS);
		inner.setInqaccAccType(SAMPLE_ACC_TYPE);
		inner.setInqaccAccno(SAMPLE_ACC_NUMBER);
		inner.setInqaccSortcode(SAMPLE_SORT_CODE);
		// Money is scale-2 BigDecimal (never a floating-point type); the two
		// balances are deliberately distinct to prove they are not collapsed.
		inner.setInqaccAvailableBalance(AVAILABLE_BALANCE);
		inner.setInqaccActualBalance(ACTUAL_BALANCE);
		inner.setInqaccInterestRate(INTEREST_RATE);
		// Opened date as the COBOL DDMMYYYY-packed integer.
		inner.setInqaccOpened(SAMPLE_OPENED_DDMMYYYY);

		AccountEnquiryJson envelope = new AccountEnquiryJson();
		envelope.setInqaccCommarea(inner);
		return envelope;
	}

}
