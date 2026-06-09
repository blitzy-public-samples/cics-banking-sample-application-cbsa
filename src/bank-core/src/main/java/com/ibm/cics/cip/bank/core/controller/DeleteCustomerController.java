/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.core.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ibm.cics.cip.bank.core.dto.deletecustomer.DelcusJson;
import com.ibm.cics.cip.bank.core.dto.deletecustomer.DeleteCustomerJson;
import com.ibm.cics.cip.bank.core.exception.BusinessRuleException;
import com.ibm.cics.cip.bank.core.service.CustomerService;
import com.ibm.cics.cip.bank.core.util.BankFormat;

import jakarta.validation.Valid;

/**
 * REST controller reproducing the frozen z/OS Connect <em>delete-customer</em>
 * endpoint (the {@code delcus} API, mapping the {@code CScustdel} service onto
 * {@code DELCUS.cbl}), feature&nbsp;F-019.
 *
 * <p>This is a deliberately <strong>thin adapter</strong>: it owns no business
 * logic whatsoever. The HTTP verb, route, and JSON envelope are reproduced
 * verbatim and the request is delegated straight to
 * {@link CustomerService#deleteCustomer(long)}, which carries the authoritative
 * {@code DELCUS} behaviour (F-014): it cascade-deletes every account the
 * customer owns (each appending its own account-close {@code PROCTRAN} record),
 * physically removes the customer row, and then appends a customer-close
 * {@code PROCTRAN} record &mdash; all inside one {@code @Transactional} unit of
 * work so the cascade, the row removal, and the audit appends commit or roll
 * back together (reproducing the COBOL {@code EXEC CICS SYNCPOINT}/{@code
 * ROLLBACK} boundary). The customer counter is deliberately not decremented,
 * exactly as {@code DELCUS.cbl} leaves {@code NUMBER-OF-CUSTOMERS} untouched.</p>
 *
 * <h2>Frozen contract (verified)</h2>
 * <p>The route, method, and envelope match
 * {@code src/zosconnect_artefacts/apis/delcus/api-docs/swagger.json} and
 * {@code package.xml} exactly:</p>
 * <ul>
 *   <li><strong>Method&nbsp;+&nbsp;path:</strong>
 *       {@code DELETE /delcus/remove/{custno}} (operationId
 *       {@code deleteCScustdel}; basePath {@code /delcus}, relativePath
 *       {@code /remove/{custno}}). The class-level
 *       {@link RequestMapping @RequestMapping("/delcus")} composes with the
 *       method-level {@link DeleteMapping @DeleteMapping("/remove/{custno}")} to
 *       form the full route at the ROOT context (no servlet context-path; the
 *       module runs on {@code server.port} 8080).</li>
 *   <li><strong>Produces:</strong> {@code application/json} only, with a single
 *       HTTP&nbsp;{@code 200} response.</li>
 *   <li><strong>Response envelope:</strong> the outer wrapper
 *       {@link DeleteCustomerJson} serialises to exactly one top-level key,
 *       {@code DelCus}, over the inner {@link DelcusJson} payload.</li>
 *   <li><strong>Parameter source (path + optional body):</strong> the swagger
 *       declares both the {@code {custno}} path variable and a body parameter
 *       {@code deleteCScustdel_request} carrying the {@code DelCus} envelope. The
 *       real consumer ({@code WebController}) invokes the endpoint with
 *       {@code client.delete().retrieve()} and <em>no</em> body (zero-padding the
 *       customer number to width&nbsp;10 in the path), so the {@code {custno}}
 *       path variable is the AUTHORITATIVE parameter source and identifies the
 *       customer. For frozen-contract fidelity the handler ALSO accepts the
 *       swagger-declared {@code DelCus} body as an OPTIONAL ({@code required =
 *       false}), {@code @Valid}-checked {@link DeleteCustomerJson} so that
 *       swagger-shaped callers are not rejected; a present body is structurally
 *       validated but does not override the authoritative path variable.</li>
 * </ul>
 *
 * <h2>Success / failure convention (byte-for-byte envelope fidelity)</h2>
 * <p>On success the service returns the fully populated {@link DeleteCustomerJson}
 * envelope &mdash; capturing the deleted customer's details (name, address, date
 * of birth, credit score and review date) &mdash; which this controller returns
 * verbatim at HTTP&nbsp;{@code 200}.</p>
 *
 * <p>When the service raises a {@link BusinessRuleException} (the COBOL
 * {@code DELCUS} fail code is {@code "1"} for a customer that was not found),
 * this controller does NOT defer to the generic {@code GlobalExceptionHandler}
 * body; instead it reconstructs the frozen {@code DelCus} envelope so the wire
 * shape stays byte-for-byte intact. It builds a fresh {@link DeleteCustomerJson}
 * whose inner {@link DelcusJson} echoes the requested customer number, flags the
 * operative {@code CommDelSuccess} as {@code "N"}, and carries the verbatim
 * COBOL fail code &mdash; whatever code arrives &mdash; in {@code CommDelFailCd}.
 * That envelope is returned at HTTP&nbsp;{@code 200}, exactly as the legacy z/OS
 * Connect contract delivers a business outcome (the consumer inspects the body,
 * not the HTTP status).</p>
 *
 * <p>A blank, non-numeric or over-width {@code {custno}} raises
 * {@link NumberFormatException} from
 * {@link BankFormat#parseCustomerNumber(String)}, which enforces the
 * 1-to-10-digit contract width; that is deliberately <em>not</em> caught here so
 * it propagates to {@code GlobalExceptionHandler} (rendered as HTTP&nbsp;400).
 * Only {@link BusinessRuleException} is caught.</p>
 *
 * <p><strong>No mainframe coupling.</strong> This controller imports only Spring
 * MVC and {@code bank-core} types; it never references
 * {@code com.ibm.cics.server} (JCICS), {@code com.ibm.jzos}, or
 * {@code com.ibm.websphere} &mdash; those legacy runtimes are decommissioned in
 * the target.</p>
 *
 * @see CustomerService#deleteCustomer(long)
 * @see DeleteCustomerJson
 * @see DelcusJson
 */
