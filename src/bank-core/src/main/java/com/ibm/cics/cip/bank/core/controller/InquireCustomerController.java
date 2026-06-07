/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.core.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ibm.cics.cip.bank.core.dto.customerenquiry.CustomerEnquiryJson;
import com.ibm.cics.cip.bank.core.dto.customerenquiry.InqCustZJson;
import com.ibm.cics.cip.bank.core.exception.BusinessRuleException;
import com.ibm.cics.cip.bank.core.service.CustomerService;

/**
 * REST controller reproducing the frozen z/OS Connect <em>inquire-customer</em>
 * endpoint (the {@code inqcustz} API, mapping the {@code CScustenq} service onto
 * {@code INQCUST.cbl}), feature&nbsp;F-019.
 *
 * <p>This is a deliberately <strong>thin adapter</strong>: it owns no business
 * logic. The HTTP verb, route, and JSON envelope are reproduced verbatim and the
 * request is delegated straight to
 * {@link CustomerService#inquireCustomer(long)}, which carries the authoritative
 * {@code INQCUST} behaviour &mdash; including the {@code 0000000000} sentinel
 * (pick a random customer) and the {@code 9999999999} sentinel (resolve the
 * highest existing customer from the control-row {@code LAST-CUSTOMER-NUMBER}
 * rather than a {@code MAX()} scan), feature&nbsp;F-008.</p>
 *
 * <h2>Frozen contract (verified)</h2>
 * <p>The route, method, and envelope match
 * {@code src/zosconnect_artefacts/apis/inqcustz/api-docs/swagger.json} and
 * {@code package.xml} exactly:</p>
 * <ul>
 *   <li><strong>Method&nbsp;+&nbsp;path:</strong>
 *       {@code GET /inqcustz/enquiry/{custno}} (operationId {@code getCScustenq};
 *       basePath {@code /inqcustz}, relativePath {@code /enquiry/{custno}}). The
 *       class-level {@link RequestMapping @RequestMapping("/inqcustz")} composes
 *       with the method-level {@link GetMapping @GetMapping("/enquiry/{custno}")}
 *       to form the full route at the ROOT context (no servlet context-path; the
 *       module runs on {@code server.port} 8080).</li>
 *   <li><strong>Produces:</strong> {@code application/json} only, with a single
 *       HTTP&nbsp;{@code 200} response.</li>
 *   <li><strong>Response envelope:</strong> the outer wrapper
 *       {@link CustomerEnquiryJson} serialises to exactly one top-level key over
 *       the inner {@link InqCustZJson} payload, reproducing the z/OS Connect
 *       {@code getCScustenq_response_200} envelope.</li>
 *   <li><strong>No request body:</strong> although the swagger lists a body
 *       parameter, the real consumer ({@code WebController}) calls
 *       {@code client.get().retrieve()} with <em>no</em> body, so this handler
 *       accepts ONLY the {@code {custno}} path variable and declares no
 *       {@code @RequestBody}.</li>
 * </ul>
 *
 * <h2>Success / not-found convention</h2>
 * <p>Unlike the mutating endpoints, {@code INQCUST} signals a plain
 * &quot;not&nbsp;found&quot; not as a hard failure but by RETURNING a soft
 * envelope whose inner {@code InqCustInqSuccess} flag is {@code "N"} (the read
 * path needs no rollback). That envelope is produced by the service and returned
 * by this controller verbatim at HTTP&nbsp;{@code 200}. The {@code InqCustZ}
 * envelope DOES carry a separate single-character fail-code field
 * ({@code InqCustInqFailCd}), which the service populates on the soft not-found
 * outcome.</p>
 *
 * <h2>Safeguard failure handling</h2>
 * <p>As defence-in-depth, the delegation is wrapped in a {@code try}/{@code catch}
 * for {@link BusinessRuleException}: should the service ever raise one on this
 * read path, the controller still honours the contract by returning a fresh
 * {@link CustomerEnquiryJson} whose inner payload echoes the requested customer
 * number, sets {@code InqCustInqSuccess = "N"}, and carries the exact COBOL fail
 * code via {@code InqCustInqFailCd}, again at HTTP&nbsp;{@code 200}. This mirrors
 * the service's own not-found shaping. Parameter-binding and generic exceptions
 * (for example a blank, non-numeric or over-width path variable raising
 * {@link NumberFormatException} from {@link Long#parseLong(String)}) are
 * deliberately NOT caught here; they propagate to {@code GlobalExceptionHandler},
 * which renders them as HTTP&nbsp;400.</p>
 *
 * <p><strong>No mainframe coupling.</strong> This controller imports only Spring
 * MVC and {@code bank-core} types; it never references {@code com.ibm.cics.server}
 * (JCICS), {@code com.ibm.jzos}, or {@code com.ibm.websphere} &mdash; those
 * legacy runtimes are decommissioned in the target.</p>
 *
 * @see CustomerService#inquireCustomer(long)
 * @see CustomerEnquiryJson
 * @see InqCustZJson
 */
