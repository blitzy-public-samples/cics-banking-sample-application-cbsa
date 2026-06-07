/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.core.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ibm.cics.cip.bank.core.dto.deleteaccount.DelaccJson;
import com.ibm.cics.cip.bank.core.dto.deleteaccount.DeleteAccountJson;
import com.ibm.cics.cip.bank.core.exception.BusinessRuleException;
import com.ibm.cics.cip.bank.core.service.AccountService;

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
 *   <li><strong>No request body:</strong> although the swagger lists a body
 *       parameter, the real consumer ({@code WebController}) invokes the
 *       endpoint with {@code client.delete().retrieve()} and <em>no</em> body,
 *       so this handler accepts ONLY the {@code {accno}} path variable and
 *       declares no {@code @RequestBody}.</li>
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
 * echoes the requested account number, flags both the primary
 * {@code DelAccSuccess} and the secondary {@code DelAccDelSuccess} as
 * {@code "N"}, and carries the verbatim fail code in {@code DelAccDelFailCd}.
 * That envelope is returned at HTTP&nbsp;{@code 200}, exactly as the legacy z/OS
 * Connect contract delivers a business outcome (the consumer inspects the body,
 * not the HTTP status).</p>
 *
 * <p>A non-numeric {@code {accno}} raises {@link NumberFormatException} from the
 * {@link Long#parseLong(String)} parse; that is deliberately <em>not</em> caught
 * here so it propagates to {@code GlobalExceptionHandler} (rendered as
 * HTTP&nbsp;500). This is acceptable because the frozen contract supplies numeric
 * identifiers. Only {@link BusinessRuleException} is caught.</p>
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
	 * Failure flag value ({@code "N"}) written to {@code DelAccSuccess} and
	 * {@code DelAccDelSuccess} on the reconstructed failure envelope (COBOL
	 * {@code DELACC} success flags set to {@code 'N'} on a rejected delete).
	 */
	private static final String FLAG_FAILURE = "N";

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
	 * <p>The path variable is parsed to a {@code long} and delegated unchanged to
	 * {@link AccountService#deleteAccount(long)}, which carries the authoritative
	 * {@code DELACC} behaviour (terminal-balance capture, account-close
	 * {@code PROCTRAN} append, and physical row removal in one transaction). The
	 * service returns the fully populated {@link DeleteAccountJson} envelope
	 * (echoing the deleted account's terminal available and actual balances),
	 * which is returned as-is at HTTP&nbsp;{@code 200}.</p>
	 *
	 * <p>A {@link BusinessRuleException} is caught and shaped into a
	 * contract-faithful {@code DelAcc} failure envelope echoing the requested
	 * account number, with {@code DelAccSuccess = "N"},
	 * {@code DelAccDelSuccess = "N"}, and {@code DelAccDelFailCd} carrying the
	 * verbatim COBOL fail code &mdash; whatever code arrives &mdash; again at
	 * HTTP&nbsp;{@code 200}. A non-numeric {@code accno} raises
	 * {@link NumberFormatException}, which is left to propagate to the global
	 * exception handler.</p>
	 *
	 * @param accno the 8-digit account number from the path
	 * @return the delete-account response envelope at HTTP&nbsp;{@code 200},
	 *         {@code application/json}
	 */
	@DeleteMapping(value = "/remove/{accno}",
			produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<DeleteAccountJson> deleteAccount(
			@PathVariable("accno") String accno)
	{
		// Parse OUTSIDE the try so a non-numeric path variable raises
		// NumberFormatException, which is intentionally left to reach
		// GlobalExceptionHandler (HTTP 500) rather than being shaped here.
		long accountNumber = Long.parseLong(accno);
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
			// generic advice body): echo the requested account number and the
			// verbatim COBOL fail code, flagging both success indicators "N".
			DelaccJson payload = new DelaccJson();
			payload.setDelaccAccno(accno);
			payload.setDelaccSuccess(FLAG_FAILURE);
			payload.setDelaccDelSuccess(FLAG_FAILURE);
			payload.setDelaccDelFailCode(ex.getFailCode());
			return ResponseEntity.ok(new DeleteAccountJson(payload));
		}
	}
}