@RestController
@RequestMapping("/delcus")
public class DeleteCustomerController
{

	/**
	 * Failure flag value ({@code "N"}) written to the operative
	 * {@code CommDelSuccess} field on the reconstructed failure envelope,
	 * reproducing the COBOL {@code DELCUS} success indicator on a rejected
	 * delete.
	 */
	private static final String FLAG_FAILURE = "N";

	/**
	 * The customer business service holding all {@code DELCUS} logic. Injected by
	 * constructor for immutability and testability.
	 */
	private final CustomerService customerService;

	/**
	 * Constructs the controller with its collaborating service.
	 *
	 * @param customerService the customer business service (DELCUS parity, F-014)
	 */
	public DeleteCustomerController(CustomerService customerService)
	{
		this.customerService = customerService;
	}

	/**
	 * Deletes a single customer and cascade-deletes their accounts, reproducing
	 * the frozen {@code DELETE /delcus/remove/{custno}} contract.
	 *
	 * <p>The path variable is parsed to a {@code long} via
	 * {@link BankFormat#parseCustomerNumber(String)} (the customer number is a
	 * display-numeric value of 1 to 10 digits, whose width is enforced) and
	 * delegated unchanged to
	 * {@link CustomerService#deleteCustomer(long)}, which carries the
	 * authoritative {@code DELCUS} behaviour (account cascade with per-account
	 * close {@code PROCTRAN} appends, physical customer-row removal, and a
	 * customer-close {@code PROCTRAN} append &mdash; all in one transaction). The
	 * service returns the fully populated {@link DeleteCustomerJson} envelope
	 * capturing the deleted customer's details, which is returned as-is at
	 * HTTP&nbsp;{@code 200}.</p>
	 *
	 * <p>A {@link BusinessRuleException} is caught and shaped into a
	 * contract-faithful {@code DelCus} failure envelope echoing the requested
	 * customer number, with the operative {@code CommDelSuccess = "N"} and
	 * {@code CommDelFailCd} carrying the verbatim COBOL fail code &mdash;
	 * whatever code arrives &mdash; again at HTTP&nbsp;{@code 200}. A blank,
	 * non-numeric or over-width {@code custno} raises
	 * {@link NumberFormatException}, which is left to propagate to the global
	 * exception handler (HTTP&nbsp;400).</p>
	 *
	 * @param custno the display-numeric customer number from the path (the
	 *               caller zero-pads it to width&nbsp;10)
	 * @param body   the OPTIONAL swagger-declared {@code DelCus} request envelope
	 *               ({@code required = false}); accepted and {@code @Valid}-checked
	 *               for frozen-contract fidelity but NOT authoritative &mdash; the
	 *               {@code {custno}} path variable identifies the customer. May be
	 *               {@code null} (the real no-body consumer path).
	 * @return the delete-customer response envelope at HTTP&nbsp;{@code 200},
	 *         {@code application/json}
	 */
	@DeleteMapping(value = "/remove/{custno}",
			produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<DeleteCustomerJson> deleteCustomer(
			@PathVariable("custno") String custno,
			@RequestBody(required = false) @Valid DeleteCustomerJson body)
	{
		// Parse OUTSIDE the try: the customer number is display-numeric (1 to ten
		// digits). BankFormat.parseCustomerNumber enforces the contract width, so
		// a blank, non-numeric or over-width path variable (e.g. "10000000000")
		// raises NumberFormatException, intentionally left to reach
		// GlobalExceptionHandler as HTTP 400 rather than being shaped into the
		// business-failure envelope (unlike Long.parseLong, which would accept an
		// 11-digit value inside the long range). Only BusinessRuleException is
		// caught below. The optional swagger body is accepted for contract
		// fidelity; the path variable stays authoritative.
		long customerNumber = BankFormat.parseCustomerNumber(custno);
		try
		{
			// Thin pass-through: all DELCUS logic (account cascade with per-account
			// ODA audit appends, physical customer-row removal, and the
			// customer-close ODC PROCTRAN append) lives in the service, which
			// returns the fully populated DelCus success envelope.
			DeleteCustomerJson result = customerService
					.deleteCustomer(customerNumber);
			return ResponseEntity.ok(result);
		}
		catch (BusinessRuleException ex)
		{
			// Reproduce the DelCus failure envelope byte-for-byte (NOT the generic
			// advice body): echo the requested customer number, flag the operative
			// CommDelSuccess as "N", and carry the verbatim COBOL fail code in
			// CommDelFailCd. HTTP 200 is still returned because the legacy z/OS
			// Connect contract delivers the business outcome in the body, not via
			// the HTTP status.
			DelcusJson payload = new DelcusJson();
			payload.setCommCustno(custno);
			payload.setCommDelSuccess(FLAG_FAILURE);
			payload.setCommDelFailCode(ex.getFailCode());
			return ResponseEntity.ok(new DeleteCustomerJson(payload));
		}
	}
}
