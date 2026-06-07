/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.core.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ibm.cics.cip.bank.core.domain.AccountType;
import com.ibm.cics.cip.bank.core.dto.updateaccount.UpdaccJson;
import com.ibm.cics.cip.bank.core.dto.updateaccount.UpdateAccountForm;
import com.ibm.cics.cip.bank.core.dto.updateaccount.UpdateAccountJson;
import com.ibm.cics.cip.bank.core.exception.BusinessRuleException;
import com.ibm.cics.cip.bank.core.service.AccountService;

import jakarta.validation.Valid;

/**
 * REST controller reproducing the frozen z/OS Connect <em>update-account</em>
 * ({@code updacc}) endpoint VERBATIM (feature&nbsp;F-019), mapping the
 * {@code CSaccupd} service onto
 * {@link AccountService#updateAccount(UpdateAccountForm)}.
 *
 * <h2>Frozen contract (pinned, never altered)</h2>
 * <p>The HTTP method, path, and JSON envelope are reproduced byte-for-byte from
 * the authoritative {@code src/zosconnect_artefacts/apis/updacc/api-docs/swagger.json}
 * (operationId {@code putCSaccupd}) and {@code package.xml}:</p>
 * <ul>
 *   <li><strong>{@code PUT /updacc/update}</strong> &mdash; declared as the
 *       class-level base path {@code /updacc}
 *       ({@link RequestMapping @RequestMapping}) plus the handler-level relative
 *       path {@code /update} ({@link PutMapping @PutMapping}); the application
 *       runs at the ROOT context (port&nbsp;8080) with no servlet context-path,
 *       so the absolute path is exactly {@code /updacc/update};</li>
 *   <li><strong>{@code consumes}/{@code produces} {@code application/json}</strong>
 *       with a single HTTP&nbsp;{@code 200} response in every outcome (success
 *       and business rejection alike);</li>
 *   <li>the request body and the response body share the identical top-level
 *       envelope key {@code "UpdAcc"} ({@code {"UpdAcc": { &hellip; }}}),
 *       modelled by the outer wrapper {@link UpdateAccountJson} over the inner
 *       commarea {@link UpdaccJson}. Using the wrapper as both the
 *       {@code @RequestBody} type and the {@link ResponseEntity} body type
 *       guarantees the exact {@code {"UpdAcc":{...}}} envelope.</li>
 * </ul>
 *
 * <h2>Failure channel &mdash; {@code CommSuccess} only, no fail-code field</h2>
 * <p>Unlike the create-account envelope, the {@code UpdAcc} envelope declares a
 * success flag ({@code CommSuccess}) but <strong>no</strong> fail-code field
 * (confirmed against the swagger request and response schemas and against
 * {@code UPDACC.cpy}). A business rejection is therefore signalled solely by
 * setting {@code CommSuccess="N"}; the carried
 * {@link BusinessRuleException#getFailCode()} is deliberately
 * <strong>not</strong> surfaced on this endpoint, because the frozen contract
 * provides nowhere to put it. The preserved customer-services consumer detects a
 * failure precisely by testing {@code UpdAcc.CommSuccess.equals("N")}, so
 * reproducing that flag is the contract guarantee here.</p>
 *
 * <h2>Thin adapter &mdash; no business logic here</h2>
 * <p>{@code UPDACC.cbl} is the authoritative behavioural specification and ALL
 * of its logic lives in {@link AccountService#updateAccount(UpdateAccountForm)}:
 * the RESTRICTED update (feature&nbsp;F-012) changes only the account
 * <em>type</em>, <em>interest rate</em>, and <em>overdraft limit</em> &mdash;
 * never either balance &mdash; and writes <strong>no</strong> {@code PROCTRAN}
 * record, locating the account and rejecting an absent account or a blank/invalid
 * type under the service's
 * {@link org.springframework.transaction.annotation.Transactional @Transactional}
 * boundary. This controller performs only request binding, a trivial
 * wrapper&rarr;form field copy, delegation, and response shaping. It contains no
 * balance arithmetic, no field-whitelisting of which attributes may change, and
 * no Jackson configuration.</p>
 *
 * <h2>Request adaptation (trivial copy only)</h2>
 * <p>The inbound {@link UpdaccJson} commarea is adapted into the service's
 * {@link UpdateAccountForm} input by a straight field copy:
 * {@code CommCustno}&rarr;{@code custNumber},
 * {@code CommAccno}&rarr;{@code acctNumber},
 * {@code CommAccType}&rarr;{@code acctType},
 * {@code CommIntRate}&rarr;{@code acctInterestRate}, and
 * {@code CommOverdraft}&rarr;{@code acctOverdraft}. Two type adaptations are
 * required because the form is typed more strongly than the wire payload:</p>
 * <ul>
 *   <li>the account number is a {@code String} on the wire (the raw schema types
 *       it as a JSON integer; {@code bank-core} carries it as a left-zero-padded
 *       {@code String}) but a primitive {@code int} on the form, so it is parsed
 *       by {@link #parseAccountNumber(String)} (a {@code null}/blank value maps to
 *       {@code 0}, which the service resolves to a not-found rejection &mdash;
 *       {@code CommSuccess="N"});</li>
 *   <li>{@code UpdateAccountForm.acctType} is the domain {@link AccountType} enum
 *       (it exposes no {@code String} setter), so the raw type string is resolved
 *       through {@link AccountType#isValid(String)} /
 *       {@link AccountType#fromValue(String)}; an unrecognised or blank value
 *       resolves to {@code null}, which the service maps onto {@code UPDACC}'s
 *       blank/invalid-type rejection.</li>
 * </ul>
 * <p>Both adaptations are typing concerns only and introduce no business decision
 * in the controller; they mirror the established {@code CreateAccountController}
 * pattern.</p>
 *
 * <h2>Dual failure handling (byte-for-byte envelope fidelity)</h2>
 * <p>On success the controller returns the populated envelope produced by the
 * service ({@code CommSuccess="Y"} over the updated type, interest rate, and
 * overdraft limit, with both unchanged balances echoed) directly at
 * HTTP&nbsp;{@code 200}.</p>
 * <p>The service signals a business rejection by throwing a
 * {@link BusinessRuleException} &mdash; this is deliberate, because the throw is
 * what unwinds the service's {@code @Transactional} boundary. The controller
 * therefore <strong>catches</strong> it (rather than deferring to the generic
 * {@code GlobalExceptionHandler}) and rebuilds the frozen {@code UpdAcc} envelope
 * by echoing the inbound {@code request} with its inner payload's
 * {@code CommSuccess} set to {@code "N"}, returned at HTTP&nbsp;{@code 200}. No
 * fail code is set because the envelope has no such field.</p>
 * <p>Binding failures ({@link Valid @Valid}, HTTP&nbsp;{@code 400}) and any
 * unexpected exception (HTTP&nbsp;{@code 500}) are deliberately NOT caught here;
 * they propagate to the global advice.</p>
 *
 * <p><strong>No mainframe coupling.</strong> This controller imports only Spring
 * MVC, Jakarta Validation, and {@code bank-core} types; it never references
 * {@code com.ibm.cics.server} (JCICS), {@code com.ibm.jzos}, or
 * {@code com.ibm.websphere} &mdash; those legacy runtimes are decommissioned in
 * the target.</p>
 *
 * @see AccountService#updateAccount(UpdateAccountForm)
 * @see UpdateAccountJson
 * @see UpdaccJson
 */
