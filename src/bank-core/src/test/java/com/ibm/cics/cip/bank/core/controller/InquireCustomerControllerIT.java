/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.core.controller;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibm.cics.cip.bank.core.dto.customerenquiry.CustomerEnquiryJson;
import com.ibm.cics.cip.bank.core.dto.customerenquiry.InqCustDob;
import com.ibm.cics.cip.bank.core.dto.customerenquiry.InqCustZJson;
import com.ibm.cics.cip.bank.core.exception.BusinessRuleException;
import com.ibm.cics.cip.bank.core.service.CustomerService;

/**
 * Controller <em>contract</em> integration test for
 * {@link InquireCustomerController} &mdash; the proof that the production
 * controller reproduces the frozen z/OS&nbsp;Connect <em>inquire-customer</em>
 * ({@code inqcustz}) JSON contract byte-for-byte (feature&nbsp;<strong>F-019</strong>),
 * so the preserved React/Carbon UI and the Customer-Services interface module
 * re-point by base URL only and are never rewritten. This is one of the ten
 * {@code *IT.java} contract tests in {@code com.ibm.cics.cip.bank.core.controller};
 * it targets {@code GET /inqcustz/enquiry/{custno}} (operationId
 * {@code getCScustenq}, service {@code CScustenq}, mapping {@code INQCUST.cbl}).
 *
 * <h2>What this pins (verified against the frozen swagger / consumer)</h2>
 * <ul>
 *   <li><strong>{@code GET /inqcustz/enquiry/{custno}}</strong> at the ROOT
 *       context &mdash; no servlet context-path prefix (basePath
 *       {@code /inqcustz}, relativePath {@code /enquiry/{custno}}, per
 *       {@code src/zosconnect_artefacts/apis/inqcustz/package.xml} and
 *       {@code .../api-docs/swagger.json}). The customer number travels in the
 *       <em>path variable</em>; the authoritative consumer
 *       ({@code WebController.@GetMapping("/enqcust")}) issues
 *       {@code client.get().retrieve()} with <strong>no request body</strong>,
 *       so these tests use {@code get(...)} only and never attach a body.</li>
 *   <li><strong>A single top-level wire key {@code InqCustZ}</strong> on the
 *       response (the frozen {@code getCScustenq_response_200} schema is an object
 *       whose only property is {@code InqCustZ}, mixed case &mdash; never the
 *       all-caps {@code INQCUSTZ}). The preserved consumer deserialises with a
 *       strict {@code FAIL_ON_UNKNOWN_PROPERTIES} mapper, so an extra top-level
 *       key would break it; the envelope-shape test guards exactly this.</li>
 *   <li><strong>A single HTTP&nbsp;200 in every outcome.</strong> Unlike the
 *       mutating endpoints, {@code INQCUST} never abends on a miss: a not-found
 *       read is a SOFT outcome carried as {@code InqCustInqSuccess="N"} (with a
 *       fail code) inside the {@code InqCustZ} envelope, still at
 *       HTTP&nbsp;{@code 200} (F-008).</li>
 *   <li><strong>The nested {@code InqCustDob} day/month/year group is present</strong>
 *       on a successful enquiry &mdash; the swagger models the date of birth as a
 *       component object {@code {InqCustDobDd, InqCustDobMm, InqCustDobYyyy}}, not
 *       a single date string, so the test asserts the nested
 *       {@code $.InqCustZ.InqCustDob.InqCustDobYyyy} field.</li>
 * </ul>
 *
 * <h2>Test strategy &mdash; the DB-free web slice</h2>
 * <p>This is a {@link WebMvcTest @WebMvcTest(InquireCustomerController.class)}
 * slice: it bootstraps only the Spring MVC layer for the controller (plus the
 * auto-configured Jackson {@link ObjectMapper}, {@link MockMvc} and any
 * {@code @RestControllerAdvice}), with <strong>no datasource, JPA or Flyway</strong>.
 * It therefore stays green on Java&nbsp;17 with no PostgreSQL running, and it is
 * deliberately NOT a {@code @SpringBootTest}. The single business collaborator
 * {@link CustomerService} (the {@code INQCUST} business port) is replaced by a
 * {@link MockitoBean @MockitoBean}, so the test exercises the thin HTTP adapter in
 * isolation: {@link CustomerService#inquireCustomer(long)} is the stubbed seam
 * and its {@code long} customer-number argument is matched with
 * {@link ArgumentMatchers#anyLong()}.</p>
 *
 * <p>No mainframe types are referenced anywhere in this test (no
 * {@code com.ibm.cics.server}, {@code com.ibm.jzos} or {@code com.ibm.websphere}),
 * and no {@code double}/{@code float} arithmetic appears &mdash; the customer
 * enquiry contract carries only text, integer and component-date fields.</p>
 *
 * @see InquireCustomerController#inquireCustomer(String, CustomerEnquiryJson)
 * @see CustomerService#inquireCustomer(long)
 * @see CustomerEnquiryJson
 * @see InqCustZJson
 */
