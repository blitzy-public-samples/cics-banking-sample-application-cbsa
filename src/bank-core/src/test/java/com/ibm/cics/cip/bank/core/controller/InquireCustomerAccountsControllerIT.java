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
import com.ibm.cics.cip.bank.core.dto.listaccounts.InqAccczJson;
import com.ibm.cics.cip.bank.core.dto.listaccounts.ListAccountsJson;
import com.ibm.cics.cip.bank.core.service.AccountService;

/**
 * Controller <em>contract</em> integration test for
 * {@link InquireCustomerAccountsController} &mdash; proof for the F-019
 * parameter-source finding (the swagger declares both the {@code {custno}} path
 * variable and an optional body) and the fixed-width path enforcement.
 *
 * <h2>What this pins</h2>
 * <ul>
 *   <li><strong>{@code GET /inqacccz/list/{custno}}</strong> (root context),
 *       single top-level wire key {@code InqAccZ}.</li>
 *   <li><strong>The swagger-declared request body is accepted but optional</strong>
 *       ({@code @RequestBody(required = false)}); the path variable stays
 *       authoritative.</li>
 *   <li><strong>The {@code {custno}} path variable is bound to the ten-digit
 *       contract width</strong> ({@code PIC 9(10)}): an eleven-digit value is
 *       rejected as HTTP&nbsp;400 and the service is never invoked.</li>
 * </ul>
 *
 * <p>DB-free {@link WebMvcTest @WebMvcTest(InquireCustomerAccountsController.class)}
 * slice with a {@link MockBean @MockitoBean} {@link AccountService}.</p>
 */
@WebMvcTest(InquireCustomerAccountsController.class)
@DisplayName("InquireCustomerAccountsController contract IT — GET /inqacccz/list/{custno}, InqAccZ envelope + optional body")
class InquireCustomerAccountsControllerIT
{

	/** Frozen endpoint base: root context, no context-path prefix. */
	private static final String ENDPOINT = "/inqacccz/list/";

	/** A within-width (&le; 10-digit) customer number used on the happy path. */
	private static final String VALID_CUSTNO = "1";

	/** An eleven-digit value: over the ten-digit contract width. */
	private static final String OVER_WIDTH_CUSTNO = "10000000000";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@MockitoBean
	private AccountService accountService;

	/**
	 * A valid customer number delegates to the service and returns the single-key
	 * {@code InqAccZ} envelope at HTTP&nbsp;200.
	 *
	 * @throws Exception if the MockMvc exchange or JSON parsing fails
	 */
	@Test
	@DisplayName("GET /inqacccz/list/{custno} — a valid custno delegates and returns the single-key InqAccZ envelope (200)")
	void getList_validCustno_delegatesAndReturnsInqAccZ() throws Exception
	{
		when(accountService.listAccountsByCustomer(anyLong()))
				.thenReturn(successEnvelope());

		MvcResult result = mockMvc
				.perform(get(ENDPOINT + VALID_CUSTNO)
						.accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(content()
						.contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.InqAccZ").exists())
				.andReturn();

		verify(accountService).listAccountsByCustomer(anyLong());

		JsonNode root = objectMapper
				.readTree(result.getResponse().getContentAsString());
		assertEquals(1, root.size(),
				"Response must carry exactly one top-level key; body=" + root);
		assertTrue(root.has("InqAccZ"),
				"The single top-level key must be InqAccZ; body=" + root);
	}

	/**
	 * The swagger-declared request body is accepted (but optional).
	 *
	 * @throws Exception if the MockMvc exchange or JSON serialisation fails
	 */
	@Test
	@DisplayName("GET /inqacccz/list/{custno} — an optional InqAccZ request body is accepted (swagger parameter source)")
	void getList_withOptionalBody_isAccepted() throws Exception
	{
		when(accountService.listAccountsByCustomer(anyLong()))
				.thenReturn(successEnvelope());

		String body = objectMapper.writeValueAsString(successEnvelope());

		mockMvc.perform(get(ENDPOINT + VALID_CUSTNO)
				.contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.APPLICATION_JSON)
				.content(body))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.InqAccZ").exists());
	}

	/**
	 * An over-width (eleven-digit) customer number is rejected as HTTP&nbsp;400
	 * and the service is never invoked.
	 *
	 * @throws Exception if the MockMvc exchange fails
	 */
	@Test
	@DisplayName("GET /inqacccz/list/10000000000 — an over-width (11-digit) custno returns HTTP 400")
	void getList_overWidthCustno_returns400() throws Exception
	{
		mockMvc.perform(get(ENDPOINT + OVER_WIDTH_CUSTNO)
				.accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isBadRequest());

		verify(accountService, never()).listAccountsByCustomer(anyLong());
	}

	/**
	 * Builds a populated success-response envelope.
	 *
	 * @return a success {@link ListAccountsJson} envelope
	 */
	private ListAccountsJson successEnvelope()
	{
		InqAccczJson inner = new InqAccczJson();
		inner.setCommSuccess("Y");
		ListAccountsJson envelope = new ListAccountsJson();
		envelope.setInqAcccz(inner);
		return envelope;
	}

}
