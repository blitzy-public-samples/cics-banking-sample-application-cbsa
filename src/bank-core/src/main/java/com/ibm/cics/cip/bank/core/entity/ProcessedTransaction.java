/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.core.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

import com.ibm.cics.cip.bank.core.domain.TransactionType;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/**
 * JPA entity for the {@code processed_transaction} table &mdash; the Java
 * rendering of the COBOL {@code PROCTRAN.cpy} ({@code PROC-TRAN-DATA}) record,
 * which is the authoritative structural specification.
 *
 * <p>{@code processed_transaction} is an <strong>APPEND-ONLY</strong> audit log:
 * rows are inserted and may be <em>logically</em> deleted by setting the
 * {@link #deleted} flag, but are <strong>NEVER physically removed</strong>
 * (ADR-006). Only financial movements and create/delete operations append a
 * row; the update programs ({@code UPDCUST}, {@code UPDACC}) deliberately write
 * none.</p>
 *
 * <pre>
 *   PROC-TRAN-ID (sort_code + ref)     -&gt; {@link ProcessedTransactionId} (@EmbeddedId)
 *   PROC-TRAN-NUMBER     9(8)          -&gt; transactionNumber (transaction_number CHAR(8))
 *   PROC-TRAN-DATE       9(8)          -&gt; date              (date DATE; YYYY/MM/DD on the wire)
 *   PROC-TRAN-TIME       9(6)          -&gt; time              (time TIME; HHMMSS on the wire)
 *   PROC-TRAN-REF        9(12)         -&gt; (in {@link ProcessedTransactionId}; ref CHAR(12))
 *   PROC-TRAN-TYPE       X(3)          -&gt; typeCode          (type_code CHAR(3))
 *   PROC-TRAN-DESC       X(40)         -&gt; description       (description VARCHAR(40))
 *   PROC-TRAN-AMOUNT     S9(10)V99     -&gt; amount            (amount NUMERIC(12,2))
 *   PROC-TRAN-LOGICAL-DELETE-FLAG      -&gt; deleted           (deleted BOOLEAN)
 *   PROC-TRAN-EYE-CATCHER 'PRTR'       -&gt; (dropped)
 * </pre>
 *
 * <h2>Composite key vs. account number</h2>
 * <p>The primary key is {@code (sort_code, ref)} (see
 * {@link ProcessedTransactionId}), <em>not</em> {@code (sort_code,
 * transaction_number)}. {@link #transactionNumber} is a NON-key column holding
 * the eight-digit <em>account number</em> the transaction pertains to (the COBOL
 * {@code MOVE COMM-ACCNO TO HV-PROCTRAN-ACC-NUMBER}) or {@code "00000000"} for
 * customer-level transactions ({@code MOVE ZEROS TO HV-PROCTRAN-ACC-NUMBER}). It
 * is therefore not unique per row; the frozen webui JSON contract emits it under
 * the field name {@code accountNumber}, so it must always carry the account
 * number and must never be replaced by a generated audit sequence (that unique
 * per-row sequence is {@code ref}, the second component of the key).</p>
 *
 * <h2>Transaction type ({@code @Enumerated})</h2>
 * <p>{@link #typeCode} is the {@link TransactionType} domain enum, persisted with
 * {@link jakarta.persistence.EnumType#STRING}. Because every {@code TransactionType}
 * constant's {@code name()} <em>is</em> its canonical three-character PROCTRAN
 * code (for example {@link TransactionType#CRE} stores {@code "CRE"}), the value
 * written to {@code type_code CHAR(3)} is exactly one of the eighteen codes the
 * {@code ck_proctran_type_code} CHECK constraint enumerates &mdash; no
 * {@code AttributeConverter} is required.</p>
 *
 * <h2>Money fidelity (ADR-005)</h2>
 * <p>{@link #amount} is a scale-2 {@link BigDecimal} mapped to
 * {@code NUMERIC(12,2)} &mdash; never {@code double}/{@code float}. The sign
 * convention (a negative amount is a debit, a positive amount is a credit) is a
 * service-layer concern; this entity simply stores the signed value.</p>
 *
 * <h2>{@code ddl-auto: validate} contract</h2>
 * <p>The relational schema is owned by Flyway ({@code V1__create_core_tables.sql})
 * and Hibernate runs in {@code validate} mode, so every {@link Column} mapping
 * here matches the {@code processed_transaction} columns EXACTLY (name,
 * type/length, nullability). The reserved-word columns {@code date} and
 * {@code time} are declared with explicit, unquoted lower-case names to match the
 * DDL. The fixed-width {@code CHAR(n)} columns pin
 * {@code columnDefinition = "bpchar(n)"} so Hibernate {@code validate} accepts
 * the PostgreSQL {@code CHAR} columns (reported as {@code bpchar}) without
 * relaxing {@code validate} or altering the schema. Date/time wire-format
 * divergence (PROCTRAN serialises {@code YYYY/MM/DD}) is handled in the DTO
 * layer, never here.</p>
 */
