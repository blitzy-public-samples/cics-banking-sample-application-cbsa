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
import com.ibm.cics.cip.bank.core.dto.createcustomer.CreateCustomerJson;
import com.ibm.cics.cip.bank.core.dto.createcustomer.CrecustJson;
import com.ibm.cics.cip.bank.core.service.CustomerService;

/**
 * Controller <em>contract / validation</em> integration test for
 * {@link CreateCustomerController} &mdash; proof for the F-021 cascaded
 * -validation finding.
 *
 * <h2>What this pins</h2>
 * <ul>
 *   <li><strong>{@code POST /crecust/insert}</strong> (root context), single
 *       top-level wire key {@code CreCust}.</li>
 *   <li><strong>Cascaded Bean Validation (F-021).</strong> A request with an
 *       explicit {@code null} nested envelope ({@code {"CreCust": null}}) is
 *       rejected with HTTP&nbsp;400 by the {@code @Valid}+{@code @NotNull}
 *       cascade; the service is never invoked.</li>
 *   <li><strong>A contract-valid request delegates</strong> and the success
 *       envelope round-trips at HTTP&nbsp;200 with the single {@code CreCust}
 *       key.</li>
 * </ul>
 *
 * <p>DB-free {@link WebMvcTest @WebMvcTest(CreateCustomerController.class)} slice
 * with a {@link MockBean @MockitoBean} {@link CustomerService}.</p>
 */
@WebMvcTest(CreateCustomerController.class)
@DisplayName("CreateCustomerController contract IT — POST /crecust/insert, CreCust envelope + F-021 cascade")
class CreateCustomerControllerIT
{

	/** Frozen endpoint path: root context, no context-path prefix. */
	private static final String ENDPOINT = "/crecust/insert";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@MockitoBean
	private CustomerService customerService;

	/**
	 * F-021 cascade: an explicit {@code null} nested {@code CreCust} is rejected
	 * as HTTP&nbsp;400 and the service is never invoked.
	 *
	 * @throws Exception if the MockMvc exchange fails
	 */
	@Test
	@DisplayName("POST /crecust/insert — a null nested CreCust ({\"CreCust\":null}) returns HTTP 400")
	void postInsert_nullNestedCreCust_returns400() throws Exception
	{
		mockMvc.perform(post(ENDPOINT)
				.contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.APPLICATION_JSON)
				.content("{\"CreCust\":null}"))
				.andExpect(status().isBadRequest());

		verify(customerService, never()).createCustomer(any());
	}

	/**
	 * A contract-valid request delegates to the service and returns the
	 * single-key {@code CreCust} success envelope at HTTP&nbsp;200.
	 *
	 * @throws Exception if the MockMvc exchange or JSON parsing fails
	 */
	@Test
	@DisplayName("POST /crecust/insert — a valid CreCust request delegates and returns the single-key CreCust envelope (200)")
	void postInsert_validRequest_delegatesAndReturnsCreCust() throws Exception
	{
		when(customerService.createCustomer(any()))
				.thenReturn(successEnvelope());

		MvcResult result = mockMvc
				.perform(post(ENDPOINT)
						.contentType(MediaType.APPLICATION_JSON)
						.accept(MediaType.APPLICATION_JSON)
						.content("{\"CreCust\":{}}"))
				.andExpect(status().isOk())
				.andExpect(content()
						.contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.CreCust").exists())
				.andReturn();

		verify(customerService).createCustomer(any());

		JsonNode root = objectMapper
				.readTree(result.getResponse().getContentAsString());
		assertEquals(1, root.size(),
				"Response must carry exactly one top-level key; body=" + root);
		assertTrue(root.has("CreCust"),
				"The single top-level key must be CreCust; body=" + root);
	}

	/**
	 * Builds the populated success-response envelope the mocked service returns.
	 *
	 * @return a success {@link CreateCustomerJson} envelope
	 */
	private CreateCustomerJson successEnvelope()
	{
		CrecustJson inner = new CrecustJson();
		inner.setCommSuccess("Y");
		CreateCustomerJson envelope = new CreateCustomerJson();
		envelope.setCreCust(inner);
		return envelope;
	}

}
