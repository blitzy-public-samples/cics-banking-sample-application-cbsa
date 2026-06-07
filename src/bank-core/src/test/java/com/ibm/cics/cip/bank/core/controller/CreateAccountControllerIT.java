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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibm.cics.cip.bank.core.dto.createaccount.CreaccJson;
import com.ibm.cics.cip.bank.core.dto.createaccount.CreateAccountJson;
import com.ibm.cics.cip.bank.core.service.AccountService;

/**
 * Controller <em>contract / validation</em> integration test for
 * {@link CreateAccountController} &mdash; proof for the F-021 cascaded-validation
 * finding raised at this checkpoint.
 *
 * <h2>What this pins</h2>
 * <ul>
 *   <li><strong>{@code POST /creacc/insert}</strong> (root context), consuming
 *       and producing {@code application/json}, single top-level wire key
 *       {@code CreAcc}.</li>
 *   <li><strong>Cascaded Bean Validation (F-021).</strong> A request whose
 *       wrapper carries an explicit {@code null} nested envelope
 *       ({@code {"CreAcc": null}}) is rejected with HTTP&nbsp;400 by the
 *       {@code @Valid}+{@code @NotNull} cascade, rather than flowing into the
 *       mapping as a {@code NullPointerException} (HTTP&nbsp;500) or a silent
 *       business failure. The service is never invoked.</li>
 *   <li><strong>A contract-valid request delegates to the service</strong> and
 *       the success envelope round-trips at HTTP&nbsp;200 with the single
 *       {@code CreAcc} key.</li>
 * </ul>
 *
 * <p>DB-free {@link WebMvcTest @WebMvcTest(CreateAccountController.class)} slice
 * with a {@link MockBean @MockitoBean} {@link AccountService}; no datasource, JPA or
 * Flyway is started.</p>
 */
@WebMvcTest(CreateAccountController.class)
@DisplayName("CreateAccountController contract IT — POST /creacc/insert, CreAcc envelope + F-021 cascade")
class CreateAccountControllerIT
{

	/** Frozen endpoint path: root context, no context-path prefix. */
	private static final String ENDPOINT = "/creacc/insert";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@MockitoBean
	private AccountService accountService;

	/**
	 * F-021 cascade: an explicit {@code null} nested {@code CreAcc} is rejected
	 * as HTTP&nbsp;400 and the service is never invoked.
	 *
	 * @throws Exception if the MockMvc exchange fails
	 */
	@Test
	@DisplayName("POST /creacc/insert — a null nested CreAcc ({\"CreAcc\":null}) returns HTTP 400")
	void postInsert_nullNestedCreAcc_returns400() throws Exception
	{
		mockMvc.perform(post(ENDPOINT)
				.contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.APPLICATION_JSON)
				.content("{\"CreAcc\":null}"))
				.andExpect(status().isBadRequest());

		verify(accountService, never()).createAccount(any());
	}

	/**
	 * A contract-valid request (a present, format-valid {@code CreAcc} envelope)
	 * delegates to the service and returns the single-key {@code CreAcc} success
	 * envelope at HTTP&nbsp;200.
	 *
	 * @throws Exception if the MockMvc exchange or JSON parsing fails
	 */
	@Test
	@DisplayName("POST /creacc/insert — a valid CreAcc request delegates and returns the single-key CreAcc envelope (200)")
	void postInsert_validRequest_delegatesAndReturnsCreAcc() throws Exception
	{
		when(accountService.createAccount(any())).thenReturn(successEnvelope());

		MvcResult result = mockMvc
				.perform(post(ENDPOINT)
						.contentType(MediaType.APPLICATION_JSON)
						.accept(MediaType.APPLICATION_JSON)
						.content("{\"CreAcc\":{}}"))
				.andExpect(status().isOk())
				.andExpect(content()
						.contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.CreAcc").exists())
				.andReturn();

		verify(accountService).createAccount(any());

		JsonNode root = objectMapper
				.readTree(result.getResponse().getContentAsString());
		assertEquals(1, root.size(),
				"Response must carry exactly one top-level key; body=" + root);
		assertTrue(root.has("CreAcc"),
				"The single top-level key must be CreAcc; body=" + root);
	}

	/**
	 * Builds the populated success-response envelope the mocked service returns.
	 *
	 * @return a success {@link CreateAccountJson} envelope
	 */
	private CreateAccountJson successEnvelope()
	{
		CreaccJson inner = new CreaccJson();
		inner.setCommSuccess("Y");
		CreateAccountJson envelope = new CreateAccountJson();
		envelope.setCreAcc(inner);
		return envelope;
	}

}
