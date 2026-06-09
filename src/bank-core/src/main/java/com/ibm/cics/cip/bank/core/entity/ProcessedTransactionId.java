/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.core.entity;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * Composite primary key for the {@code processed_transaction} table, used as the
 * {@code @EmbeddedId} of {@link ProcessedTransaction}.
 *
 * <h2>Why the key is {@code (sortCode, ref)} and not {@code (sortCode,
 * transactionNumber)}</h2>
 * <p>{@code PROCTRAN.cpy} models a processed transaction with two distinct
 * identifier-like fields, and the COBOL programs assign them with different
 * meanings that the relational model must preserve:</p>
 * <ul>
 *   <li><strong>{@code PROC-TRAN-NUMBER PIC 9(8)}</strong> &rarr; the
 *       {@code transaction_number} column. This holds the <em>account number</em>
 *       the transaction pertains to (DBCRFUN.cbl line 468
 *       {@code MOVE COMM-ACCNO TO HV-PROCTRAN-ACC-NUMBER}) or {@code '00000000'}
 *       for customer-level transactions (CRECUST.cbl line 1197
 *       {@code MOVE ZEROS TO HV-PROCTRAN-ACC-NUMBER}). It is therefore
 *       <strong>not</strong> unique per row, and the webui frozen JSON contract
 *       emits it under the field name {@code accountNumber}. It is a plain
 *       (non-key) column on {@link ProcessedTransaction}.</li>
 *   <li><strong>{@code PROC-TRAN-REF PIC 9(12)}</strong> &rarr; the {@code ref}
 *       column. This is the per-transaction reference (the CICS task number,
 *       {@code WS-EIBTASKN12}), which uniquely identifies a single appended
 *       audit row, so it is this composite key's second component. bank-core
 *       allocates a gap-free, monotonic, zero-padded 12-character ref for every
 *       appended row.</li>
 * </ul>
 * <p>The reference Db2 DDL ({@code PROCDB2.cpy}) declares no primary key at all
 * (PROCTRAN is a pure append-only log); promoting the unique {@code ref} to the
 * relational primary key is the faithful relational rendering and is what keeps
 * the append-only, never-physically-deleted contract (ADR-006) intact.</p>
 *
 * <h2>Fixed-width character identifiers</h2>
 * <p>Although the copybook declares both sub-fields as display-numeric
 * ({@code PIC 9(n)}), they are modelled here as {@link String}, never a numeric
 * Java type, to retain the fixed-width, left-zero-padded representation
 * verbatim (AAP &sect;0.6). Each {@link Column} pins
 * {@code columnDefinition = "bpchar(n)"} so Hibernate {@code validate} accepts
 * the PostgreSQL {@code CHAR(n)} columns (reported as {@code bpchar}) without
 * relaxing {@code validate} or altering the schema.</p>
 *
 * <h2>Identity semantics</h2>
 * <p>JPA composite-key classes used with {@code @EmbeddedId} must implement
 * {@link Serializable} and provide value-based {@link #equals(Object)} and
 * {@link #hashCode()} over the complete set of key fields; both are implemented
 * here over {@code sortCode} <em>and</em> {@code ref}.</p>
 */
@Embeddable
public class ProcessedTransactionId implements Serializable
{

	/** Serialization version for this composite-key value object. */
	private static final long serialVersionUID = 2L;

	/**
	 * Branch sort code &mdash; COBOL {@code PROC-TRAN-SORT-CODE PIC 9(6)}.
	 * Mapped to {@code sort_code CHAR(6) NOT NULL}.
	 */
	@Column(name = "sort_code", length = 6, nullable = false, columnDefinition = "bpchar(6)")
	private String sortCode;

	/**
	 * Per-transaction reference &mdash; COBOL {@code PROC-TRAN-REF PIC 9(12)}
	 * (the CICS task number). Mapped to the fixed-width {@code ref CHAR(12) NOT
	 * NULL} column; it is the unique identifier of a single appended audit row
	 * and therefore the second component of this composite key. Stored as a
	 * {@link String} to preserve the twelve-digit, zero-padded form verbatim.
	 */
	@Column(name = "ref", length = 12, nullable = false, columnDefinition = "bpchar(12)")
	private String ref;

	/**
	 * No-argument constructor required by the JPA specification for
	 * {@code @Embeddable} types.
	 */
	public ProcessedTransactionId()
	{
	}

	/**
	 * Convenience constructor that fully populates the composite key.
	 *
	 * @param sortCode the branch sort code (six-digit, zero-padded)
	 * @param ref      the transaction reference (twelve-digit, zero-padded)
	 */
	public ProcessedTransactionId(String sortCode, String ref)
	{
		this.sortCode = sortCode;
		this.ref = ref;
	}

	/**
	 * Returns the branch sort code component of the key.
	 *
	 * @return the sort code, or {@code null} if not yet set
	 */
	public String getSortCode()
	{
		return sortCode;
	}

	/**
	 * Sets the branch sort code component of the key.
	 *
	 * @param sortCode the six-digit, zero-padded sort code
	 */
	public void setSortCode(String sortCode)
	{
		this.sortCode = sortCode;
	}

	/**
	 * Returns the transaction reference component of the key.
	 *
	 * @return the reference, or {@code null} if not yet set
	 */
	public String getRef()
	{
		return ref;
	}

	/**
	 * Sets the transaction reference component of the key.
	 *
	 * @param ref the twelve-digit, zero-padded transaction reference
	 */
	public void setRef(String ref)
	{
		this.ref = ref;
	}

	/**
	 * Value-based equality over the full composite key. Two
	 * {@code ProcessedTransactionId} instances are equal exactly when both their
	 * {@code sortCode} and {@code ref} components are equal.
	 *
	 * @param obj the object to compare against
	 * @return {@code true} if {@code obj} is a {@code ProcessedTransactionId}
	 *         with equal key components, {@code false} otherwise
	 */
	@Override
	public boolean equals(Object obj)
	{
		if (this == obj)
		{
			return true;
		}
		if (obj == null || getClass() != obj.getClass())
		{
			return false;
		}
		ProcessedTransactionId other = (ProcessedTransactionId) obj;
		return Objects.equals(sortCode, other.sortCode)
				&& Objects.equals(ref, other.ref);
	}

	/**
	 * Hash code consistent with {@link #equals(Object)}, computed over both key
	 * components.
	 *
	 * @return the hash code of this composite key
	 */
	@Override
	public int hashCode()
	{
		return Objects.hash(sortCode, ref);
	}

	/**
	 * Diagnostic representation of the composite key (logging/debugging only).
	 *
	 * @return a string describing both key components
	 */
	@Override
	public String toString()
	{
		return "ProcessedTransactionId{" + "sortCode='" + sortCode + '\''
				+ ", ref='" + ref + '\'' + '}';
	}

}
