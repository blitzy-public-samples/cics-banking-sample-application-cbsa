/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.core.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibm.cics.cip.bank.core.dto.customerenquiry.CustomerEnquiryJson;
import com.ibm.cics.cip.bank.core.dto.customerenquiry.InqCustZJson;
import com.ibm.cics.cip.bank.core.service.CustomerService;

/**
 * Controller <em>contract</em> integration test for
 * {@link InquireCustomerController} &mdash; the proof for the
 * <strong>CRITICAL</strong> F-019 frozen-contract finding and the customer
 * -number fixed-width parse finding raised at this checkpoint.
 *
 * <h2>What this pins (the frozen {@code inqcustz} contract)</h2>
 * <ul>
 *   <li><strong>The response envelope's single top-level key is
 *       {@code InqCustZ}</strong> (mixed case), exactly as declared by
 *       {@code src/zosconnect_artefacts/apis/inqcustz/.../swagger.json}
 *       ({@code getCScustenq_response_200.InqCustZ}) and the service interface
 *       {@code INQCUSTZ.si} ({@code field name="InqCustZ" originalName="INQCUSTZ"}).
 *       This guards against the previous all-caps {@code INQCUSTZ} regression,
 *       which diverged from the swagger and from the sibling
 *       {@code AccountEnquiryJson} ({@code InqAcc}) reference.</li>
 *   <li><strong>{@code GET /inqcustz/enquiry/{custno}}</strong> at the root
 *       context (no context-path prefix), producing {@code application/json}.</li>
 *   <li><strong>The {@code {custno}} path variable is bound to the 1-to-10-digit
 *       contract width.</strong> An over-width value such as {@code 10000000000}
 *       (eleven digits) is rejected as HTTP&nbsp;400 by
 *       {@code BankFormat.parseCustomerNumber} &rarr; {@code GlobalExceptionHandler},
 *       rather than being silently accepted by a raw {@code Long.parseLong}.</li>
 *   <li><strong>The swagger-declared request body is accepted but optional</strong>
 *       ({@code @RequestBody(required = false)}): a caller that sends the frozen
 *       {@code InqCustZ} envelope alongside the path variable is honoured, and a
 *       caller that sends none (the preserved no-body Java consumer) still works.
 *       The path variable remains authoritative.</li>
 *   <li><strong>A not-found read is a soft outcome:</strong> HTTP&nbsp;200 with
 *       {@code InqCustZ.InqCustInqSuccess="N"} and {@code InqCustInqFailCd="1"}
 *       in the envelope &mdash; INQCUST never abends on a miss (F-008).</li>
 * </ul>
 *
 * <h2>Test strategy &mdash; the DB-free web slice</h2>
 * <p>This is a {@link WebMvcTest @WebMvcTest(InquireCustomerController.class)}
 * slice: it bootstraps only the Spring MVC layer for the controller (plus the
 * auto-configured Jackson {@link ObjectMapper}, {@link MockMvc} and the
 * {@code @RestControllerAdvice}), with <strong>no datasource, JPA or Flyway</strong>,
 * so it is green on Java&nbsp;17 with no PostgreSQL running. The single
 * collaborator {@link CustomerService} (the {@code INQCUST} business port) is a
 * {@link MockBean @MockitoBean}, so the test exercises the adapter in isolation.</p>
 */
@WebMvcTest(InquireCustomerController.class)
@DisplayName("InquireCustomerController contract IT — frozen inqcustz InqCustZ envelope + fixed-width custno (F-019/F-021)")
class InquireCustomerControllerIT
{

	/** Frozen endpoint base: root context, no context-path prefix. */
	private static final String ENDPOINT = "/inqcustz/enquiry/";

	/** A within-width (&le; 10-digit) customer number used on the happy path. */
	private static final String VALID_CUSTNO = "1";

	/** An eleven-digit value: inside {@code long} range, but over the contract width. */
	private static final String OVER_WIDTH_CUSTNO = "10000000000";

	/** Auto-configured MockMvc driving the {@code InquireCustomerController} slice. */
	@Autowired
	private MockMvc mockMvc;

	/** Auto-configured Jackson mapper (serialises requests, reads the response tree). */
	@Autowired
	private ObjectMapper objectMapper;

	/** The single business collaborator, replaced by a Mockito mock. */
	@MockitoBean
	private CustomerService customerService;

