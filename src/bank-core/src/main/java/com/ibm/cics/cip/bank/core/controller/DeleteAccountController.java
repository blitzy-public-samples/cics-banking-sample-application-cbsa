/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.core.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.ibm.cics.cip.bank.core.constants.BankConstants;
import com.ibm.cics.cip.bank.core.dto.DtoFormat;
import com.ibm.cics.cip.bank.core.dto.deleteaccount.DelaccJson;
import com.ibm.cics.cip.bank.core.dto.deleteaccount.DeleteAccountJson;
import com.ibm.cics.cip.bank.core.entity.Account;
import com.ibm.cics.cip.bank.core.exception.BusinessRuleException;
import com.ibm.cics.cip.bank.core.service.AccountService;

/**
 * REST controller reproducing the frozen z/OS Connect <em>delete-account</em>
 * endpoint (feature F-019), mapping the {@code CSaccdel} service onto
 * {@link AccountService#deleteAccount}.
 *
 * <p>The path ({@code DELETE /delacc/remove/{accountNumber}}), HTTP verb, and
 * JSON envelope ({@code {"DelAcc": {...}}}) are preserved verbatim.
 * {@code DELACC.cbl} is the authoritative behavioural specification: it captures
 * the account's terminal balance into an account-close PROCTRAN record and
 * physically removes the account row, all in one transaction.</p>
 *
 * <h2>Success / failure convention (endpoint-specific)</h2>
 * <p>The consumer treats JSON {@code DelAccFailCd == 1} as &quot;account not
 * found&quot;. This controller sets the single-character primary fail code
 * {@code DelAccFailCd} to {@code "0"} on success and {@code "1"} on a
 * {@link BusinessRuleException} (the only failure being the not-found case), and
 * echoes the deleted account's terminal state on success. HTTP 200 is always
 * returned.</p>
 */
@RestController
public class DeleteAccountController
{

	/** Logger for request/outcome diagnostics. */
	private static final Logger LOG = LoggerFactory
			.getLogger(DeleteAccountController.class);

	/** Primary fail code (wire {@code DelAccFailCd}) denoting success. */
	private static final String FAIL_NONE = "0";

	/** Primary fail code (wire {@code DelAccFailCd}) denoting &quot;account not found&quot;. */
	private static final String FAIL_NOT_FOUND = "1";

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
	public DeleteAccountController(AccountService accountService)
	{
		this.accountService = accountService;
	}

	/**
	 * Deletes the account identified by the path variable.
	 *
	 * @param accountNumber the account number to delete
	 * @return the delete-account response envelope, always HTTP 200
	 */
	@DeleteMapping(path = "/delacc/remove/{accountNumber}",
			produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<DeleteAccountJson> deleteAccount(
			@PathVariable long accountNumber)
	{
		try
		{
			Account deleted = accountService.deleteAccount(accountNumber);
			LOG.info("Account deleted: {}",
					deleted.getId().getAccountNumber());
			return ResponseEntity.ok(success(deleted));
		}
		catch (BusinessRuleException ex)
		{
			LOG.info("Delete-account rejected, failCode={}", ex.getFailCode());
			return ResponseEntity.ok(failure(accountNumber));
		}
	}

	/**
	 * Builds the success envelope echoing the deleted account's terminal state.
	 *
	 * @param account the deleted account (detached snapshot with terminal
	 *                balances)
	 * @return the populated success envelope
	 */
	private DeleteAccountJson success(Account account)
	{
		DelaccJson out = new DelaccJson();
		out.setDelaccAccno(account.getId().getAccountNumber());
		out.setDelaccSortcode(account.getId().getSortCode());
		out.setDelaccCustno(account.getCustomerNumber());
		out.setDelaccAccType(account.getAccountType());
		out.setDelaccInterestRate(account.getInterestRate());
		out.setDelaccOverdraft(account.getOverdraftLimit() == null ? 0
				: account.getOverdraftLimit().intValue());
		out.setDelaccAvailableBalance(account.getAvailableBalance());
		out.setDelaccActualBalance(account.getActualBalance());
		out.setDelaccOpened(DtoFormat.dateToString(account.getOpened()));
		out.setDelaccLastStatementDate(
				DtoFormat.dateToString(account.getLastStatementDate()));
		out.setDelaccNextStatementDate(
				DtoFormat.dateToString(account.getNextStatementDate()));
		out.setDelaccFailCode(FAIL_NONE);
		out.setDelaccSuccess(FLAG_SUCCESS);
		out.setDelaccDelSuccess(FLAG_SUCCESS);
		return new DeleteAccountJson(out);
	}

	/**
	 * Builds the not-found failure envelope.
	 *
	 * @param accountNumber the account number that was not found
	 * @return the populated failure envelope
	 */
	private DeleteAccountJson failure(long accountNumber)
	{
		DelaccJson out = new DelaccJson();
		out.setDelaccAccno(String.format("%08d", accountNumber));
		out.setDelaccSortcode(BankConstants.SORT_CODE);
		out.setDelaccFailCode(FAIL_NOT_FOUND);
		out.setDelaccSuccess(FLAG_FAILURE);
		out.setDelaccDelSuccess(FLAG_FAILURE);
		return new DeleteAccountJson(out);
	}

}