@Entity
@Table(name = "processed_transaction")
public class ProcessedTransaction
{

	/**
	 * Composite primary key (sort code + reference). See
	 * {@link ProcessedTransactionId}.
	 */
	@EmbeddedId
	private ProcessedTransactionId id;

	/**
	 * Account number the transaction pertains to &mdash; COBOL
	 * {@code PROC-TRAN-NUMBER PIC 9(8)} (or {@code "00000000"} for customer-level
	 * transactions). Mapped to {@code transaction_number CHAR(8) NOT NULL};
	 * stored as a {@link String} to preserve the eight-digit, zero-padded
	 * display-numeric form verbatim. This is a NON-key column (the unique per-row
	 * identifier is {@code ref}, inside {@link ProcessedTransactionId}); it backs
	 * the webui {@code accountNumber} response field.
	 */
	@Column(name = "transaction_number", length = 8, nullable = false, columnDefinition = "bpchar(8)")
	private String transactionNumber;

	/**
	 * Transaction date &mdash; COBOL {@code PROC-TRAN-DATE 9(8)} (serialised
	 * {@code YYYY/MM/DD} on the wire, a DTO concern). Mapped to the reserved-word
	 * {@code date DATE} column via an explicit lower-case {@code @Column} name.
	 */
	@Column(name = "date")
	private LocalDate date;

	/**
	 * Transaction time &mdash; COBOL {@code PROC-TRAN-TIME 9(6)} (serialised
	 * {@code HHMMSS} on the wire). Mapped to the reserved-word {@code time TIME}
	 * column via an explicit lower-case {@code @Column} name.
	 */
	@Column(name = "time")
	private LocalTime time;

	/**
	 * Transaction type code &mdash; COBOL {@code PROC-TRAN-TYPE PIC X(3)}; one of
	 * the eighteen codes modelled by {@link TransactionType}. Persisted with
	 * {@link EnumType#STRING}, which writes the constant's {@code name()} (the
	 * exact three-character code) into {@code type_code CHAR(3) NOT NULL}; a
	 * {@code CHECK} constraint enforces the eighteen-value set.
	 */
	@Enumerated(EnumType.STRING)
	@Column(name = "type_code", length = 3, nullable = false, columnDefinition = "bpchar(3)")
	private TransactionType typeCode;

	/**
	 * Free-text / structured description &mdash; COBOL {@code PROC-TRAN-DESC PIC
	 * X(40)}. The copybook REDEFINES this forty-byte area many ways (transfer,
	 * create-account, delete-account, create-customer, delete-customer), but it
	 * is modelled here as ONE free-text column; the service / DTO layer composes
	 * and parses the fixed-width content. Mapped to {@code description
	 * VARCHAR(40)} (nullable).
	 */
	@Column(name = "description", length = 40)
	private String description;

	/**
	 * Transaction amount &mdash; COBOL {@code PROC-TRAN-AMOUNT S9(10)V99}. Mapped
	 * to {@code amount NUMERIC(12,2) NOT NULL}; a scale-2 {@link BigDecimal},
	 * never {@code double}/{@code float} (ADR-005). The signed value follows the
	 * COBOL convention (negative = debit, positive = credit).
	 */
	@Column(name = "amount", nullable = false, precision = 12, scale = 2)
	private BigDecimal amount;

	/**
	 * Logical-delete flag &mdash; the COBOL {@code PROC-TRAN-LOGICAL-DELETE-FLAG}
	 * that REDEFINES the {@code 'PRTR'} eye-catcher ({@code 88 ... VALUE X'FF'}).
	 * Mapped to {@code deleted BOOLEAN NOT NULL}. Initialised to {@code false} so
	 * every freshly appended row is active; "deletion" sets it to {@code true}.
	 * PROCTRAN rows are soft-deleted, never physically removed (ADR-006).
	 */
	@Column(name = "deleted", nullable = false)
	private boolean deleted = false;

