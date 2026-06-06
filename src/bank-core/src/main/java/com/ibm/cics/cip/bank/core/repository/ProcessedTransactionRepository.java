/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.core.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ibm.cics.cip.bank.core.entity.ProcessedTransaction;
import com.ibm.cics.cip.bank.core.entity.ProcessedTransactionId;

/**
 * Spring Data JPA repository for {@link ProcessedTransaction} (the append-only
 * {@code processed_transaction} audit log), keyed by the composite
 * {@link ProcessedTransactionId} (sort code + reference).
 *
 * <p>This repository replaces the legacy JCICS / embedded Db2 access to the
 * {@code PROCTRAN} dataset. PROCTRAN is APPEND-ONLY with logical delete
 * (ADR-006): rows are inserted (via {@link #save}) and may be soft-deleted, but
 * are never physically removed.</p>
 */
public interface ProcessedTransactionRepository
		extends JpaRepository<ProcessedTransaction, ProcessedTransactionId>
{

	/**
	 * Returns the highest numeric {@code ref} currently stored for the given
	 * sort code, or {@code 0} if none. Used to allocate the next gap-free,
	 * monotonic transaction reference for an appended audit row. The reference
	 * is a fixed-width {@code CHAR(12)} display-numeric string, so it is trimmed
	 * and cast to {@code BIGINT} for the {@code MAX} comparison.
	 *
	 * @param sortCode the six-digit, zero-padded sort code
	 * @return the greatest stored {@code ref} as a number, or {@code 0}
	 */
	@Query(value = "SELECT COALESCE(MAX(CAST(TRIM(ref) AS BIGINT)), 0) "
			+ "FROM processed_transaction WHERE sort_code = :sortCode",
			nativeQuery = true)
	long findMaxReference(@Param("sortCode") String sortCode);

	/**
	 * Returns the active (non-soft-deleted) transactions for a single account,
	 * newest activity last, ordered by date then time. Supports active-only
	 * audit-history reads (the logical-delete model: {@code WHERE deleted =
	 * false}).
	 *
	 * @param sortCode          the six-digit, zero-padded sort code
	 * @param transactionNumber the eight-digit, zero-padded account number
	 * @return the active transactions for the account (possibly empty)
	 */
	List<ProcessedTransaction> findByIdSortCodeAndTransactionNumberAndDeletedFalseOrderByDateAscTimeAsc(
			String sortCode, String transactionNumber);

}
