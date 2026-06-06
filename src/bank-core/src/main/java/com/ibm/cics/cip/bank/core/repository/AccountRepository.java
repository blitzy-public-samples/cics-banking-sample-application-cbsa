/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.core.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ibm.cics.cip.bank.core.entity.Account;
import com.ibm.cics.cip.bank.core.entity.AccountId;

import jakarta.persistence.LockModeType;

/**
 * Spring Data JPA repository for {@link Account}, keyed by the composite
 * {@link AccountId} (sort code + account number).
 *
 * <p>This repository replaces the legacy JCICS file control / embedded Db2 SQL
 * for the {@code ACCOUNT} VSAM/Db2 dataset. The derived queries reproduce the
 * access paths the COBOL business programs rely on:</p>
 * <ul>
 *   <li>{@link #findByCustomerNumberOrderByIdAccountNumberAsc(String)} &mdash;
 *       a customer's accounts in ascending account-number order (INQACCCU /
 *       the customer account list, and the cascade in DELCUS).</li>
 *   <li>{@link #countByCustomerNumber(String)} &mdash; the per-customer account
 *       count enforced by CREACC (maximum of ten accounts per customer).</li>
 *   <li>{@link #findFirstByIdSortCodeOrderByIdAccountNumberDesc(String)} &mdash;
 *       the INQACC "highest account number" sentinel ({@code 99999999}).</li>
 *   <li>{@link #findByIdForUpdate(AccountId)} &mdash; a {@code PESSIMISTIC_WRITE}
 *       row read for DBCRFUN / XFRFUN balance mutation (the COBOL
 *       {@code READ ... UPDATE} record lock).</li>
 * </ul>
 */
public interface AccountRepository extends JpaRepository<Account, AccountId>
{

	/**
	 * Returns all accounts owned by the given customer, ordered by ascending
	 * account number.
	 *
	 * @param customerNumber the ten-digit, zero-padded customer number
	 * @return the customer's accounts (possibly empty), never {@code null}
	 */
	List<Account> findByCustomerNumberOrderByIdAccountNumberAsc(
			String customerNumber);

	/**
	 * Counts the accounts owned by the given customer.
	 *
	 * @param customerNumber the ten-digit, zero-padded customer number
	 * @return the number of accounts the customer owns
	 */
	long countByCustomerNumber(String customerNumber);

	/**
	 * Returns the account with the highest account number for the given sort
	 * code &mdash; the INQACC {@code 99999999} "highest account" sentinel.
	 *
	 * @param sortCode the six-digit, zero-padded sort code
	 * @return the highest-numbered account, or {@link Optional#empty()} if the
	 *         sort code has no accounts
	 */
	Optional<Account> findFirstByIdSortCodeOrderByIdAccountNumberDesc(
			String sortCode);

	/**
	 * Reads an account under a {@code PESSIMISTIC_WRITE} row lock so the caller
	 * may mutate its balances atomically within the enclosing transaction
	 * (reproducing the COBOL {@code EXEC CICS READ ... UPDATE} lock used by
	 * DBCRFUN and XFRFUN).
	 *
	 * @param id the composite account key (sort code + account number)
	 * @return the locked {@link Account}, or {@link Optional#empty()} if none
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT a FROM Account a WHERE a.id = :id")
	Optional<Account> findByIdForUpdate(@Param("id") AccountId id);

}
