/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.core.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ibm.cics.cip.bank.core.dto.deleteaccount.DelaccJson;
import com.ibm.cics.cip.bank.core.dto.deleteaccount.DeleteAccountJson;
import com.ibm.cics.cip.bank.core.exception.BusinessRuleException;
import com.ibm.cics.cip.bank.core.service.AccountService;
import com.ibm.cics.cip.bank.core.util.BankFormat;

import jakarta.validation.Valid;

/**
 * REST controller reproducing the frozen z/OS Connect <em>delete-account</em>
 * endpoint (the {@code delacc} API, mapping the {@code CSaccdel} service onto
 * {@code DELACC.cbl}), feature&nbsp;F-019.
 *
 * <p>This is a deliberately <strong>thin adapter</strong>: it owns no business
 * logic whatsoever. The HTTP verb, route, and JSON envelope are reproduced
 * verbatim and the request is delegated straight to
 * {@link AccountService#deleteAccount(long)}, which carries the authoritative
 * {@code DELACC} behaviour (F-013): it captures the account's <em>terminal</em>
 * (closing) available and actual balances, appends an account-close
 * {@code PROCTRAN} record carrying that balance (PROCTRAN is append-only), and
 * physically removes the account row &mdash; all inside one transaction so the
 * audit record and the row removal commit or roll back together.</p>
 *
 * <h2>Frozen contract (verified)</h2>
 * <p>The route, method, and envelope match
 * {@code src/zosconnect_artefacts/apis/delacc/api-docs/swagger.json} and
 * {@code package.xml} exactly:</p>
 * <ul>
 *   <li><strong>Method&nbsp;+&nbsp;path:</strong>
 *       {@code DELETE /delacc/remove/{accno}} (operationId
 *       {@code deleteCSaccdel}; basePath {@code /delacc}, relativePath
 *       {@code /remove/{accno}}). The class-level
 *       {@link RequestMapping @RequestMapping("/delacc")} composes with the
 *       method-level {@link DeleteMapping @DeleteMapping("/remove/{accno}")} to
 *       form the full route at the ROOT context (no servlet context-path; the
 *       module runs on {@code server.port} 8080).</li>
 *   <li><strong>Produces:</strong> {@code application/json} only, with a single
 *       HTTP&nbsp;{@code 200} response.</li>
 *   <li><strong>Response envelope:</strong> the outer wrapper
 *       {@link DeleteAccountJson} serialises to exactly one top-level key,
 *       {@code DelAcc}, over the inner {@link DelaccJson} payload.</li>
 *   <li><strong>Parameter source (path + optional body):</strong> the swagger
 *       declares both the {@code {accno}} path variable and a body parameter
 *       {@code deleteCSaccdel_request} carrying the {@code DelAcc} envelope. The
 *       real consumer ({@code WebController}) invokes the endpoint with
 *       {@code client.delete().retrieve()} and <em>no</em> body, so the
 *       {@code {accno}} path variable is the AUTHORITATIVE parameter source and
 *       identifies the account. For frozen-contract fidelity the handler ALSO
 *       accepts the swagger-declared {@code DelAcc} body as an OPTIONAL
 *       ({@code required = false}), {@code @Valid}-checked
 *       {@link DeleteAccountJson} so that swagger-shaped callers are not
 *       rejected; a present body is structurally validated but does not override
 *       the authoritative path variable.</li>
 * </ul>
 *
 * <h2>Success / failure convention (byte-for-byte envelope fidelity)</h2>
 * <p>On success the service returns the fully populated {@link DeleteAccountJson}
 * envelope &mdash; including the deleted account's terminal
 * {@code DelAccAvailBal} and {@code DelAccActualBal} &mdash; which this
 * controller returns verbatim at HTTP&nbsp;{@code 200}.</p>
 *
 * <p>When the service raises a {@link BusinessRuleException} (the COBOL
 * {@code DELACC} fail codes include {@code "1"} for an account that was not
 * found and {@code "3"} for a failed delete), this controller does NOT defer to
 * the generic {@code GlobalExceptionHandler} body; instead it reconstructs the
 * frozen {@code DelAcc} envelope so the wire shape stays byte-for-byte intact.
 * It builds a fresh {@link DeleteAccountJson} whose inner {@link DelaccJson}
 * echoes the requested account number, blank-fills the primary
 * {@code DelAccSuccess} and flags the operative {@code DelAccDelSuccess} as
 * {@code "N"} (matching {@code DELACC.cbl}, which leaves {@code DELACC-SUCCESS}
 * blank on every failure path), and carries the verbatim fail code in
 * {@code DelAccDelFailCd}. That envelope is returned at HTTP&nbsp;{@code 200},
 * exactly as the legacy z/OS Connect contract delivers a business outcome (the
 * consumer inspects the body, not the HTTP status).</p>
 *
 * <p>A blank, non-numeric or over-width {@code {accno}} raises
 * {@link NumberFormatException} from {@link BankFormat#parseAccountNumber(String)};
 * that is deliberately <em>not</em> caught here so it propagates to
 * {@code GlobalExceptionHandler} (rendered as HTTP&nbsp;400). Only
 * {@link BusinessRuleException} is caught.</p>
 *
 * <p><strong>No mainframe coupling.</strong> This controller imports only Spring
 * MVC and {@code bank-core} types; it never references
 * {@code com.ibm.cics.server} (JCICS), {@code com.ibm.jzos}, or
 * {@code com.ibm.websphere} &mdash; those legacy runtimes are decommissioned in
 * the target.</p>
 *
 * @see AccountService#deleteAccount(long)
 * @see DeleteAccountJson
 * @see DelaccJson
 */