	/**
	 * <strong>CRITICAL proof.</strong> The response body must carry
	 * <em>exactly one</em> top-level key and it must be the mixed-case
	 * {@code InqCustZ} (never the all-caps {@code INQCUSTZ}). This is the verbatim
	 * frozen {@code getCScustenq_response_200} envelope key.
	 *
	 * @throws Exception if the MockMvc exchange or JSON parsing fails
	 */
	@Test
	@DisplayName("GET /inqcustz/enquiry/{custno} — response has exactly one top-level key: InqCustZ (not INQCUSTZ)")
	void getEnquiry_responseSingleTopLevelKeyIsInqCustZ() throws Exception
	{
		when(customerService.inquireCustomer(anyLong()))
				.thenReturn(successEnvelope());

		MvcResult result = mockMvc
				.perform(get(ENDPOINT + VALID_CUSTNO)
						.accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(content()
						.contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.InqCustZ").exists())
				.andReturn();

		JsonNode root = objectMapper
				.readTree(result.getResponse().getContentAsString());

		assertEquals(1, root.size(),
				"Response must carry exactly one top-level key; body=" + root);
		assertTrue(root.has("InqCustZ"),
				"The single top-level key must be the frozen mixed-case InqCustZ; body="
						+ root);
		assertFalse(root.has("INQCUSTZ"),
				"The all-caps INQCUSTZ key must NOT be emitted (frozen-contract regression); body="
						+ root);
	}

	/**
	 * The fixed-width parse finding: an eleven-digit customer number (inside the
	 * {@code long} range, so {@code Long.parseLong} would have accepted it) must
	 * be rejected as HTTP&nbsp;400, and the service must never be invoked.
	 *
	 * @throws Exception if the MockMvc exchange fails
	 */
	@Test
	@DisplayName("GET /inqcustz/enquiry/10000000000 — an over-width (11-digit) custno is rejected as HTTP 400")
	void getEnquiry_overWidthCustno_returns400() throws Exception
	{
		mockMvc.perform(get(ENDPOINT + OVER_WIDTH_CUSTNO)
				.accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isBadRequest());

		// The malformed path variable is rejected before any delegation.
		verify(customerService, never()).inquireCustomer(anyLong());
	}

	/**
	 * A not-found read returns HTTP&nbsp;200 with the soft
	 * {@code InqCustInqSuccess="N"}/{@code InqCustInqFailCd="1"} envelope (INQCUST
	 * does not abend on a miss, F-008).
	 *
	 * @throws Exception if the MockMvc exchange fails
	 */
	@Test
	@DisplayName("GET /inqcustz/enquiry/{custno} — a miss returns 200 with InqCustZ.InqCustInqSuccess=N / InqCustInqFailCd=1")
	void getEnquiry_notFound_returns200WithSoftEnvelope() throws Exception
	{
		when(customerService.inquireCustomer(anyLong()))
				.thenReturn(notFoundEnvelope());

		mockMvc.perform(get(ENDPOINT + "555")
				.accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.InqCustZ").exists())
				.andExpect(jsonPath("$.InqCustZ.InqCustInqSuccess").value("N"))
				.andExpect(jsonPath("$.InqCustZ.InqCustInqFailCd").value("1"));
	}

	/**
	 * Contract parameter-source fidelity: the swagger-declared request body is
	 * accepted (but optional). A caller sending the frozen {@code InqCustZ}
	 * envelope alongside the path variable still gets the {@code InqCustZ}
	 * response; the path variable stays authoritative.
	 *
	 * @throws Exception if the MockMvc exchange or JSON serialisation fails
	 */
	@Test
	@DisplayName("GET /inqcustz/enquiry/{custno} — an optional InqCustZ request body is accepted (swagger parameter source)")
	void getEnquiry_withOptionalBody_isAccepted() throws Exception
	{
		when(customerService.inquireCustomer(anyLong()))
				.thenReturn(successEnvelope());

		String body = objectMapper.writeValueAsString(successEnvelope());

		mockMvc.perform(get(ENDPOINT + VALID_CUSTNO)
				.contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.APPLICATION_JSON)
				.content(body))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.InqCustZ").exists());
	}

	/**
	 * Builds a populated success-response envelope (the mocked service's
	 * {@code 'Y'} outcome) so the controller answers HTTP&nbsp;200 with a fully
	 * formed {@code InqCustZ} payload.
	 *
	 * @return a success {@link CustomerEnquiryJson} envelope
	 */
	private CustomerEnquiryJson successEnvelope()
	{
		InqCustZJson inner = new InqCustZJson();
		inner.setInqCustCustno("0000000001");
		inner.setInqCustInqSuccess("Y");
		CustomerEnquiryJson envelope = new CustomerEnquiryJson();
		envelope.setInqCustZ(inner);
		return envelope;
	}

	/**
	 * Builds the soft not-found envelope INQCUST returns on a miss:
	 * {@code InqCustInqSuccess="N"} with fail code {@code "1"}.
	 *
	 * @return a not-found {@link CustomerEnquiryJson} envelope
	 */
	private CustomerEnquiryJson notFoundEnvelope()
	{
		InqCustZJson inner = new InqCustZJson();
		inner.setInqCustCustno("0000000555");
		inner.setInqCustInqSuccess("N");
		inner.setInqCustInqFailCd("1");
		CustomerEnquiryJson envelope = new CustomerEnquiryJson();
		envelope.setInqCustZ(inner);
		return envelope;
	}

}
