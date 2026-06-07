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

import com.ibm.cics.cip.bank.core.dto.listaccounts.InqAccczJson;
import com.ibm.cics.cip.bank.core.dto.listaccounts.ListAccountsJson;
import com.ibm.cics.cip.bank.core.exception.BusinessRuleException;
import com.ibm.cics.cip.bank.core.service.AccountService;

/**
 * REST controller reproducing the frozen z/OS Connect
 * <em>list-customer-accounts</em> endpoint (the {@code inqacccz} API, mapping the
 * {@code CScustacc} service onto {@code INQACCCU.cbl}), feature&nbsp;F-019.
 *
 * <p>This is a deliberately <strong>thin adapter</strong>: it owns no business
 * logic. The HTTP verb, route, and JSON envelope are reproduced verbatim and the
 * request is delegated straight to
 * {@link AccountService#listAccountsByCustomer(long)}, which carries the
 * authoritative {@code INQACCCU} behaviour &mdash; including the cap of twenty
 * accounts ({@code ACCOUNT-DETAILS OCCURS 1 TO 20}, F-010). The controller never
 * caps, slices, or re-orders the returned list; doing so here would diverge from
 * the record count the COBOL produced.</p>
 *
 * <h2>Frozen contract (verified)</h2>
 * <p>The route, method, and envelope match
 * {@code src/zosconnect_artefacts/apis/inqacccz/api-docs/swagger.json} and
 * {@code package.xml} exactly:</p>
 * <ul>
 *   <li><strong>Method&nbsp;+&nbsp;path:</strong> {@code GET /inqacccz/list/{custno}}
 *       (operationId {@code getCScustacc}; basePath {@code /inqacccz},
 *       relativePath {@code /list/{custno}}). The class-level
 *       {@link RequestMapping @RequestMapping("/inqacccz")} composes with the
 *       method-level {@link GetMapping @GetMapping("/list/{custno}")} to form the
 *       full route at the ROOT context (no servlet context-path; the module runs
 *       on {@code server.port} 8080).</li>
 *   <li><strong>Produces:</strong> {@code application/json} only, with a single
 *       HTTP&nbsp;{@code 200} response.</li>
 *   <li><strong>Response envelope:</strong> the outer wrapper
 *       {@link ListAccountsJson} serialises to exactly one top-level key,
 *       {@code InqAccZ}, over the inner {@link InqAccczJson} payload (which holds
 *       {@code CustomerNumber}, {@code CommSuccess}, {@code CommFailCode},
 *       {@code CustomerFound}, {@code CommPcbPointer}, and the
 *       {@code AccountDetails} array).</li>
 *   <li><strong>No request body:</strong> although the swagger lists a body
 *       parameter, the real consumer ({@code WebController}) calls
 *       {@code client.get().retrieve()} with <em>no</em> body, so this handler
 *       accepts ONLY the {@code {custno}} path variable and declares no
 *       {@code @RequestBody}.</li>
 * </ul>
 *
 * <h2>Dual outcome handling (byte-for-byte envelope fidelity)</h2>
 * <p>The {@code INQACCCU} service signals a missing customer as a hard failure:
 * {@link AccountService#listAccountsByCustomer(long)} throws a
 * {@link BusinessRuleException} carrying fail code {@code "1"} (customer not
 * found). To honour the frozen contract &mdash; which exposes a single
 * HTTP&nbsp;{@code 200} response shape for both success and not-found &mdash;
 * that exception is caught here and shaped into a contract-faithful envelope:</p>
 * <ol>
 *   <li><strong>Success / found:</strong> the populated envelope returned by the
 *       service (with {@code CustomerFound = "Y"}, {@code CommSuccess = "Y"} and
 *       up to twenty {@code AccountDetails}) is returned unchanged at
 *       HTTP&nbsp;{@code 200}.</li>
 *   <li><strong>Not found:</strong> the {@link BusinessRuleException} catch
 *       builds a fresh {@link ListAccountsJson} whose inner {@link InqAccczJson}
 *       echoes the requested {@code CustomerNumber} and sets
 *       {@code CustomerFound = "N"}, {@code CommSuccess = "N"} and
 *       {@code CommFailCode} to the carried fail code (typically {@code "1"}),
 *       again at HTTP&nbsp;{@code 200}.</li>
 * </ol>
 * <p>Validation and generic exceptions (for example a non-numeric path variable
 * raising {@link NumberFormatException}) are deliberately NOT caught here; they
 * propagate to {@code GlobalExceptionHandler}. The path variable is therefore
 * parsed <em>outside</em> the {@code try} so such failures bypass the
 * business-rule catch entirely.</p>
 *
 * <p><strong>No mainframe coupling.</strong> This controller imports only Spring
 * MVC and {@code bank-core} types; it never references {@code com.ibm.cics.server}
 * (JCICS), {@code com.ibm.jzos}, or {@code com.ibm.websphere} &mdash; those
 * legacy runtimes are decommissioned in the target.</p>
 *
 * @see AccountService#listAccountsByCustomer(long)
 * @see ListAccountsJson
 * @see InqAccczJson
 */
