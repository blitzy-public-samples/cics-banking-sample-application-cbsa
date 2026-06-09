/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.core.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * JPA entity for the {@code customer_control} table &mdash; the Java rendering
 * of the COBOL {@code CUSTCTRL.cpy} control record.
 *
 * <pre>
 *   CUSTOMER-CONTROL-SORTCODE 9(6)  -&gt; sortCode           (sort_code CHAR(6), @Id)
 *   NUMBER-OF-CUSTOMERS       9(10) -&gt; numberOfCustomers  (number_of_customers BIGINT)
 *   LAST-CUSTOMER-NUMBER      9(10) -&gt; lastCustomerNumber (last_customer_number BIGINT)
 *   eye-catcher 'CTRL', FILLERs, flags, CUSTOMER-CONTROL-NUMBER -&gt; (dropped)
 * </pre>
 *
 * <h2>Identity allocation</h2>
 * <p>{@link #lastCustomerNumber} is read and incremented under a
 * {@code PESSIMISTIC_WRITE} row lock by the identity service to allocate
 * gap-free, roll-back-able customer numbers (ADR-003). A database
 * {@code IDENTITY}/{@code SEQUENCE} is deliberately NOT used because a consumed
 * counter must roll back with its enclosing transaction. The counters are
 * {@link Long} ({@code BIGINT}).</p>
 */
@Entity
@Table(name = "customer_control")
public class CustomerControl
{

	/**
	 * Branch sort code &mdash; COBOL {@code CUSTOMER-CONTROL-SORTCODE PIC 9(6)};
	 * the single-column primary key. Mapped to {@code sort_code CHAR(6)}.
	 */
	@Id
	@Column(name = "sort_code", length = 6, nullable = false, columnDefinition = "bpchar(6)")
	private String sortCode;

	/**
	 * Number of customers currently allocated for this sort code &mdash; COBOL
	 * {@code NUMBER-OF-CUSTOMERS PIC 9(10)}. Mapped to {@code number_of_customers
	 * BIGINT NOT NULL}.
	 */
	@Column(name = "number_of_customers", nullable = false)
	private Long numberOfCustomers;

	/**
	 * Highest customer number allocated so far &mdash; COBOL
	 * {@code LAST-CUSTOMER-NUMBER PIC 9(10)}. Mapped to
	 * {@code last_customer_number BIGINT NOT NULL}; read+incremented under a
	 * pessimistic lock to allocate the next customer number.
	 */
	@Column(name = "last_customer_number", nullable = false)
	private Long lastCustomerNumber;

	/** No-argument constructor required by the JPA specification. */
	public CustomerControl()
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
	 * Returns the number of customers allocated for this sort code.
	 *
	 * @return the customer count
	 */
	public Long getNumberOfCustomers()
	{
		return numberOfCustomers;
	}

	/**
	 * Sets the number of customers allocated for this sort code.
	 *
	 * @param numberOfCustomers the customer count
	 */
	public void setNumberOfCustomers(Long numberOfCustomers)
	{
		this.numberOfCustomers = numberOfCustomers;
	}

	/**
	 * Returns the highest customer number allocated so far.
	 *
	 * @return the last allocated customer number
	 */
	public Long getLastCustomerNumber()
	{
		return lastCustomerNumber;
	}

	/**
	 * Sets the highest customer number allocated so far.
	 *
	 * @param lastCustomerNumber the last allocated customer number
	 */
	public void setLastCustomerNumber(Long lastCustomerNumber)
	{
		this.lastCustomerNumber = lastCustomerNumber;
	}

}
