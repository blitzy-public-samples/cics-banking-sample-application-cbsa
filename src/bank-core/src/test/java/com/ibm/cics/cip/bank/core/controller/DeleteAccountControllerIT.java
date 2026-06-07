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
import com.ibm.cics.cip.bank.core.dto.deleteaccount.DelaccJson;
import com.ibm.cics.cip.bank.core.dto.deleteaccount.DeleteAccountJson;
import com.ibm.cics.cip.bank.core.service.AccountService;

/**
 * Controller <em>contract</em> integration test for
 * {@link DeleteAccountController} &mdash; proof for the F-019 parameter-source
 * finding (the swagger declares both the {@code {accno}} path variable and an
 * optional body) and the fixed-width path enforcement.
 *
 * <h2>What this pins</h2>
 * <ul>
 *   <li><strong>{@code DELETE /delacc/remove/{accno}}</strong> (root context),
 *       single top-level wire key {@code DelAcc}, returned at HTTP&nbsp;200 with
 *       a body.</li>
 *   <li><strong>The swagger-declared request body is accepted but optional</strong>
 *       ({@code @RequestBody(required = false)}); the path variable stays
 *       authoritative.</li>
 *   <li><strong>The {@code {accno}} path variable is bound to the eight-digit
 *       contract width</strong>: a nine-digit value is rejected as HTTP&nbsp;400
 *       and the service is never invoked.</li>
 * </ul>
 *
 * <p>DB-free {@link WebMvcTest @WebMvcTest(DeleteAccountController.class)} slice
 * with a {@link MockBean @MockitoBean} {@link AccountService}.</p>
 */
@WebMvcTest(DeleteAccountController.class)
@DisplayName("DeleteAccountController contract IT — DELETE /delacc/remove/{accno}, DelAcc envelope + optional body")
class DeleteAccountControllerIT
{

	/** Frozen endpoint base: root context, no context-path prefix. */
	private static final String ENDPOINT = "/delacc/remove/";

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
	 * {@code DelAcc} envelope at HTTP&nbsp;200.
	 *
	 * @throws Exception if the MockMvc exchange or JSON parsing fails
	 */
	@Test
	@DisplayName("DELETE /delacc/remove/{accno} — a valid accno delegates and returns the single-key DelAcc envelope (200)")
	void deleteRemove_validAccno_delegatesAndReturnsDelAcc() throws Exception
	{
		when(accountService.deleteAccount(anyLong()))
				.thenReturn(responseEnvelope());

		MvcResult result = mockMvc
				.perform(delete(ENDPOINT + VALID_ACCNO)
						.accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(content()
						.contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.DelAcc").exists())
				.andReturn();

		verify(accountService).deleteAccount(anyLong());

		JsonNode root = objectMapper
				.readTree(result.getResponse().getContentAsString());
		assertEquals(1, root.size(),
				"Response must carry exactly one top-level key; body=" + root);
		assertTrue(root.has("DelAcc"),
				"The single top-level key must be DelAcc; body=" + root);
	}

	/**
	 * The swagger-declared request body is accepted (but optional).
	 *
	 * @throws Exception if the MockMvc exchange or JSON serialisation fails
	 */
	@Test
	@DisplayName("DELETE /delacc/remove/{accno} — an optional DelAcc request body is accepted (swagger parameter source)")
	void deleteRemove_withOptionalBody_isAccepted() throws Exception
	{
		when(accountService.deleteAccount(anyLong()))
				.thenReturn(responseEnvelope());

		String body = objectMapper.writeValueAsString(responseEnvelope());

		mockMvc.perform(delete(ENDPOINT + VALID_ACCNO)
				.contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.APPLICATION_JSON)
				.content(body))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.DelAcc").exists());
	}

	/**
	 * An over-width (nine-digit) account number is rejected as HTTP&nbsp;400 and
	 * the service is never invoked.
	 *
	 * @throws Exception if the MockMvc exchange fails
	 */
	@Test
	@DisplayName("DELETE /delacc/remove/123456789 — an over-width (9-digit) accno returns HTTP 400")
	void deleteRemove_overWidthAccno_returns400() throws Exception
	{
		mockMvc.perform(delete(ENDPOINT + OVER_WIDTH_ACCNO)
				.accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isBadRequest());

		verify(accountService, never()).deleteAccount(anyLong());
	}

	/**
	 * Builds the response envelope the mocked service returns (nested
	 * {@code DelAcc} populated so the single-key wrapper serialises).
	 *
	 * @return a {@link DeleteAccountJson} response envelope
	 */
	private DeleteAccountJson responseEnvelope()
	{
		DeleteAccountJson envelope = new DeleteAccountJson();
		envelope.setDelAcc(new DelaccJson());
		return envelope;
	}

}
