/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.core.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ibm.cics.cip.bank.core.dto.createcustomer.CreateCustomerForm;
import com.ibm.cics.cip.bank.core.dto.createcustomer.CreateCustomerJson;
import com.ibm.cics.cip.bank.core.dto.createcustomer.CrecustJson;
import com.ibm.cics.cip.bank.core.exception.BusinessRuleException;
import com.ibm.cics.cip.bank.core.service.CustomerService;

import jakarta.validation.Valid;

/**
 * REST controller reproducing the frozen z/OS Connect <em>create-customer</em>
 * ({@code crecust}) endpoint VERBATIM (feature&nbsp;F-019), mapping the
 * {@code CScustcre} service onto
 * {@link CustomerService#createCustomer(CreateCustomerForm)}.
 *
 * <h2>Frozen contract (pinned, never altered)</h2>
 * <p>The HTTP method, path, and JSON envelope are reproduced byte-for-byte from
 * the authoritative {@code src/zosconnect_artefacts/apis/crecust/api-docs/swagger.json}
 * (operationId {@code postCScustcre}) and {@code package.xml}:</p>
 * <ul>
 *   <li><strong>{@code POST /crecust/insert}</strong> &mdash; declared as the
 *       class-level base path {@code /crecust}
 *       ({@link RequestMapping @RequestMapping}) plus the handler-level relative
 *       path {@code /insert} ({@link PostMapping @PostMapping}); the application
 *       runs at the ROOT context (port&nbsp;8080) with no servlet context-path,
 *       so the absolute path is exactly {@code /crecust/insert};</li>
 *   <li><strong>{@code consumes}/{@code produces} {@code application/json}</strong>
 *       with a single HTTP&nbsp;{@code 200} response in every outcome (success
 *       and business rejection alike);</li>
 *   <li>the request body and the response body share the identical top-level
 *       envelope key {@code "CreCust"} ({@code {"CreCust": { &hellip; }}}),
 *       modelled by the outer wrapper {@link CreateCustomerJson} over the inner
 *       commarea {@link CrecustJson}.</li>
 * </ul>
 *
 * <h2>Thin adapter &mdash; no business logic here</h2>
 * <p>{@code CRECUST.cbl} is the authoritative behavioural specification and ALL
 * of its logic lives in
 * {@link CustomerService#createCustomer(CreateCustomerForm)}: the honorific
 * title check (fail {@code "T"}), the asynchronous five-way credit-agency check
 * with a three-second deadline (fail {@code "C"} when no agency replies in
 * time), the date-of-birth validation, and the gap-free customer-number
 * allocation from the {@code CUSTCTRL} control row &mdash; all ordered so that a
 * failed validation rolls back the consumed customer number under the service's
 * {@link org.springframework.transaction.annotation.Transactional
 * &#64;Transactional} boundary. This controller performs only request binding,
 * a trivial wrapper&rarr;form field copy, delegation, and response shaping. It
 * contains no title/date validation, no credit-agency fan-out, no
 * customer-numbering, and no Jackson configuration.</p>
 *
 * <h2>Request adaptation (trivial copy only)</h2>
 * <p>The inbound {@link CrecustJson} commarea is adapted into the service's
 * {@link CreateCustomerForm} input by a straight field copy of the three
 * client-supplied inputs ({@code CommName}&rarr;{@code custName},
 * {@code CommAddress}&rarr;{@code custAddress},
 * {@code CommDateOfBirth}&rarr;{@code custDob}) through the form's all-args
 * constructor. That constructor assigns each field directly &mdash; it does
 * <em>not</em> re-order the date of birth &mdash; which is exactly what is
 * required here, because the wire {@code CommDateOfBirth} is already the compact
 * eight-character {@code DDMMYYYY} digit string the service expects. No
 * trimming, normalisation, or validation is performed in the controller (Bean
 * Validation and the service own those concerns).</p>
 *
 * <h2>Dual failure handling (byte-for-byte envelope fidelity)</h2>
 * <p>On success the controller returns the populated success envelope produced
 * by the service (allocated {@code CommKey.CommNumber}, agency-derived
 * {@code CommCreditScore}, {@code CommCsReviewDate}, {@code CommSuccess="Y"} and
 * an empty {@code CommFailCode}) directly at HTTP&nbsp;{@code 200}.</p>
 * <p>The service signals a business rejection by throwing a
 * {@link BusinessRuleException} &mdash; this is deliberate, because the throw is
 * what unwinds the {@code @Transactional} boundary and rolls back the allocated
 * customer number (gap-free identity, ADR-003). The controller therefore
 * <strong>catches</strong> it (rather than deferring to the generic
 * {@code GlobalExceptionHandler}) and rebuilds the frozen {@code CreCust}
 * envelope by echoing the inbound {@code request} with its inner payload's
 * {@code CommSuccess} set to {@code "N"} and {@code CommFailCode} set to the
 * verbatim COBOL fail code (for example {@code "T"} invalid title, {@code "C"}
 * no credit-agency reply), returned at HTTP&nbsp;{@code 200}. The inbound
 * request is echoed (no values are invented) so the response preserves the
 * caller's payload exactly. This is required for the preserved consumer
 * ({@code WebController.checkIfResponseValidCreateCust}), which inspects
 * {@code CreCust.CommFailCode} (treating a non-empty value as failure) after
 * deserialising the body with a default {@code ObjectMapper}; a generic advice
 * body would lack the {@code CreCust} envelope and break that consumer.</p>
 * <p>Binding failures ({@link Valid @Valid}, HTTP&nbsp;{@code 400}) and any
 * unexpected exception (HTTP&nbsp;{@code 500}) are deliberately NOT caught here;
 * they propagate to the global advice.</p>
 *
 * @see CustomerService#createCustomer(CreateCustomerForm)
 * @see CreateCustomerJson
 * @see CrecustJson
 * @see CreateCustomerForm
 * @see BusinessRuleException
 */
