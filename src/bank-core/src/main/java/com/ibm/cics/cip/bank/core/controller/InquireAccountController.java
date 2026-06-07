/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.core.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ibm.cics.cip.bank.core.dto.accountenquiry.AccountEnquiryJson;
import com.ibm.cics.cip.bank.core.dto.accountenquiry.InqaccJson;
import com.ibm.cics.cip.bank.core.exception.BusinessRuleException;
import com.ibm.cics.cip.bank.core.service.AccountService;

/**
 * REST controller reproducing the frozen z/OS Connect <em>inquire-account</em>
 * endpoint (the {@code inqaccz} API, mapping the {@code CSaccenq} service onto
 * {@code INQACC.cbl}), feature&nbsp;F-019.
 *
 * <p>This is a deliberately <strong>thin adapter</strong>: it owns no business
 * logic. The HTTP verb, route, and JSON envelope are reproduced verbatim and the
 * request is delegated straight to
 * {@link AccountService#inquireAccount(long)}, which carries the authoritative
 * {@code INQACC} behaviour &mdash; including the {@code 99999999} sentinel that
 * resolves the highest existing account from the control-row
 * {@code LAST-ACCOUNT-NUMBER} rather than a {@code MAX()} scan (F-009).</p>
 *
 * <h2>Frozen contract (verified)</h2>
 * <p>The route, method, and envelope match
 * {@code src/zosconnect_artefacts/apis/inqaccz/api-docs/swagger.json} and
 * {@code package.xml} exactly:</p>
 * <ul>
 *   <li><strong>Method&nbsp;+&nbsp;path:</strong> {@code GET /inqaccz/enquiry/{accno}}
 *       (operationId {@code getCSaccenq}; basePath {@code /inqaccz}, relativePath
 *       {@code /enquiry/{accno}}). The class-level
 *       {@link RequestMapping @RequestMapping("/inqaccz")} composes with the
 *       method-level {@link GetMapping @GetMapping("/enquiry/{accno}")} to form
 *       the full route at the ROOT context (no servlet context-path; the module
 *       runs on {@code server.port} 8080).</li>
 *   <li><strong>Produces:</strong> {@code application/json} only, with a single
 *       HTTP&nbsp;{@code 200} response.</li>
 *   <li><strong>Response envelope:</strong> the outer wrapper
 *       {@link AccountEnquiryJson} serialises to exactly one top-level key,
 *       {@code InqAcc}, over the inner {@link InqaccJson} payload.</li>
 *   <li><strong>No request body:</strong> although the swagger lists a body
 *       parameter, the real consumer ({@code WebController}) calls
 *       {@code client.get().retrieve()} with <em>no</em> body, so this handler
 *       accepts ONLY the {@code {accno}} path variable and declares no
 *       {@code @RequestBody}.</li>
 * </ul>
 *
 * <h2>Success / not-found convention</h2>
 * <p>Unlike the mutating endpoints, {@code INQACC} signals a plain
 * &quot;not&nbsp;found&quot; not as a hard failure but by RETURNING a soft
 * envelope whose inner {@code InqAccSuccess} flag is {@code "N"} (the read path
 * needs no rollback). That envelope is produced by the service and returned by
 * this controller verbatim at HTTP&nbsp;{@code 200}. The {@code InqAcc} envelope
 * has NO separate fail-code field &mdash; only the single-character
 * {@code InqAccSuccess} flag &mdash; so no fail code is ever surfaced here.</p>
 *
 * <h2>Safeguard failure handling</h2>
 * <p>As defence-in-depth, the delegation is wrapped in a {@code try}/{@code catch}
 * for {@link BusinessRuleException}: should the service ever raise one on this
 * read path, the controller still honours the contract by returning a fresh
 * {@link AccountEnquiryJson} whose inner payload echoes the requested account
 * number and sets {@code InqAccSuccess = "N"}, again at HTTP&nbsp;{@code 200}.
 * This mirrors the service's own not-found shaping. Validation and generic
 * exceptions (for example a non-numeric path variable raising
 * {@link NumberFormatException}) are deliberately NOT caught here; they propagate
 * to {@code GlobalExceptionHandler}.</p>
 *
 * <p><strong>No mainframe coupling.</strong> This controller imports only Spring
 * MVC and {@code bank-core} types; it never references {@code com.ibm.cics.server}
 * (JCICS), {@code com.ibm.jzos}, or {@code com.ibm.websphere} &mdash; those
 * legacy runtimes are decommissioned in the target.</p>
 *
 * @see AccountService#inquireAccount(long)
 * @see AccountEnquiryJson
 * @see InqaccJson
 */
