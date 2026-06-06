/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.core.controller;

import jakarta.validation.Valid;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ibm.cics.cip.bank.core.dto.payment.DbcrJson;
import com.ibm.cics.cip.bank.core.dto.payment.PaymentJson;
import com.ibm.cics.cip.bank.core.exception.BusinessRuleException;
import com.ibm.cics.cip.bank.core.service.PaymentService;

/**
 * Thin Spring MVC adapter that reproduces the frozen z/OS&nbsp;Connect
 * <em>make-payment</em> (debit/credit) endpoint verbatim (feature&nbsp;F-019),
 * mapping the legacy {@code Pay} service onto
 * {@link PaymentService#processPayment(PaymentJson)} ({@code DBCRFUN} parity,
 * feature&nbsp;F-015).
 *
 * <h2>Frozen contract (reproduced exactly)</h2>
 * <ul>
 *   <li><strong>HTTP method + path:</strong> {@code PUT /makepayment/dbcr}
 *       (operationId {@code putPay}) &mdash; the class-level
 *       {@link RequestMapping @RequestMapping("/makepayment")} supplies the
 *       {@code basePath} and the method-level
 *       {@link PutMapping @PutMapping("/dbcr")} supplies the relative path. The
 *       module runs at the root context on {@code server.port} 8080, so there is
 *       deliberately <strong>no context-path prefix</strong>.</li>
 *   <li><strong>Content type:</strong> consumes and produces
 *       {@code application/json}, and always answers with a single HTTP
 *       <strong>200</strong> &mdash; even on a business failure, because the
 *       frozen consumers inspect the response body (not the status code) for the
 *       outcome.</li>
 *   <li><strong>Wire envelope:</strong> both request and response carry the
 *       single top-level key {@code PAYDBCR}, i.e. {@code {"PAYDBCR":{ ... }}}.
 *       The envelope shape is owned entirely by the
 *       {@link PaymentJson}/{@link DbcrJson} DTOs (their {@code @JsonNaming} and
 *       {@code @JsonProperty} annotations); this controller adds no Jackson
 *       configuration of its own.</li>
 * </ul>
 *
 * <h2>Responsibility boundary (pure adapter)</h2>
 * <p>This controller holds <strong>no business logic</strong>: no balance
 * arithmetic, no facility-type-496 channel rules, no signed-amount handling, no
 * {@code BigDecimal} math. Every such concern lives in {@link PaymentService}
 * (the {@code DBCRFUN} port), which owns the dual-balance update, the
 * insufficient-funds / restricted-account checks, and the
 * negative-is-debit / positive-is-credit sign convention. The controller only
 * carries the {@code PAYDBCR} envelope across the wire and renders the outcome
 * onto it.</p>
 *
 * <h2>Outcome rendering &mdash; the numeric {@code CommFailCode} contract
 * (BIND)</h2>
 * <p>The frozen Payment-Interface consumer validates the response with
 * {@code switch (Integer.parseInt(response.getPAYDBCR().getCommFailCode()))}
 * (case&nbsp;{@code 1} account-not-found, case&nbsp;{@code 3} insufficient
 * funds, case&nbsp;{@code 4} invalid account type, {@code default} success).
 * The {@code CommFailCode} on the response therefore <strong>must be a
 * parseable integer</strong>: a blank value would raise
 * {@code NumberFormatException} and break the consumer. Because
 * {@link PaymentService} writes a blank {@code CommFailCode} (a single space) on
 * success, this controller translates the success outcome to the numeric code
 * {@code "0"} &mdash; the division of responsibility documented on
 * {@link DbcrJson} ("the payment controller writes a numeric code
 * {@code \"0\"}&nbsp;=&nbsp;success"). This is envelope/contract adaptation, not
 * business logic, and it is exactly what preserves the frozen contract
 * (§0.7&nbsp;/&nbsp;F-019) so the Payment Interface UI re-points by base URL
 * only.</p>
 *
 * <h2>Dual failure handling</h2>
 * <p>A {@link BusinessRuleException} thrown by the service carries the verbatim
 * COBOL fail code ({@code "1"}, {@code "2"}, {@code "3"}, or {@code "4"} on this
 * path &mdash; all numeric). The controller catches it and re-renders the
 * <em>same</em> {@code PAYDBCR} request envelope with {@code CommSuccess="N"} and
 * {@code CommFailCode} set to the carried code, returning HTTP&nbsp;200 so the
 * envelope reaches the consumer intact. This is the <strong>primary</strong>
 * envelope-fidelity path: it deliberately bypasses the generic
 * {@code GlobalExceptionHandler} body ({@code {success,failCode,message}}),
 * which would not satisfy the {@code PAYDBCR} consumer. Validation failures
 * ({@code MethodArgumentNotValidException}) and any other unexpected exception
 * are <strong>not</strong> caught here and propagate to
 * {@code GlobalExceptionHandler} (HTTP&nbsp;400 / HTTP&nbsp;500 respectively),
 * which remains the safety net.</p>
 */
@RestController
@RequestMapping("/makepayment")
public class PaymentController
{

	/**
	 * Success flag written onto the response envelope ({@code COMM-SUCCESS}).
	 * Mirrors the COBOL {@code 'Y'} success indicator.
	 */
	private static final String FLAG_SUCCESS = "Y";