@RestController
@RequestMapping("/inqcustz")
public class InquireCustomerController
{

	/**
	 * Failure flag value written to {@code InqCustInqSuccess} on the safeguard
	 * not-found envelope (COBOL {@code INQCUST-INQ-SUCCESS = 'N'}).
	 */
	private static final String FLAG_FAILURE = "N";

	/**
	 * The customer business service holding all {@code INQCUST} logic. Injected
	 * by constructor for immutability and testability.
	 */
	private final CustomerService customerService;

	/**
	 * Constructs the controller with its collaborating service.
	 *
	 * @param customerService the customer business service (INQCUST parity,
	 *                        F-008)
	 */
	public InquireCustomerController(CustomerService customerService)
	{
		this.customerService = customerService;
	}

	/**
	 * Inquires on a single customer, reproducing the frozen
	 * {@code GET /inqcustz/enquiry/{custno}} contract.
	 *
	 * <p>The path variable is parsed to a {@code long} via
	 * {@link Long#parseLong(String)} and delegated unchanged to
	 * {@link CustomerService#inquireCustomer(long)} &mdash; including the
	 * {@code 0000000000} sentinel (random customer) and the {@code 9999999999}
	 * sentinel (highest existing customer), which the service interprets via the
	 * {@code CUSTCTRL} control row (F-008). The service returns the fully
	 * populated {@link CustomerEnquiryJson} envelope (with
	 * {@code InqCustInqSuccess = "Y"} when the customer is found, or {@code "N"}
	 * with a fail code for the soft not-found outcome), which is returned as-is at
	 * HTTP&nbsp;{@code 200}.</p>
	 *
	 * <p>A {@link BusinessRuleException} is caught as a safeguard and shaped into
	 * a contract-faithful {@code InqCustInqSuccess = "N"} envelope echoing the
	 * requested customer number and carrying the exact fail code via
	 * {@code InqCustInqFailCd}, also at HTTP&nbsp;{@code 200}. A blank,
	 * non-numeric or over-width {@code custno} raises
	 * {@link NumberFormatException}, which is left to propagate to the global
	 * exception handler (HTTP&nbsp;400).</p>
	 *
	 * @param custno the 10-digit customer number from the path (the sentinels
	 *              {@code 0000000000} and {@code 9999999999} are passed through
	 *              unchanged for the service to interpret)
	 * @return the customer-enquiry response envelope at HTTP&nbsp;{@code 200},
	 *         {@code application/json}
	 */
	@GetMapping(value = "/enquiry/{custno}",
			produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<CustomerEnquiryJson> inquireCustomer(
			@PathVariable("custno") String custno)
	{
		// Parse outside the try so a malformed path variable surfaces as a
		// NumberFormatException to GlobalExceptionHandler (HTTP 400) rather than
		// being absorbed by the business-rule safeguard below. The sentinels
		// 0000000000 / 9999999999 parse cleanly and are passed through unchanged
		// for CustomerService to interpret (F-008).
		long customerNumber = Long.parseLong(custno);
		try
		{
			// Thin pass-through: INQCUST logic (incl. the sentinel resolution and
			// the soft InqCustInqSuccess="N" not-found case with its fail code)
			// lives entirely in the service.
			CustomerEnquiryJson result = customerService
					.inquireCustomer(customerNumber);
			return ResponseEntity.ok(result);
		}
		catch (BusinessRuleException ex)
		{
			// Safeguard: honour the frozen contract even if the read path ever
			// raises a business-rule exception. The InqCustZ envelope carries a
			// fail-code field, so both the success flag and the fail code are set.
			InqCustZJson payload = new InqCustZJson();
			payload.setInqCustCustno(custno);
			payload.setInqCustInqSuccess(FLAG_FAILURE);
			payload.setInqCustInqFailCd(ex.getFailCode());
			CustomerEnquiryJson failureEnvelope = new CustomerEnquiryJson();
			failureEnvelope.setInqCustZ(payload);
			return ResponseEntity.ok(failureEnvelope);
		}
	}
}
