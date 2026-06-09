/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.core.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibm.cics.cip.bank.core.dto.deleteaccount.DelaccJson;
import com.ibm.cics.cip.bank.core.dto.deleteaccount.DeleteAccountJson;
import com.ibm.cics.cip.bank.core.exception.BusinessRuleException;
import com.ibm.cics.cip.bank.core.service.AccountService;

/**
 * Controller <em>contract</em> integration test for {@link DeleteAccountController}
 * &mdash; proof that the standalone {@code bank-core} module reproduces the frozen
 * z/OS Connect <em>delete-account</em> ({@code delacc}) JSON contract byte-for-byte
 * (feature&nbsp;F-019). It is one of the ten {@code *IT.java} controller contract
 * tests that pin the preserved REST surface so the existing React/Carbon front end
 * and the Spring Boot interface modules integrate with only a base-URL re-point.
 *
 * <h2>Frozen contract pinned here (verified against {@code delacc/api-docs/swagger.json})</h2>
 * <ul>
 *   <li><strong>Method&nbsp;+&nbsp;path:</strong> {@code DELETE /delacc/remove/{accno}}
 *       at the ROOT context (no servlet context-path; operationId
 *       {@code deleteCSaccdel}, basePath {@code /delacc}, relativePath
 *       {@code /remove/{accno}}). The account number is carried as the path
 *       variable &mdash; the real consumer ({@code WebController}) invokes
 *       {@code client.delete()...} with <strong>no request body</strong>, so these
 *       tests issue {@code delete(...)} without content.</li>
 *   <li><strong>Single HTTP&nbsp;200:</strong> the endpoint returns
 *       {@code application/json} with HTTP&nbsp;{@code 200} on success
 *       <em>and</em> on a business failure &mdash; the legacy z/OS Connect
 *       contract delivers the business outcome in the body, never via the HTTP
 *       status, so the consumer inspects the envelope rather than the status
 *       code.</li>
 *   <li><strong>Top-level envelope:</strong> exactly one wire key, {@code DelAcc}
 *       (the {@link DeleteAccountJson} wrapper over the inner {@link DelaccJson}
 *       payload).</li>
 *   <li><strong>Two success/fail groups:</strong> the {@code DelAcc} payload is
 *       the richest delete envelope &mdash; it carries an account-inquiry group
 *       ({@code DelAccSuccess}/{@code DelAccFailCd}) AND a delete-status group
 *       ({@code DelAccDelSuccess}/{@code DelAccDelFailCd}). The production
 *       controller populates the <em>delete-status</em> group on a rejected
 *       delete (blank-filling {@code DelAccSuccess}, flagging
 *       {@code DelAccDelSuccess = "N"} and carrying the verbatim COBOL fail code
 *       in {@code DelAccDelFailCd}), so the failure assertion below targets that
 *       same group.</li>
 *   <li><strong>Terminal balances:</strong> {@code DelAccAvailBal} and
 *       {@code DelAccActualBal} are the account's closing available and actual
 *       balances captured at delete time, serialised as scale-2
 *       {@link BigDecimal} (the money-fidelity rule &mdash; no {@code double} /
 *       {@code float}). The success test asserts both reach the wire with exactly
 *       two decimal places.</li>
 * </ul>
 *
 * <h2>Test strategy &mdash; DB-free web slice</h2>
 * <p>This is a {@link WebMvcTest @WebMvcTest(DeleteAccountController.class)} slice:
 * it loads only the {@code DeleteAccountController} MVC web layer (plus the
 * module's {@code @RestControllerAdvice}) and replaces the business
 * {@link AccountService} with a {@link MockitoBean @MockitoBean}. No datasource,
 * JPA, or Flyway is started, so the test is green on Java&nbsp;17 without a running
 * PostgreSQL instance. {@code @SpringBootTest} is deliberately <em>not</em> used.
 * The module {@code @SpringBootApplication} ({@code BankCoreApplication}) at the
 * package root is auto-discovered as the {@code @SpringBootConfiguration} that
 * bootstraps the sliced context.</p>
 *
 * <p>The class never references {@code com.ibm.cics.server} (JCICS),
 * {@code com.ibm.jzos}, or {@code com.ibm.websphere}; those legacy mainframe
 * runtimes are decommissioned in the target.</p>
 *
 * @see DeleteAccountController
 * @see DeleteAccountJson
 * @see DelaccJson
 * @see AccountService#deleteAccount(long)
 */
@WebMvcTest(DeleteAccountController.class)
@DisplayName("DeleteAccountController contract IT — DELETE /delacc/remove/{accno}, frozen DelAcc envelope (F-019)")
class DeleteAccountControllerIT
{

	/** Frozen route template: ROOT context, no context-path prefix. */
	private static final String ENDPOINT = "/delacc/remove/{accno}";

	/**
	 * Eight-digit, left-zero-padded account number used as the path variable
	 * (parsed by the controller to {@code 123L}). It is purely the route input;
	 * the mocked service returns the fixture regardless of the parsed value.
	 */
	private static final String ACCNO_PATH = "00000123";

	/** The single top-level wire key the frozen contract mandates. */
	private static final String TOP_LEVEL_KEY = "DelAcc";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@MockitoBean
	private AccountService accountService;

