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
import com.ibm.cics.cip.bank.core.dto.deletecustomer.DelcusJson;
import com.ibm.cics.cip.bank.core.dto.deletecustomer.DeleteCustomerJson;
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
 * <p>The consumer treats a JSON {@code CommDelFailCd} value of {@code "1"} as
 * &quot;customer not found&quot;. This controller sets
 * {@code CommDelFailCd="0"} with {@code CommDelSuccess="Y"} on success, and
 * {@code CommDelFailCd="1"} with {@code CommDelSuccess="N"} on a
 * {@link BusinessRuleException} (the only failure being the not-found case).
 * HTTP 200 is always returned.</p>
 */
@RestController
public class DeleteCustomerController
{

	/** Logger for request/outcome diagnostics. */
	private static final Logger LOG = LoggerFactory
			.getLogger(DeleteCustomerController.class);

	/** Single-character fail code denoting &quot;customer not found&quot; (wire value {@code "1"}). */
	private static final String FAIL_NOT_FOUND = "1";

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
			DeleteCustomerJson response = customerService
					.deleteCustomer(customerNumber);
			LOG.info("Customer deleted: {}",
					response.getDelCus().getCommCustno());
			return ResponseEntity.ok(response);
		}
		catch (BusinessRuleException ex)
		{
			LOG.info("Delete-customer rejected, failCode={}",
					ex.getFailCode());
			return ResponseEntity.ok(failure(customerNumber));
		}
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
		out.setCommSortcode(BankConstants.SORT_CODE);
		out.setCommCustno(String.format("%010d", customerNumber));
		out.setCommDelFailCode(FAIL_NOT_FOUND);
		out.setCommDelSuccess(FLAG_FAILURE);
		return new DeleteCustomerJson(out);
	}

}
