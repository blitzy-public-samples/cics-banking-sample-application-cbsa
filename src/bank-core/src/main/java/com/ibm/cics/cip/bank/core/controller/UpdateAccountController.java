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

import com.ibm.cics.cip.bank.core.dto.DtoFormat;
import com.ibm.cics.cip.bank.core.dto.updateaccount.UpdaccJson;
import com.ibm.cics.cip.bank.core.dto.updateaccount.UpdateAccountJson;
import com.ibm.cics.cip.bank.core.entity.Account;
import com.ibm.cics.cip.bank.core.exception.BusinessRuleException;
import com.ibm.cics.cip.bank.core.service.AccountService;

/**
 * REST controller reproducing the frozen z/OS Connect <em>update-account</em>
 * endpoint (feature F-019), mapping the {@code CSaccupd} service onto
 * {@link AccountService#updateAccount}.
 *
 * <p>The path ({@code PUT /updacc/update}), HTTP verb, and JSON envelope
 * ({@code {"UpdAcc": {...}}}) are preserved verbatim. {@code UPDACC.cbl} is the
 * authoritative behavioural specification: it changes only the account type,
 * interest rate, and overdraft limit (never balances), and it writes no PROCTRAN
 * record.</p>
 *
 * <h2>Success / failure convention (endpoint-specific)</h2>
 * <p>This envelope carries no fail-code field; the consumer treats
 * {@code CommSuccess="N"} as failure. This controller sets {@code CommSuccess="Y"}
 * on success and {@code CommSuccess="N"} on a {@link BusinessRuleException} (the
 * only failure being {@code "1"} when the account does not exist). HTTP 200 is
 * always returned.</p>
 */
@RestController
public class UpdateAccountController
{

	/** Logger for request/outcome diagnostics. */
	private static final Logger LOG = LoggerFactory
			.getLogger(UpdateAccountController.class);

	/** Success flag value. */
	private static final String FLAG_SUCCESS = "Y";

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
		UpdaccJson in = request.getUpdacc();
		long accountNumber = Long.parseLong(in.getCommAccno().trim());

		try
		{
			Account updated = accountService.updateAccount(accountNumber,
					in.getCommAccountType(), in.getCommInterestRate(),
					in.getCommOverdraft());
			LOG.info("Account updated: {}",
					updated.getId().getAccountNumber());
			return ResponseEntity.ok(success(updated));
		}
		catch (BusinessRuleException ex)
		{
			LOG.info("Update-account rejected, failCode={}", ex.getFailCode());
			return ResponseEntity.ok(failure(in));
		}
	}

	/**
	 * Builds the success envelope echoing the persisted account.
	 *
	 * @param account the persisted account
	 * @return the populated success envelope
	 */
	private UpdateAccountJson success(Account account)
	{
		UpdaccJson out = new UpdaccJson();
		out.setCommCustno(account.getCustomerNumber());
		out.setCommSortcode(account.getId().getSortCode());
		out.setCommAccno(account.getId().getAccountNumber());
		out.setCommInterestRate(account.getInterestRate());
		out.setCommOpened(DtoFormat.dateToString(account.getOpened()));
		out.setCommOverdraft(account.getOverdraftLimit() == null ? 0
				: account.getOverdraftLimit().intValue());
		out.setCommLastStatementDate(
				DtoFormat.dateToString(account.getLastStatementDate()));
		out.setCommNextStatementDate(
				DtoFormat.dateToString(account.getNextStatementDate()));
		out.setCommAvailableBalance(account.getAvailableBalance());
		out.setCommActualBalance(account.getActualBalance());
		out.setCommAccountType(account.getAccountType());
		out.setCommSuccess(FLAG_SUCCESS);
		return new UpdateAccountJson(out);
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