@RestController
@RequestMapping("/inqaccz")
public class InquireAccountController
{

	/**
	 * Failure flag value written to {@code InqAccSuccess} on the safeguard
	 * not-found envelope (COBOL {@code INQACC-SUCCESS = 'N'}).
	 */
	private static final String FLAG_FAILURE = "N";

	/**
	 * The account business service holding all {@code INQACC} logic. Injected by
	 * constructor for immutability and testability.
	 */
	private final AccountService accountService;

	/**
	 * Constructs the controller with its collaborating service.
	 *
	 * @param accountService the account business service (INQACC parity, F-009)
	 */
	public InquireAccountController(AccountService accountService)
	{
		this.accountService = accountService;
	}

	/**
	 * Inquires on a single account, reproducing the frozen
	 * {@code GET /inqaccz/enquiry/{accno}} contract.
	 *
	 * <p>The path variable is parsed to a {@code long} and delegated unchanged to
	 * {@link AccountService#inquireAccount(long)} &mdash; including the
	 * {@code 99999999} sentinel, which the service interprets as a request for the
	 * highest existing account. The service returns the fully populated
	 * {@link AccountEnquiryJson} envelope (with {@code InqAccSuccess = "Y"} when
	 * the account is found, or {@code "N"} for the soft not-found outcome), which
	 * is returned as-is at HTTP&nbsp;{@code 200}.</p>
	 *
	 * <p>A {@link BusinessRuleException} is caught as a safeguard and shaped into
	 * a contract-faithful {@code InqAccSuccess = "N"} envelope echoing the
	 * requested account number, also at HTTP&nbsp;{@code 200}. A non-numeric
	 * {@code accno} raises {@link NumberFormatException}, which is left to
	 * propagate to the global exception handler.</p>
	 *
	 * @param accno the 8-digit account number from the path (the sentinel
	 *              {@code 99999999} is passed through unchanged for the service to
	 *              interpret)
	 * @return the account-enquiry response envelope at HTTP&nbsp;{@code 200},
	 *         {@code application/json}
	 */
	@GetMapping(value = "/enquiry/{accno}",
			produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<AccountEnquiryJson> inquireAccount(
			@PathVariable("accno") String accno)
	{
		// Parse outside the try so the value is definitely assigned for the catch
		// block; a non-numeric path variable raises NumberFormatException, which
		// is intentionally left to reach GlobalExceptionHandler.
		long accountNumber = Long.parseLong(accno);
		try
		{
			// Thin pass-through: INQACC logic (incl. the 99999999 sentinel and
			// the soft InqAccSuccess="N" not-found case) lives in the service.
			AccountEnquiryJson result = accountService
					.inquireAccount(accountNumber);
			return ResponseEntity.ok(result);
		}
		catch (BusinessRuleException ex)
		{
			// Safeguard: honour the frozen contract even if the read path ever
			// raises a business-rule exception. The InqAcc envelope has no
			// fail-code field, so only the success flag is set to "N".
			InqaccJson payload = new InqaccJson();
			payload.setInqaccAccno((int) accountNumber);
			payload.setInqaccSuccess(FLAG_FAILURE);
			AccountEnquiryJson failureEnvelope = new AccountEnquiryJson();
			failureEnvelope.setInqaccCommarea(payload);
			return ResponseEntity.ok(failureEnvelope);
		}
	}
}
