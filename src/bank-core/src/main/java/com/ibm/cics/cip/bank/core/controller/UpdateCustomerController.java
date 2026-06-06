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

import com.ibm.cics.cip.bank.core.dto.DtoFormat;
import com.ibm.cics.cip.bank.core.dto.updatecustomer.UpdcustJson;
import com.ibm.cics.cip.bank.core.dto.updatecustomer.UpdateCustomerJson;
import com.ibm.cics.cip.bank.core.entity.Customer;
import com.ibm.cics.cip.bank.core.exception.BusinessRuleException;
import com.ibm.cics.cip.bank.core.service.CustomerService;

/**
 * REST controller reproducing the frozen z/OS Connect <em>update-customer</em>
 * endpoint (feature F-019), mapping the {@code CScustupd} service onto
 * {@link CustomerService#updateCustomer}.
 *
 * <p>The path ({@code PUT /updcust/update}), HTTP verb, and JSON envelope
 * ({@code {"UpdCust": {...}}}) are preserved verbatim. {@code UPDCUST.cbl} is the
 * authoritative behavioural specification: it changes only the customer name and
 * address (never balances or any account field), and it writes no PROCTRAN
 * record.</p>
 *
 * <h2>Success / failure convention (endpoint-specific)</h2>
 * <p>This controller sets {@code CommUpdSuccess="Y"} on success and
 * {@code CommUpdSuccess="N"} with the verbatim fail code on a
 * {@link BusinessRuleException} (for example {@code "4"} when neither name nor
 * address was supplied, {@code "T"} for an invalid title, {@code "1"} when the
 * customer does not exist). HTTP 200 is always returned.</p>
 */
@RestController
public class UpdateCustomerController
{

	/** Logger for request/outcome diagnostics. */
	private static final Logger LOG = LoggerFactory
			.getLogger(UpdateCustomerController.class);

	/** Success flag value. */
	private static final String FLAG_SUCCESS = "Y";

	/** Failure flag value. */
	private static final String FLAG_FAILURE = "N";

	/** The customer business service. */
	private final CustomerService customerService;

	/**
	 * Constructs the controller with its collaborating service.
	 *
	 * @param customerService the customer business service
	 */
	public UpdateCustomerController(CustomerService customerService)
	{
		this.customerService = customerService;
	}

	/**
	 * Updates a customer's name and/or address from the supplied envelope.
	 *
	 * @param request the update-customer request envelope
	 * @return the update-customer response envelope, always HTTP 200
	 */
	@PutMapping(path = "/updcust/update",
			consumes = MediaType.APPLICATION_JSON_VALUE,
			produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<UpdateCustomerJson> updateCustomer(
			@RequestBody UpdateCustomerJson request)
	{
		UpdcustJson in = request.getUpdcust();
		long customerNumber = Long.parseLong(in.getCommCustno().trim());

		try
		{
			Customer updated = customerService.updateCustomer(customerNumber,
					in.getCommName(), in.getCommAddress());
			LOG.info("Customer updated: {}",
					updated.getId().getCustomerNumber());
			return ResponseEntity.ok(success(updated));
		}
		catch (BusinessRuleException ex)
		{
			LOG.info("Update-customer rejected, failCode={}",
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
	private UpdateCustomerJson success(Customer customer)
	{
		UpdcustJson out = new UpdcustJson();
		out.setCommSortcode(customer.getId().getSortCode());
		out.setCommCustno(customer.getId().getCustomerNumber());
		out.setCommName(customer.getName());
		out.setCommAddress(customer.getAddress());
		out.setCommDateOfBirth(DtoFormat.dateToInt(customer.getDateOfBirth()));
		out.setCommCreditScore(customer.getCreditScore() == null ? 0
				: customer.getCreditScore().intValue());
		out.setCommCreditScoreReviewDate(
				DtoFormat.dateToInt(customer.getCsReviewDate()));
		out.setCommUpdateSuccess(FLAG_SUCCESS);
		out.setCommUpdateFailCode("");
		UpdateCustomerJson wrapper = new UpdateCustomerJson();
		wrapper.setUpdcust(out);
		return wrapper;
	}

	/**
	 * Builds the failure envelope, echoing the caller's customer number and the
	 * verbatim COBOL fail code.
	 *
	 * @param in       the original request commarea
	 * @param failCode the COBOL fail code to surface
	 * @return the populated failure envelope
	 */
	private UpdateCustomerJson failure(UpdcustJson in, String failCode)
	{
		UpdcustJson out = new UpdcustJson();
		out.setCommCustno(in.getCommCustno());
		out.setCommName(in.getCommName());
		out.setCommAddress(in.getCommAddress());
		out.setCommUpdateSuccess(FLAG_FAILURE);
		out.setCommUpdateFailCode(failCode);
		UpdateCustomerJson wrapper = new UpdateCustomerJson();
		wrapper.setUpdcust(out);
		return wrapper;
	}

}