@RestController
@RequestMapping("/inqacccz")
public class InquireCustomerAccountsController
{

	/**
	 * Flag value written to {@code CustomerFound} and {@code CommSuccess} on the
	 * not-found envelope (COBOL {@code CUSTOMER-FOUND = 'N'} /
	 * {@code COMM-SUCCESS = 'N'}).
	 */
	private static final String FLAG_NOT_FOUND = "N";

	/**
	 * The account business service holding all {@code INQACCCU} logic (including
	 * the twenty-account cap). Injected by constructor for immutability and
	 * testability.
	 */
	private final AccountService accountService;

	/**
	 * Constructs the controller with its collaborating service.
	 *
	 * @param accountService the account business service (INQACCCU parity, F-010)
	 */
	public InquireCustomerAccountsController(AccountService accountService)
	{
		this.accountService = accountService;
	}

	/**
	 * Lists the accounts owned by a customer, reproducing the frozen
	 * {@code GET /inqacccz/list/{custno}} contract.
	 *
	 * <p>The path variable is parsed to a {@code long} and delegated unchanged to
	 * {@link AccountService#listAccountsByCustomer(long)}. The service returns the
	 * fully populated {@link ListAccountsJson} envelope (with
	 * {@code CustomerFound = "Y"} and up to twenty {@code AccountDetails}), which
	 * is returned as-is at HTTP&nbsp;{@code 200}. The controller performs no list
	 * capping or slicing &mdash; the cap of twenty is the service's behavioural
	 * rule.</p>
	 *
	 * <p>If the customer does not exist the service throws a
	 * {@link BusinessRuleException} (fail code {@code "1"}); it is caught here and
	 * shaped into a not-found envelope ({@code CustomerFound = "N"},
	 * {@code CommSuccess = "N"}, {@code CommFailCode} = the carried code) that
	 * echoes the requested customer number, also at HTTP&nbsp;{@code 200}. A
	 * non-numeric {@code custno} raises {@link NumberFormatException}, which is
	 * left to propagate to the global exception handler.</p>
	 *
	 * @param custno the 10-digit customer number from the path
	 * @return the list-customer-accounts response envelope at HTTP&nbsp;{@code 200},
	 *         {@code application/json}
	 */
	@GetMapping(value = "/list/{custno}",
			produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<ListAccountsJson> listAccounts(
			@PathVariable("custno") String custno)
	{
		// Parse outside the try so a non-numeric path variable raises
		// NumberFormatException that bypasses the business-rule catch and reaches
		// GlobalExceptionHandler (validation/generic exceptions are not shaped
		// into the business not-found envelope).
		long customerNumber = Long.parseLong(custno);
		try
		{
			// Thin pass-through: INQACCCU logic (incl. the twenty-account cap)
			// lives in the service; the populated envelope is returned untouched.
			ListAccountsJson result = accountService
					.listAccountsByCustomer(customerNumber);
			return ResponseEntity.ok(result);
		}
		catch (BusinessRuleException ex)
		{
			// Customer not found (INQACCCU fail code '1'): honour the frozen
			// single-200-shape contract by emitting a not-found envelope that
			// echoes the requested customer number, rather than an HTTP error.
			InqAccczJson payload = new InqAccczJson();
			payload.setCustomerNumber(custno);
			payload.setCustomerFound(FLAG_NOT_FOUND);
			payload.setCommSuccess(FLAG_NOT_FOUND);
			payload.setCommFailCode(ex.getFailCode());
			ListAccountsJson notFoundEnvelope = new ListAccountsJson();
			notFoundEnvelope.setInqAcccz(payload);
			return ResponseEntity.ok(notFoundEnvelope);
		}
	}
}