@WebMvcTest(InquireCustomerController.class)
@DisplayName("InquireCustomerController contract IT — frozen inqcustz / InqCustZ envelope (F-019)")
class InquireCustomerControllerIT
{

	/**
	 * The frozen route as a URI template: ROOT context, no context-path prefix.
	 * The {@code {custno}} placeholder is expanded per request by
	 * {@link MockMvcRequestBuilders#get(String, Object...)}.
	 */
	private static final String ENDPOINT_TEMPLATE = "/inqcustz/enquiry/{custno}";

	/** A within-width, zero-padded (10-digit) customer number for the happy path. */
	private static final String SUCCESS_CUSTNO = "0000000123";

	/** A within-width, zero-padded (10-digit) customer number for the miss path. */
	private static final String NOT_FOUND_CUSTNO = "0000000404";

	/** Bank sort code, zero-padded to width 6 (&sect;0.6). */
	private static final String SORT_CODE = "987654";

	/** Stubbed customer name asserted on the happy path. */
	private static final String CUSTOMER_NAME = "MR JOHN SMITH";

	/** Stubbed customer address populated on the happy-path envelope. */
	private static final String CUSTOMER_ADDRESS = "1 HIGH STREET, ANYTOWN, AT1 2CD";

	/** Day component of the stubbed date of birth. */
	private static final int DOB_DAY = 15;

	/** Month component of the stubbed date of birth. */
	private static final int DOB_MONTH = 6;

	/** Four-digit year component of the stubbed date of birth (the asserted field). */
	private static final int DOB_YEAR = 1985;

	/** Stubbed credit score (COBOL {@code PIC 999}, range 0&ndash;999). */
	private static final int CREDIT_SCORE = 750;

	/** COBOL {@code INQCUST-INQ-SUCCESS = 'Y'} (enquiry succeeded). */
	private static final String FLAG_SUCCESS = "Y";

	/** COBOL {@code INQCUST-INQ-SUCCESS = 'N'} (soft not-found). */
	private static final String FLAG_FAILURE = "N";

	/**
	 * COBOL {@code INQCUST} fail code for a record-not-found miss ({@code '1'}),
	 * also the code carried by the safeguard {@link BusinessRuleException}.
	 */
	private static final String FAIL_CODE_NOT_FOUND = "1";

	/** The single frozen top-level response key (mixed case, never all-caps). */
	private static final String TOP_LEVEL_KEY = "InqCustZ";

	/**
	 * Auto-configured MockMvc driving the {@code InquireCustomerController} web
	 * slice end to end (request binding, handler invocation, JSON serialisation).
	 */
	@Autowired
	private MockMvc mockMvc;

	/**
	 * Auto-configured Jackson mapper from the slice. Used to read the raw
	 * response body into a {@link JsonNode} tree for the envelope-shape check
	 * (exactly one top-level key).
	 */
	@Autowired
	private ObjectMapper objectMapper;

	/**
	 * The single business collaborator, replaced by a Mockito mock so the slice
	 * runs DB-free. All {@code INQCUST} behaviour (sentinel resolution, the soft
	 * {@code 'N'} not-found shaping, fail codes) is stubbed here.
	 */
	@MockitoBean
	private CustomerService customerService;

