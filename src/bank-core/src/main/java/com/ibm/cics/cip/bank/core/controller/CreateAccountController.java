/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.core.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ibm.cics.cip.bank.core.domain.AccountType;
import com.ibm.cics.cip.bank.core.dto.createaccount.CreaccJson;
import com.ibm.cics.cip.bank.core.dto.createaccount.CreateAccountForm;
import com.ibm.cics.cip.bank.core.dto.createaccount.CreateAccountJson;
import com.ibm.cics.cip.bank.core.exception.BusinessRuleException;
import com.ibm.cics.cip.bank.core.service.AccountService;

import jakarta.validation.Valid;

/**
 * REST controller reproducing the frozen z/OS Connect <em>create-account</em>
 * ({@code creacc}) endpoint VERBATIM (feature&nbsp;F-019), mapping the
 * {@code CSacccre} service onto
 * {@link AccountService#createAccount(CreateAccountForm)}.
 *
 * <h2>Frozen contract (pinned, never altered)</h2>
 * <p>The HTTP method, path, and JSON envelope are reproduced byte-for-byte from
 * the authoritative {@code src/zosconnect_artefacts/apis/creacc/api-docs/swagger.json}
 * (operationId {@code postCSacccre}):</p>
 * <ul>
 *   <li><strong>{@code POST /creacc/insert}</strong> &mdash; declared as the
 *       class-level base path {@code /creacc}
 *       ({@link RequestMapping @RequestMapping}) plus the handler-level relative
 *       path {@code /insert} ({@link PostMapping @PostMapping}); the application
 *       runs at the ROOT context (port&nbsp;8080) with no servlet context-path,
 *       so the absolute path is exactly {@code /creacc/insert};</li>
 *   <li><strong>{@code consumes}/{@code produces} {@code application/json}</strong>
 *       with a single HTTP&nbsp;{@code 200} response in every outcome (success
 *       and business rejection alike);</li>
 *   <li>the request body and the response body share the identical top-level
 *       envelope key {@code "CreAcc"} ({@code {"CreAcc": { &hellip; }}}),
 *       modelled by the outer wrapper {@link CreateAccountJson} over the inner
 *       commarea {@link CreaccJson}.</li>
 * </ul>
 *
 * <h2>Thin adapter &mdash; no business logic here</h2>
 * <p>{@code CREACC.cbl} is the authoritative behavioural specification and ALL
 * of its logic lives in {@link AccountService#createAccount(CreateAccountForm)}:
 * the ordered five-step create (validate the owning customer exists &rarr; fail
 * {@code "1"}; enforce the maximum of ten accounts &rarr; fail {@code "8"};
 * validate the account type &rarr; fail {@code "A"}; allocate the account number
 * <em>last</em>; insert the account and append the {@code PROCTRAN} audit row),
 * so that a failed validation rolls back the consumed account number under the
 * service's {@link org.springframework.transaction.annotation.Transactional
 * &#64;Transactional} boundary. This controller performs only request binding,
 * a trivial wrapper&rarr;form field copy, delegation, and response shaping. It
 * contains no account-numbering, no balance arithmetic, no ten-account ceiling,
 * and no Jackson configuration.</p>
 *
 * <h2>Request adaptation (trivial copy only)</h2>
 * <p>The inbound {@link CreaccJson} commarea is adapted into the service's
 * {@link CreateAccountForm} input by a straight field copy of the four
 * client-supplied inputs ({@code CommCustno}&rarr;{@code custNumber},
 * {@code CommAccType}&rarr;{@code accountType},
 * {@code CommOverdrLim}&rarr;{@code overdraftLimit},
 * {@code CommIntRt}&rarr;{@code interestRate}). Because {@code CreateAccountForm}
 * types the account type as the domain {@link AccountType} enum (it exposes no
 * {@code String} setter for it), the raw account-type string is resolved through
 * {@link AccountType#isValid(String)} / {@link AccountType#fromValue(String)};
 * an unrecognised or blank value resolves to {@code null}, which the service maps
 * onto the COBOL fail code {@code "A"} (invalid account type). This enum
 * resolution is the established repository pattern (mirroring
 * {@code UpdateAccountController}); it is a typing concern only and introduces no
 * business decision in the controller.</p>
 *
 * <h2>Dual failure handling (byte-for-byte envelope fidelity)</h2>
 * <p>On success the controller returns the populated success envelope produced by
 * the service ({@code CommSuccess="Y"}, allocated {@code CommKey}, both balances
 * {@code 0.00}, opened date) directly at HTTP&nbsp;{@code 200}.</p>
 * <p>The service signals a business rejection by throwing a
 * {@link BusinessRuleException} &mdash; this is deliberate, because the throw is
 * what unwinds the {@code @Transactional} boundary and rolls back the allocated
 * account number. The controller therefore <strong>catches</strong> it (rather
 * than deferring to the generic {@code GlobalExceptionHandler}) and rebuilds the
 * frozen {@code CreAcc} envelope by echoing the inbound {@code request} with its
 * inner payload's {@code CommSuccess} set to {@code "N"} and {@code CommFailCode}
 * set to the verbatim COBOL fail code, returned at HTTP&nbsp;{@code 200}. This is
 * required for the preserved consumer, which inspects
 * {@code CreAcc.CommSuccess}/{@code CreAcc.CommFailCode} (and, for the {@code "8"}
 * too-many-accounts case, re-reads {@code CreAcc.CommCustno}); a generic advice
 * body would lack the {@code CreAcc} envelope and break that consumer.</p>
 * <p>Binding failures ({@link Valid @Valid}, HTTP&nbsp;{@code 400}) and any
 * unexpected exception (HTTP&nbsp;{@code 500}) are deliberately NOT caught here;
 * they propagate to the global advice.</p>
 */