	/**
	 * Failure flag written onto the response envelope ({@code COMM-SUCCESS}) when
	 * a {@link BusinessRuleException} is caught. Mirrors the COBOL {@code 'N'}
	 * failure indicator.
	 */
	private static final String FLAG_FAILURE = "N";

	/**
	 * Numeric success {@code CommFailCode}. The frozen consumer parses
	 * {@code CommFailCode} with {@code Integer.parseInt(...)} and treats any
	 * value other than {@code 1}/{@code 3}/{@code 4} as success, so a parseable
	 * {@code "0"} is written rather than the blank the service leaves in place.
	 */
	private static final String SUCCESS_FAIL_CODE = "0";

	/**
	 * The payment (debit/credit) business service &mdash; the authoritative
	 * {@code DBCRFUN} port. Injected via the constructor and held {@code final};
	 * no field injection is used.
	 */
	private final PaymentService paymentService;

	/**
	 * Constructs the controller with its collaborating payment service
	 * (constructor injection only, for testability and immutability).
	 *
	 * @param paymentService the payment (debit/credit) business service
	 */
	public PaymentController(PaymentService paymentService)
	{
		this.paymentService = paymentService;
	}

	/**
	 * Handles {@code PUT /makepayment/dbcr}: delegates the inbound {@code PAYDBCR}
	 * envelope to {@link PaymentService#processPayment(PaymentJson)} and renders
	 * the outcome back onto a {@code PAYDBCR} envelope at HTTP&nbsp;200.
	 *
	 * <p>The {@link Valid @Valid} annotation triggers cascaded Jakarta Bean
	 * Validation of the request envelope before delegation (BMS field rules
	 * &rarr; annotations, F-021); a validation failure raises
	 * {@code MethodArgumentNotValidException}, which is intentionally left to
	 * {@code GlobalExceptionHandler} (HTTP&nbsp;400).</p>
	 *
	 * <p>On the normal path the service result is returned with its success
	 * flags rendered for the frozen consumer ({@code CommSuccess="Y"},
	 * {@code CommFailCode="0"}). A {@link BusinessRuleException} is caught and
	 * rendered onto the original request envelope ({@code CommSuccess="N"},
	 * {@code CommFailCode} = the carried COBOL fail code), preserving the
	 * envelope exactly.</p>
	 *
	 * @param request the inbound make-payment request envelope
	 *                ({@code {"PAYDBCR":{ ... }}})
	 * @return the make-payment response envelope wrapped in a HTTP&nbsp;200
	 *         {@link ResponseEntity}, always {@code application/json}
	 */
	@PutMapping(value = "/dbcr",
			consumes = MediaType.APPLICATION_JSON_VALUE,
			produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<PaymentJson> makePayment(
			@Valid @RequestBody PaymentJson request)
	{
		try
		{
			// Delegate ALL business logic to the DBCRFUN port. The service
			// mutates and returns the same PAYDBCR envelope with both balances
			// updated and COMM-SUCCESS='Y'.
			PaymentJson result = paymentService.processPayment(request);

			// Render the success outcome for the frozen consumer, which parses
			// CommFailCode with Integer.parseInt(...): the service leaves a blank
			// fail code, so translate it to the numeric "0" the contract
			// requires (see DbcrJson Javadoc). Envelope adaptation only.
			renderSuccess(result);
			return ResponseEntity.ok(result);
		}
		catch (BusinessRuleException ex)
		{
			// PRIMARY envelope fidelity: re-render the SAME request envelope as a
			// failure (COMM-SUCCESS='N' + the verbatim COBOL fail code) and answer
			// HTTP 200, instead of letting the generic GlobalExceptionHandler body
			// break the PAYDBCR consumer. ex.getFailCode() is a String (the
			// DBCRFUN codes "1"/"2"/"3"/"4" are numeric and parse cleanly).
			renderFailure(request, ex.getFailCode());
			return ResponseEntity.ok(request);
		}
	}

	/**
	 * Renders the success flags onto the response envelope's inner payload. The
	 * {@code null} guard is defensive: on the success path the service always
	 * returns a populated payload, but guarding keeps the controller robust
	 * against any future service contract change.
	 *
	 * @param response the populated response envelope returned by the service
	 */
	private void renderSuccess(PaymentJson response)
	{
		DbcrJson payload = (response == null) ? null : response.getPAYDBCR();
		if (payload != null)
		{
			payload.setCommSuccess(FLAG_SUCCESS);
			payload.setCommFailCode(SUCCESS_FAIL_CODE);
		}
	}

	/**
	 * Renders a failure outcome onto the original request envelope, preserving
	 * every other field ({@code CommAccno}, {@code CommAmt}, {@code mSortC},
	 * balances, {@code CommOrigin}) so the {@code PAYDBCR} envelope is echoed
	 * back exactly. If the request carried no inner payload (a malformed
	 * {@code {"PAYDBCR":null}} body, which the service rejects with fail
	 * code&nbsp;{@code "1"}), a fresh {@link DbcrJson} is attached so the failure
	 * envelope is always well formed.
	 *
	 * @param request  the original request envelope to re-render as a failure
	 * @param failCode the verbatim COBOL fail code carried by the exception
	 */
	private void renderFailure(PaymentJson request, String failCode)
	{
		DbcrJson payload = request.getPAYDBCR();
		if (payload == null)
		{
			payload = new DbcrJson();
			request.setPAYDBCR(payload);
		}
		payload.setCommSuccess(FLAG_FAILURE);
		payload.setCommFailCode(failCode);
	}

}
