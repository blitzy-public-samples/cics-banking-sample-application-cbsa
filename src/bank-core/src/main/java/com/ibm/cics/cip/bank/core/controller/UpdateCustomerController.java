/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.core.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ibm.cics.cip.bank.core.dto.updatecustomer.UpdateCustomerForm;
import com.ibm.cics.cip.bank.core.dto.updatecustomer.UpdateCustomerJson;
import com.ibm.cics.cip.bank.core.dto.updatecustomer.UpdcustJson;
import com.ibm.cics.cip.bank.core.exception.BusinessRuleException;
import com.ibm.cics.cip.bank.core.service.CustomerService;

import jakarta.validation.Valid;

/**
 * REST controller reproducing the frozen z/OS Connect <em>update-customer</em>
 * ({@code updcust}) endpoint VERBATIM (feature&nbsp;F-019), mapping the
 * {@code CScustupd} service onto
 * {@link CustomerService#updateCustomer(UpdateCustomerForm)}.
 *
 * <h2>Frozen contract (pinned, never altered)</h2>
 * <p>The HTTP method, path, and JSON envelope are reproduced byte-for-byte from
 * the authoritative {@code src/zosconnect_artefacts/apis/updcust/api-docs/swagger.json}
 * (operationId {@code putCScustupd}) and {@code updcust/package.xml}
 * ({@code basePath="/updcust"}, {@code relativePath="/update"},
 * {@code zosConnectServiceRef="CScustupd"}):</p>
 * <ul>
 *   <li><strong>{@code PUT /updcust/update}</strong> &mdash; declared as the
 *       class-level base path {@code /updcust}
 *       ({@link RequestMapping @RequestMapping}) plus the handler-level relative
 *       path {@code /update} ({@link PutMapping @PutMapping}); the application
 *       runs at the ROOT context (port&nbsp;8080) with no servlet context-path,
 *       so the absolute path is exactly {@code /updcust/update};</li>
 *   <li><strong>{@code consumes}/{@code produces} {@code application/json}</strong>
 *       with a single HTTP&nbsp;{@code 200} response in every outcome (success
 *       and business rejection alike) &mdash; the frozen consumers inspect the
 *       response body, not the HTTP status, to decide the outcome;</li>
 *   <li>the request body and the response body share the identical top-level
 *       envelope key {@code "UpdCust"} ({@code {"UpdCust": { &hellip; }}}),
 *       modelled by the outer wrapper {@link UpdateCustomerJson} over the inner
 *       commarea {@link UpdcustJson}. Using the wrapper as both the
 *       {@code @RequestBody} type and the {@link ResponseEntity} body type
 *       guarantees the exact {@code {"UpdCust":{...}}} envelope.</li>
 * </ul>
 *
 * <h2>Thin adapter &mdash; no business logic here</h2>
 * <p>{@code UPDCUST.cbl} is the authoritative behavioural specification and ALL
 * of its logic lives in
 * {@link CustomerService#updateCustomer(UpdateCustomerForm)}: per feature
 * <strong>F-011</strong> the update changes only the customer <em>name</em> and
 * <em>address</em> (never balances, credit score, or any other field), enforces
 * the honorific-title check (fail {@code "T"}), rejects a request that supplies
 * neither a name nor an address (fail {@code "4"}), rejects an absent customer
 * (fail {@code "1"}), and writes <strong>no</strong> {@code PROCTRAN} audit
 * record &mdash; all under the service's
 * {@link org.springframework.transaction.annotation.Transactional &#64;Transactional}
 * boundary (propagation {@code REQUIRED}, isolation {@code READ_COMMITTED}) that
 * reproduces the COBOL {@code SYNCPOINT}/{@code ROLLBACK} semantics. This
 * controller performs only request binding, a trivial wrapper&rarr;form field
 * copy, delegation, and response shaping. It contains no title/name validation,
 * no field-whitelisting of which attributes may change, and no Jackson
 * configuration.</p>
 *
 * <h2>Request adaptation (trivial copy only)</h2>
 * <p>The inbound {@link UpdcustJson} commarea is adapted into the service's
 * {@link UpdateCustomerForm} input by a straight field copy of the three values
 * the {@code UPDCUST} flow consumes:
 * {@code CommCustno}&rarr;{@code custNumber} (the identifier used to locate the
 * record), {@code CommName}&rarr;{@code custName}, and
 * {@code CommAddress}&rarr;{@code custAddress} (the two updatable fields). No
 * type adaptation is required because all three are {@link String}s on both
 * sides; the remaining envelope fields ({@code CommDob},
 * {@code CommCreditScore}, {@code CommCsReviewDate}) are not consumed by
 * {@code UPDCUST} and are therefore not copied. This is a pure typing/transport
 * concern with no business decision in the controller, mirroring the established
 * {@code UpdateAccountController} / {@code CreateCustomerController} pattern.</p>
 *
 * <h2>Dual failure handling (byte-for-byte envelope fidelity)</h2>
 * <p>On success the controller returns the populated envelope produced by the
 * service ({@code CommUpdSuccess="Y"} over the updated name and/or address)
 * directly at HTTP&nbsp;{@code 200}.</p>
 * <p>The service signals a business rejection by throwing a
 * {@link BusinessRuleException} &mdash; this is deliberate, because the throw is
 * what unwinds the service's {@code @Transactional} unit of work. The controller
 * therefore <strong>catches</strong> it (rather than deferring to the generic
 * {@code GlobalExceptionHandler}) and rebuilds the frozen {@code UpdCust}
 * envelope by echoing the inbound {@code request} with its inner payload's
 * {@code CommUpdSuccess} set to {@code "N"} <strong>and</strong>
 * {@code CommUpdFailCd} set to the verbatim {@link BusinessRuleException#getFailCode()}
 * value, returned at HTTP&nbsp;{@code 200}. Unlike the sibling
 * {@code UpdateAccountController} (whose {@code UpdAcc} envelope carries no
 * fail-code field), the {@code UpdCust} envelope <em>does</em> declare
 * {@code CommUpdFailCd}, so BOTH the flag and the code are surfaced &mdash; the
 * preserved customer-services consumer reads {@code CommUpdSuccess == "N"} and
 * then distinguishes {@code "4"} (no name/address), {@code "T"} (invalid title),
 * and any other value (customer not found) from {@code CommUpdFailCd}. Echoing
 * the inbound payload (rather than constructing a fresh one) preserves every
 * other field the caller sent ({@code CommEye}, {@code CommScode},
 * {@code CommDob}, &hellip;), which is the byte-for-byte F-019 guarantee.</p>
 * <p>Binding failures ({@link Valid @Valid}, HTTP&nbsp;{@code 400}) and any
 * unexpected exception (HTTP&nbsp;{@code 500}) are deliberately NOT caught here;
 * they propagate to the global advice.</p>
 *
 * <p><strong>No mainframe coupling.</strong> This controller imports only Spring
 * MVC, Jakarta Validation, and {@code bank-core} types; it never references
 * {@code com.ibm.cics.server} (JCICS), {@code com.ibm.jzos}, or
 * {@code com.ibm.websphere} &mdash; those legacy runtimes are decommissioned in
 * the target.</p>
 *
 * @see CustomerService#updateCustomer(UpdateCustomerForm)
 * @see UpdateCustomerJson
 * @see UpdcustJson
 */
