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
import com.ibm.cics.cip.bank.core.dto.accountenquiry.AccountEnquiryJson;
import com.ibm.cics.cip.bank.core.dto.accountenquiry.InqaccJson;
import com.ibm.cics.cip.bank.core.service.AccountService;

/**
 * Controller <em>contract</em> integration test for
 * {@link InquireAccountController} &mdash; proof for the F-019 parameter-source
 * finding (the swagger declares both the {@code {accno}} path variable and an
 * optional body) and the fixed-width path enforcement.
 *
 * <h2>What this pins</h2>
 * <ul>
 *   <li><strong>{@code GET /inqaccz/enquiry/{accno}}</strong> (root context),
 *       single top-level wire key {@code InqAcc}.</li>
 *   <li><strong>The swagger-declared request body is accepted but optional</strong>
 *       ({@code @RequestBody(required = false)}): both the no-body Java consumer
 *       and a caller sending the frozen {@code InqAcc} envelope are honoured; the
 *       path variable stays authoritative.</li>
 *   <li><strong>The {@code {accno}} path variable is bound to the eight-digit
 *       contract width</strong> ({@code PIC 9(8)}): a nine-digit value is
 *       rejected as HTTP&nbsp;400 and the service is never invoked.</li>
 * </ul>
 *
 * <p>DB-free {@link WebMvcTest @WebMvcTest(InquireAccountController.class)} slice
 * with a {@link MockBean @MockitoBean} {@link AccountService}.</p>
 */
@WebMvcTest(InquireAccountController.class)
@DisplayName("InquireAccountController contract IT — GET /inqaccz/enquiry/{accno}, InqAcc envelope + optional body")
class InquireAccountControllerIT
{

	/** Frozen endpoint base: root context, no context-path prefix. */
	private static final String ENDPOINT = "/inqaccz/enquiry/";

	/** A within-width (&le; 8-digit) account number used on the happy path. */
	private static final String VALID_ACCNO = "1";

	/** A nine-digit value: over the eight-digit contract width. */
	private static final String OVER_WIDTH_ACCNO = "123456789";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@MockitoBean
	private AccountService accountService;

	/**
	 * A valid account number delegates to the service and returns the single-key
	 * {@code InqAcc} envelope at HTTP&nbsp;200.
	 *
	 * @throws Exception if the MockMvc exchange or JSON parsing fails
	 */
	@Test
	@DisplayName("GET /inqaccz/enquiry/{accno} — a valid accno delegates and returns the single-key InqAcc envelope (200)")
	void getEnquiry_validAccno_delegatesAndReturnsInqAcc() throws Exception
	{
		when(accountService.inquireAccount(anyLong()))
				.thenReturn(successEnvelope());

		MvcResult result = mockMvc
				.perform(get(ENDPOINT + VALID_ACCNO)
						.accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(content()
						.contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.InqAcc").exists())
				.andReturn();

		verify(accountService).inquireAccount(anyLong());

		JsonNode root = objectMapper
				.readTree(result.getResponse().getContentAsString());
		assertEquals(1, root.size(),
				"Response must carry exactly one top-level key; body=" + root);
		assertTrue(root.has("InqAcc"),
				"The single top-level key must be InqAcc; body=" + root);
	}

	/**
	 * The swagger-declared request body is accepted (but optional). A caller
	 * sending the frozen {@code InqAcc} envelope alongside the path variable still
	 * gets the {@code InqAcc} response.
	 *
	 * @throws Exception if the MockMvc exchange or JSON serialisation fails
	 */
	@Test
	@DisplayName("GET /inqaccz/enquiry/{accno} — an optional InqAcc request body is accepted (swagger parameter source)")
	void getEnquiry_withOptionalBody_isAccepted() throws Exception
	{
		when(accountService.inquireAccount(anyLong()))
				.thenReturn(successEnvelope());

		String body = objectMapper.writeValueAsString(successEnvelope());

		mockMvc.perform(get(ENDPOINT + VALID_ACCNO)
				.contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.APPLICATION_JSON)
				.content(body))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.InqAcc").exists());
	}

	/**
	 * An over-width (nine-digit) account number is rejected as HTTP&nbsp;400 and
	 * the service is never invoked.
	 *
	 * @throws Exception if the MockMvc exchange fails
	 */
	@Test
	@DisplayName("GET /inqaccz/enquiry/123456789 — an over-width (9-digit) accno returns HTTP 400")
	void getEnquiry_overWidthAccno_returns400() throws Exception
	{
		mockMvc.perform(get(ENDPOINT + OVER_WIDTH_ACCNO)
				.accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isBadRequest());

		verify(accountService, never()).inquireAccount(anyLong());
	}

	/**
	 * Builds a populated success-response envelope (the mocked service's
	 * {@code 'Y'} outcome).
	 *
	 * @return a success {@link AccountEnquiryJson} envelope
	 */
	private AccountEnquiryJson successEnvelope()
	{
		InqaccJson inner = new InqaccJson();
		inner.setInqaccSuccess("Y");
		AccountEnquiryJson envelope = new AccountEnquiryJson();
		envelope.setInqaccCommarea(inner);
		return envelope;
	}

}
