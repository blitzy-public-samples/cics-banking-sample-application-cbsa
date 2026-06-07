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
import com.ibm.cics.cip.bank.core.dto.updatecustomer.UpdateCustomerJson;
import com.ibm.cics.cip.bank.core.dto.updatecustomer.UpdcustJson;
import com.ibm.cics.cip.bank.core.service.CustomerService;

/**
 * Controller <em>contract / validation</em> integration test for
 * {@link UpdateCustomerController} &mdash; proof for the F-021 cascaded
 * -validation finding (the {@code @Valid} on the wrapper parameter must cascade
 * into the nested {@code UpdCust} envelope so that null and over-length payloads
 * are rejected as HTTP&nbsp;400).
 *
 * <h2>What this pins</h2>
 * <ul>
 *   <li><strong>{@code PUT /updcust/update}</strong> (root context), single
 *       top-level wire key {@code UpdCust}.</li>
 *   <li><strong>An explicit {@code {"UpdCust":null}}</strong> is rejected as
 *       HTTP&nbsp;400 by {@code @NotNull}; the service is never invoked.</li>
 *   <li><strong>An over-length nested field</strong> (a {@code CommName} beyond
 *       the {@code PIC X(60)} contract width) is rejected as HTTP&nbsp;400 by the
 *       {@code @Valid}+{@code @Size(max=60)} cascade; the service is never
 *       invoked.</li>
 *   <li><strong>A contract-valid request delegates</strong> and the envelope
 *       round-trips at HTTP&nbsp;200 with the single {@code UpdCust} key.</li>
 * </ul>
 *
 * <p>DB-free {@link WebMvcTest @WebMvcTest(UpdateCustomerController.class)} slice
 * with a {@link MockBean @MockitoBean} {@link CustomerService}.</p>
 */
@WebMvcTest(UpdateCustomerController.class)
@DisplayName("UpdateCustomerController contract IT — PUT /updcust/update, UpdCust envelope + F-021 cascade")
class UpdateCustomerControllerIT
{

	/** Frozen endpoint path: root context, no context-path prefix. */
	private static final String ENDPOINT = "/updcust/update";

	/** COBOL {@code CUSTOMER-NAME} / {@code COMM-NAME} fixed width. */
	private static final int NAME_WIDTH = 60;

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@MockitoBean
	private CustomerService customerService;

	/**
	 * An explicit {@code null} nested {@code UpdCust} must be rejected as
	 * HTTP&nbsp;400.
	 *
	 * @throws Exception if the MockMvc exchange fails
	 */
	@Test
	@DisplayName("PUT /updcust/update — a null nested UpdCust ({\"UpdCust\":null}) returns HTTP 400")
	void putUpdate_nullNestedUpdCust_returns400() throws Exception
	{
		mockMvc.perform(put(ENDPOINT)
				.contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.APPLICATION_JSON)
				.content("{\"UpdCust\":null}"))
				.andExpect(status().isBadRequest());

		verify(customerService, never()).updateCustomer(any());
	}

	/**
	 * An over-length {@code CommName} (one character beyond {@code PIC X(60)})
	 * must be rejected as HTTP&nbsp;400 by the cascaded {@code @Size(max=60)},
	 * and the service must never be invoked. The request is built via setters and
	 * serialised with the autowired mapper so the wire-field names are exact.
	 *
	 * @throws Exception if the MockMvc exchange or JSON serialisation fails
	 */
	@Test
	@DisplayName("PUT /updcust/update — an over-length CommName (61 chars) returns HTTP 400")
	void putUpdate_overLengthName_returns400() throws Exception
	{
		UpdcustJson inner = new UpdcustJson();
		inner.setCommName("A".repeat(NAME_WIDTH + 1));
		UpdateCustomerJson request = new UpdateCustomerJson();
		request.setUpdcust(inner);

		mockMvc.perform(put(ENDPOINT)
				.contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isBadRequest());

		verify(customerService, never()).updateCustomer(any());
	}

	/**
	 * A contract-valid request delegates to the service and returns the
	 * single-key {@code UpdCust} envelope at HTTP&nbsp;200.
	 *
	 * @throws Exception if the MockMvc exchange or JSON parsing fails
	 */
	@Test
	@DisplayName("PUT /updcust/update — a valid UpdCust request delegates and returns the single-key UpdCust envelope (200)")
	void putUpdate_validRequest_delegatesAndReturnsUpdCust() throws Exception
	{
		when(customerService.updateCustomer(any()))
				.thenReturn(responseEnvelope());

		MvcResult result = mockMvc
				.perform(put(ENDPOINT)
						.contentType(MediaType.APPLICATION_JSON)
						.accept(MediaType.APPLICATION_JSON)
						.content("{\"UpdCust\":{}}"))
				.andExpect(status().isOk())
				.andExpect(content()
						.contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.UpdCust").exists())
				.andReturn();

		verify(customerService).updateCustomer(any());

		JsonNode root = objectMapper
				.readTree(result.getResponse().getContentAsString());
		assertEquals(1, root.size(),
				"Response must carry exactly one top-level key; body=" + root);
		assertTrue(root.has("UpdCust"),
				"The single top-level key must be UpdCust; body=" + root);
	}

	/**
	 * Builds the response envelope the mocked service returns (nested
	 * {@code UpdCust} populated so the single-key wrapper serialises).
	 *
	 * @return an {@link UpdateCustomerJson} response envelope
	 */
	private UpdateCustomerJson responseEnvelope()
	{
		UpdateCustomerJson envelope = new UpdateCustomerJson();
		envelope.setUpdcust(new UpdcustJson());
		return envelope;
	}

}
