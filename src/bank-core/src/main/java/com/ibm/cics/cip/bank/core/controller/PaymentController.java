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
import com.ibm.cics.cip.bank.core.dto.payment.OriginJson;
import com.ibm.cics.cip.bank.core.dto.payment.PaymentInterfaceJson;
import com.ibm.cics.cip.bank.core.entity.Account;
import com.ibm.cics.cip.bank.core.exception.BusinessRuleException;
import com.ibm.cics.cip.bank.core.service.PaymentService;

/**
 * REST controller reproducing the frozen z/OS Connect <em>make-payment</em>
 * (debit/credit) endpoint (feature F-019), mapping the {@code Pay} service onto
 * {@link PaymentService#processDebitCredit}.
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
	 * Processes a debit or credit from the supplied envelope.
	 *
	 * @param request the make-payment request envelope
	 * @return the make-payment response envelope, always HTTP 200
	 */
	@PutMapping(path = "/makepayment/dbcr",
			consumes = MediaType.APPLICATION_JSON_VALUE,
			produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<PaymentInterfaceJson> makePayment(
			@RequestBody PaymentInterfaceJson request)
	{
		DbcrJson in = request.getPAYDBCR();
		long accountNumber = Long.parseLong(in.getCommAccno().trim());
		int facilityType = resolveFacilityType(in.getCommOrigin());
		String origin = resolveOrigin(in.getCommOrigin());

		try
		{
			Account updated = paymentService.processDebitCredit(accountNumber,
					in.getCommAmt(), facilityType, origin);
			LOG.info("Payment applied to account {}, amount {}",
					in.getCommAccno(), in.getCommAmt());
			return ResponseEntity.ok(success(in, updated));
		}
		catch (BusinessRuleException ex)
		{
			LOG.info("Payment rejected, failCode={}", ex.getFailCode());
			return ResponseEntity.ok(failure(in, ex.getFailCode()));
		}
	}

	/**
	 * Resolves the facility type from the origin's {@code CommFaciltype} (for
	 * example {@code 496}). Defaults to the payment facility type when the origin
	 * or its facility type is absent, matching the payment-channel caller.
	 *
	 * <p>{@code CommFaciltype} is modelled as an {@link Integer} (the frozen
	 * schema declares it {@code type=integer}), so no string parsing is required:
	 * a present value is returned directly and an absent ({@code null}) value
	 * falls back to {@link BankConstants#PAYMENT_FACILITY_TYPE}.</p>
	 *
	 * @param origin the request origin, possibly {@code null}
	 * @return the resolved facility type
	 */
	private int resolveFacilityType(OriginJson origin)
	{
		if (origin == null || origin.getCommFaciltype() == null)
		{
			return BankConstants.PAYMENT_FACILITY_TYPE;
		}
		return origin.getCommFaciltype();
	}

	/**
	 * Resolves the origin string (application id concatenated with user id) used
	 * by {@code PaymentService} for the payment-channel PROCTRAN description. The
	 * service truncates it to fourteen characters, reproducing
	 * {@code DBCRFUN}'s {@code COMM-ORIGIN(1:14)} slice.
	 *
	 * @param origin the request origin, possibly {@code null}
	 * @return the concatenated origin string (never {@code null})
	 */
	private String resolveOrigin(OriginJson origin)
	{
		if (origin == null)
		{
			return "";
		}
		String applid = origin.getCommApplid() == null ? ""
				: origin.getCommApplid();
		String userid = origin.getCommUserid() == null ? ""
				: origin.getCommUserid();
		return applid + userid;
	}

	/**
	 * Builds the success envelope echoing the request and the post-movement
	 * balances.
	 *
	 * @param in      the original request commarea
	 * @param account the updated account
	 * @return the populated success envelope
	 */
	private PaymentInterfaceJson success(DbcrJson in, Account account)
	{
		DbcrJson out = new DbcrJson();
		out.setCommAccno(in.getCommAccno());
		out.setCommAmt(in.getCommAmt());
		out.setCommSortC(Integer.parseInt(BankConstants.SORT_CODE));
		out.setCommAvBal(account.getAvailableBalance());
		out.setCommActBal(account.getActualBalance());
		out.setCommOrigin(in.getCommOrigin());
		out.setCommSuccess(FLAG_SUCCESS);
		out.setCommFailCode(SUCCESS_FAIL_CODE);
		return new PaymentInterfaceJson(out);
	}

	/**
	 * Builds the failure envelope, echoing the request and surfacing the numeric
	 * fail code.
	 *
	 * @param in       the original request commarea
	 * @param failCode the COBOL fail code to surface (numeric)
	 * @return the populated failure envelope
	 */
	private PaymentInterfaceJson failure(DbcrJson in, String failCode)
	{
		DbcrJson out = new DbcrJson();
		out.setCommAccno(in.getCommAccno());
		out.setCommAmt(in.getCommAmt());
		out.setCommSortC(Integer.parseInt(BankConstants.SORT_CODE));
		out.setCommOrigin(in.getCommOrigin());
		out.setCommSuccess(FLAG_FAILURE);
		out.setCommFailCode(failCode);
		return new PaymentInterfaceJson(out);
	}

}
