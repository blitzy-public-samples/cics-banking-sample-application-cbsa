/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.core.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * JPA entity for the {@code processed_transaction} table &mdash; the Java
 * rendering of the COBOL {@code PROCTRAN.cpy} record, which is the authoritative
 * structural specification. This is an APPEND-ONLY audit log: rows are inserted
 * and may be logically deleted (the {@link #deleted} flag) but are NEVER
 * physically removed (ADR-006).
 *
 * <pre>
 *   PROC-TRAN-ID (sort_code + ref)     -&gt; {@link ProcessedTransactionId} (@EmbeddedId)
 *   PROC-TRAN-NUMBER     9(8)          -&gt; transactionNumber (transaction_number CHAR(8))
 *   PROC-TRAN-DATE       9(8)          -&gt; date              (date DATE; YYYY/MM/DD wire)
 *   PROC-TRAN-TIME       9(6)          -&gt; time              (time TIME; HHMMSS wire)
 *   PROC-TRAN-TYPE       X(3)          -&gt; typeCode          (type_code CHAR(3))
 *   PROC-TRAN-DESC       X(40)         -&gt; description       (description VARCHAR(40))
 *   PROC-TRAN-AMOUNT     S9(10)V99     -&gt; amount            (amount NUMERIC(12,2))
 *   PROC-TRAN-LOGICAL-DELETE-FLAG      -&gt; deleted           (deleted BOOLEAN)
 *   PROC-TRAN-EYE-CATCHER 'PRTR'       -&gt; (dropped)
 * </pre>
 *
 * <h2>Key vs. account number</h2>
 * <p>The primary key is {@code (sort_code, ref)} (see
 * {@link ProcessedTransactionId}). {@link #transactionNumber} is a NON-key
 * column holding the account number the transaction pertains to (or
 * {@code "00000000"} for customer-level transactions). The webui frozen JSON
 * contract emits {@code transactionNumber} under the field name
 * {@code accountNumber}; it must therefore always carry the account number and
 * must never be replaced by a generated audit sequence (that sequence lives in
 * {@code ref}).</p>
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
	 * {@code PROC-TRAN-NUMBER PIC 9(8)} (or {@code "00000000"} for
	 * customer-level transactions). Mapped to {@code transaction_number CHAR(8)
	 * NOT NULL}; stored as a {@link String} to preserve the eight-digit
	 * zero-padded form. Backs the webui {@code accountNumber} response field.
	 */
	@Column(name = "transaction_number", length = 8, nullable = false, columnDefinition = "bpchar(8)")
	private String transactionNumber;

	/**
	 * Transaction date &mdash; COBOL {@code PROC-TRAN-DATE 9(8)} (serialised
	 * {@code YYYY/MM/DD} on the wire). Mapped to the {@code date DATE} column.
	 */
	@Column(name = "date")
	private LocalDate date;

	/**
	 * Transaction time &mdash; COBOL {@code PROC-TRAN-TIME 9(6)} (serialised
	 * {@code HHMMSS} on the wire). Mapped to the {@code time TIME} column.
	 */
	@Column(name = "time")
	private LocalTime time;

	/**
	 * Transaction type code &mdash; COBOL {@code PROC-TRAN-TYPE PIC X(3)}; one of
	 * the eighteen codes enumerated by
	 * {@link com.ibm.cics.cip.bank.core.domain.TransactionType} (a {@code CHECK}
	 * constraint enforces the set). Mapped to {@code type_code CHAR(3) NOT NULL}.
	 */
	@Column(name = "type_code", length = 3, nullable = false, columnDefinition = "bpchar(3)")
	private String typeCode;

	/**
	 * Free-text / structured description &mdash; COBOL {@code PROC-TRAN-DESC PIC
	 * X(40)} (the copybook REDEFINES this 40-byte area for transfer, create and
	 * delete records). Mapped to {@code description VARCHAR(40)}.
	 */
	@Column(name = "description", length = 40)
	private String description;

	/**
	 * Transaction amount &mdash; COBOL {@code PROC-TRAN-AMOUNT S9(10)V99}. Mapped
	 * to {@code amount NUMERIC(12,2) NOT NULL}; a scale-2 {@link BigDecimal},
	 * never {@code double}/{@code float}.
	 */
	@Column(name = "amount", nullable = false, precision = 12, scale = 2)
	private BigDecimal amount;

	/**
	 * Logical-delete flag &mdash; the COBOL {@code PROC-TRAN-LOGICAL-DELETE-FLAG}
	 * that REDEFINES the {@code 'PRTR'} eye-catcher ({@code 88 ... VALUE X'FF'}).
	 * Mapped to {@code deleted BOOLEAN NOT NULL}. PROCTRAN rows are soft-deleted,
	 * never physically removed.
	 */
	@Column(name = "deleted", nullable = false)
	private boolean deleted;

	/** No-argument constructor required by the JPA specification. */
	public ProcessedTransaction()
	{
	}

	/**
	 * Returns the composite primary key.
	 *
	 * @return the {@link ProcessedTransactionId}, or {@code null} if not set
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
	 * @param transactionNumber the eight-digit zero-padded account number
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
	 * @return one of the eighteen PROCTRAN type codes
	 */
	public String getTypeCode()
	{
		return typeCode;
	}

	/**
	 * Sets the transaction type code.
	 *
	 * @param typeCode one of the eighteen PROCTRAN type codes
	 */
	public void setTypeCode(String typeCode)
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
	 * @param description the description
	 */
	public void setDescription(String description)
	{
		this.description = description;
	}

	/**
	 * Returns the transaction amount.
	 *
	 * @return the scale-2 amount
	 */
	public BigDecimal getAmount()
	{
		return amount;
	}

	/**
	 * Sets the transaction amount.
	 *
	 * @param amount the scale-2 amount
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