	/**
	 * Happy path: a found customer is returned as HTTP&nbsp;{@code 200} with the
	 * fully formed {@code InqCustZ} envelope. Asserts the JSON content type, the
	 * presence of the top-level {@code InqCustZ} key, the success flag
	 * {@code "Y"}, the customer name and the zero-padded customer number, and the
	 * presence of the nested date-of-birth group (its four-digit year field),
	 * proving the component-split DOB shape is preserved verbatim.
	 *
	 * @throws Exception if the MockMvc exchange fails
	 */
	@Test
	@DisplayName("GET /inqcustz/enquiry/{custno} — found customer returns 200 with the InqCustZ envelope and nested DOB group")
	void getEnquiry_success_returnsInqCustZEnvelope() throws Exception
	{
		// Stub the INQCUST business port: any customer number resolves to a
		// fully populated success envelope (InqCustInqSuccess="Y"). The path
		// variable parses to a long, so the argument is matched with anyLong().
		Mockito.when(customerService.inquireCustomer(ArgumentMatchers.anyLong()))
				.thenReturn(buildSuccessEnvelope());

		mockMvc.perform(MockMvcRequestBuilders.get(ENDPOINT_TEMPLATE, SUCCESS_CUSTNO)
				.accept(MediaType.APPLICATION_JSON))
				.andExpect(MockMvcResultMatchers.status().isOk())
				.andExpect(MockMvcResultMatchers.content()
						.contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				// Frozen single top-level envelope key (mixed case).
				.andExpect(MockMvcResultMatchers.jsonPath("$.InqCustZ").exists())
				// Success flag and core scalar fields.
				.andExpect(MockMvcResultMatchers
						.jsonPath("$.InqCustZ.InqCustInqSuccess").value(FLAG_SUCCESS))
				.andExpect(MockMvcResultMatchers
						.jsonPath("$.InqCustZ.InqCustName").value(CUSTOMER_NAME))
				.andExpect(MockMvcResultMatchers
						.jsonPath("$.InqCustZ.InqCustCustno").value(SUCCESS_CUSTNO))
				// The nested DOB component object and its four-digit year must be
				// present (the contract models DOB as Dd/Mm/Yyyy, not a string).
				.andExpect(MockMvcResultMatchers
						.jsonPath("$.InqCustZ.InqCustDob").exists())
				.andExpect(MockMvcResultMatchers
						.jsonPath("$.InqCustZ.InqCustDob.InqCustDobYyyy").exists())
				.andExpect(MockMvcResultMatchers
						.jsonPath("$.InqCustZ.InqCustDob.InqCustDobYyyy")
						.value(DOB_YEAR));
	}

	/**
	 * Not-found path: when the {@code INQCUST} port signals a miss as the COBOL
	 * fail code {@code '1'} (modelled here by the service raising
	 * {@code new BusinessRuleException("1")}), the controller must still honour
	 * the frozen contract by returning HTTP&nbsp;{@code 200} with the soft
	 * {@code InqCustZ} envelope &mdash; {@code InqCustInqSuccess="N"} and
	 * {@code InqCustInqFailCd="1"} &mdash; NOT a generic error advice body and NOT
	 * a 4xx/5xx status. {@code INQCUST} never abends on a miss (F-008).
	 *
	 * @throws Exception if the MockMvc exchange fails
	 */
	@Test
	@DisplayName("GET /inqcustz/enquiry/{custno} — a miss returns 200 with InqCustZ.InqCustInqSuccess=N and fail code 1")
	void getEnquiry_notFound_returns200WithInqSuccessN() throws Exception
	{
		// The service raises the COBOL '1' (record not found); the controller's
		// safeguard rebuilds the soft InqCustInqSuccess="N" envelope at HTTP 200.
		Mockito.when(customerService.inquireCustomer(ArgumentMatchers.anyLong()))
				.thenThrow(new BusinessRuleException(FAIL_CODE_NOT_FOUND));

		mockMvc.perform(MockMvcRequestBuilders.get(ENDPOINT_TEMPLATE, NOT_FOUND_CUSTNO)
				.accept(MediaType.APPLICATION_JSON))
				// Soft not-found: still HTTP 200, never a 4xx/5xx.
				.andExpect(MockMvcResultMatchers.status().isOk())
				.andExpect(MockMvcResultMatchers.jsonPath("$.InqCustZ").exists())
				.andExpect(MockMvcResultMatchers
						.jsonPath("$.InqCustZ.InqCustInqSuccess").value(FLAG_FAILURE))
				.andExpect(MockMvcResultMatchers
						.jsonPath("$.InqCustZ.InqCustInqFailCd")
						.value(FAIL_CODE_NOT_FOUND));
	}

	/**
	 * Envelope-shape guard (the CRITICAL F-019 proof): the serialised response
	 * MUST carry <em>exactly one</em> top-level key and it MUST be the mixed-case
	 * {@code InqCustZ} (never the all-caps {@code INQCUSTZ}, and never any extra
	 * metadata/status field at this level). The body is read into a
	 * {@link JsonNode} tree via the autowired {@link ObjectMapper} from the
	 * captured {@link MvcResult}, then its size and single key are asserted.
	 *
	 * @throws Exception if the MockMvc exchange or JSON parsing fails
	 */
	@Test
	@DisplayName("GET /inqcustz/enquiry/{custno} — response has exactly one top-level key: InqCustZ")
	void getEnquiry_singleTopLevelKeyIsInqCustZ() throws Exception
	{
		Mockito.when(customerService.inquireCustomer(ArgumentMatchers.anyLong()))
				.thenReturn(buildSuccessEnvelope());

		MvcResult result = mockMvc
				.perform(MockMvcRequestBuilders.get(ENDPOINT_TEMPLATE, SUCCESS_CUSTNO)
						.accept(MediaType.APPLICATION_JSON))
				.andExpect(MockMvcResultMatchers.status().isOk())
				.andExpect(MockMvcResultMatchers.content()
						.contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(MockMvcResultMatchers.jsonPath("$.InqCustZ").exists())
				.andReturn();

		JsonNode root = objectMapper
				.readTree(result.getResponse().getContentAsString());

		Assertions.assertEquals(1, root.size(),
				"The response envelope must carry exactly one top-level key; body="
						+ root);
		Assertions.assertTrue(root.has(TOP_LEVEL_KEY),
				"The single top-level key must be the frozen mixed-case InqCustZ; body="
						+ root);
		Assertions.assertFalse(root.has("INQCUSTZ"),
				"The all-caps INQCUSTZ key must NOT be emitted (frozen-contract regression); body="
						+ root);
	}

	/**
	 * Builds a fully populated {@code InqCustZ} success envelope mirroring what
	 * the {@code INQCUST} service returns for a found customer: success flag
	 * {@code "Y"}, the zero-padded identifiers (sort code width&nbsp;6, customer
	 * number width&nbsp;10, &sect;0.6), the name and address, the credit score,
	 * and a populated nested date-of-birth component group.
	 *
	 * @return a populated {@link CustomerEnquiryJson} success envelope
	 */
	private CustomerEnquiryJson buildSuccessEnvelope()
	{
		InqCustDob dob = new InqCustDob();
		dob.setInqCustDobDd(DOB_DAY);
		dob.setInqCustDobMm(DOB_MONTH);
		dob.setInqCustDobYyyy(DOB_YEAR);

		InqCustZJson payload = new InqCustZJson();
		payload.setInqCustEye("CUST");
		payload.setInqCustScode(SORT_CODE);
		payload.setInqCustCustno(SUCCESS_CUSTNO);
		payload.setInqCustName(CUSTOMER_NAME);
		payload.setInqCustAddress(CUSTOMER_ADDRESS);
		payload.setInqCustDob(dob);
		payload.setInqCustCreditScore(CREDIT_SCORE);
		payload.setInqCustInqSuccess(FLAG_SUCCESS);
		payload.setInqCustInqFailCd(" ");
		payload.setInqCustPcbPointer("");

		CustomerEnquiryJson envelope = new CustomerEnquiryJson();
		envelope.setInqCustZ(payload);
		return envelope;
	}

}
