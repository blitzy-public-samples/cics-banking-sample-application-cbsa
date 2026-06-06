/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.core.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * JPA entity for the {@code account_control} table &mdash; the Java rendering of
 * the COBOL {@code ACCTCTRL.cpy} control record.
 *
 * <pre>
 *   ACCOUNT-CONTROL-SORT-CODE 9(6)  -&gt; sortCode          (sort_code CHAR(6), @Id)
 *   NUMBER-OF-ACCOUNTS        9(8)  -&gt; numberOfAccounts  (number_of_accounts BIGINT)
 *   LAST-ACCOUNT-NUMBER       9(8)  -&gt; lastAccountNumber (last_account_number BIGINT)
 *   eye-catcher 'CTRL', FILLERs, flags, ACCOUNT-CONTROL-NUMBER -&gt; (dropped)
 * </pre>
 *
 * <h2>Identity allocation</h2>
 * <p>{@link #lastAccountNumber} is read and incremented under a
 * {@code PESSIMISTIC_WRITE} row lock by the identity service to allocate
 * gap-free, roll-back-able account numbers (ADR-003). Because the counter is
 * consumed inside the same transaction as the insert it feeds, a rolled-back
 * transaction restores the counter automatically &mdash; which is why a database
 * {@code IDENTITY}/{@code SEQUENCE} is deliberately NOT used. The counters are
 * {@link Long} ({@code BIGINT}).</p>
 */
@Entity
@Table(name = "account_control")
public class AccountControl
{

	/**
	 * Branch sort code &mdash; COBOL {@code ACCOUNT-CONTROL-SORT-CODE PIC 9(6)};
	 * the single-column primary key. Mapped to {@code sort_code CHAR(6)} and
	 * stored as a {@link String} to preserve the six-digit zero-padded form.
	 */
	@Id
	@Column(name = "sort_code", length = 6, nullable = false, columnDefinition = "bpchar(6)")
	private String sortCode;

	/**
	 * Number of accounts currently allocated for this sort code &mdash; COBOL
	 * {@code NUMBER-OF-ACCOUNTS PIC 9(8)}. Mapped to {@code number_of_accounts
	 * BIGINT NOT NULL}.
	 */
	@Column(name = "number_of_accounts", nullable = false)
	private Long numberOfAccounts;

	/**
	 * Highest account number allocated so far &mdash; COBOL
	 * {@code LAST-ACCOUNT-NUMBER PIC 9(8)}. Mapped to {@code last_account_number
	 * BIGINT NOT NULL}; read+incremented under a pessimistic lock to allocate
	 * the next account number.
	 */
	@Column(name = "last_account_number", nullable = false)
	private Long lastAccountNumber;

	/** No-argument constructor required by the JPA specification. */
	public AccountControl()
	{
	}

	/**
	 * Returns the sort code (primary key).
	 *
	 * @return the six-digit zero-padded sort code
	 */
	public String getSortCode()
	{
		return sortCode;
	}

	/**
	 * Sets the sort code (primary key).
	 *
	 * @param sortCode the six-digit zero-padded sort code
	 */
	public void setSortCode(String sortCode)
	{
		this.sortCode = sortCode;
	}

	/**
	 * Returns the number of accounts allocated for this sort code.
	 *
	 * @return the account count
	 */
	public Long getNumberOfAccounts()
	{
		return numberOfAccounts;
	}

	/**
	 * Sets the number of accounts allocated for this sort code.
	 *
	 * @param numberOfAccounts the account count
	 */
	public void setNumberOfAccounts(Long numberOfAccounts)
	{
		this.numberOfAccounts = numberOfAccounts;
	}

	/**
	 * Returns the highest account number allocated so far.
	 *
	 * @return the last allocated account number
	 */
	public Long getLastAccountNumber()
	{
		return lastAccountNumber;
	}

	/**
	 * Sets the highest account number allocated so far.
	 *
	 * @param lastAccountNumber the last allocated account number
	 */
	public void setLastAccountNumber(Long lastAccountNumber)
	{
		this.lastAccountNumber = lastAccountNumber;
	}

}
