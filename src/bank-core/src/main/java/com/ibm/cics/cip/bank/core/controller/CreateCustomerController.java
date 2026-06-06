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

import com.ibm.cics.cip.bank.core.constants.BankConstants;
import com.ibm.cics.cip.bank.core.dto.DtoFormat;
import com.ibm.cics.cip.bank.core.dto.common.CommKey;
import com.ibm.cics.cip.bank.core.dto.createcustomer.CrecustJson;
import com.ibm.cics.cip.bank.core.dto.createcustomer.CreateCustomerJson;
import com.ibm.cics.cip.bank.core.entity.Customer;
import com.ibm.cics.cip.bank.core.exception.BusinessRuleException;
import com.ibm.cics.cip.bank.core.service.CustomerService;

/**
 * REST controller reproducing the frozen z/OS Connect <em>create-customer</em>
 * endpoint (feature F-019), mapping the {@code CScustcre} service onto
 * {@link CustomerService#createCustomer}.
 *
 * <p>The path ({@code POST /crecust/insert}), HTTP verb, and JSON envelope
 * ({@code {"CreCust": {...}}}) are preserved verbatim so the existing consumers
 * require only a base-URL re-point. The legacy program {@code CRECUST.cbl} is
 * the authoritative behavioural specification.</p>
 *
 * <h2>Success / failure convention (endpoint-specific)</h2>
 * <p>The consumer treats the response as a failure when {@code CommFailCode} is
 * <em>not</em> the empty string. This controller therefore sets
 * {@code CommSuccess="Y"} and {@code CommFailCode=""} on success, and on a
 * {@link BusinessRuleException} sets {@code CommSuccess="N"} with the verbatim
 * COBOL fail code (for example {@code "T"} invalid title, {@code "C"} no
 * credit-agency reply). The failure is returned with HTTP 200 because the
 * consumer inspects the body, not the status, for business outcomes.</p>
 */
@RestController
public class CreateCustomerController
{

	/** Logger for request/outcome diagnostics. */
	private static final Logger LOG = LoggerFactory
			.getLogger(CreateCustomerController.class);

	/** Success sentinel: empty fail code (consumer treats non-empty as failure). */
	private static final String SUCCESS_FAIL_CODE = "";

	/** Success flag value. */
	private static final String FLAG_SUCCESS = "Y";

	/** Failure flag value. */
	private static final String FLAG_FAILURE = "N";

	/** The create-customer business service. */
	private final CustomerService customerService;

	/**
	 * Constructs the controller with its collaborating service.
	 *
	 * @param customerService the create-customer business service
	 */
	public CreateCustomerController(CustomerService customerService)
	{
		this.customerService = customerService;
	}

	/**
	 * Creates a customer from the supplied envelope.
	 *
	 * @param request the create-customer request envelope
	 * @return the create-customer response envelope, always HTTP 200
	 */
	@PostMapping(path = "/crecust/insert",
			consumes = MediaType.APPLICATION_JSON_VALUE,
			produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<CreateCustomerJson> createCustomer(
			@RequestBody CreateCustomerJson request)
	{
		CrecustJson in = request.getCreCust();
		String name = in.getCommName();
		String address = in.getCommAddress();

		try
		{
			Customer created = customerService.createCustomer(name, address,
					DtoFormat.parseDate(in.getCommDateOfBirth()));
			LOG.info("Customer created: {}",
					created.getId().getCustomerNumber());
			return ResponseEntity.ok(success(created));
		}
		catch (BusinessRuleException ex)
		{
			LOG.info("Create-customer rejected, failCode={}",
					ex.getFailCode());
			return ResponseEntity.ok(failure(in, ex.getFailCode()));
		}
	}

	/**
	 * Builds the success envelope echoing the persisted customer.
	 *
	 * @param customer the persisted customer
	 * @return the populated success envelope
	 */
	private CreateCustomerJson success(Customer customer)
	{
		CrecustJson out = new CrecustJson();
		out.setCommName(customer.getName());
		out.setCommAddress(customer.getAddress());
		out.setCommDateOfBirth(
				DtoFormat.dateToString(customer.getDateOfBirth()));
		out.setCommCreditScore(customer.getCreditScore() == null ? 0
				: customer.getCreditScore().intValue());
		out.setCommCsReviewDate(
				DtoFormat.dateToString(customer.getCsReviewDate()));
		out.setCommKey(new CommKey(
				Integer.parseInt(customer.getId().getSortCode()),
				Long.parseLong(customer.getId().getCustomerNumber())));
		out.setCommSuccess(FLAG_SUCCESS);
		out.setCommFailCode(SUCCESS_FAIL_CODE);
		return new CreateCustomerJson(out);
	}

	/**
	 * Builds the failure envelope, echoing the caller's inputs and the verbatim
	 * COBOL fail code.
	 *
	 * @param in       the original request commarea
	 * @param failCode the COBOL fail code to surface
	 * @return the populated failure envelope
	 */
	private CreateCustomerJson failure(CrecustJson in, String failCode)
	{
		CrecustJson out = new CrecustJson();
		out.setCommName(in.getCommName());
		out.setCommAddress(in.getCommAddress());
		out.setCommDateOfBirth(in.getCommDateOfBirth());
		out.setCommKey(new CommKey(
				Integer.parseInt(BankConstants.SORT_CODE), 0L));
		out.setCommSuccess(FLAG_FAILURE);
		out.setCommFailCode(failCode);
		return new CreateCustomerJson(out);
	}

}
