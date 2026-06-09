/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.core.entity;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * JPA entity for the {@code customer} table &mdash; the Java rendering of the
 * COBOL {@code CUSTOMER.cpy} record, which is the authoritative structural
 * specification.
 *
 * <pre>
 *   CUSTOMER-KEY (sort+number)         -&gt; {@link CustomerId} (@EmbeddedId)
 *   CUSTOMER-NAME           X(60)      -&gt; name           (name VARCHAR(60))
 *   CUSTOMER-ADDRESS        X(160)     -&gt; address        (address VARCHAR(160))
 *   CUSTOMER-DATE-OF-BIRTH  9(8)       -&gt; dateOfBirth    (date_of_birth DATE)
 *   CUSTOMER-CREDIT-SCORE   999        -&gt; creditScore    (credit_score SMALLINT)
 *   CUSTOMER-CS-REVIEW-DATE 9(8)       -&gt; csReviewDate   (cs_review_date DATE)
 *   CUSTOMER-EYECATCHER 'CUST'         -&gt; (dropped)
 * </pre>
 *
 * <p>The copybook keeps name and address each as a single fixed field (their
 * sub-fields are commented out), so each is modelled as a single column &mdash;
 * no separate title/town/surname columns are invented. The credit score is a
 * {@code 0..999} value held as a {@link Short} ({@code SMALLINT}, with a
 * {@code CHECK} constraint in the schema). All dates are {@link LocalDate};
 * formatting back to {@code DD/MM/YYYY} on the wire is a DTO concern.</p>
 */
@Entity
@Table(name = "customer")
public class Customer
{

	/**
	 * Composite primary key (sort code + customer number). See
	 * {@link CustomerId}.
	 */
	@EmbeddedId
	private CustomerId id;

	/**
	 * Customer name &mdash; COBOL {@code CUSTOMER-NAME PIC X(60)} (title, given
	 * name, initials and family name concatenated into one field). Mapped to
	 * {@code name VARCHAR(60) NOT NULL}.
	 */
	@Column(name = "name", length = 60, nullable = false)
	private String name;

	/**
	 * Customer address &mdash; COBOL {@code CUSTOMER-ADDRESS PIC X(160)}. Mapped
	 * to {@code address VARCHAR(160) NOT NULL}.
	 */
	@Column(name = "address", length = 160, nullable = false)
	private String address;

	/**
	 * Date of birth &mdash; COBOL {@code CUSTOMER-DATE-OF-BIRTH 9(8)}. Mapped to
	 * {@code date_of_birth DATE NOT NULL}.
	 */
	@Column(name = "date_of_birth", nullable = false)
	private LocalDate dateOfBirth;

	/**
	 * Credit score &mdash; COBOL {@code CUSTOMER-CREDIT-SCORE PIC 999} (range
	 * {@code 0..999}). Mapped to {@code credit_score SMALLINT NOT NULL}; held as
	 * a {@link Short}.
	 */
	@Column(name = "credit_score", nullable = false)
	private Short creditScore;

	/**
	 * Credit-score review date &mdash; COBOL {@code CUSTOMER-CS-REVIEW-DATE
	 * 9(8)}. Mapped to {@code cs_review_date DATE} (nullable).
	 */
	@Column(name = "cs_review_date")
	private LocalDate csReviewDate;

	/** No-argument constructor required by the JPA specification. */
	public Customer()
	{
	}

	/**
	 * Returns the composite primary key.
	 *
	 * @return the {@link CustomerId}, or {@code null} if not yet set
	 */
	public CustomerId getId()
	{
		return id;
	}

	/**
	 * Sets the composite primary key.
	 *
	 * @param id the {@link CustomerId} (sort code + customer number)
	 */
	public void setId(CustomerId id)
	{
		this.id = id;
	}

	/**
	 * Returns the customer name.
	 *
	 * @return the name
	 */
	public String getName()
	{
		return name;
	}

	/**
	 * Sets the customer name.
	 *
	 * @param name the name
	 */
	public void setName(String name)
	{
		this.name = name;
	}

	/**
	 * Returns the customer address.
	 *
	 * @return the address
	 */
	public String getAddress()
	{
		return address;
	}

	/**
	 * Sets the customer address.
	 *
	 * @param address the address
	 */
	public void setAddress(String address)
	{
		this.address = address;
	}

	/**
	 * Returns the date of birth.
	 *
	 * @return the date of birth
	 */
	public LocalDate getDateOfBirth()
	{
		return dateOfBirth;
	}

	/**
	 * Sets the date of birth.
	 *
	 * @param dateOfBirth the date of birth
	 */
	public void setDateOfBirth(LocalDate dateOfBirth)
	{
		this.dateOfBirth = dateOfBirth;
	}

	/**
	 * Returns the credit score ({@code 0..999}).
	 *
	 * @return the credit score
	 */
	public Short getCreditScore()
	{
		return creditScore;
	}

	/**
	 * Sets the credit score ({@code 0..999}).
	 *
	 * @param creditScore the credit score
	 */
	public void setCreditScore(Short creditScore)
	{
		this.creditScore = creditScore;
	}

	/**
	 * Returns the credit-score review date.
	 *
	 * @return the review date, or {@code null}
	 */
	public LocalDate getCsReviewDate()
	{
		return csReviewDate;
	}

	/**
	 * Sets the credit-score review date.
	 *
	 * @param csReviewDate the review date
	 */
	public void setCsReviewDate(LocalDate csReviewDate)
	{
		this.csReviewDate = csReviewDate;
	}

}