	/**
	 * Happy path: a populated {@link DeleteAccountJson} returned by the service
	 * reaches the wire verbatim under the single {@code DelAcc} key at
	 * HTTP&nbsp;200, with the delete-status flag {@code DelAccDelSuccess = "Y"},
	 * the account number present, and the terminal available/actual balances
	 * serialised at scale&nbsp;2.
	 *
	 * @throws Exception if the MockMvc exchange fails
	 */
	@Test
	@DisplayName("DELETE /delacc/remove/{accno} — success returns the DelAcc envelope (200, DelAccDelSuccess=Y, scale-2 terminal balances)")
	void deleteRemove_success_returnsDelAccEnvelope() throws Exception
	{
		// anyLong(): the path variable is an account number; the stub is
		// indifferent to its parsed value and always yields the success fixture.
		when(accountService.deleteAccount(anyLong()))
				.thenReturn(successEnvelope());

		// DELETE with a path variable and NO request body (the real no-body
		// consumer path); the response is captured for the scale-2 wire check.
		MvcResult result = mockMvc
				.perform(delete(ENDPOINT, ACCNO_PATH)
						.accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(content()
						.contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.DelAcc").exists())
				.andExpect(jsonPath("$.DelAcc.DelAccDelSuccess").value("Y"))
				.andExpect(jsonPath("$.DelAcc.DelAccAccno").exists())
				.andExpect(jsonPath("$.DelAcc.DelAccAccType").value("CURRENT"))
				.andReturn();

		verify(accountService).deleteAccount(anyLong());

		// Scale-2 money fidelity: the frozen contract carries the terminal
		// balances with exactly two decimal places. Jackson preserves the
		// BigDecimal scale on the wire, so the compact JSON shows "100.00" /
		// "150.00" verbatim (a collapsed 100.0 or 100 would fail this guard).
		String body = result.getResponse().getContentAsString();
		assertTrue(body.contains("\"DelAccAvailBal\":100.00"),
				"DelAccAvailBal must serialise at scale 2 (100.00); body=" + body);
		assertTrue(body.contains("\"DelAccActualBal\":150.00"),
				"DelAccActualBal must serialise at scale 2 (150.00); body=" + body);
	}

	/**
	 * Failure path: when the service raises {@link BusinessRuleException} (COBOL
	 * {@code DELACC} fail code {@code "1"} &mdash; account not found), the
	 * controller does NOT defer to the generic advice body; it rebuilds the
	 * frozen {@code DelAcc} envelope, flagging the operative delete-status group
	 * ({@code DelAccDelSuccess = "N"}, {@code DelAccDelFailCd = "1"}) and still
	 * returns HTTP&nbsp;200. This asserts the SAME field group the production
	 * controller populates.
	 *
	 * @throws Exception if the MockMvc exchange fails
	 */
	@Test
	@DisplayName("DELETE /delacc/remove/{accno} — account-not-found returns HTTP 200 with the rebuilt DelAcc failure envelope (DelAccDelSuccess=N, DelAccDelFailCd=1)")
	void deleteRemove_notFound_returns200WithFailCode() throws Exception
	{
		when(accountService.deleteAccount(anyLong()))
				.thenThrow(new BusinessRuleException("1"));

		mockMvc.perform(delete(ENDPOINT, ACCNO_PATH)
				.accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(content()
						.contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.DelAcc").exists())
				.andExpect(jsonPath("$.DelAcc.DelAccDelSuccess").value("N"))
				.andExpect(jsonPath("$.DelAcc.DelAccDelFailCd").value("1"));

		verify(accountService).deleteAccount(anyLong());
	}

	/**
	 * Envelope-shape guard: the serialised response carries exactly one
	 * top-level key, {@code DelAcc}, and nothing else &mdash; the contract is a
	 * single-wrapper envelope.
	 *
	 * @throws Exception if the MockMvc exchange or JSON parsing fails
	 */
	@Test
	@DisplayName("DELETE /delacc/remove/{accno} — the response has exactly one top-level key: DelAcc")
	void deleteRemove_singleTopLevelKeyIsDelAcc() throws Exception
	{
		when(accountService.deleteAccount(anyLong()))
				.thenReturn(successEnvelope());

		MvcResult result = mockMvc
				.perform(delete(ENDPOINT, ACCNO_PATH)
						.accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andReturn();

		JsonNode root = objectMapper
				.readTree(result.getResponse().getContentAsString());
		assertEquals(1, root.size(),
				"Response must carry exactly one top-level key; body=" + root);
		assertTrue(root.has(TOP_LEVEL_KEY),
				"The single top-level key must be DelAcc; body=" + root);
	}

	/**
	 * Builds the populated success envelope the mocked service returns: a
	 * {@code DelAcc} payload echoing a deleted {@code CURRENT} account, with the
	 * delete-status flag {@code "Y"} and the terminal available/actual balances
	 * as scale-2 {@link BigDecimal}s (no {@code double} / {@code float}).
	 *
	 * @return a fully populated {@link DeleteAccountJson} response envelope
	 */
	private DeleteAccountJson successEnvelope()
	{
		DelaccJson payload = new DelaccJson();
		payload.setDelaccEye("ACCT");
		payload.setDelaccCustno("0000000077");
		payload.setDelaccSortcode("987654");
		// DelAccAccno is the frozen contract's INTEGER field (serialised as a
		// JSON number), so it carries the numeric value 123 rather than a
		// zero-padded string. The path variable above ("00000123") parses to
		// the same 123, keeping the fixture self-consistent.
		payload.setDelaccAccno(123);
		payload.setDelaccAccType("CURRENT");
		payload.setDelaccAvailableBalance(new BigDecimal("100.00"));
		payload.setDelaccActualBalance(new BigDecimal("150.00"));
		// A successful close: the account was found (inquiry group "Y") and
		// deleted (delete-status group "Y").
		payload.setDelaccSuccess("Y");
		payload.setDelaccDelSuccess("Y");
		return new DeleteAccountJson(payload);
	}

}
