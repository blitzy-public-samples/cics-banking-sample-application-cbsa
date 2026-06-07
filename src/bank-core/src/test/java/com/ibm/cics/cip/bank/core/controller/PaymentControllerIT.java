/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.core.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibm.cics.cip.bank.core.dto.payment.DbcrJson;
import com.ibm.cics.cip.bank.core.dto.payment.OriginJson;
import com.ibm.cics.cip.bank.core.dto.payment.PaymentJson;
import com.ibm.cics.cip.bank.core.exception.BusinessRuleException;
import com.ibm.cics.cip.bank.core.service.PaymentService;

/**
 * Controller <em>contract</em> integration test for {@link PaymentController}
 * &mdash; it proves the new Spring MVC adapter reproduces the frozen
 * z/OS&nbsp;Connect <em>make-payment</em> (debit/credit) JSON contract
 * byte-for-byte (feature <strong>F-019</strong>), so the preserved Payment
 * Interface UI re-points by base URL only and is never rewritten.
 *
 * <h2>What this pins (the frozen {@code makepayment} contract)</h2>
 * <p>Every assertion below is the verbatim contract declared by
 * {@code src/zosconnect_artefacts/apis/makepayment/api-docs/swagger.json} and
 * {@code .../makepayment/package.xml} (basePath {@code /makepayment}, PUT,
 * relativePath {@code /dbcr}, operationId {@code putPay}, service {@code Pay}):</p>
 * <ul>
 *   <li><strong>{@code PUT /makepayment/dbcr}</strong> at the <em>root</em>
 *       context &mdash; there is deliberately <strong>no context-path</strong>
 *       prefix. Consumes and produces {@code application/json}.</li>
 *   <li>A single HTTP&nbsp;<strong>200</strong> on <em>both</em> success and a
 *       business failure, because the frozen consumers inspect the response body
 *       (the success flag and fail code), not the HTTP status, to decide the
 *       outcome.</li>
 *   <li>The single top-level wire key {@code PAYDBCR} (UPPERCASE) on both the
 *       request and the response, i.e. {@code {"PAYDBCR":{ ... }}}.</li>
 *   <li>The nested {@code CommOrigin} sub-object (carrying {@code CommFaciltype})
 *       round-trips inside the {@code PAYDBCR} payload.</li>
 *   <li>The monetary fields ({@code CommAmt}, {@code CommAvBal},
 *       {@code CommActBal}) are scale-2 {@code BigDecimal} on the wire &mdash;
 *       never {@code double}/{@code float} &mdash; and the two balances are
 *       independent (cleared vs. pending) and never collapsed.</li>
 *   <li>The envelope carries a dedicated {@code CommFailCode} field: on a
 *       business failure the controller sets <strong>both</strong>
 *       {@code CommSuccess="N"} and {@code CommFailCode} (e.g. {@code "3"} =
 *       insufficient funds, {@code "4"} = invalid/restricted account type).</li>
 * </ul>
 *
 * <h2>Test strategy &mdash; the DB-free web slice</h2>
 * <p>This is a {@link WebMvcTest @WebMvcTest(PaymentController.class)} slice: it
 * bootstraps <em>only</em> the Spring MVC layer for {@code PaymentController}
 * (plus the auto-configured Jackson {@link ObjectMapper}, {@link MockMvc}, and
 * the {@code @RestControllerAdvice}). It deliberately starts <strong>no
 * datasource, no JPA/Hibernate, and no Flyway</strong>, so it is green on
 * Java&nbsp;17 with <strong>no PostgreSQL</strong> running. The single
 * collaborator {@link PaymentService} (the {@code DBCRFUN} business port) is
 * replaced by a {@link MockBean @MockBean} so the test exercises the controller
 * adapter in isolation and never touches a database. A full-context
 * {@code @SpringBootTest} is intentionally <strong>not</strong> used, as it would
 * require the datasource and the whole bean graph.</p>
 *
 * <h2>Why stub {@code processPayment(PaymentJson)} with {@code any()}</h2>
 * <p>Unlike the other write endpoints (which accept a {@code Form} DTO), the make
 * -payment service takes the wire envelope <em>directly</em> &mdash; its
 * signature is {@code PaymentJson processPayment(PaymentJson)} (wrapper-in /
 * wrapper-out). The stub therefore matches the inbound envelope with
 * {@link org.mockito.ArgumentMatchers#any() any()} and returns either a populated
 * success envelope (case&nbsp;1/3) or throws a {@link BusinessRuleException}
 * carrying the verbatim COBOL fail code (case&nbsp;2). The controller's own
 * success-flag rendering and its in-controller {@code catch} of
 * {@code BusinessRuleException} (which re-renders the {@code PAYDBCR} failure
 * envelope, <em>not</em> the generic advice body) are what these tests verify.</p>
 */