@RestController
@RequestMapping("/crecust")
public class CreateCustomerController
{

	/** Success-flag value written to the response envelope ({@code COMM-SUCCESS = 'N'} on rejection). */
	private static final String FLAG_FAILURE = "N";

	/** The customer business service (CRECUST parity, F-006); all create logic lives here. */
	private final CustomerService customerService;

	/**
	 * Constructs the controller with its collaborating service. The single
	 * collaborator is injected by constructor for testability and immutability.
	 *
	 * @param customerService the customer business service that performs the
	 *                         {@code CRECUST} create sequence
	 */
	public CreateCustomerController(CustomerService customerService)
	{
		this.customerService = customerService;
	}

	/**
	 * Handles {@code POST /crecust/insert}, creating a customer from the
	 * supplied {@code CreCust} envelope.
	 *
	 * <p>The handler adapts the inbound wrapper into a {@link CreateCustomerForm}
	 * by a trivial field copy, delegates the entire create sequence to
	 * {@link CustomerService#createCustomer(CreateCustomerForm)}, and returns the
	 * service's populated success envelope at HTTP&nbsp;{@code 200}. A
	 * {@link BusinessRuleException} thrown by the service is caught and rendered
	 * as the frozen failure envelope ({@code CommSuccess="N"} plus the verbatim
	 * fail code) by echoing the inbound request, also at HTTP&nbsp;{@code 200}.</p>
	 *
	 * @param request the create-customer request envelope
	 *                ({@code {"CreCust": {...}}}); {@link Valid @Valid} triggers
	 *                Jakarta Bean Validation of the bound payload, a failure of
	 *                which yields HTTP&nbsp;{@code 400} via the global advice
	 * @return the create-customer response envelope, always HTTP&nbsp;{@code 200}
	 *         and always carrying the single top-level {@code CreCust} key
	 */
	@PostMapping(value = "/insert",
			consumes = MediaType.APPLICATION_JSON_VALUE,
			produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<CreateCustomerJson> createCustomer(
			@Valid @RequestBody CreateCustomerJson request)
	{
		try
		{
			// Trivial wrapper -> service-form adaptation (no business logic).
			// The all-args CreateCustomerForm constructor assigns each field
			// directly and does NOT re-order the date of birth, which is correct
			// because the wire CommDateOfBirth is already the compact DDMMYYYY
			// eight-character digit string the service expects.
			CrecustJson in = request.getCreCust();
			CreateCustomerForm form = new CreateCustomerForm(in.getCommName(),
					in.getCommAddress(), in.getCommDateOfBirth());

			// All CRECUST logic (title/DOB validation, asynchronous credit
			// check, gap-free customer-number allocation, PROCTRAN audit) is
			// owned by the service.
			CreateCustomerJson response = customerService.createCustomer(form);
			return ResponseEntity.ok(response);
		}
		catch (BusinessRuleException ex)
		{
			// The service threw to roll back the allocated customer number under
			// @Transactional. Rebuild the frozen CreCust envelope by echoing the
			// request with the failure flag and verbatim fail code so the
			// preserved consumer reads CreCust.CommSuccess / CommFailCode
			// unchanged. HTTP 200 preserves the contract body. No values are
			// invented: the caller's inputs are echoed as-is.
			CrecustJson echo = request.getCreCust();
			if (echo == null)
			{
				echo = new CrecustJson();
				request.setCreCust(echo);
			}
			echo.setCommSuccess(FLAG_FAILURE);
			echo.setCommFailCode(ex.getFailCode());
			return ResponseEntity.ok(request);
		}
	}

}
