/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.core.controller;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibm.cics.cip.bank.core.dto.transfer.TransferJson;
import com.ibm.cics.cip.bank.core.entity.Account;
import com.ibm.cics.cip.bank.core.entity.AccountId;
import com.ibm.cics.cip.bank.core.exception.BusinessRuleException;
import com.ibm.cics.cip.bank.core.service.TransferService;
import com.ibm.cics.cip.bank.core.service.TransferService.TransferResult;

/**
 * Web-slice integration test for {@link TransferController} &mdash; the thin MVC
 * adapter over {@link TransferService} (which reproduces {@code XFRFUN.cbl},
 * feature&nbsp;F-016) exposing {@code PUT /transfer}.
 *
 * <p><strong>Not a frozen z/OS Connect contract.</strong> {@code XFRFUN} is one
 * of the thirteen business programs but is <em>not</em> among the ten frozen
 * z/OS Connect endpoints; {@code /transfer} is a purpose-built internal endpoint
 * consumed by the {@code webui} adapter, so this is a flat (single-level)
 * envelope rather than one of the wrapped {@code *IT} frozen-contract envelopes.
 * The transfer <em>business</em> behaviour (lock ordering, deadlock retry,
 * dual-balance movement and the fail codes {@code 4}/{@code 1}/{@code 2}/{@code
 * 3}/{@code SAME}) is covered by {@code TransferServiceTest}; this slice pins the
 * controller's web contract: method/path, the success/failure flag mapping, the
 * always-HTTP-200 outcome convention, and scale-2 balance serialisation.</p>
 *
 * <h2>Test strategy &mdash; DB-free web slice</h2>
 * <p>This is a {@link WebMvcTest @WebMvcTest(TransferController.class)} slice: it
 * loads only the {@code TransferController} MVC layer (plus the module's
 * {@code @RestControllerAdvice}) and replaces the business
 * {@link TransferService} with a {@link MockitoBean @MockitoBean}. No datasource,
 * JPA or Flyway is started, so the test is green on Java&nbsp;17 without a running
 * PostgreSQL instance; {@code @SpringBootTest} is deliberately <em>not</em> used.
 * The class never references {@code com.ibm.cics.server} (JCICS),
 * {@code com.ibm.jzos} or {@code com.ibm.websphere}.</p>
 *
 * @see TransferController
 * @see TransferService#transfer(long, long, java.math.BigDecimal)
 */
@WebMvcTest(TransferController.class)
@DisplayName("TransferController web-slice IT — PUT /transfer, flat success/failure envelope, always HTTP 200")
class TransferControllerIT
{

	/** Internal (non-frozen) route exposed for the webui adapter. */
	private static final String ENDPOINT = "/transfer";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@MockitoBean
	private TransferService transferService;

	/**
	 * Builds a request envelope.
	 *
	 * @param from   the source account number
	 * @param to     the target account number
	 * @param amount the transfer amount
	 * @return the populated {@link TransferJson}
	 */
	private static TransferJson request(long from, long to, BigDecimal amount)
	{
		TransferJson request = new TransferJson();
		request.setFromAccount(from);
		request.setToAccount(to);
		request.setAmount(amount);
		return request;
	}

	/**
	 * Builds an {@link Account} with the given key and balances.
	 *
	 * @param accountNumber the eight-digit account number
	 * @param available     the available balance after the transfer
	 * @param actual        the actual balance after the transfer
	 * @return the populated account
	 */
	private static Account account(String accountNumber, String available,
			String actual)
	{
		Account account = new Account();
		account.setId(new AccountId("987654", accountNumber));
		account.setAvailableBalance(new BigDecimal(available));
		account.setActualBalance(new BigDecimal(actual));
		return account;
	}

	/**
	 * Happy path: a successful transfer maps to a flat envelope at HTTP&nbsp;200
	 * with {@code success="Y"}, an empty {@code failCode}, both account numbers,
	 * and all four post-transfer balances serialised at scale&nbsp;2 (the
	 * money-fidelity rule &mdash; the available and actual balances are
	 * independent and each reaches the wire with exactly two decimal places).
	 *
	 * @throws Exception if the MockMvc exchange fails
	 */
	@Test
	@DisplayName("PUT /transfer — success returns 200, success=Y, both account numbers, scale-2 dual balances")
	void transfer_success_returns200WithBothBalances() throws Exception
	{
		when(transferService.transfer(anyLong(), anyLong(),
				any(BigDecimal.class)))
				.thenReturn(new TransferResult(
						account("00000001", "450.00", "430.00"),
						account("00000002", "150.00", "140.00")));

		MvcResult result = mockMvc
				.perform(put(ENDPOINT)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(
								request(1L, 2L, new BigDecimal("200.00"))))
						.accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(content()
						.contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.success").value("Y"))
				.andExpect(jsonPath("$.failCode").value(""))
				.andExpect(jsonPath("$.fromAccountNumber").value("00000001"))
				.andExpect(jsonPath("$.toAccountNumber").value("00000002"))
				.andReturn();

		verify(transferService).transfer(anyLong(), anyLong(),
				any(BigDecimal.class));

		// Scale-2 money fidelity: Jackson preserves the BigDecimal scale, so the
		// compact JSON shows each balance with exactly two decimal places (a
		// collapsed 450.0 or 450 would fail this guard).
		String body = result.getResponse().getContentAsString();
		assertTrue(body.contains("\"fromAvailableBalance\":450.00"),
				"fromAvailableBalance must serialise at scale 2; body=" + body);
		assertTrue(body.contains("\"fromActualBalance\":430.00"),
				"fromActualBalance must serialise at scale 2; body=" + body);
		assertTrue(body.contains("\"toAvailableBalance\":150.00"),
				"toAvailableBalance must serialise at scale 2; body=" + body);
		assertTrue(body.contains("\"toActualBalance\":140.00"),
				"toActualBalance must serialise at scale 2; body=" + body);
	}

	/**
	 * Failure path: every {@code XFRFUN} fail code the service can raise
	 * ({@code "4"} non-positive amount, {@code "1"} source not found, {@code "2"}
	 * target not found, {@code "3"} deadlock/lock failure, {@code "SAME"}
	 * same-account) is surfaced verbatim in the flat envelope with
	 * {@code success="N"} and still returns HTTP&nbsp;200 (the business outcome is
	 * carried in the body, never via the HTTP status). The controller rebuilds
	 * the envelope itself rather than deferring to the generic advice body.
	 *
	 * @param failCode the verbatim {@code XFRFUN} fail code raised by the service
	 * @throws Exception if the MockMvc exchange fails
	 */
	@ParameterizedTest(name = "failCode \"{0}\" → HTTP 200, success=N")
	@ValueSource(strings = {"4", "1", "2", "3", "SAME"})
	@DisplayName("PUT /transfer — a BusinessRuleException surfaces verbatim at HTTP 200 with success=N")
	void transfer_businessFailure_returns200WithVerbatimFailCode(String failCode)
			throws Exception
	{
		when(transferService.transfer(anyLong(), anyLong(),
				any(BigDecimal.class)))
				.thenThrow(new BusinessRuleException(failCode));

		mockMvc.perform(put(ENDPOINT)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(
						request(1L, 2L, new BigDecimal("10.00"))))
				.accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(content()
						.contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.success").value("N"))
				.andExpect(jsonPath("$.failCode").value(failCode));

		verify(transferService).transfer(anyLong(), anyLong(),
				any(BigDecimal.class));
	}

}
