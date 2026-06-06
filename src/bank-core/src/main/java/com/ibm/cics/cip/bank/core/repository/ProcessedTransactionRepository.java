/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.core.repository;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ibm.cics.cip.bank.core.entity.ProcessedTransaction;
import com.ibm.cics.cip.bank.core.entity.ProcessedTransactionId;

/**
 * Spring Data JPA repository for {@link ProcessedTransaction} &mdash; the Java
 * data-access tier for the {@code processed_transaction} audit log, keyed by the
 * composite {@link ProcessedTransactionId} ({@code sort_code} + {@code ref}).
 *
 * <p>This interface replaces the legacy JCICS file control / embedded Db2 access
 * to the {@code PROCTRAN} dataset (the COBOL programs and the {@code webui}
 * {@code ...web.db2.ProcessedTransaction} JDBC helper). The repository pattern
 * supersedes the hand-written {@code PreparedStatement} SQL: appends become the
 * inherited {@link #save(Object) save(...)}, and the legacy
 * {@code getProcessedTransactions(sortCode, limit, offset)} list &mdash;
 * {@code SELECT * FROM PROCTRAN WHERE PROCTRAN_SORTCODE like ? ORDER BY
 * PROCTRAN_DATE ASC, PROCTRAN_TIME ASC} with paging &mdash; becomes the derived
 * active-only ordered queries below.</p>
 *
 * <h2>Append-only with logical delete (ADR-006)</h2>
 * <p>{@code processed_transaction} is an <strong>APPEND-ONLY</strong> audit log.
 * Two invariants follow and shape this interface:</p>
 * <ol>
 *   <li><strong>Append-only:</strong> new audit rows are added exclusively via
 *       the inherited {@link #save(Object) save(...)} /
 *       {@link #saveAll(Iterable) saveAll(...)}. No bespoke insert method is
 *       declared.</li>
 *   <li><strong>Logical delete only &mdash; NEVER physical delete:</strong>
 *       "deleting" a transaction means setting its {@code deleted} flag to
 *       {@code true} and {@code save}-ing it (a service-layer concern); the row
 *       is retained forever. The inherited
 *       {@link org.springframework.data.repository.CrudRepository#deleteById}
 *       /{@code delete} affordances exist on {@link JpaRepository} but
 *       <strong>MUST NOT</strong> be used for {@code PROCTRAN}, and this
 *       interface deliberately declares no additional physical-delete method,
 *       no {@code @Modifying} delete query, and no truncate affordance.</li>
 * </ol>
 *
 * <h2>Active-only reads</h2>
 * <p>Every list/browse query filters {@code deleted = false} (the derived
 * {@code DeletedFalse} predicate) so that logically-deleted history stays hidden
 * from normal reads, and orders by {@code date} then {@code time} &mdash;
 * reproducing the frozen {@code ORDER BY PROCTRAN_DATE ASC, PROCTRAN_TIME ASC}
 * listing contract. A partial index ({@code idx_proctran_active ... WHERE
 * deleted = false}) backs these reads.</p>
 *
 * <h2>Derived-query property paths</h2>
 * <p>{@code IdSortCode} resolves through the {@code @EmbeddedId} field
 * {@link ProcessedTransaction#getId() id} to its {@code sortCode} component;
 * {@code Deleted}, {@code Date} and {@code Time} are plain entity properties.
 * The reserved-word columns {@code date}/{@code time} are referenced by their
 * Java property names here &mdash; Hibernate quotes the underlying columns
 * automatically, so no manual quoting is required in this interface.</p>
 */
