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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibm.cics.cip.bank.core.dto.deletecustomer.DelcusJson;
import com.ibm.cics.cip.bank.core.dto.deletecustomer.DeleteCustomerJson;
import com.ibm.cics.cip.bank.core.service.CustomerService;

/**
 * Controller <em>contract</em> integration test for
 * {@link DeleteCustomerController} &mdash; proof for the fixed-width customer
 * -number parse finding (the controller now uses
 * {@code BankFormat.parseCustomerNumber} instead of {@code Long.parseLong}) and
 * the F-019 optional-body parameter source.
 *
 * <h2>What this pins</h2>
 * <ul>
 *   <li><strong>{@code DELETE /delcus/remove/{custno}}</strong> (root context),
 *       single top-level wire key {@code DelCus}, returned at HTTP&nbsp;200 with
 *       a body.</li>
 *   <li><strong>The {@code {custno}} path variable is bound to the ten-digit
 *       contract width</strong> ({@code PIC 9(10)}): an eleven-digit value
 *       (inside {@code long} range, so the old {@code Long.parseLong} would have
 *       accepted it) is rejected as HTTP&nbsp;400 and the service is never
 *       invoked.</li>
 *   <li><strong>The swagger-declared {@code DelCus} request body is accepted but
 *       optional</strong> ({@code @RequestBody(required = false)}); the path
 *       variable stays authoritative.</li>
 * </ul>
 *
 * <p>DB-free {@link WebMvcTest @WebMvcTest(DeleteCustomerController.class)} slice
 * with a {@link MockBean @MockitoBean} {@link CustomerService}.</p>
 */
@WebMvcTest(DeleteCustomerController.class)
@DisplayName("DeleteCustomerController contract IT — DELETE /delcus/remove/{custno}, DelCus envelope + fixed-width custno")
class DeleteCustomerControllerIT
{

	/** Frozen endpoint base: root context, no context-path prefix. */
	private static final String ENDPOINT = "/delcus/remove/";

	/** A within-width (&le; 10-digit) customer number used on the happy path. */
	private static final String VALID_CUSTNO = "1";

	/** An eleven-digit value: inside {@code long} range, but over the width. */
	private static final String OVER_WIDTH_CUSTNO = "10000000000";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@MockitoBean
	private CustomerService customerService;

	/**
	 * The fixed-width parse finding: an eleven-digit customer number (inside the
	 * {@code long} range) must be rejected as HTTP&nbsp;400, and the service must
	 * never be invoked.
	 *
	 * @throws Exception if the MockMvc exchange fails
	 */
	@Test
	@DisplayName("DELETE /delcus/remove/10000000000 — an over-width (11-digit) custno is rejected as HTTP 400")
	void deleteRemove_overWidthCustno_returns400() throws Exception
	{
		mockMvc.perform(delete(ENDPOINT + OVER_WIDTH_CUSTNO)
				.accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isBadRequest());

		verify(customerService, never()).deleteCustomer(anyLong());
	}

	/**
	 * A valid customer number delegates to the service and returns the single-key
	 * {@code DelCus} envelope at HTTP&nbsp;200.
	 *
	 * @throws Exception if the MockMvc exchange or JSON parsing fails
	 */
	@Test
	@DisplayName("DELETE /delcus/remove/{custno} — a valid custno delegates and returns the single-key DelCus envelope (200)")
	void deleteRemove_validCustno_delegatesAndReturnsDelCus() throws Exception
	{
		when(customerService.deleteCustomer(anyLong()))
				.thenReturn(responseEnvelope());

		MvcResult result = mockMvc
				.perform(delete(ENDPOINT + VALID_CUSTNO)
						.accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(content()
						.contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.DelCus").exists())
				.andReturn();

		verify(customerService).deleteCustomer(anyLong());

		JsonNode root = objectMapper
				.readTree(result.getResponse().getContentAsString());
		assertEquals(1, root.size(),
				"Response must carry exactly one top-level key; body=" + root);
		assertTrue(root.has("DelCus"),
				"The single top-level key must be DelCus; body=" + root);
	}

	/**
	 * The swagger-declared request body is accepted (but optional).
	 *
	 * @throws Exception if the MockMvc exchange or JSON serialisation fails
	 */
	@Test
	@DisplayName("DELETE /delcus/remove/{custno} — an optional DelCus request body is accepted (swagger parameter source)")
	void deleteRemove_withOptionalBody_isAccepted() throws Exception
	{
		when(customerService.deleteCustomer(anyLong()))
				.thenReturn(responseEnvelope());

		String body = objectMapper.writeValueAsString(responseEnvelope());

		mockMvc.perform(delete(ENDPOINT + VALID_CUSTNO)
				.contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.APPLICATION_JSON)
				.content(body))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.DelCus").exists());
	}

	/**
	 * Builds the response envelope the mocked service returns (nested
	 * {@code DelCus} populated so the single-key wrapper serialises).
	 *
	 * @return a {@link DeleteCustomerJson} response envelope
	 */
	private DeleteCustomerJson responseEnvelope()
	{
		DeleteCustomerJson envelope = new DeleteCustomerJson();
		envelope.setDelCus(new DelcusJson());
		return envelope;
	}

}
