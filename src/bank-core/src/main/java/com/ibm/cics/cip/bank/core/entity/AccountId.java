/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.core.entity;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * Composite primary key for the {@code account} table, used as the
 * {@code @EmbeddedId} of {@link Account}.
 *
 * <p>This value object is the Java rendering of the COBOL {@code ACCOUNT-KEY}
 * group inside {@code ACCOUNT.cpy}, which is the authoritative structural
 * specification for the {@code account} record:</p>
 *
 * <pre>
 *   05 ACCOUNT-KEY.
 *      07 ACCOUNT-SORT-CODE  PIC 9(6).   --&gt; sortCode      (sort_code CHAR(6))
 *      07 ACCOUNT-NUMBER     PIC 9(8).   --&gt; accountNumber (account_number CHAR(8))
 * </pre>
 *
 * <p>Both sub-fields are display-numeric ({@code PIC 9(n)}) in the copybook but
 * are modelled here as {@link String}, never a numeric Java type, so the
 * fixed-width, left-zero-padded on-disk representation (including leading zeros)
 * is preserved verbatim (AAP &sect;0.6, "Fixed-width character identifiers").
 * The relational columns are {@code CHAR(n)}; each {@link Column} pins
 * {@code columnDefinition = "bpchar(n)"} so Hibernate {@code validate} accepts
 * the PostgreSQL {@code CHAR(n)} column (reported as {@code bpchar}) without
 * relaxing {@code validate} or altering the schema.</p>
 */
@Embeddable
public class AccountId implements Serializable
{

	/** Serialization version for this composite-key value object. */
	private static final long serialVersionUID = 1L;

	/**
	 * Branch sort code &mdash; COBOL {@code ACCOUNT-SORT-CODE PIC 9(6)}. Mapped
	 * to {@code sort_code CHAR(6) NOT NULL}; stored as a {@link String} to keep
	 * the six-digit, zero-padded display-numeric form verbatim.
	 */
	@Column(name = "sort_code", length = 6, nullable = false, columnDefinition = "bpchar(6)")
	private String sortCode;

	/**
	 * Account number &mdash; COBOL {@code ACCOUNT-NUMBER PIC 9(8)}. Mapped to
	 * {@code account_number CHAR(8) NOT NULL}; stored as a {@link String} to
	 * keep the eight-digit, zero-padded display-numeric form verbatim.
	 */
	@Column(name = "account_number", length = 8, nullable = false, columnDefinition = "bpchar(8)")
	private String accountNumber;

	/**
	 * No-argument constructor required by the JPA specification for
	 * {@code @Embeddable} types.
	 */
	public AccountId()
	{
	}

	/**
	 * Convenience constructor that fully populates the composite key.
	 *
	 * @param sortCode      the branch sort code (six-digit, zero-padded)
	 * @param accountNumber the account number (eight-digit, zero-padded)
	 */
	public AccountId(String sortCode, String accountNumber)
	{
		this.sortCode = sortCode;
		this.accountNumber = accountNumber;
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
	 * Returns the account number component of the key.
	 *
	 * @return the account number, or {@code null} if not yet set
	 */
	public String getAccountNumber()
	{
		return accountNumber;
	}

	/**
	 * Sets the account number component of the key.
	 *
	 * @param accountNumber the eight-digit, zero-padded account number
	 */
	public void setAccountNumber(String accountNumber)
	{
		this.accountNumber = accountNumber;
	}

	/**
	 * Value-based equality over the full composite key.
	 *
	 * @param obj the object to compare against
	 * @return {@code true} if {@code obj} is an {@code AccountId} with equal key
	 *         components, {@code false} otherwise
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
		AccountId other = (AccountId) obj;
		return Objects.equals(sortCode, other.sortCode)
				&& Objects.equals(accountNumber, other.accountNumber);
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
		return Objects.hash(sortCode, accountNumber);
	}

	/**
	 * Diagnostic representation of the composite key (logging/debugging only).
	 *
	 * @return a string describing both key components
	 */
	@Override
	public String toString()
	{
		return "AccountId{" + "sortCode='" + sortCode + '\'' + ", accountNumber='"
				+ accountNumber + '\'' + '}';
	}

}
