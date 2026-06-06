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

import com.ibm.cics.cip.bank.core.domain.AccountType;
import com.ibm.cics.cip.bank.core.dto.createaccount.CreaccJson;
import com.ibm.cics.cip.bank.core.dto.createaccount.CreateAccountForm;
import com.ibm.cics.cip.bank.core.dto.createaccount.CreateAccountJson;
import com.ibm.cics.cip.bank.core.exception.BusinessRuleException;
import com.ibm.cics.cip.bank.core.service.AccountService;

/**
 * REST controller reproducing the frozen z/OS Connect <em>create-account</em>
 * endpoint (feature F-019), mapping the {@code CSacccre} service onto
 * {@link AccountService#createAccount(CreateAccountForm)}.
 *
 * <p>The path ({@code POST /creacc/insert}), HTTP verb, and JSON envelope
 * ({@code {"CreAcc": {...}}}) are preserved verbatim. {@code CREACC.cbl} is the
 * authoritative behavioural specification, including its ordered validation:
 * customer must exist (fail {@code "1"}), no more than ten accounts (fail
 * {@code "8"}), and a valid account type (fail {@code "A"}).</p>
 *
 * <h2>Request adaptation</h2>
 * <p>The inbound {@link CreaccJson} commarea is adapted into the service's
 * {@link CreateAccountForm} input: the customer number is trimmed, and the raw
 * account-type string is resolved to an {@link AccountType} (an unrecognised
 * value becomes {@code null}, which the service rejects with fail code
 * {@code "A"}). The service performs the ordered validation and returns the
 * fully populated success envelope.</p>
 *
 * <h2>Success / failure convention (endpoint-specific)</h2>
 * <p>The consumer treats the response as a failure when {@code CommSuccess}
 * equals {@code "N"}. The service builds the {@code CommSuccess="Y"} success
 * envelope; on a {@link BusinessRuleException} this controller sets
 * {@code CommSuccess="N"} with the verbatim fail code and echoes
 * {@code CommCustno} (the consumer parses it as an integer when reporting the
 * {@code "8"} too-many-accounts case). The response is HTTP 200 because the
 * consumer inspects the body for business outcomes.</p>
 */
@RestController
public class CreateAccountController
{

	/** Logger for request/outcome diagnostics. */
	private static final Logger LOG = LoggerFactory
			.getLogger(CreateAccountController.class);

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
		try
		{
			CreateAccountForm form = new CreateAccountForm();
			form.setCustNumber(in.getCommCustno() == null ? null
					: in.getCommCustno().trim());
			String rawType = in.getCommAccType();
			form.setAccountType(AccountType.isValid(rawType)
					? AccountType.fromValue(rawType)
					: null);
			form.setOverdraftLimit(in.getCommOverdraftLimit());
			form.setInterestRate(in.getCommInterestRate());

			CreateAccountJson response = accountService.createAccount(form);
			LOG.info("Account created for customer {}", form.getCustNumber());
			return ResponseEntity.ok(response);
		}
		catch (BusinessRuleException ex)
		{
			LOG.info("Create-account rejected, failCode={}", ex.getFailCode());
			return ResponseEntity.ok(failure(in, ex.getFailCode()));
		}
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