@WebMvcTest(PaymentController.class)
@DisplayName("PaymentController contract IT — frozen makepayment PAYDBCR envelope (F-019)")
class PaymentControllerIT
{

	/** Frozen endpoint path: root context, no context-path prefix. */
	private static final String ENDPOINT = "/makepayment/dbcr";

	/** Sample account number, zero-padded to the contract width {@code PIC X(8)}. */
	private static final String SAMPLE_ACCOUNT = "00000001";

	/** Sample sort code (the bank's single sort code, within {@code mSortC} range). */
	private static final int SAMPLE_SORT_CODE = 987654;

	/** Default facility type populated by {@link OriginJson} (the PAYMENT channel). */
	private static final int FACILITY_TYPE_496 = 496;

	/** Positive credit amount used on the success path (scale-2). */
	private static final BigDecimal CREDIT_AMOUNT = new BigDecimal("100.00");

	/**
	 * Available (cleared) balance returned on the success path (scale-2).
	 * Deliberately <em>distinct</em> from {@link #RESPONSE_ACTUAL_BALANCE} so the
	 * test proves the two balances round-trip as independent fields and are never
	 * collapsed onto one another (&sect;0.6).
	 */
	private static final BigDecimal RESPONSE_AVAILABLE_BALANCE = new BigDecimal("1100.00");

	/** Actual balance returned on the success path (scale-2, distinct from available). */
	private static final BigDecimal RESPONSE_ACTUAL_BALANCE = new BigDecimal("1234.56");

	/** Fail code for an insufficient-funds debit ({@code DBCRFUN} {@code '3'}). */
	private static final String FAIL_CODE_INSUFFICIENT_FUNDS = "3";

	/** Auto-configured MockMvc that drives the {@code PaymentController} web slice. */
	@Autowired
	private MockMvc mockMvc;

	/** Auto-configured Jackson mapper (serialises the request, reads the response tree). */
	@Autowired
	private ObjectMapper objectMapper;

	/**
	 * The single business collaborator, replaced by a Mockito mock. Stubbed per
	 * test to return a populated {@link PaymentJson} envelope or to throw a
	 * {@link BusinessRuleException}.
	 */
	@MockBean
	private PaymentService paymentService;

