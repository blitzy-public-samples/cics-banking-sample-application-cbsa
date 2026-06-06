/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.core.entity;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * JPA entity for the {@code account} table &mdash; the Java rendering of the
 * COBOL {@code ACCOUNT.cpy} record, which is the authoritative structural
 * specification.
 *
 * <pre>
 *   ACCOUNT-CUST-NO           9(10)      -&gt; customerNumber     (customer_number CHAR(10))
 *   ACCOUNT-KEY (sort+number)            -&gt; {@link AccountId} (@EmbeddedId)
 *   ACCOUNT-TYPE              X(8)       -&gt; accountType        (account_type VARCHAR(8))
 *   ACCOUNT-INTEREST-RATE     9(4)V99    -&gt; interestRate       (interest_rate NUMERIC(6,2))
 *   ACCOUNT-OPENED            9(8)       -&gt; opened             (opened DATE)
 *   ACCOUNT-OVERDRAFT-LIMIT   9(8)       -&gt; overdraftLimit     (overdraft_limit INTEGER)
 *   ACCOUNT-LAST-STMT-DATE    9(8)       -&gt; lastStatementDate  (last_statement_date DATE)
 *   ACCOUNT-NEXT-STMT-DATE    9(8)       -&gt; nextStatementDate  (next_statement_date DATE)
 *   ACCOUNT-AVAILABLE-BALANCE S9(10)V99  -&gt; availableBalance   (available_balance NUMERIC(12,2))
 *   ACCOUNT-ACTUAL-BALANCE    S9(10)V99  -&gt; actualBalance      (actual_balance NUMERIC(12,2))
 *   ACCOUNT-EYE-CATCHER 'ACCT'           -&gt; (dropped)
 * </pre>
 *
 * <h2>Money &amp; rounding</h2>
 * <p>Both balances and the interest rate are {@link BigDecimal} &mdash; never
 * {@code double}/{@code float} (AAP user rule "Use BigDecimal for all money;
 * avoid double"). Balances are scale-2 {@code NUMERIC(12,2)}; the interest rate
 * is scale-2 {@code NUMERIC(6,2)}; the overdraft limit is a whole-pounds
 * {@link Integer} ({@code INTEGER}, no decimals). The two balances
 * (available vs. actual) are INDEPENDENT and are never collapsed.</p>
 *
 * <h2>{@code ddl-auto: validate} contract</h2>
 * <p>The schema is owned by Flyway ({@code V1__create_core_tables.sql}) and
 * Hibernate runs in {@code validate} mode, so every {@link Column} mapping here
 * must match the {@code account} columns EXACTLY (name, type/length,
 * nullability). The fixed-width {@code CHAR(n)} key columns are handled by
 * {@link AccountId}; {@code customer_number} pins {@code columnDefinition =
 * "bpchar(10)"} for the same {@code CHAR}/{@code VARCHAR} reconciliation reason.</p>
 */
@Entity
@Table(name = "account")
public class Account
{

	/**
	 * Composite primary key (sort code + account number). See {@link AccountId}.
	 */
	@EmbeddedId
	private AccountId id;

	/**
	 * Owning customer number &mdash; COBOL {@code ACCOUNT-CUST-NO PIC 9(10)}.
	 * Mapped to {@code customer_number CHAR(10) NOT NULL}; stored as a
	 * {@link String} to preserve the ten-digit zero-padded form. The link to
	 * {@link Customer} is enforced in the service layer (no DB foreign key).
	 */
	@Column(name = "customer_number", length = 10, nullable = false, columnDefinition = "bpchar(10)")
	private String customerNumber;

	/**
	 * Account type &mdash; COBOL {@code ACCOUNT-TYPE PIC X(8)}; one of
	 * {@code ISA, MORTGAGE, SAVING, CURRENT, LOAN} (a {@code CHECK} constraint
	 * enforces this set). Mapped to {@code account_type VARCHAR(8) NOT NULL}.
	 */
	@Column(name = "account_type", length = 8, nullable = false)
	private String accountType;

	/**
	 * Annual interest rate &mdash; COBOL {@code ACCOUNT-INTEREST-RATE 9(4)V99}.
	 * Mapped to {@code interest_rate NUMERIC(6,2) NOT NULL}; held as a scale-2
	 * {@link BigDecimal}.
	 */
	@Column(name = "interest_rate", nullable = false, precision = 6, scale = 2)
	private BigDecimal interestRate;

	/**
	 * Date the account was opened &mdash; COBOL {@code ACCOUNT-OPENED 9(8)}
	 * (stored {@code DD/MM/YYYY} on the wire). Mapped to {@code opened DATE}.
	 */
	@Column(name = "opened")
	private LocalDate opened;

	/**
	 * Overdraft limit in whole pounds &mdash; COBOL
	 * {@code ACCOUNT-OVERDRAFT-LIMIT 9(8)} (no decimals). Mapped to
	 * {@code overdraft_limit INTEGER NOT NULL}.
	 */
	@Column(name = "overdraft_limit", nullable = false)
	private Integer overdraftLimit;

	/**
	 * Date of the last statement &mdash; COBOL {@code ACCOUNT-LAST-STMT-DATE
	 * 9(8)}. Mapped to {@code last_statement_date DATE}.
	 */
	@Column(name = "last_statement_date")
	private LocalDate lastStatementDate;

	/**
	 * Date of the next statement &mdash; COBOL {@code ACCOUNT-NEXT-STMT-DATE
	 * 9(8)}. Mapped to {@code next_statement_date DATE}.
	 */
	@Column(name = "next_statement_date")
	private LocalDate nextStatementDate;

	/**
	 * Available (cleared) balance &mdash; COBOL
	 * {@code ACCOUNT-AVAILABLE-BALANCE S9(10)V99}. Mapped to
	 * {@code available_balance NUMERIC(12,2) NOT NULL}; scale-2
	 * {@link BigDecimal}. Independent of {@link #actualBalance}.
	 */
	@Column(name = "available_balance", nullable = false, precision = 12, scale = 2)
	private BigDecimal availableBalance;

	/**
	 * Actual balance &mdash; COBOL {@code ACCOUNT-ACTUAL-BALANCE S9(10)V99}.
	 * Mapped to {@code actual_balance NUMERIC(12,2) NOT NULL}; scale-2
	 * {@link BigDecimal}. Independent of {@link #availableBalance}.
	 */
	@Column(name = "actual_balance", nullable = false, precision = 12, scale = 2)
	private BigDecimal actualBalance;

	/** No-argument constructor required by the JPA specification. */
	public Account()
	{
	}

	/**
	 * Returns the composite primary key.
	 *
	 * @return the {@link AccountId}, or {@code null} if not yet set
	 */
	public AccountId getId()
	{
		return id;
	}

	/**
	 * Sets the composite primary key.
	 *
	 * @param id the {@link AccountId} (sort code + account number)
	 */
	public void setId(AccountId id)
	{
		this.id = id;
	}

	/**
	 * Returns the owning customer number.
	 *
	 * @return the ten-digit zero-padded customer number
	 */
	public String getCustomerNumber()
	{
		return customerNumber;
	}

	/**
	 * Sets the owning customer number.
	 *
	 * @param customerNumber the ten-digit zero-padded customer number
	 */
	public void setCustomerNumber(String customerNumber)
	{
		this.customerNumber = customerNumber;
	}

	/**
	 * Returns the account type.
	 *
	 * @return one of {@code ISA, MORTGAGE, SAVING, CURRENT, LOAN}
	 */
	public String getAccountType()
	{
		return accountType;
	}

	/**
	 * Sets the account type.
	 *
	 * @param accountType one of {@code ISA, MORTGAGE, SAVING, CURRENT, LOAN}
	 */
	public void setAccountType(String accountType)
	{
		this.accountType = accountType;
	}

	/**
	 * Returns the annual interest rate.
	 *
	 * @return the scale-2 interest rate
	 */
	public BigDecimal getInterestRate()
	{
		return interestRate;
	}

	/**
	 * Sets the annual interest rate.
	 *
	 * @param interestRate the scale-2 interest rate
	 */
	public void setInterestRate(BigDecimal interestRate)
	{
		this.interestRate = interestRate;
	}

	/**
	 * Returns the date the account was opened.
	 *
	 * @return the opened date, or {@code null}
	 */
	public LocalDate getOpened()
	{
		return opened;
	}

	/**
	 * Sets the date the account was opened.
	 *
	 * @param opened the opened date
	 */
	public void setOpened(LocalDate opened)
	{
		this.opened = opened;
	}

	/**
	 * Returns the overdraft limit (whole pounds).
	 *
	 * @return the overdraft limit
	 */
	public Integer getOverdraftLimit()
	{
		return overdraftLimit;
	}

	/**
	 * Sets the overdraft limit (whole pounds).
	 *
	 * @param overdraftLimit the overdraft limit
	 */
	public void setOverdraftLimit(Integer overdraftLimit)
	{
		this.overdraftLimit = overdraftLimit;
	}

	/**
	 * Returns the last statement date.
	 *
	 * @return the last statement date, or {@code null}
	 */
	public LocalDate getLastStatementDate()
	{
		return lastStatementDate;
	}

	/**
	 * Sets the last statement date.
	 *
	 * @param lastStatementDate the last statement date
	 */
	public void setLastStatementDate(LocalDate lastStatementDate)
	{
		this.lastStatementDate = lastStatementDate;
	}

	/**
	 * Returns the next statement date.
	 *
	 * @return the next statement date, or {@code null}
	 */
	public LocalDate getNextStatementDate()
	{
		return nextStatementDate;
	}

	/**
	 * Sets the next statement date.
	 *
	 * @param nextStatementDate the next statement date
	 */
	public void setNextStatementDate(LocalDate nextStatementDate)
	{
		this.nextStatementDate = nextStatementDate;
	}

	/**
	 * Returns the available (cleared) balance.
	 *
	 * @return the scale-2 available balance
	 */
	public BigDecimal getAvailableBalance()
	{
		return availableBalance;
	}

	/**
	 * Sets the available (cleared) balance.
	 *
	 * @param availableBalance the scale-2 available balance
	 */
	public void setAvailableBalance(BigDecimal availableBalance)
	{
		this.availableBalance = availableBalance;
	}

	/**
	 * Returns the actual balance.
	 *
	 * @return the scale-2 actual balance
	 */
	public BigDecimal getActualBalance()
	{
		return actualBalance;
	}

	/**
	 * Sets the actual balance.
	 *
	 * @param actualBalance the scale-2 actual balance
	 */
	public void setActualBalance(BigDecimal actualBalance)
	{
		this.actualBalance = actualBalance;
	}

}