@RestController
@RequestMapping("/updcust")
public class UpdateCustomerController
{

	/**
	 * Success-flag value written to the response envelope when a business rule
	 * rejects the update (COBOL {@code COMM-UPD-SUCCESS = 'N'}). Paired with the
	 * verbatim fail code in {@code CommUpdFailCd} on the failure path.
	 */
	private static final String FLAG_FAILURE = "N";

	/**
	 * The customer business service (UPDCUST parity, feature&nbsp;F-011); all
	 * update logic lives here. Injected by constructor for testability and
	 * immutability.
	 */
	private final CustomerService customerService;

	/**
	 * Constructs the controller with its collaborating service. The single
	 * collaborator is injected by constructor for testability and immutability.
	 *
	 * @param customerService the customer business service that performs the
	 *                        {@code UPDCUST} restricted update (name and address
	 *                        only)
	 */
	public UpdateCustomerController(CustomerService customerService)
	{
		this.customerService = customerService;
	}

	/**
	 * Handles {@code PUT /updcust/update}, applying the {@code UPDCUST} update
	 * (customer name and/or address only) from the supplied {@code UpdCust}
	 * envelope.
	 *
	 * <p>The handler adapts the inbound wrapper into an
	 * {@link UpdateCustomerForm} (a trivial field copy &mdash; see the class
	 * Javadoc), delegates the entire update to
	 * {@link CustomerService#updateCustomer(UpdateCustomerForm)}, and returns the
	 * service's populated success envelope at HTTP&nbsp;{@code 200}. A
	 * {@link BusinessRuleException} thrown by the service is caught and rendered
	 * as the frozen failure envelope ({@code CommUpdSuccess="N"} plus the
	 * verbatim {@code CommUpdFailCd}), also at HTTP&nbsp;{@code 200}.</p>
	 *
	 * @param request the update-customer request envelope
	 *                ({@code {"UpdCust": {...}}}); {@link Valid @Valid} triggers
	 *                Jakarta Bean Validation of the bound payload, a failure of
	 *                which yields HTTP&nbsp;{@code 400} via the global advice
	 * @return the update-customer response envelope, always HTTP&nbsp;{@code 200}
	 *         and always carrying the single top-level {@code UpdCust} key
	 */
	@PutMapping(value = "/update",
			consumes = MediaType.APPLICATION_JSON_VALUE,
			produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<UpdateCustomerJson> updateCustomer(
			@Valid @RequestBody UpdateCustomerJson request)
	{
		try
		{
			// Trivial wrapper -> service-form adaptation (no business logic).
			// UPDCUST consumes only the customer number (to locate the record)
			// and the two updatable fields (name, address); all three are
			// Strings on both sides, so this is a straight field copy.
			UpdcustJson in = request.getUpdcust();
			UpdateCustomerForm form = new UpdateCustomerForm();
			form.setCustNumber(in.getCommCustno());
			form.setCustName(in.getCommName());
			form.setCustAddress(in.getCommAddress());

			// All UPDCUST logic (title validation 'T', no-name/address '4',
			// not-found '1', name/address-only update, no PROCTRAN audit row) is
			// owned by the service under its @Transactional boundary.
			UpdateCustomerJson response = customerService.updateCustomer(form);
			return ResponseEntity.ok(response);
		}
		catch (BusinessRuleException ex)
		{
			// The service threw to unwind its @Transactional unit of work. Echo
			// the inbound UpdCust envelope unchanged except for the failure
			// signal so the preserved consumer (which tests
			// UpdCust.CommUpdSuccess == "N" and then reads CommUpdFailCd to
			// distinguish '4'/'T'/not-found) reads the outcome. The UpdCust
			// envelope carries a dedicated fail-code field, so BOTH the flag and
			// the verbatim COBOL fail code are surfaced. HTTP 200 preserves the
			// contract body.
			UpdcustJson echo = request.getUpdcust();
			if (echo == null)
			{
				echo = new UpdcustJson();
				request.setUpdcust(echo);
			}
			echo.setCommUpdateSuccess(FLAG_FAILURE);
			echo.setCommUpdateFailCode(ex.getFailCode());
			return ResponseEntity.ok(request);
		}
	}

}