	/**
	 * Verifies the happy path: a credit (positive {@code CommAmt}) returns the
	 * frozen {@code PAYDBCR} envelope at HTTP&nbsp;200 with {@code CommSuccess="Y"},
	 * both scale-2 balances updated, and the nested {@code CommOrigin} (carrying
	 * {@code CommFaciltype}) round-tripped.
	 *
	 * <p>The stubbed service returns a populated wrapper; the controller then
	 * renders the success flags onto it ({@code CommSuccess="Y"},
	 * {@code CommFailCode="0"}) before answering HTTP&nbsp;200. The scale-2
	 * fidelity of the money fields is asserted against the raw response body
	 * (compact JSON), because {@code BigDecimal("1100.00")} must serialise as the
	 * two-decimal number {@code 1100.00} &mdash; the byte-for-byte F-019
	 * guarantee.</p>
	 *
	 * @throws Exception if the MockMvc exchange or JSON (de)serialisation fails
	 */
	@Test
	@DisplayName("PUT /makepayment/dbcr — credit success returns the PAYDBCR envelope (200, CommSuccess=Y, scale-2 balances, nested CommOrigin)")
	void putDbcr_creditSuccess_returnsPaydbcrEnvelope() throws Exception
	{
		// The service takes the PaymentJson wrapper directly, so any() matches the
		// inbound envelope; return a fully-populated success envelope.
		when(paymentService.processPayment(any()))
				.thenReturn(buildSuccessResponseEnvelope());

		String requestBody = objectMapper
				.writeValueAsString(buildRequestEnvelope());

		MvcResult result = mockMvc
				.perform(put(ENDPOINT)
						.contentType(MediaType.APPLICATION_JSON)
						.accept(MediaType.APPLICATION_JSON)
						.content(requestBody))
				// Single HTTP 200, JSON content type, single top-level PAYDBCR key.
				.andExpect(status().isOk())
				.andExpect(content()
						.contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.PAYDBCR").exists())
				// Success flag rendered by the controller onto the envelope.
				.andExpect(jsonPath("$.PAYDBCR.CommSuccess").value("Y"))
				// Both (independent) balances present on the wire.
				.andExpect(jsonPath("$.PAYDBCR.CommAvBal").exists())
				.andExpect(jsonPath("$.PAYDBCR.CommActBal").exists())
				// The signed amount field is present (positive = credit).
				.andExpect(jsonPath("$.PAYDBCR.CommAmt").exists())
				// The nested CommOrigin block round-trips, including CommFaciltype.
				.andExpect(jsonPath("$.PAYDBCR.CommOrigin").exists())
				.andExpect(jsonPath("$.PAYDBCR.CommOrigin.CommFaciltype").exists())
				.andReturn();

		String body = result.getResponse().getContentAsString();

		// Scale-2 money fidelity on the wire (compact JSON): each balance must
		// serialise with exactly two fraction digits, and the two balances must be
		// the two distinct values supplied (proving they are never collapsed).
		assertTrue(body.contains("\"CommAvBal\":1100.00"),
				"Available balance must serialise at scale 2 (1100.00); body=" + body);
		assertTrue(body.contains("\"CommActBal\":1234.56"),
				"Actual balance must serialise at scale 2 (1234.56) and stay independent of the available balance; body="
						+ body);
		// Positive (credit) amount, scale-2, preserving the sign convention.
		assertTrue(body.contains("\"CommAmt\":100.00"),
				"Credit amount must serialise positive and at scale 2 (100.00); body=" + body);
		// The nested CommFaciltype round-trips as the bare integer 496.
		assertTrue(body.contains("\"CommFaciltype\":" + FACILITY_TYPE_496),
				"Nested CommOrigin.CommFaciltype must round-trip as the integer 496; body=" + body);
	}

	/**
	 * Verifies the business-failure path: an insufficient-funds debit. The stubbed
	 * service throws {@code BusinessRuleException("3")}; the controller catches it
	 * <em>in-controller</em> and re-renders the original {@code PAYDBCR} request
	 * envelope with {@code CommSuccess="N"} and {@code CommFailCode="3"}, returning
	 * HTTP&nbsp;<strong>200</strong> (never 4xx/5xx) so the envelope reaches the
	 * frozen consumer intact.
	 *
	 * <p>Asserting {@code $.PAYDBCR} exists confirms the response is the rebuilt
	 * {@code PAYDBCR} envelope &mdash; <em>not</em> the generic
	 * {@code {success,failCode,message}} body that {@code GlobalExceptionHandler}
	 * would emit &mdash; which is the primary envelope-fidelity guarantee for this
	 * endpoint.</p>
	 *
	 * @throws Exception if the MockMvc exchange or JSON serialisation fails
	 */
	@Test
	@DisplayName("PUT /makepayment/dbcr — insufficient funds returns HTTP 200 with CommSuccess=N and CommFailCode=3")
	void putDbcr_insufficientFunds_returns200WithFailCode3() throws Exception
	{
		// Drive the controller's in-controller catch path with the verbatim COBOL
		// fail code '3' (insufficient funds); the service rolls back by throwing.
		when(paymentService.processPayment(any()))
				.thenThrow(new BusinessRuleException(
						FAIL_CODE_INSUFFICIENT_FUNDS));

		String requestBody = objectMapper
				.writeValueAsString(buildRequestEnvelope());

		mockMvc.perform(put(ENDPOINT)
				.contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.APPLICATION_JSON)
				.content(requestBody))
				// HTTP 200 even on a business failure (body carries the outcome).
				.andExpect(status().isOk())
				.andExpect(content()
						.contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				// The rebuilt PAYDBCR envelope (NOT the generic advice body).
				.andExpect(jsonPath("$.PAYDBCR").exists())
				.andExpect(jsonPath("$.PAYDBCR.CommSuccess").value("N"))
				.andExpect(jsonPath("$.PAYDBCR.CommFailCode")
						.value(FAIL_CODE_INSUFFICIENT_FUNDS));
	}

	/**
	 * Envelope-shape guard: the response must carry <strong>exactly one</strong>
	 * top-level key, and it must be {@code PAYDBCR}. This pins the single-key
	 * wrapper shape of the frozen {@code putPay_response_200} schema and guards
	 * against any accidental flattening or addition of sibling top-level fields.
	 *
	 * @throws Exception if the MockMvc exchange or JSON (de)serialisation fails
	 */
	@Test
	@DisplayName("PUT /makepayment/dbcr — response body has exactly one top-level key: PAYDBCR")
	void putDbcr_singleTopLevelKeyIsPaydbcr() throws Exception
	{
		when(paymentService.processPayment(any()))
				.thenReturn(buildSuccessResponseEnvelope());

		String requestBody = objectMapper
				.writeValueAsString(buildRequestEnvelope());

		MvcResult result = mockMvc
				.perform(put(ENDPOINT)
						.contentType(MediaType.APPLICATION_JSON)
						.accept(MediaType.APPLICATION_JSON)
						.content(requestBody))
				.andExpect(status().isOk())
				.andReturn();

		JsonNode root = objectMapper
				.readTree(result.getResponse().getContentAsString());

		// Exactly one top-level field, and it is the uppercase PAYDBCR envelope key.
		assertEquals(1, root.size(),
				"Response must carry exactly one top-level key; body=" + root);
		assertTrue(root.has("PAYDBCR"),
				"The single top-level key must be PAYDBCR; body=" + root);
	}

	/**
	 * Builds a contract-valid inbound {@code PAYDBCR} request envelope that passes
	 * the controller's {@code @Valid @RequestBody} cascade (so the request reaches
	 * the stubbed service rather than being rejected at HTTP&nbsp;400). All field
	 * widths and ranges honour the frozen {@code makepayment} schema.
	 *
	 * @return a populated, validation-passing {@link PaymentJson} request envelope
	 */
	private PaymentJson buildRequestEnvelope()
	{
		OriginJson origin = new OriginJson();
		// CommFaciltype defaults to 496 (the PAYMENT channel); set the two origin
		// strings to contract-valid 8-character values so the nested block is fully
		// populated on the request side too.
		origin.setCommApplid("CICSAPPL");
		origin.setCommUserid("USER0001");

		DbcrJson dbcr = new DbcrJson();
		dbcr.setCommAccno(SAMPLE_ACCOUNT);
		dbcr.setCommAmt(CREDIT_AMOUNT);
		dbcr.setCommSortC(SAMPLE_SORT_CODE);
		dbcr.setCommAvBal(new BigDecimal("0.00"));
		dbcr.setCommActBal(new BigDecimal("0.00"));
		dbcr.setCommOrigin(origin);

		PaymentJson request = new PaymentJson();
		request.setPAYDBCR(dbcr);
		return request;
	}

	/**
	 * Builds the populated success-response envelope the mocked service returns: a
	 * credit (positive {@code CommAmt}) with both balances updated at scale&nbsp;2
	 * to two <em>distinct</em> values, and a populated nested {@code CommOrigin}
	 * (default {@code CommFaciltype=496}). The controller overwrites
	 * {@code CommSuccess}/{@code CommFailCode} with its success rendering, so the
	 * value set here is illustrative of the service's {@code 'Y'} outcome.
	 *
	 * @return a fully-populated success {@link PaymentJson} response envelope
	 */
	private PaymentJson buildSuccessResponseEnvelope()
	{
		OriginJson origin = new OriginJson();
		origin.setCommApplid("CICSAPPL");
		origin.setCommUserid("USER0001");

		DbcrJson dbcr = new DbcrJson();
		dbcr.setCommAccno(SAMPLE_ACCOUNT);
		// Positive amount = credit (sign convention preserved end-to-end).
		dbcr.setCommAmt(CREDIT_AMOUNT);
		dbcr.setCommSortC(SAMPLE_SORT_CODE);
		// Both balances updated to distinct scale-2 values (independent fields).
		dbcr.setCommAvBal(RESPONSE_AVAILABLE_BALANCE);
		dbcr.setCommActBal(RESPONSE_ACTUAL_BALANCE);
		dbcr.setCommOrigin(origin);
		// The service flags success; the controller re-renders this to "Y"/"0".
		dbcr.setCommSuccess("Y");

		PaymentJson response = new PaymentJson();
		response.setPAYDBCR(dbcr);
		return response;
	}

}
