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
import com.ibm.cics.cip.bank.core.dto.deleteaccount.DelaccJson;
import com.ibm.cics.cip.bank.core.dto.deleteaccount.DeleteAccountJson;
import com.ibm.cics.cip.bank.core.exception.BusinessRuleException;
import com.ibm.cics.cip.bank.core.service.AccountService;

/**
 * REST controller reproducing the frozen z/OS Connect <em>delete-account</em>
 * endpoint (feature F-019), mapping the {@code CSaccdel} service onto
 * {@link AccountService#deleteAccount(long)}.
 *
 * <p>The path ({@code DELETE /delacc/remove/{accountNumber}}), HTTP verb, and
 * JSON envelope ({@code {"DelAcc": {...}}}) are preserved verbatim.
 * {@code DELACC.cbl} is the authoritative behavioural specification: it captures
 * the account's terminal balance into an account-close PROCTRAN record and
 * physically removes the account row, all in one transaction. The service
 * returns the fully populated success envelope (both terminal balances).</p>
 *
 * <h2>Success / failure convention (endpoint-specific)</h2>
 * <p>The consumer treats JSON {@code DelAccFailCd == 1} as &quot;account not
 * found&quot;. The service sets the single-character primary fail code
 * {@code DelAccFailCd} to {@code "0"} on success. On a
 * {@link BusinessRuleException} carrying a single-character fail code (the
 * not-found {@code "1"} or delete-failed {@code "3"} cases) this controller
 * surfaces that code on a {@code CommSuccess="N"} envelope; a multi-character
 * abend marker (such as {@code "HRAC"} on a read SQL error) is re-thrown so the
 * global handler renders it, because the wire field is single-character. HTTP
 * 200 is returned for the modelled business outcomes.</p>
 */
@RestController
public class DeleteAccountController
{

	/** Logger for request/outcome diagnostics. */
	private static final Logger LOG = LoggerFactory
			.getLogger(DeleteAccountController.class);

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
	 * @return the delete-account response envelope, HTTP 200 for modelled
	 *         business outcomes
	 */
	@DeleteMapping(path = "/delacc/remove/{accountNumber}",
			produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<DeleteAccountJson> deleteAccount(
			@PathVariable long accountNumber)
	{
		try
		{
			DeleteAccountJson response = accountService
					.deleteAccount(accountNumber);
			LOG.info("Account deleted: {}", accountNumber);
			return ResponseEntity.ok(response);
		}
		catch (BusinessRuleException ex)
		{
			LOG.info("Delete-account rejected, failCode={}", ex.getFailCode());
			String failCode = ex.getFailCode();
			if (failCode != null && failCode.length() == 1)
			{
				return ResponseEntity.ok(failure(accountNumber, failCode));
			}
			// A multi-character abend marker (e.g. "HRAC") cannot fit the
			// single-character wire field; let the global handler render it.
			throw ex;
		}
	}

	/**
	 * Builds the failure envelope, echoing the requested account number and the
	 * verbatim single-character fail code.
	 *
	 * @param accountNumber the account number that failed to delete
	 * @param failCode      the single-character COBOL fail code to surface
	 * @return the populated failure envelope
	 */
	private DeleteAccountJson failure(long accountNumber, String failCode)
	{
		DelaccJson out = new DelaccJson();
		out.setDelaccAccno(String.format("%08d", accountNumber));
		out.setDelaccSortcode(BankConstants.SORT_CODE);
		out.setDelaccFailCode(failCode);
		out.setDelaccSuccess(FLAG_FAILURE);
		out.setDelaccDelSuccess(FLAG_FAILURE);
		return new DeleteAccountJson(out);
	}

}
