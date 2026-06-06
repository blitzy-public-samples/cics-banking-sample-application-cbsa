/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.core.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ibm.cics.cip.bank.core.entity.Customer;
import com.ibm.cics.cip.bank.core.entity.CustomerId;

/**
 * Spring Data JPA repository for {@link Customer}, keyed by the composite
 * {@link CustomerId} (sort code + customer number).
 *
 * <p>This repository replaces the legacy JCICS file control / embedded Db2 SQL
 * for the {@code CUSTOMER} VSAM/Db2 dataset. The derived queries reproduce the
 * access paths the COBOL business programs rely on:</p>
 * <ul>
 *   <li>{@link #findFirstByIdSortCodeOrderByIdCustomerNumberDesc(String)} &mdash;
 *       the INQCUST {@code 9999999999} "highest customer" sentinel.</li>
 *   <li>{@link #findByIdSortCodeOrderByIdCustomerNumberAsc(String)} &mdash; the
 *       ordered customer list used by the INQCUST {@code 0000000000} "random"
 *       sentinel pick.</li>
 * </ul>
 */
public interface CustomerRepository extends JpaRepository<Customer, CustomerId>
{

	/**
	 * Returns the customer with the highest customer number for the given sort
	 * code &mdash; the INQCUST {@code 9999999999} "highest customer" sentinel.
	 *
	 * @param sortCode the six-digit, zero-padded sort code
	 * @return the highest-numbered customer, or {@link Optional#empty()} if the
	 *         sort code has no customers
	 */
	Optional<Customer> findFirstByIdSortCodeOrderByIdCustomerNumberDesc(
			String sortCode);

	/**
	 * Returns all customers for the given sort code in ascending
	 * customer-number order. Supports the INQCUST {@code 0000000000} "random
	 * pick" sentinel (the caller chooses one of the returned rows).
	 *
	 * @param sortCode the six-digit, zero-padded sort code
	 * @return the customers for the sort code (possibly empty), never
	 *         {@code null}
	 */
	List<Customer> findByIdSortCodeOrderByIdCustomerNumberAsc(String sortCode);

}
