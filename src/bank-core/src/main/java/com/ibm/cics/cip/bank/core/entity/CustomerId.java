/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.core.entity;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * Composite primary key for the {@code customer} table, used as the
 * {@code @EmbeddedId} of {@link Customer}.
 *
 * <p>Java rendering of the COBOL {@code CUSTOMER-KEY} group inside
 * {@code CUSTOMER.cpy}:</p>
 *
 * <pre>
 *   05 CUSTOMER-KEY.
 *      07 CUSTOMER-SORTCODE  PIC 9(6)  DISPLAY.  --&gt; sortCode       (sort_code CHAR(6))
 *      07 CUSTOMER-NUMBER    PIC 9(10) DISPLAY.  --&gt; customerNumber (customer_number CHAR(10))
 * </pre>
 *
 * <p>Both sub-fields are display-numeric but are modelled as {@link String} to
 * preserve the fixed-width, zero-padded form; the columns are {@code CHAR(n)}
 * and each {@link Column} pins {@code columnDefinition = "bpchar(n)"} so
 * Hibernate {@code validate} accepts them.</p>
 */
@Embeddable
public class CustomerId implements Serializable
{

	/** Serialization version for this composite-key value object. */
	private static final long serialVersionUID = 1L;

	/**
	 * Branch sort code &mdash; COBOL {@code CUSTOMER-SORTCODE PIC 9(6)}. Mapped
	 * to {@code sort_code CHAR(6) NOT NULL}.
	 */
	@Column(name = "sort_code", length = 6, nullable = false, columnDefinition = "bpchar(6)")
	private String sortCode;

	/**
	 * Customer number &mdash; COBOL {@code CUSTOMER-NUMBER PIC 9(10)}. Mapped to
	 * {@code customer_number CHAR(10) NOT NULL}.
	 */
	@Column(name = "customer_number", length = 10, nullable = false, columnDefinition = "bpchar(10)")
	private String customerNumber;

	/**
	 * No-argument constructor required by the JPA specification for
	 * {@code @Embeddable} types.
	 */
	public CustomerId()
	{
	}

	/**
	 * Convenience constructor that fully populates the composite key.
	 *
	 * @param sortCode       the branch sort code (six-digit, zero-padded)
	 * @param customerNumber the customer number (ten-digit, zero-padded)
	 */
	public CustomerId(String sortCode, String customerNumber)
	{
		this.sortCode = sortCode;
		this.customerNumber = customerNumber;
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
	 * Returns the customer number component of the key.
	 *
	 * @return the customer number, or {@code null} if not yet set
	 */
	public String getCustomerNumber()
	{
		return customerNumber;
	}

	/**
	 * Sets the customer number component of the key.
	 *
	 * @param customerNumber the ten-digit, zero-padded customer number
	 */
	public void setCustomerNumber(String customerNumber)
	{
		this.customerNumber = customerNumber;
	}

	/**
	 * Value-based equality over the full composite key.
	 *
	 * @param obj the object to compare against
	 * @return {@code true} if {@code obj} is a {@code CustomerId} with equal key
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
		CustomerId other = (CustomerId) obj;
		return Objects.equals(sortCode, other.sortCode)
				&& Objects.equals(customerNumber, other.customerNumber);
	}

	/**
	 * Hash code consistent with {@link #equals(Object)}.
	 *
	 * @return the hash code of this composite key
	 */
	@Override
	public int hashCode()
	{
		return Objects.hash(sortCode, customerNumber);
	}

	/**
	 * Diagnostic representation of the composite key (logging/debugging only).
	 *
	 * @return a string describing both key components
	 */
	@Override
	public String toString()
	{
		return "CustomerId{" + "sortCode='" + sortCode + '\'' + ", customerNumber='"
				+ customerNumber + '\'' + '}';
	}

}
