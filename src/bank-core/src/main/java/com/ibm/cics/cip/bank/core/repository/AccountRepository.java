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
 * used by the {@code ACCOUNT} VSAM/Db2 dataset (the legacy
 * {@code com.ibm.cics.cip.bankliberty.web.db2.Account} hand-coded the SQL
 * {@code SELECT ... FROM ACCOUNT WHERE ... ORDER BY ACCOUNT_NUMBER}). Those
 * access paths are recast here as declarative Spring Data derived queries so the
 * database performs the filtering, scoping and ordering.</p>
 *
 * <h2>Two distinct numeric identifiers</h2>
 * <p>The {@code account} record carries two different numbers, and the derived
 * queries below are deliberately precise about which is which:</p>
 * <ul>
 *   <li>the account's own number lives inside the composite primary key
 *       ({@code AccountId.accountNumber}), navigated as the
 *       {@code IdAccountNumber} property path (and the sort code as
 *       {@code IdSortCode});</li>
 *   <li>the owning customer's number is a <em>non-key</em> column on
 *       {@link Account} ({@code Account.customerNumber}), navigated as the
 *       {@code CustomerNumber} property path and backed by the
 *       {@code idx_account_customer_number} index.</li>
 * </ul>
 * <p>The marquee finder therefore <strong>filters</strong> on the non-key
 * {@code customerNumber} together with the key {@code id.sortCode}, while
 * <strong>ordering</strong> by the key {@code id.accountNumber}.</p>
 *
 * <h2>Derived access paths</h2>
 * <ul>
 *   <li>{@link #findByIdSortCodeAndCustomerNumberOrderByIdAccountNumberAsc(String, String)}
 *       &mdash; a customer's accounts within a branch, in ascending
 *       account-number order. Reproduces the legacy
 *       {@code getAccounts(customerNumber, sortCode)} and backs {@code INQACCCU}
 *       (list a customer's accounts) and the {@code DELCUS} cascade (find a
 *       customer's accounts to close).</li>
 *   <li>{@link #countByIdSortCodeAndCustomerNumber(String, String)} &mdash; the
 *       per-customer account count that enforces the {@code CREACC} maximum of
 *       ten accounts per customer (fail code {@code 8}); reproduces the legacy
 *       {@code getAccountsCountOnly} restricted to a single customer.</li>
 *   <li>{@link #findByIdSortCodeOrderByIdAccountNumberAsc(String)} &mdash; every
 *       account for a branch, in ascending account-number order; reproduces the
 *       legacy {@code getAccounts(sortCode)} (useful for listing, seeding and
 *       inquiry).</li>
 *   <li>{@link #findByIdForUpdate(AccountId)} &mdash; a single account read under
 *       a {@code PESSIMISTIC_WRITE} row lock for the {@code DBCRFUN}/{@code XFRFUN}
 *       balance-mutation path (the COBOL {@code EXEC CICS READ ... UPDATE} record
 *       lock; AAP &sect;0.6).</li>
 * </ul>
 *
 * <h2>Not resolved here: the "highest account" sentinel</h2>
 * <p>The {@code INQACC} {@code 99999999} ("highest account") sentinel and the
 * account-number allocation are <em>not</em> answered by a {@code MAX} aggregate
 * or an {@code ORDER BY ... DESC} table scan in this repository; the highest /
 * last-allocated account number is read from the {@code account_control} counter
 * row via {@code AccountControlRepository} (AAP &sect;0.6). No such scan is
 * exposed here by design.</p>
 *
 * <p>Single-account-by-key access ({@link #findById(Object) findById}),
 * persistence ({@code save}/{@code saveAll}), existence ({@code existsById}),
 * counting ({@code count}) and deletion ({@code delete}/{@code deleteById}) are
 * inherited unchanged from {@link JpaRepository}.</p>
 */
public interface AccountRepository extends JpaRepository<Account, AccountId>
{

	/**
	 * Returns all accounts owned by the given customer within the given branch,
	 * ordered by ascending account number.
	 *
	 * <p>Filters on the non-key {@code customerNumber} column and the key
	 * {@code id.sortCode}, ordering by the key {@code id.accountNumber}. This is
	 * the ordered fetch behind {@code INQACCCU} (the customer-account list,
	 * which the service caps at twenty rendered entries) and the {@code DELCUS}
	 * cascade (closing every account a customer holds). Reproduces the legacy
	 * {@code getAccounts(customerNumber, sortCode)} access path.</p>
	 *
	 * @param sortCode       the six-digit, zero-padded branch sort code (the
	 *                       {@code id.sortCode} key component)
	 * @param customerNumber the ten-digit, zero-padded owning customer number
	 *                       (the non-key {@code customerNumber} column)
	 * @return the customer's accounts in ascending account-number order
	 *         (possibly empty), never {@code null}
	 */
	List<Account> findByIdSortCodeAndCustomerNumberOrderByIdAccountNumberAsc(
			String sortCode, String customerNumber);

	/**
	 * Counts the accounts owned by the given customer within the given branch.
	 *
	 * <p>Backs the {@code CREACC} pre-insert check that a customer holds fewer
	 * than the maximum of ten accounts (fail code {@code 8}). Filters on the
	 * key {@code id.sortCode} and the non-key {@code customerNumber} column.</p>
	 *
	 * @param sortCode       the six-digit, zero-padded branch sort code (the
	 *                       {@code id.sortCode} key component)
	 * @param customerNumber the ten-digit, zero-padded owning customer number
	 *                       (the non-key {@code customerNumber} column)
	 * @return the number of accounts the customer owns in the branch
	 */
	long countByIdSortCodeAndCustomerNumber(String sortCode,
			String customerNumber);

	/**
	 * Returns every account for the given branch, ordered by ascending account
	 * number.
	 *
	 * <p>Filters on the key {@code id.sortCode} and orders by the key
	 * {@code id.accountNumber}. Reproduces the legacy {@code getAccounts(sortCode)}
	 * access path; useful for branch-wide listing, seeding and inquiry.</p>
	 *
	 * @param sortCode the six-digit, zero-padded branch sort code (the
	 *                 {@code id.sortCode} key component)
	 * @return the branch's accounts in ascending account-number order (possibly
	 *         empty), never {@code null}
	 */
	List<Account> findByIdSortCodeOrderByIdAccountNumberAsc(String sortCode);

	/**
	 * Reads a single account by its composite key under a
	 * {@code PESSIMISTIC_WRITE} row lock, so the caller may mutate its balances
	 * atomically within the enclosing {@code @Transactional} boundary.
	 *
	 * <p>Reproduces the COBOL {@code EXEC CICS READ ... UPDATE} record lock that
	 * {@code DBCRFUN} (debit/credit) and {@code XFRFUN} (transfer) hold from read
	 * through update. {@code XFRFUN} acquires this lock on the lower-numbered
	 * account first to avoid deadlock, then on the higher-numbered account (AAP
	 * &sect;0.6); the deterministic key ordering and deadlock retry are applied
	 * by the calling service.</p>
	 *
	 * @param id the composite account key (sort code + account number)
	 * @return the locked {@link Account}, or {@link Optional#empty()} if no
	 *         account exists for the key
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT a FROM Account a WHERE a.id = :id")
	Optional<Account> findByIdForUpdate(@Param("id") AccountId id);

}
