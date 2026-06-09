/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.core.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ibm.cics.cip.bank.core.entity.CustomerControl;

import jakarta.persistence.LockModeType;

/**
 * Spring Data JPA repository for {@link CustomerControl} (the {@code
 * customer_control} counter table), keyed by the {@code sort_code} string.
 *
 * <p>This repository replaces the legacy {@code NEWCUSNO} named-counter /
 * VSAM control-record access. The customer-number allocator reads the control
 * row with {@link #findBySortCodeForUpdate(String)}, which acquires a
 * {@code PESSIMISTIC_WRITE} row lock so concurrent allocations serialise and the
 * allocation is gap-free and roll-back-able within the enclosing transaction
 * (ADR-003).</p>
 */
public interface CustomerControlRepository
		extends JpaRepository<CustomerControl, String>
{

	/**
	 * Reads the customer-control row for the given sort code under a
	 * {@code PESSIMISTIC_WRITE} row lock, so that the caller may read and
	 * increment {@code last_customer_number} atomically within its transaction.
	 *
	 * @param sortCode the six-digit, zero-padded sort code (primary key)
	 * @return the locked {@link CustomerControl} row, or
	 *         {@link Optional#empty()} if no control row exists for the sort code
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT cc FROM CustomerControl cc WHERE cc.sortCode = :sortCode")
	Optional<CustomerControl> findBySortCodeForUpdate(
			@Param("sortCode") String sortCode);

}