	/** No-argument constructor required by the JPA specification. */
	public ProcessedTransaction()
	{
	}

	/**
	 * Returns the composite primary key.
	 *
	 * @return the {@link ProcessedTransactionId} (sort code + reference), or
	 *         {@code null} if not yet set
	 */
	public ProcessedTransactionId getId()
	{
		return id;
	}

	/**
	 * Sets the composite primary key.
	 *
	 * @param id the {@link ProcessedTransactionId} (sort code + reference)
	 */
	public void setId(ProcessedTransactionId id)
	{
		this.id = id;
	}

	/**
	 * Convenience accessor for the transaction reference &mdash; the unique
	 * twelve-digit, zero-padded identifier of this appended audit row.
	 *
	 * <p>The reference physically lives inside the {@link #id} composite key
	 * ({@link ProcessedTransactionId#getRef()}); this accessor exposes it
	 * directly on the entity for the service layer, which reasons in terms of the
	 * reference rather than the whole key. It is a plain Java accessor, not a
	 * separately mapped column.</p>
	 *
	 * @return the twelve-digit reference, or {@code null} if the key is unset
	 */
	public String getRef()
	{
		return id == null ? null : id.getRef();
	}

	/**
	 * Convenience setter for the transaction reference component of the composite
	 * key. If the key has not yet been created it is lazily instantiated, so the
	 * reference may be set independently of the sort code.
	 *
	 * @param ref the twelve-digit, zero-padded transaction reference
	 */
	public void setRef(String ref)
	{
		if (id == null)
		{
			id = new ProcessedTransactionId();
		}
		id.setRef(ref);
	}

	/**
	 * Returns the account number the transaction pertains to.
	 *
	 * @return the eight-digit zero-padded account number (or {@code "00000000"})
	 */
	public String getTransactionNumber()
	{
		return transactionNumber;
	}

	/**
	 * Sets the account number the transaction pertains to.
	 *
	 * @param transactionNumber the eight-digit zero-padded account number (or
	 *                          {@code "00000000"} for customer-level events)
	 */
	public void setTransactionNumber(String transactionNumber)
	{
		this.transactionNumber = transactionNumber;
	}

	/**
	 * Returns the transaction date.
	 *
	 * @return the date, or {@code null}
	 */
	public LocalDate getDate()
	{
		return date;
	}

	/**
	 * Sets the transaction date.
	 *
	 * @param date the date
	 */
	public void setDate(LocalDate date)
	{
		this.date = date;
	}

	/**
	 * Returns the transaction time.
	 *
	 * @return the time, or {@code null}
	 */
	public LocalTime getTime()
	{
		return time;
	}

	/**
	 * Sets the transaction time.
	 *
	 * @param time the time
	 */
	public void setTime(LocalTime time)
	{
		this.time = time;
	}

	/**
	 * Returns the transaction type code.
	 *
	 * @return the {@link TransactionType}, or {@code null}
	 */
	public TransactionType getTypeCode()
	{
		return typeCode;
	}

	/**
	 * Sets the transaction type code.
	 *
	 * @param typeCode one of the eighteen {@link TransactionType} codes
	 */
	public void setTypeCode(TransactionType typeCode)
	{
		this.typeCode = typeCode;
	}

	/**
	 * Returns the transaction description.
	 *
	 * @return the description, or {@code null}
	 */
	public String getDescription()
	{
		return description;
	}

	/**
	 * Sets the transaction description.
	 *
	 * @param description the description (up to forty characters)
	 */
	public void setDescription(String description)
	{
		this.description = description;
	}

	/**
	 * Returns the transaction amount.
	 *
	 * @return the scale-2 signed amount
	 */
	public BigDecimal getAmount()
	{
		return amount;
	}

	/**
	 * Sets the transaction amount.
	 *
	 * @param amount the scale-2 signed amount (negative debit, positive credit)
	 */
	public void setAmount(BigDecimal amount)
	{
		this.amount = amount;
	}

	/**
	 * Returns whether this row is logically deleted.
	 *
	 * @return {@code true} if soft-deleted, {@code false} if active
	 */
	public boolean isDeleted()
	{
		return deleted;
	}

	/**
	 * Sets the logical-delete flag.
	 *
	 * @param deleted {@code true} to soft-delete, {@code false} for active
	 */
	public void setDeleted(boolean deleted)
	{
		this.deleted = deleted;
	}

}
