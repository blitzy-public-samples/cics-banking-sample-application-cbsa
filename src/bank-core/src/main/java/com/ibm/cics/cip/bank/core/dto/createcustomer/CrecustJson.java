/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.core.dto.createcustomer;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ibm.cics.cip.bank.core.dto.common.EnvelopeKeyJson;

/**
 * Frozen z/OS Connect <em>create-customer</em> envelope ({@code CrecustJson}),
 * reproduced from the interface module's class of the same name so that the
 * {@code bank-core} module deserialises and serialises the create-customer
 * contract byte-for-byte (feature F-019).
 *
 * <p>It is nested inside {@link CreateCustomerJson} under the {@code CreCust}
 * key. On a request the meaningful inputs are {@code CommName},
 * {@code CommAddress} and {@code CommDateOfBirth} (an eight-character
 * {@code DDMMYYYY} string). On a response {@code bank-core} additionally
 * populates the allocated {@link #commKey key}, the agency-derived
 * {@code CommCreditScore}, the {@code CommCsReviewDate}, and the
 * {@code CommSuccess}/{@code CommFailCode} status pair. A blank
 * {@code CommFailCode} denotes success, matching the interface's
 * {@code equals("")} success test.</p>
 */
public class CrecustJson
{

	/** Eye-catcher; preserved as four spaces for wire parity. */
	@JsonProperty("CommEyecatcher")
	private String commEyecatcher = "    ";

	/** Allocated identity (sort code + customer number). */
	@JsonProperty("CommKey")
	private EnvelopeKeyJson commKey = new EnvelopeKeyJson();

	/** Customer name (first token is the honorific title). */
	@JsonProperty("CommName")
	private String commName;

	/** Customer address. */
	@JsonProperty("CommAddress")
	private String commAddress;

	/** Date of birth, encoded as an eight-character {@code DDMMYYYY} string. */
	@JsonProperty("CommDateOfBirth")
	private String commDateOfBirth;

	/** Agency-derived credit score (0&ndash;999). */
	@JsonProperty("CommCreditScore")
	private int commCreditScore = 0;

	/** Credit-score review date, as a {@code DDMMYYYY} string. */
	@JsonProperty("CommCsReviewDate")
	private String commCsReviewDate = "0";

	/** Success flag ({@code Y}/{@code N}). */
	@JsonProperty("CommSuccess")
	private String commSuccess;

	/** Single-character fail code; blank on success. */
	@JsonProperty("CommFailCode")
	private String commFailCode;

	/**
	 * Default constructor for Jackson (de)serialisation.
	 */
	public CrecustJson()
	{
		// Field defaults preserve the legacy envelope's initial values.
	}

	/**
	 * Returns the eye-catcher.
	 *
	 * @return the eye-catcher
	 */
	public String getCommEyecatcher()
	{
		return commEyecatcher;
	}

	/**
	 * Sets the eye-catcher.
	 *
	 * @param commEyecatcher the eye-catcher
	 */
	public void setCommEyecatcher(String commEyecatcher)
	{
		this.commEyecatcher = commEyecatcher;
	}

	/**
	 * Returns the allocated identity key.
	 *
	 * @return the key
	 */
	public EnvelopeKeyJson getCommKey()
	{
		return commKey;
	}

	/**
	 * Sets the allocated identity key.
	 *
	 * @param commKey the key
	 */
	public void setCommKey(EnvelopeKeyJson commKey)
	{
		this.commKey = commKey;
	}

	/**
	 * Returns the customer name.
	 *
	 * @return the name
	 */
	public String getCommName()
	{
		return commName;
	}

	/**
	 * Sets the customer name.
	 *
	 * @param commName the name
	 */
	public void setCommName(String commName)
	{
		this.commName = commName;
	}

	/**
	 * Returns the customer address.
	 *
	 * @return the address
	 */
	public String getCommAddress()
	{
		return commAddress;
	}

	/**
	 * Sets the customer address.
	 *
	 * @param commAddress the address
	 */
	public void setCommAddress(String commAddress)
	{
		this.commAddress = commAddress;
	}

	/**
	 * Returns the date of birth ({@code DDMMYYYY}).
	 *
	 * @return the date of birth
	 */
	public String getCommDateOfBirth()
	{
		return commDateOfBirth;
	}

	/**
	 * Sets the date of birth ({@code DDMMYYYY}).
	 *
	 * @param commDateOfBirth the date of birth
	 */
	public void setCommDateOfBirth(String commDateOfBirth)
	{
		this.commDateOfBirth = commDateOfBirth;
	}

	/**
	 * Returns the credit score.
	 *
	 * @return the credit score
	 */
	public int getCommCreditScore()
	{
		return commCreditScore;
	}

	/**
	 * Sets the credit score.
	 *
	 * @param commCreditScore the credit score
	 */
	public void setCommCreditScore(int commCreditScore)
	{
		this.commCreditScore = commCreditScore;
	}

	/**
	 * Returns the credit-score review date ({@code DDMMYYYY}).
	 *
	 * @return the review date
	 */
	public String getCommCsReviewDate()
	{
		return commCsReviewDate;
	}

	/**
	 * Sets the credit-score review date ({@code DDMMYYYY}).
	 *
	 * @param commCsReviewDate the review date
	 */
	public void setCommCsReviewDate(String commCsReviewDate)
	{
		this.commCsReviewDate = commCsReviewDate;
	}

	/**
	 * Returns the success flag.
	 *
	 * @return the success flag
	 */
	public String getCommSuccess()
	{
		return commSuccess;
	}

	/**
	 * Sets the success flag.
	 *
	 * @param commSuccess the success flag
	 */
	public void setCommSuccess(String commSuccess)
	{
		this.commSuccess = commSuccess;
	}

	/**
	 * Returns the fail code.
	 *
	 * @return the fail code
	 */
	public String getCommFailCode()
	{
		return commFailCode;
	}

	/**
	 * Sets the fail code.
	 *
	 * @param commFailCode the fail code
	 */
	public void setCommFailCode(String commFailCode)
	{
		this.commFailCode = commFailCode;
	}

}
