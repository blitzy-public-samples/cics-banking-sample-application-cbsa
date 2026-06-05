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
 * <p>This value object is the Java rendering of the COBOL {@code PROC-TRAN-ID}
 * group inside {@code PROCTRAN.cpy}, which is the authoritative structural
 * specification for the {@code processed_transaction} record:</p>
 *
 * <pre>
 *   05 PROC-TRAN-ID.
 *      07 PROC-TRAN-SORT-CODE   PIC 9(6).   --&gt; sortCode          (sort_code CHAR(6))
 *      07 PROC-TRAN-NUMBER      PIC 9(8).   --&gt; transactionNumber (transaction_number CHAR(8))
 * </pre>
 *
 * <h2>Fixed-width character identifiers</h2>
 * <p>Although the copybook declares both sub-fields as display-numeric
 * ({@code PIC 9(n)}), they are modelled here as {@link String}, never as a
 * numeric Java type. The legacy records store these identifiers as fixed-width,
 * left-zero-padded display numbers, and the relational schema preserves that by
 * declaring them as {@code CHAR(n)} (AAP &sect;0.6, "Fixed-width character
 * identifiers"). Keeping them as {@code String} retains the exact on-disk
 * representation (including leading zeros); producing the correctly padded wire
 * value is the responsibility of the DTO layer, not this entity-key class.</p>
 *
 * <h2>{@code ddl-auto: validate} contract</h2>
 * <p>The relational schema is owned by Flyway ({@code V1__create_core_tables.sql})
 * and Hibernate runs in {@code validate} mode, so the {@link Column} mappings on
 * this class must match the {@code processed_transaction} primary-key columns
 * <strong>exactly</strong> &mdash; column name, length and nullability:</p>
 *
 * <ul>
 *   <li>{@code sort_code CHAR(6) NOT NULL}</li>
 *   <li>{@code transaction_number CHAR(8) NOT NULL}</li>
 *   <li>composite key {@code pk_processed_transaction (sort_code, transaction_number)}</li>
 * </ul>
 *
 * <p>Note the deliberate column name {@code transaction_number} (not
 * {@code proc_tran_number}): the COBOL field is {@code PROC-TRAN-NUMBER} but the
 * authoritative V1 column is {@code transaction_number}. Explicit snake_case
 * {@code @Column(name = ...)} values are declared because the module applies no
 * physical-naming-strategy override.</p>
 *
 * <h2>CHAR vs VARCHAR reconciliation</h2>
 * <p>Both key columns are declared {@code CHAR(n)} in V1. A plain {@link String}
 * field maps to the JDBC {@code VARCHAR} type, which Hibernate's {@code validate}
 * mode rejects against a PostgreSQL {@code CHAR(n)} column (reported as
 * {@code bpchar} / {@code Types#CHAR}) with a "wrong column type" error. To keep
 * the V1 {@code CHAR(n)} DDL authoritative &mdash; without relaxing {@code validate}
 * or altering the schema &mdash; each {@link Column} therefore pins
 * {@code columnDefinition = "bpchar(n)"}. This is the PostgreSQL internal name for
 * {@code CHAR(n)}; Hibernate's validator strips the length argument and matches
 * the normalised type name {@code bpchar} reported by the JDBC driver, so the
 * mapping validates cleanly. {@code columnDefinition} is a standard
 * {@code jakarta.persistence} attribute, so this stays within the permitted
 * import set (no {@code org.hibernate} annotations are introduced). Runtime
 * binding remains ordinary string binding; left-zero-padding the identifiers to
 * their fixed width is the DTO/service layer's responsibility.</p>
 *
 * <h2>Identity semantics</h2>
 * <p>JPA composite-key classes used with {@code @EmbeddedId}/{@code @Embeddable}
 * must implement {@link Serializable} and provide value-based {@link #equals(Object)}
 * and {@link #hashCode()} computed over the complete set of key fields. Both are
 * implemented here over {@code sortCode} <em>and</em> {@code transactionNumber}
 * via {@link Objects}, so that two keys are equal exactly when both components
 * are equal &mdash; the contract Hibernate relies on for first-level cache
 * lookups, dirty checking and {@code Map} keys.</p>
 *
 * <h2>What this class deliberately excludes</h2>
 * <p>The {@code PROC-TRAN-EYE-CATCHER} ('PRTR') marker and its
 * {@code REDEFINES PROC-TRAN-LOGICAL-DELETE-FLAG} are <strong>not</strong>
 * part of the key. The eye-catcher is dropped (relational typing supersedes it)
 * and the logical-delete flag is materialised as the {@code deleted BOOLEAN}
 * column on the {@link ProcessedTransaction} entity, not here. This class holds
 * only the two key components.</p>
 */
@Embeddable
public class ProcessedTransactionId implements Serializable
{

	/** Serialization version for this composite-key value object. */
	private static final long serialVersionUID = 1L;

	/**
	 * Branch sort code &mdash; COBOL {@code PROC-TRAN-SORT-CODE PIC 9(6)}.
	 *
	 * <p>Mapped to the fixed-width {@code sort_code CHAR(6) NOT NULL} primary-key
	 * column. Stored as a {@link String} to preserve the six-digit, zero-padded
	 * display-numeric form verbatim. {@code columnDefinition = "bpchar(6)"} pins
	 * the JDBC type to PostgreSQL {@code CHAR(6)} so Hibernate {@code validate}
	 * accepts the column (see the class-level "CHAR vs VARCHAR reconciliation"
	 * note); {@code length = 6} documents the declared width.</p>
	 */
	@Column(name = "sort_code", length = 6, nullable = false, columnDefinition = "bpchar(6)")
	private String sortCode;

	/**
	 * Transaction number &mdash; COBOL {@code PROC-TRAN-NUMBER PIC 9(8)}.
	 *
	 * <p>Mapped to the fixed-width {@code transaction_number CHAR(8) NOT NULL}
	 * primary-key column (note the column name is {@code transaction_number},
	 * not {@code proc_tran_number}). Stored as a {@link String} to preserve the
	 * eight-digit, zero-padded display-numeric form verbatim.
	 * {@code columnDefinition = "bpchar(8)"} pins the JDBC type to PostgreSQL
	 * {@code CHAR(8)} so Hibernate {@code validate} accepts the column (see the
	 * class-level "CHAR vs VARCHAR reconciliation" note); {@code length = 8}
	 * documents the declared width.</p>
	 */
	@Column(name = "transaction_number", length = 8, nullable = false, columnDefinition = "bpchar(8)")
	private String transactionNumber;

	/**
	 * No-argument constructor required by the JPA specification for
	 * {@code @Embeddable} types (used by the persistence provider when
	 * materialising keys).
	 */
	public ProcessedTransactionId()
	{
	}

	/**
	 * Convenience constructor that fully populates the composite key.
	 *
	 * @param sortCode          the branch sort code (six-digit, zero-padded)
	 * @param transactionNumber the transaction number (eight-digit, zero-padded)
	 */
	public ProcessedTransactionId(String sortCode, String transactionNumber)
	{
		this.sortCode = sortCode;
		this.transactionNumber = transactionNumber;
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
	 * Returns the transaction number component of the key.
	 *
	 * @return the transaction number, or {@code null} if not yet set
	 */
	public String getTransactionNumber()
	{
		return transactionNumber;
	}

	/**
	 * Sets the transaction number component of the key.
	 *
	 * @param transactionNumber the eight-digit, zero-padded transaction number
	 */
	public void setTransactionNumber(String transactionNumber)
	{
		this.transactionNumber = transactionNumber;
	}

	/**
	 * Value-based equality over the full composite key. Two
	 * {@code ProcessedTransactionId} instances are equal exactly when both their
	 * {@code sortCode} and {@code transactionNumber} components are equal.
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
				&& Objects.equals(transactionNumber, other.transactionNumber);
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
		return Objects.hash(sortCode, transactionNumber);
	}

	/**
	 * Diagnostic representation of the composite key. Intended for logging and
	 * debugging only; it is not part of any persisted or wire format.
	 *
	 * @return a string describing both key components
	 */
	@Override
	public String toString()
	{
		return "ProcessedTransactionId{" + "sortCode='" + sortCode + '\''
				+ ", transactionNumber='" + transactionNumber + '\'' + '}';
	}

}
