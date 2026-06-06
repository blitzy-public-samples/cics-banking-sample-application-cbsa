/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.core.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.ibm.cics.cip.bank.core.constants.BankConstants;
import com.ibm.cics.cip.bank.core.dto.DtoFormat;
import com.ibm.cics.cip.bank.core.dto.deletecustomer.DelcusJson;
import com.ibm.cics.cip.bank.core.dto.deletecustomer.DeleteCustomerJson;
import com.ibm.cics.cip.bank.core.entity.Customer;
import com.ibm.cics.cip.bank.core.exception.BusinessRuleException;
import com.ibm.cics.cip.bank.core.service.CustomerService;

/**
 * REST controller reproducing the frozen z/OS Connect <em>delete-customer</em>
 * endpoint (feature F-019), mapping the {@code CScustdel} service onto
 * {@link CustomerService#deleteCustomer}.
 *
 * <p>The path ({@code DELETE /delcus/remove/{customerNumber}}), HTTP verb, and
 * JSON envelope ({@code {"DelCus": {...}}}) are preserved verbatim.
 * {@code DELCUS.cbl} is the authoritative behavioural specification: it cascades
 * the customer's accounts (each appending an account-close PROCTRAN record) and
 * then appends a customer-close PROCTRAN record, all in one transaction.</p>
 *
 * <h2>Success / failure convention (endpoint-specific)</h2>
 * <p>The consumer treats {@code getCommDelFailCode() == 1} (JSON
 * {@code CommDelFailCd}) as &quot;customer not found&quot;. This controller sets
 * {@code CommDelFailCd=0} with {@code CommDelSuccess="Y"} on success, and
 * {@code CommDelFailCd=1} with {@code CommDelSuccess="N"} on a
 * {@link BusinessRuleException} (the only failure being the not-found case).
 * HTTP 200 is always returned.</p>
 */
@RestController
public class DeleteCustomerController
{

	/** Logger for request/outcome diagnostics. */
	private static final Logger LOG = LoggerFactory
			.getLogger(DeleteCustomerController.class);

	/** Integer fail code denoting success. */
	private static final int FAIL_NONE = 0;

	/** Integer fail code denoting &quot;customer not found&quot;. */
	private static final int FAIL_NOT_FOUND = 1;

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
	public DeleteCustomerController(CustomerService customerService)
	{
		this.customerService = customerService;
	}

	/**
	 * Deletes the customer identified by the path variable.
	 *
	 * @param customerNumber the customer number to delete
	 * @return the delete-customer response envelope, always HTTP 200
	 */
	@DeleteMapping(path = "/delcus/remove/{customerNumber}",
			produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<DeleteCustomerJson> deleteCustomer(
			@PathVariable long customerNumber)
	{
		try
		{
			Customer deleted = customerService.deleteCustomer(customerNumber);
			LOG.info("Customer deleted: {}",
					deleted.getId().getCustomerNumber());
			return ResponseEntity.ok(success(deleted));
		}
		catch (BusinessRuleException ex)
		{
			LOG.info("Delete-customer rejected, failCode={}",
					ex.getFailCode());
			return ResponseEntity.ok(failure(customerNumber));
		}
	}

	/**
	 * Builds the success envelope echoing the deleted customer snapshot.
	 *
	 * @param customer the deleted customer (detached snapshot)
	 * @return the populated success envelope
	 */
	private DeleteCustomerJson success(Customer customer)
	{
		DelcusJson out = new DelcusJson();
		out.setCommSortcode(Integer.parseInt(customer.getId().getSortCode()));
		out.setCommCustno(
				(int) Long.parseLong(customer.getId().getCustomerNumber()));
		out.setCommName(customer.getName());
		out.setCommAddress(customer.getAddress());
		out.setCommDateOfBirth(
				DtoFormat.dateToString(customer.getDateOfBirth()));
		out.setCommCsReviewDate(
				DtoFormat.dateToString(customer.getCsReviewDate()));
		out.setCommCreditScore(customer.getCreditScore() == null ? 0
				: customer.getCreditScore().intValue());
		out.setCommDelFailCode(FAIL_NONE);
		out.setCommDelSuccess(FLAG_SUCCESS);
		return new DeleteCustomerJson(out);
	}

	/**
	 * Builds the not-found failure envelope.
	 *
	 * @param customerNumber the customer number that was not found
	 * @return the populated failure envelope
	 */
	private DeleteCustomerJson failure(long customerNumber)
	{
		DelcusJson out = new DelcusJson();
		out.setCommSortcode(Integer.parseInt(BankConstants.SORT_CODE));
		out.setCommCustno((int) customerNumber);
		out.setCommDelFailCode(FAIL_NOT_FOUND);
		out.setCommDelSuccess(FLAG_FAILURE);
		return new DeleteCustomerJson(out);
	}

}
