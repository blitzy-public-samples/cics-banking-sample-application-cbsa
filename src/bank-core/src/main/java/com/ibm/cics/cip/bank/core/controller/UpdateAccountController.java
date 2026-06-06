/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.core.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.ibm.cics.cip.bank.core.domain.AccountType;
import com.ibm.cics.cip.bank.core.dto.updateaccount.UpdaccJson;
import com.ibm.cics.cip.bank.core.dto.updateaccount.UpdateAccountForm;
import com.ibm.cics.cip.bank.core.dto.updateaccount.UpdateAccountJson;
import com.ibm.cics.cip.bank.core.exception.BusinessRuleException;
import com.ibm.cics.cip.bank.core.service.AccountService;

/**
 * REST controller reproducing the frozen z/OS Connect <em>update-account</em>
 * endpoint (feature F-019), mapping the {@code CSaccupd} service onto
 * {@link AccountService#updateAccount(UpdateAccountForm)}.
 *
 * <p>The path ({@code PUT /updacc/update}), HTTP verb, and JSON envelope
 * ({@code {"UpdAcc": {...}}}) are preserved verbatim. {@code UPDACC.cbl} is the
 * authoritative behavioural specification: it changes only the account type,
 * interest rate, and overdraft limit (never balances), and it writes no PROCTRAN
 * record.</p>
 *
 * <h2>Request adaptation</h2>
 * <p>The inbound {@link UpdaccJson} commarea is adapted into the service's
 * {@link UpdateAccountForm} input: the account number is parsed and the raw
 * account-type string is resolved to an {@link AccountType} (an unrecognised or
 * blank value becomes {@code null}, which the service treats as the
 * {@code "spaces"} rejection).</p>
 *
 * <h2>Success / failure convention (endpoint-specific)</h2>
 * <p>This envelope carries no fail-code field; the consumer treats
 * {@code CommSuccess="N"} as failure. The service builds the
 * {@code CommSuccess="Y"} success envelope and throws a
 * {@link BusinessRuleException} for a missing account or blank type, which this
 * controller renders as {@code CommSuccess="N"}. HTTP 200 is always
 * returned.</p>
 */
@RestController
public class UpdateAccountController
{

	/** Logger for request/outcome diagnostics. */
	private static final Logger LOG = LoggerFactory
			.getLogger(UpdateAccountController.class);

	/** Failure flag value. */
	private static final String FLAG_FAILURE = "N";

	/** The account business service. */
	private final AccountService accountService;

	/**
	 * Constructs the controller with its collaborating service.
	 *
	 * @param accountService the account business service
	 */
	public UpdateAccountController(AccountService accountService)
	{
		this.accountService = accountService;
	}

	/**
	 * Updates an account's type, interest rate, and overdraft limit from the
	 * supplied envelope.
	 *
	 * @param request the update-account request envelope
	 * @return the update-account response envelope, always HTTP 200
	 */
	@PutMapping(path = "/updacc/update",
			consumes = MediaType.APPLICATION_JSON_VALUE,
			produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<UpdateAccountJson> updateAccount(
			@RequestBody UpdateAccountJson request)
	{
		UpdaccJson in = request.getUpdAcc();
		try
		{
			UpdateAccountForm form = new UpdateAccountForm();
			form.setCustNumber(in.getCommCustno() == null ? null
					: in.getCommCustno().trim());
			form.setAcctNumber((int) Long.parseLong(in.getCommAccno().trim()));
			String rawType = in.getCommAccountType();
			form.setAcctType(AccountType.isValid(rawType)
					? AccountType.fromValue(rawType)
					: null);
			form.setAcctInterestRate(in.getCommInterestRate());
			form.setAcctOverdraft(in.getCommOverdraft());

			UpdateAccountJson response = accountService.updateAccount(form);
			LOG.info("Account updated: {}", in.getCommAccno());
			return ResponseEntity.ok(response);
		}
		catch (BusinessRuleException ex)
		{
			LOG.info("Update-account rejected, failCode={}", ex.getFailCode());
			return ResponseEntity.ok(failure(in));
		}
	}

	/**
	 * Builds the failure envelope, echoing the caller's account number and
	 * flagging {@code CommSuccess="N"}.
	 *
	 * @param in the original request commarea
	 * @return the populated failure envelope
	 */
	private UpdateAccountJson failure(UpdaccJson in)
	{
		UpdaccJson out = new UpdaccJson();
		out.setCommAccno(in.getCommAccno());
		out.setCommCustno(in.getCommCustno());
		out.setCommAccountType(in.getCommAccountType());
		out.setCommSuccess(FLAG_FAILURE);
		return new UpdateAccountJson(out);
	}

}