public interface ProcessedTransactionRepository
		extends JpaRepository<ProcessedTransaction, ProcessedTransactionId>
{

	/**
	 * Returns one page of the <em>active</em> (non-logically-deleted)
	 * transactions for a branch sort code, ordered by ascending date then
	 * ascending time.
	 *
	 * <p>This is the primary list query and the faithful reproduction of the
	 * legacy {@code getProcessedTransactions(sortCode, limit, offset)} access
	 * path: it filters {@code deleted = false}, orders by
	 * {@code PROCTRAN_DATE ASC, PROCTRAN_TIME ASC}, and delegates the
	 * limit/offset windowing to the supplied {@link Pageable}.</p>
	 *
	 * @param sortCode the six-digit, zero-padded branch sort code (matched
	 *                 against the embedded key's {@code sortCode} component)
	 * @param pageable the pagination/window specification (page number and
	 *                 size); ordering is fixed by the method name, so any
	 *                 {@code Sort} carried by the {@code Pageable} is redundant
	 * @return the requested page of active transactions in date/time order
	 *         (possibly empty), never {@code null}
	 */
	List<ProcessedTransaction> findByIdSortCodeAndDeletedFalseOrderByDateAscTimeAsc(
			String sortCode, Pageable pageable);

	/**
	 * Returns the complete, unpaged list of <em>active</em>
	 * (non-logically-deleted) transactions for a branch sort code, ordered by
	 * ascending date then ascending time.
	 *
	 * <p>Convenience overload of
	 * {@link #findByIdSortCodeAndDeletedFalseOrderByDateAscTimeAsc(String, Pageable)}
	 * for callers that need the full ordered history for a sort code rather than
	 * a single page. It applies the same {@code deleted = false} active-only
	 * filter and {@code date}-then-{@code time} ordering.</p>
	 *
	 * @param sortCode the six-digit, zero-padded branch sort code (matched
	 *                 against the embedded key's {@code sortCode} component)
	 * @return all active transactions for the sort code in date/time order
	 *         (possibly empty), never {@code null}
	 */
	List<ProcessedTransaction> findByIdSortCodeAndDeletedFalseOrderByDateAscTimeAsc(
			String sortCode);

	/**
	 * Returns the greatest numeric transaction reference currently stored for
	 * the given sort code, or {@code 0} if the sort code has no rows yet.
	 *
	 * <p>This read supports the gap-free, monotonic reference allocation
	 * performed by the service layer
	 * ({@code ProcessedTransactionAppender}): the caller first acquires a
	 * {@code PESSIMISTIC_WRITE} lock on the matching {@code account_control}
	 * row to serialise concurrent appends for the sort code, then allocates the
	 * next reference as {@code findMaxReference(sortCode) + 1}. Because the
	 * COBOL {@code PROC-TRAN-REF} (the CICS task number) has no equivalent in a
	 * mainframe-free runtime, bank-core derives the reference this way instead.</p>
	 *
	 * <p>The aggregate intentionally scans <strong>all</strong> rows &mdash;
	 * <em>including</em> logically-deleted ones &mdash; so that a soft-deleted
	 * audit row can never have its reference re-used by a later append; this is
	 * why the query does not filter {@code deleted = false}. The reference is a
	 * fixed-width {@code CHAR(12)} display-numeric string, so it is trimmed and
	 * cast to {@code BIGINT} for the numeric {@code MAX} comparison, and
	 * {@code COALESCE} yields {@code 0} for an empty sort code. This is a
	 * read-only allocation aid, not an identity/sequence generator and not a
	 * physical-delete affordance, so it is consistent with the append-only,
	 * never-physically-deleted contract (ADR-006).</p>
	 *
	 * @param sortCode the six-digit, zero-padded sort code (matched against the
	 *                 {@code sort_code} column)
	 * @return the greatest stored reference as a {@code long}, or {@code 0} if
	 *         the sort code has no transactions
	 */
	@Query(value = "SELECT COALESCE(MAX(CAST(TRIM(ref) AS BIGINT)), 0) "
			+ "FROM processed_transaction WHERE sort_code = :sortCode",
			nativeQuery = true)
	long findMaxReference(@Param("sortCode") String sortCode);

}
