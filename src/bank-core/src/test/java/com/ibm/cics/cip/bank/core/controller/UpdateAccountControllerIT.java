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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import com.ibm.cics.cip.bank.core.dto.updateaccount.UpdaccJson;
import com.ibm.cics.cip.bank.core.dto.updateaccount.UpdateAccountJson;
import com.ibm.cics.cip.bank.core.service.AccountService;

/**
 * Controller <em>contract / validation</em> integration test for
 * {@link UpdateAccountController} &mdash; proof for the F-021 cascaded
 * -validation finding (a {@code {}} or {@code {"UpdAcc":null}} body previously
 * reached {@code in.getCommCustno()} and threw {@code NullPointerException}
 * &rarr; HTTP&nbsp;500 instead of the required 400).
 *
 * <h2>What this pins</h2>
 * <ul>
 *   <li><strong>{@code PUT /updacc/update}</strong> (root context), single
 *       top-level wire key {@code UpdAcc}.</li>
 *   <li><strong>An empty body {@code {}}</strong> is rejected as HTTP&nbsp;400
 *       (the wrapper does not eager-initialise the nested envelope, so
 *       {@code @NotNull} fires), and the service is never invoked.</li>
 *   <li><strong>An explicit {@code {"UpdAcc":null}}</strong> is rejected as
 *       HTTP&nbsp;400 by the same {@code @NotNull}.</li>
 *   <li><strong>A contract-valid request delegates</strong> and the envelope
 *       round-trips at HTTP&nbsp;200 with the single {@code UpdAcc} key.</li>
 * </ul>
 *
 * <p>DB-free {@link WebMvcTest @WebMvcTest(UpdateAccountController.class)} slice
 * with a {@link MockBean @MockitoBean} {@link AccountService}.</p>
 */
@WebMvcTest(UpdateAccountController.class)
@DisplayName("UpdateAccountController contract IT — PUT /updacc/update, UpdAcc envelope + F-021 cascade")
class UpdateAccountControllerIT
{

	/** Frozen endpoint path: root context, no context-path prefix. */
	private static final String ENDPOINT = "/updacc/update";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@MockitoBean
	private AccountService accountService;

	/**
	 * An empty body must be rejected as HTTP&nbsp;400 (no eager-init &rarr;
	 * {@code @NotNull} on the nested envelope fires), never reaching the mapping
	 * as a {@code NullPointerException}.
	 *
	 * @throws Exception if the MockMvc exchange fails
	 */
	@Test
	@DisplayName("PUT /updacc/update — an empty body ({}) returns HTTP 400")
	void putUpdate_emptyBody_returns400() throws Exception
	{
		mockMvc.perform(put(ENDPOINT)
				.contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.APPLICATION_JSON)
				.content("{}"))
				.andExpect(status().isBadRequest());

		verify(accountService, never()).updateAccount(any());
	}

	/**
	 * An explicit {@code null} nested {@code UpdAcc} must be rejected as
	 * HTTP&nbsp;400.
	 *
	 * @throws Exception if the MockMvc exchange fails
	 */
	@Test
	@DisplayName("PUT /updacc/update — a null nested UpdAcc ({\"UpdAcc\":null}) returns HTTP 400")
	void putUpdate_nullNestedUpdAcc_returns400() throws Exception
	{
		mockMvc.perform(put(ENDPOINT)
				.contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.APPLICATION_JSON)
				.content("{\"UpdAcc\":null}"))
				.andExpect(status().isBadRequest());

		verify(accountService, never()).updateAccount(any());
	}

	/**
	 * A contract-valid request delegates to the service and returns the
	 * single-key {@code UpdAcc} envelope at HTTP&nbsp;200.
	 *
	 * @throws Exception if the MockMvc exchange or JSON parsing fails
	 */
	@Test
	@DisplayName("PUT /updacc/update — a valid UpdAcc request delegates and returns the single-key UpdAcc envelope (200)")
	void putUpdate_validRequest_delegatesAndReturnsUpdAcc() throws Exception
	{
		when(accountService.updateAccount(any())).thenReturn(responseEnvelope());

		MvcResult result = mockMvc
				.perform(put(ENDPOINT)
						.contentType(MediaType.APPLICATION_JSON)
						.accept(MediaType.APPLICATION_JSON)
						.content("{\"UpdAcc\":{}}"))
				.andExpect(status().isOk())
				.andExpect(content()
						.contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.UpdAcc").exists())
				.andReturn();

		verify(accountService).updateAccount(any());

		JsonNode root = objectMapper
				.readTree(result.getResponse().getContentAsString());
		assertEquals(1, root.size(),
				"Response must carry exactly one top-level key; body=" + root);
		assertTrue(root.has("UpdAcc"),
				"The single top-level key must be UpdAcc; body=" + root);
	}

	/**
	 * Builds the response envelope the mocked service returns (nested
	 * {@code UpdAcc} populated so the single-key wrapper serialises).
	 *
	 * @return an {@link UpdateAccountJson} response envelope
	 */
	private UpdateAccountJson responseEnvelope()
	{
		UpdateAccountJson envelope = new UpdateAccountJson();
		envelope.setUpdAcc(new UpdaccJson());
		return envelope;
	}

}
