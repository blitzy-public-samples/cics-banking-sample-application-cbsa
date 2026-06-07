/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.core.repository;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

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
 * listing contract. A partial index ({@code idx_proctran_active} on
 * {@code (sort_code, date, time) WHERE deleted = false}) backs these reads,
 * covering both the active-only filter and the date/time ordering so the query
 * needs neither a sequential scan nor a sort.</p>
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

}