@RestController
@RequestMapping("/delacc")
public class DeleteAccountController
{

	/**
	 * Failure flag value ({@code "N"}) written to the operative
	 * {@code DelAccDelSuccess} on the reconstructed failure envelope (COBOL
	 * {@code DELACC-DEL-SUCCESS = 'N'} on a rejected delete, DELACC.cbl L354/L446).
	 */
	private static final String FLAG_FAILURE = "N";

	/**
	 * Single-space blank fill written to the primary {@code DelAccSuccess} on the
	 * failure envelope: {@code DELACC.cbl} never moves a value into
	 * {@code DELACC-SUCCESS} on the not-found path (L350-356) and explicitly
	 * blank-fills it on the delete-fail path (L445), so it is blank on every
	 * failure outcome.
	 */
	private static final String BLANK = " ";

	/**
	 * The account business service holding all {@code DELACC} logic. Injected by
	 * constructor for immutability and testability.
	 */
	private final AccountService accountService;

	/**
	 * Constructs the controller with its collaborating service.
	 *
	 * @param accountService the account business service (DELACC parity, F-013)
	 */
	public DeleteAccountController(AccountService accountService)
	{
		this.accountService = accountService;
	}

	/**
	 * Deletes a single account, reproducing the frozen
	 * {@code DELETE /delacc/remove/{accno}} contract.
	 *
	 * <p>The path variable is parsed to a {@code long} via
	 * {@link BankFormat#parseAccountNumber(String)} (which enforces the
	 * eight-digit contract width) and delegated unchanged to
	 * {@link AccountService#deleteAccount(long)}, which carries the authoritative
	 * {@code DELACC} behaviour (terminal-balance capture, account-close
	 * {@code PROCTRAN} append, and physical row removal in one transaction). The
	 * service returns the fully populated {@link DeleteAccountJson} envelope
	 * (echoing the deleted account's terminal available and actual balances),
	 * which is returned as-is at HTTP&nbsp;{@code 200}.</p>
	 *
	 * <p>A {@link BusinessRuleException} is caught and shaped into a
	 * contract-faithful {@code DelAcc} failure envelope echoing the requested
	 * account number, with the primary {@code DelAccSuccess} blank-filled, the
	 * operative {@code DelAccDelSuccess = "N"}, and {@code DelAccDelFailCd}
	 * carrying the verbatim COBOL fail code &mdash; whatever code arrives &mdash;
	 * again at HTTP&nbsp;{@code 200}. A blank, non-numeric or over-width
	 * {@code accno} raises {@link NumberFormatException}, which is left to
	 * propagate to the global exception handler (HTTP&nbsp;400).</p>
	 *
	 * @param accno the 8-digit account number from the path
	 * @param body  the OPTIONAL swagger-declared {@code DelAcc} request envelope
	 *              ({@code required = false}); accepted and {@code @Valid}-checked
	 *              for frozen-contract fidelity but NOT authoritative &mdash; the
	 *              {@code {accno}} path variable identifies the account. May be
	 *              {@code null} (the real no-body consumer path).
	 * @return the delete-account response envelope at HTTP&nbsp;{@code 200},
	 *         {@code application/json}
	 */
	@DeleteMapping(value = "/remove/{accno}",
			produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<DeleteAccountJson> deleteAccount(
			@PathVariable("accno") String accno,
			@RequestBody(required = false) @Valid DeleteAccountJson body)
	{
		// Parse OUTSIDE the try via BankFormat.parseAccountNumber, which enforces
		// the eight-digit contract width: a blank, non-numeric or over-width path
		// variable raises NumberFormatException, intentionally left to reach
		// GlobalExceptionHandler as HTTP 400 (F-013-B) rather than being shaped
		// into the business failure envelope.
		long accountNumber = BankFormat.parseAccountNumber(accno);
		try
		{
			// Thin pass-through: all DELACC logic (terminal-balance capture,
			// account-close PROCTRAN append, physical row removal) lives in the
			// service, which returns the fully populated DelAcc success envelope.
			DeleteAccountJson result = accountService
					.deleteAccount(accountNumber);
			return ResponseEntity.ok(result);
		}
		catch (BusinessRuleException ex)
		{
			// Reproduce the DelAcc failure envelope byte-for-byte (NOT the
			// generic advice body): echo the requested account number (as the
			// frozen contract's integer DelAccAccno) and the verbatim COBOL fail
			// code in the operative DelAccDelFailCd. Per DELACC.cbl the primary
			// DelAccSuccess is blank on every failure path (L350-356/L445) while
			// the operative DelAccDelSuccess is 'N' (L354/L446).
			DelaccJson payload = new DelaccJson();
			payload.setDelaccAccno((int) accountNumber);
			payload.setDelaccSuccess(BLANK);
			payload.setDelaccDelSuccess(FLAG_FAILURE);
			payload.setDelaccDelFailCode(ex.getFailCode());
			return ResponseEntity.ok(new DeleteAccountJson(payload));
		}
	}
}