@RestController
@RequestMapping("/creacc")
public class CreateAccountController
{

	/** Success-flag value written to the response envelope ({@code COMM-SUCCESS = 'N'} on rejection). */
	private static final String FLAG_FAILURE = "N";

	/** The account business service (CREACC parity, F-007); all create logic lives here. */
	private final AccountService accountService;

	/**
	 * Constructs the controller with its collaborating service. The single
	 * collaborator is injected by constructor for testability and immutability.
	 *
	 * @param accountService the account business service that performs the
	 *                        {@code CREACC} create sequence
	 */
	public CreateAccountController(AccountService accountService)
	{
		this.accountService = accountService;
	}

	/**
	 * Handles {@code POST /creacc/insert}, creating an account for an existing
	 * customer from the supplied {@code CreAcc} envelope.
	 *
	 * <p>The handler adapts the inbound wrapper into a {@link CreateAccountForm},
	 * delegates the entire create sequence to
	 * {@link AccountService#createAccount(CreateAccountForm)}, and returns the
	 * service's populated success envelope at HTTP&nbsp;{@code 200}. A
	 * {@link BusinessRuleException} thrown by the service is caught and rendered
	 * as the frozen failure envelope ({@code CommSuccess="N"} plus the verbatim
	 * fail code), also at HTTP&nbsp;{@code 200}.</p>
	 *
	 * @param request the create-account request envelope ({@code {"CreAcc": {...}}});
	 *                {@link Valid @Valid} triggers Jakarta Bean Validation of the
	 *                bound payload, a failure of which yields HTTP&nbsp;{@code 400}
	 *                via the global advice
	 * @return the create-account response envelope, always HTTP&nbsp;{@code 200}
	 *         and always carrying the single top-level {@code CreAcc} key
	 */
	@PostMapping(value = "/insert",
			consumes = MediaType.APPLICATION_JSON_VALUE,
			produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<CreateAccountJson> createAccount(
			@Valid @RequestBody CreateAccountJson request)
	{
		try
		{
			// Trivial wrapper -> service-form adaptation (no business logic).
			CreaccJson in = request.getCreAcc();
			CreateAccountForm form = new CreateAccountForm();
			form.setCustNumber(in.getCommCustno() == null ? null
					: in.getCommCustno().trim());
			// CreateAccountForm.accountType is the AccountType enum (no String
			// setter), so the raw type string is resolved here; an unknown or
			// blank value becomes null, which the service rejects with fail 'A'.
			String rawAccountType = in.getCommAccType();
			form.setAccountType(AccountType.isValid(rawAccountType)
					? AccountType.fromValue(rawAccountType)
					: null);
			form.setOverdraftLimit(in.getCommOverdraftLimit());
			form.setInterestRate(in.getCommInterestRate());

			// All CREACC logic (validation ordering, gap-free number
			// allocation, PROCTRAN audit) is owned by the service.
			CreateAccountJson response = accountService.createAccount(form);
			return ResponseEntity.ok(response);
		}
		catch (BusinessRuleException ex)
		{
			// The service threw to roll back the allocated account number under
			// @Transactional. Rebuild the frozen CreAcc envelope by echoing the
			// request with the failure flag and verbatim fail code so the
			// preserved consumer reads CreAcc.CommSuccess / CommFailCode (and the
			// echoed CommCustno) unchanged. HTTP 200 preserves the contract body.
			CreaccJson echo = request.getCreAcc();
			if (echo == null)
			{
				echo = new CreaccJson();
				request.setCreAcc(echo);
			}
			echo.setCommSuccess(FLAG_FAILURE);
			echo.setCommFailCode(ex.getFailCode());
			return ResponseEntity.ok(request);
		}
	}

}
