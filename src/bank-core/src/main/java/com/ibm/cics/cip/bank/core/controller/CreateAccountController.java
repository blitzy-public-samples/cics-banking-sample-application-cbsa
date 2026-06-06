/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.core.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.ibm.cics.cip.bank.core.dto.DtoFormat;
import com.ibm.cics.cip.bank.core.dto.common.CommKey;
import com.ibm.cics.cip.bank.core.dto.createaccount.CreaccJson;
import com.ibm.cics.cip.bank.core.dto.createaccount.CreateAccountJson;
import com.ibm.cics.cip.bank.core.entity.Account;
import com.ibm.cics.cip.bank.core.exception.BusinessRuleException;
import com.ibm.cics.cip.bank.core.service.AccountService;

/**
 * REST controller reproducing the frozen z/OS Connect <em>create-account</em>
 * endpoint (feature F-019), mapping the {@code CSacccre} service onto
 * {@link AccountService#createAccount}.
 *
 * <p>The path ({@code POST /creacc/insert}), HTTP verb, and JSON envelope
 * ({@code {"CreAcc": {...}}}) are preserved verbatim. {@code CREACC.cbl} is the
 * authoritative behavioural specification, including its ordered validation:
 * customer must exist (fail {@code "1"}), no more than ten accounts (fail
 * {@code "8"}), and a valid account type (fail {@code "A"}).</p>
 *
 * <h2>Success / failure convention (endpoint-specific)</h2>
 * <p>The consumer treats the response as a failure when {@code CommSuccess}
 * equals {@code "N"}. On success this controller sets {@code CommSuccess="Y"};
 * on a {@link BusinessRuleException} it sets {@code CommSuccess="N"} with the
 * verbatim fail code and echoes {@code CommCustno} (the consumer parses it as an
 * integer when reporting the {@code "8"} too-many-accounts case). The response
 * is HTTP 200 because the consumer inspects the body for business outcomes.</p>
 */
@RestController
public class CreateAccountController
{

	/** Logger for request/outcome diagnostics. */
	private static final Logger LOG = LoggerFactory
			.getLogger(CreateAccountController.class);

	/** Success sentinel for the fail-code field on a successful create. */
	private static final String SUCCESS_FAIL_CODE = "";

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
	public CreateAccountController(AccountService accountService)
	{
		this.accountService = accountService;
	}

	/**
	 * Creates an account from the supplied envelope.
	 *
	 * @param request the create-account request envelope
	 * @return the create-account response envelope, always HTTP 200
	 */
	@PostMapping(path = "/creacc/insert",
			consumes = MediaType.APPLICATION_JSON_VALUE,
			produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<CreateAccountJson> createAccount(
			@RequestBody CreateAccountJson request)
	{
		CreaccJson in = request.getCreAcc();
		long customerNumber = Long.parseLong(in.getCommCustno().trim());
		Integer overdraftLimit = in.getCommOverdraftLimit() == null
				? Integer.valueOf(0)
				: Integer.valueOf(in.getCommOverdraftLimit().intValue());

		try
		{
			Account created = accountService.createAccount(customerNumber,
					in.getCommAccType(), in.getCommInterestRate(),
					overdraftLimit);
			LOG.info("Account created: {}",
					created.getId().getAccountNumber());
			return ResponseEntity.ok(success(created));
		}
		catch (BusinessRuleException ex)
		{
			LOG.info("Create-account rejected, failCode={}", ex.getFailCode());
			return ResponseEntity.ok(failure(in, ex.getFailCode()));
		}
	}

	/**
	 * Builds the success envelope echoing the persisted account.
	 *
	 * @param account the persisted account
	 * @return the populated success envelope
	 */
	private CreateAccountJson success(Account account)
	{
		CreaccJson out = new CreaccJson();
		out.setCommAccType(account.getAccountType());
		out.setCommCustno(account.getCustomerNumber());
		out.setCommKey(new CommKey(
				Integer.parseInt(account.getId().getSortCode()),
				Long.parseLong(account.getId().getAccountNumber())));
		out.setCommInterestRate(account.getInterestRate());
		out.setCommOpened(DtoFormat.dateToInt(account.getOpened()));
		out.setCommOverdraftLimit(account.getOverdraftLimit() == null
				? Integer.valueOf(0)
				: account.getOverdraftLimit());
		out.setCommLastStatementDate(
				DtoFormat.dateToInt(account.getLastStatementDate()));
		out.setCommNextStatementDate(
				DtoFormat.dateToInt(account.getNextStatementDate()));
		out.setCommAvailableBalance(account.getAvailableBalance());
		out.setCommActualBalance(account.getActualBalance());
		out.setCommSuccess(FLAG_SUCCESS);
		out.setCommFailCode(SUCCESS_FAIL_CODE);
		return new CreateAccountJson(out);
	}

	/**
	 * Builds the failure envelope, echoing the caller's customer number (which
	 * the consumer parses for the {@code "8"} case) and the verbatim fail code.
	 *
	 * @param in       the original request commarea
	 * @param failCode the COBOL fail code to surface
	 * @return the populated failure envelope
	 */
	private CreateAccountJson failure(CreaccJson in, String failCode)
	{
		CreaccJson out = new CreaccJson();
		out.setCommAccType(in.getCommAccType());
		out.setCommCustno(in.getCommCustno());
		out.setCommSuccess(FLAG_FAILURE);
		out.setCommFailCode(failCode);
		return new CreateAccountJson(out);
	}

}
