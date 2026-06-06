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

import com.ibm.cics.cip.bank.core.constants.BankConstants;
import com.ibm.cics.cip.bank.core.dto.payment.DbcrJson;
import com.ibm.cics.cip.bank.core.dto.payment.PaymentJson;
import com.ibm.cics.cip.bank.core.exception.BusinessRuleException;
import com.ibm.cics.cip.bank.core.service.PaymentService;

/**
 * REST controller reproducing the frozen z/OS Connect <em>make-payment</em>
 * (debit/credit) endpoint (feature F-019), mapping the {@code Pay} service onto
 * {@link PaymentService#processPayment(PaymentJson)}.
 *
 * <p>The path ({@code PUT /makepayment/dbcr}), HTTP verb, and JSON envelope
 * ({@code {"PAYDBCR": {...}}} on both request and response) are preserved
 * verbatim. {@code DBCRFUN.cbl} is the authoritative behavioural specification,
 * including its facility-type-496 channel restrictions, the signed-amount
 * convention (negative = debit, positive = credit), and the simultaneous update
 * of both the available and actual balances.</p>
 *
 * <h2>Success / failure convention (endpoint-specific, critical)</h2>
 * <p>The consumer validates the response with
 * {@code Integer.parseInt(getPAYDBCR().getCommFailCode())}, so the response
 * <em>must</em> carry a numeric {@code CommFailCode}: {@code "0"} success,
 * {@code "1"} account-not-found, {@code "3"} insufficient funds, {@code "4"}
 * restricted account/facility-type rule. A blank value would raise
 * {@code NumberFormatException} on the consumer, so this controller always
 * writes a numeric code. HTTP 200 is always returned.</p>
 *
 * <p>The service ({@code DBCRFUN} port) owns the debit/credit posting, the
 * facility-type-496 channel restrictions, the overdraft / insufficient-funds
 * checks, the signed-amount convention, and the dual-balance update; it returns
 * the populated {@code PAYDBCR} envelope on success and throws a
 * {@link BusinessRuleException} carrying the single-character fail code on a
 * rule violation. This controller is the thin adapter that pins the response
 * sort code and translates the success/failure outcome into the numeric
 * {@code CommFailCode} the consumer expects.</p>
 */
@RestController
public class PaymentController
{

	/** Logger for request/outcome diagnostics. */
	private static final Logger LOG = LoggerFactory
			.getLogger(PaymentController.class);

	/** Numeric fail code denoting success ({@code Integer.parseInt}-friendly). */
	private static final String SUCCESS_FAIL_CODE = "0";

	/** Success flag value. */
	private static final String FLAG_SUCCESS = "Y";

	/** Failure flag value. */
	private static final String FLAG_FAILURE = "N";

	/** The payment (debit/credit) business service. */
	private final PaymentService paymentService;

	/**
	 * Constructs the controller with its collaborating service.
	 *
	 * @param paymentService the payment business service
	 */
	public PaymentController(PaymentService paymentService)
	{
		this.paymentService = paymentService;
	}

	/**
	 * Processes a debit or credit from the supplied {@code PAYDBCR} envelope.
	 *
	 * @param request the make-payment request envelope
	 * @return the make-payment response envelope, always HTTP 200
	 */
	@PutMapping(path = "/makepayment/dbcr",
			consumes = MediaType.APPLICATION_JSON_VALUE,
			produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<PaymentJson> makePayment(
			@RequestBody PaymentJson request)
	{
		DbcrJson in = request.getPAYDBCR();
		try
		{
			PaymentJson response = paymentService.processPayment(request);
			DbcrJson out = response.getPAYDBCR();
			// Pin the bank sort code and surface the success outcome as the
			// numeric fail code "0" the consumer parses with Integer.parseInt.
			out.setCommSortC(Integer.parseInt(BankConstants.SORT_CODE));
			out.setCommSuccess(FLAG_SUCCESS);
			out.setCommFailCode(SUCCESS_FAIL_CODE);
			LOG.info("Payment applied to account {}, amount {}",
					in.getCommAccno(), in.getCommAmt());
			return ResponseEntity.ok(response);
		}
		catch (BusinessRuleException ex)
		{
			LOG.info("Payment rejected, failCode={}", ex.getFailCode());
			return ResponseEntity.ok(failure(in, ex.getFailCode()));
		}
	}

	/**
	 * Builds the failure envelope, echoing the request and surfacing the numeric
	 * fail code.
	 *
	 * @param in       the original request commarea
	 * @param failCode the COBOL fail code to surface (numeric)
	 * @return the populated failure envelope
	 */
	private PaymentJson failure(DbcrJson in, String failCode)
	{
		DbcrJson out = new DbcrJson();
		out.setCommAccno(in.getCommAccno());
		out.setCommAmt(in.getCommAmt());
		out.setCommSortC(Integer.parseInt(BankConstants.SORT_CODE));
		out.setCommOrigin(in.getCommOrigin());
		out.setCommSuccess(FLAG_FAILURE);
		out.setCommFailCode(failCode);
		PaymentJson envelope = new PaymentJson();
		envelope.setPAYDBCR(out);
		return envelope;
	}

}
