/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.core.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ibm.cics.cip.bank.core.entity.AccountControl;

import jakarta.persistence.LockModeType;

/**
 * Spring Data JPA repository for {@link AccountControl} (the {@code
 * account_control} counter table), keyed by the {@code sort_code} string.
 *
 * <p>This repository replaces the legacy {@code NEWACCNO} named-counter /
 * VSAM control-record access. The account-number allocator reads the control
 * row with {@link #findBySortCodeForUpdate(String)}, which acquires a
 * {@code PESSIMISTIC_WRITE} row lock so concurrent allocations serialise and the
 * allocation is gap-free. Because the counter is incremented inside the same
 * {@code @Transactional} boundary as the account insert it feeds, a rolled-back
 * transaction restores the counter automatically (ADR-003) &mdash; which is why
 * a database {@code IDENTITY}/{@code SEQUENCE} is deliberately not used.</p>
 */
public interface AccountControlRepository
		extends JpaRepository<AccountControl, String>
{

	/**
	 * Reads the account-control row for the given sort code under a
	 * {@code PESSIMISTIC_WRITE} row lock, so that the caller may read and
	 * increment {@code last_account_number} atomically within its transaction.
	 *
	 * @param sortCode the six-digit, zero-padded sort code (primary key)
	 * @return the locked {@link AccountControl} row, or
	 *         {@link Optional#empty()} if no control row exists for the sort code
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT ac FROM AccountControl ac WHERE ac.sortCode = :sortCode")
	Optional<AccountControl> findBySortCodeForUpdate(
			@Param("sortCode") String sortCode);

}
