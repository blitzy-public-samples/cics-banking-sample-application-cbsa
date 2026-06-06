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

import com.ibm.cics.cip.bank.core.dto.transfer.TransferJson;
import com.ibm.cics.cip.bank.core.dto.transfer.TransferResultJson;
import com.ibm.cics.cip.bank.core.exception.BusinessRuleException;
import com.ibm.cics.cip.bank.core.service.TransferService;
import com.ibm.cics.cip.bank.core.service.TransferService.TransferResult;

/**
 * REST controller for the {@code bank-core} <em>transfer</em> endpoint, mapping
 * onto {@link TransferService#transfer} (which reproduces {@code XFRFUN.cbl}).
 *
 * <p><strong>Not a frozen z/OS Connect contract.</strong> {@code XFRFUN} is one
 * of the thirteen business programs but is not among the ten frozen z/OS Connect
 * endpoints (feature F-019). This is therefore a purpose-built internal endpoint
 * ({@code PUT /transfer}) consumed by the {@code webui} adapter, which maps its
 * own caller request onto {@link TransferJson} and maps {@link TransferResultJson}
 * back to the {@code /webui-1.0/banking/*} response.</p>
 *
 * <h2>Success / failure convention</h2>
 * <p>On success this controller sets {@code success="Y"} with an empty fail code
 * and returns both post-transfer balances of both accounts. On a
 * {@link BusinessRuleException} it sets {@code success="N"} and surfaces the
 * verbatim {@code XFRFUN} fail code ({@code "4"} non-positive amount,
 * {@code "1"} source not found, {@code "2"} target not found, {@code "3"}
 * deadlock/lock failure, or {@code "SAME"} for a same-account transfer). HTTP
 * 200 is always returned.</p>
 */
@RestController
public class TransferController
{

	/** Logger for request/outcome diagnostics. */
	private static final Logger LOG = LoggerFactory
			.getLogger(TransferController.class);

	/** Success sentinel for the fail-code field. */
	private static final String SUCCESS_FAIL_CODE = "";

	/** Success flag value. */
	private static final String FLAG_SUCCESS = "Y";

	/** Failure flag value. */
	private static final String FLAG_FAILURE = "N";

	/** The transfer business service. */
	private final TransferService transferService;

	/**
	 * Constructs the controller with its collaborating service.
	 *
	 * @param transferService the transfer business service
	 */
	public TransferController(TransferService transferService)
	{
		this.transferService = transferService;
	}

	/**
	 * Transfers funds between two accounts at this bank.
	 *
	 * @param request the transfer request envelope
	 * @return the transfer response envelope, always HTTP 200
	 */
	@PutMapping(path = "/transfer",
			consumes = MediaType.APPLICATION_JSON_VALUE,
			produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<TransferResultJson> transfer(
			@RequestBody TransferJson request)
	{
		try
		{
			TransferResult result = transferService.transfer(
					request.getFromAccount(), request.getToAccount(),
					request.getAmount());
			LOG.info("Transfer {} -> {} succeeded", request.getFromAccount(),
					request.getToAccount());
			return ResponseEntity.ok(success(result));
		}
		catch (BusinessRuleException ex)
		{
			LOG.info("Transfer rejected, failCode={}", ex.getFailCode());
			return ResponseEntity.ok(failure(ex.getFailCode()));
		}
	}

	/**
	 * Builds the success envelope from the transfer result.
	 *
	 * @param result the transfer result (both updated accounts)
	 * @return the populated success envelope
	 */
	private TransferResultJson success(TransferResult result)
	{
		TransferResultJson out = new TransferResultJson();
		out.setSuccess(FLAG_SUCCESS);
		out.setFailCode(SUCCESS_FAIL_CODE);
		out.setFromAccountNumber(result.getSource().getId().getAccountNumber());
		out.setFromAvailableBalance(result.getSource().getAvailableBalance());
		out.setFromActualBalance(result.getSource().getActualBalance());
		out.setToAccountNumber(result.getTarget().getId().getAccountNumber());
		out.setToAvailableBalance(result.getTarget().getAvailableBalance());
		out.setToActualBalance(result.getTarget().getActualBalance());
		return out;
	}

	/**
	 * Builds the failure envelope surfacing the verbatim fail code.
	 *
	 * @param failCode the {@code XFRFUN} fail code to surface
	 * @return the populated failure envelope
	 */
	private TransferResultJson failure(String failCode)
	{
		TransferResultJson out = new TransferResultJson();
		out.setSuccess(FLAG_FAILURE);
		out.setFailCode(failCode);
		return out;
	}

}