@RestController
@RequestMapping("/updacc")
public class UpdateAccountController
{

	/**
	 * Success-flag value written to the response envelope when a business rule
	 * rejects the update (COBOL {@code COMM-SUCCESS = 'N'}). The {@code UpdAcc}
	 * envelope has no fail-code field, so this flag is the sole failure signal.
	 */
	private static final String FLAG_FAILURE = "N";

	/**
	 * The account business service (UPDACC parity, feature&nbsp;F-012); all
	 * update logic lives here. Injected by constructor for testability and
	 * immutability.
	 */
	private final AccountService accountService;

	/**
	 * Constructs the controller with its collaborating service. The single
	 * collaborator is injected by constructor for testability and immutability.
	 *
	 * @param accountService the account business service that performs the
	 *                        {@code UPDACC} restricted update
	 */
	public UpdateAccountController(AccountService accountService)
	{
		this.accountService = accountService;
	}

	/**
	 * Handles {@code PUT /updacc/update}, applying the {@code UPDACC} restricted
	 * update (account type, interest rate, and overdraft limit only) from the
	 * supplied {@code UpdAcc} envelope.
	 *
	 * <p>The handler adapts the inbound wrapper into an {@link UpdateAccountForm}
	 * (a trivial field copy with two type adaptations &mdash; see the class
	 * Javadoc), delegates the entire update to
	 * {@link AccountService#updateAccount(UpdateAccountForm)}, and returns the
	 * service's populated success envelope at HTTP&nbsp;{@code 200}. A
	 * {@link BusinessRuleException} thrown by the service is caught and rendered
	 * as the frozen failure envelope ({@code CommSuccess="N"}), also at
	 * HTTP&nbsp;{@code 200}.</p>
	 *
	 * @param request the update-account request envelope
	 *                ({@code {"UpdAcc": {...}}}); {@link Valid @Valid} triggers
	 *                Jakarta Bean Validation of the bound payload, a failure of
	 *                which yields HTTP&nbsp;{@code 400} via the global advice
	 * @return the update-account response envelope, always HTTP&nbsp;{@code 200}
	 *         and always carrying the single top-level {@code UpdAcc} key
	 */
	@PutMapping(value = "/update",
			consumes = MediaType.APPLICATION_JSON_VALUE,
			produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<UpdateAccountJson> updateAccount(
			@Valid @RequestBody UpdateAccountJson request)
	{
		try
		{
			// Trivial wrapper -> service-form adaptation (no business logic).
			UpdaccJson in = request.getUpdAcc();
			UpdateAccountForm form = new UpdateAccountForm();
			form.setCustNumber(in.getCommCustno() == null ? null
					: in.getCommCustno().trim());
			// CommAccno is a String on the wire but a primitive int on the form;
			// parse it (null/blank -> 0, which the service resolves to a
			// not-found rejection rendered as CommSuccess="N").
			form.setAcctNumber(parseAccountNumber(in.getCommAccno()));
			// UpdateAccountForm.acctType is the AccountType enum (no String
			// setter), so the raw type string is resolved here; an unknown or
			// blank value becomes null, which the service rejects as UPDACC's
			// blank/invalid-type case.
			String rawAccountType = in.getCommAccountType();
			form.setAcctType(AccountType.isValid(rawAccountType)
					? AccountType.fromValue(rawAccountType)
					: null);
			form.setAcctInterestRate(in.getCommInterestRate());
			form.setAcctOverdraft(in.getCommOverdraft());

			// All UPDACC logic (locate the account, restricted update of
			// type/rate/overdraft, never balances, no PROCTRAN audit row) is
			// owned by the service.
			UpdateAccountJson response = accountService.updateAccount(form);
			return ResponseEntity.ok(response);
		}
		catch (BusinessRuleException ex)
		{
			// The service threw to unwind its @Transactional unit of work. Echo
			// the inbound UpdAcc envelope unchanged except for CommSuccess="N"
			// so the preserved consumer (which tests UpdAcc.CommSuccess == "N")
			// reads the outcome. This envelope has NO fail-code field, so
			// ex.getFailCode() is intentionally not surfaced here. HTTP 200
			// preserves the contract body.
			UpdaccJson echo = request.getUpdAcc();
			if (echo == null)
			{
				echo = new UpdaccJson();
				request.setUpdAcc(echo);
			}
			echo.setCommSuccess(FLAG_FAILURE);
			return ResponseEntity.ok(request);
		}
	}

	/**
	 * Parses the wire account-number {@code String} into the primitive
	 * {@code int} the {@link UpdateAccountForm} expects.
	 *
	 * <p>This is a pure type adaptation, not business logic. A {@code null} or
	 * blank value maps to {@code 0}: the service then builds the zero account
	 * key, finds no matching account, and throws the not-found
	 * {@link BusinessRuleException} that this controller renders as
	 * {@code CommSuccess="N"} &mdash; the correct soft-failure envelope rather
	 * than an HTTP&nbsp;{@code 500}. Leading zeros are accepted (the service
	 * re-pads the value to the fixed width). A genuinely non-numeric value
	 * raises {@link NumberFormatException}, which is intentionally left to
	 * propagate to {@code GlobalExceptionHandler}; a well-formed client never
	 * sends one, because the frozen schema types {@code CommAccno} as an
	 * integer.</p>
	 *
	 * @param raw the wire account-number value (may be {@code null} or blank)
	 * @return the parsed account number, or {@code 0} when the input is
	 *         {@code null} or blank
	 */
	private static int parseAccountNumber(String raw)
	{
		if (raw == null)
		{
			return 0;
		}
		String trimmed = raw.trim();
		if (trimmed.isEmpty())
		{
			return 0;
		}
		return Integer.parseInt(trimmed);
	}

}
