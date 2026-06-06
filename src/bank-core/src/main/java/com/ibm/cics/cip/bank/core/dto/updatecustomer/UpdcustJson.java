/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.core.dto.updatecustomer;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Frozen z/OS Connect <em>update-customer</em> envelope ({@code UpdcustJson}),
 * reproduced from the interface module's class of the same name (feature
 * F-019). Nested inside {@link UpdateCustomerJson} under the {@code UpdCust}
 * key.
 *
 * <p>On a request the meaningful inputs are {@code CommCustno},
 * {@code CommName} and {@code CommAddress} (the only fields {@code UPDCUST}
 * changes). On a response {@code bank-core} echoes the customer and sets
 * {@code CommUpdSuccess}/{@code CommUpdFailCd}; the interface treats fail codes
 * {@code 4} (nothing to update) and {@code T} (invalid title) as errors and any
 * other value as success.</p>
 */
public class UpdcustJson
{

	/** Eye-catcher (preserved for wire parity). */
	@JsonProperty("CommEye")
	private String commEye = "    ";

	/** Sort code. */
	@JsonProperty("CommScode")
	private String commSortcode = "";

	/** Customer number. */
	@JsonProperty("CommCustno")
	private String commCustno = " ";

	/** Customer name. */
	@JsonProperty("CommName")
	private String commName = " ";

	/** Customer address. */
	@JsonProperty("CommAddress")
	private String commAddress = " ";

	/** Date of birth, integer {@code DDMMYYYY}. */
	@JsonProperty("CommDob")
	private int commDateOfBirth = 0;

	/** Credit score. */
	@JsonProperty("CommCreditScore")
	private int commCreditScore = 0;

	/** Credit-score review date, integer {@code DDMMYYYY}. */
	@JsonProperty("CommCsReviewDate")
	private int commCreditScoreReviewDate = 0;

	/** Success flag ({@code Y}/{@code N}). */
	@JsonProperty("CommUpdSuccess")
	private String commUpdateSuccess = " ";

	/** Fail code; {@code 0} on success, {@code 4}/{@code T}/{@code 1} on error. */
	@JsonProperty("CommUpdFailCd")
	private String commUpdateFailCode = " ";

	/**
	 * Default constructor for Jackson (de)serialisation.
	 */
	public UpdcustJson()
	{
		// Field defaults preserve the legacy envelope's initial values.
	}

	/**
	 * Returns the eye-catcher.
	 *
	 * @return the eye-catcher
	 */
	public String getCommEye()
	{
		return commEye;
	}

	/**
	 * Sets the eye-catcher.
	 *
	 * @param commEye the eye-catcher
	 */
	public void setCommEye(String commEye)
	{
		this.commEye = commEye;
	}

	/**
	 * Returns the sort code.
	 *
	 * @return the sort code
	 */
	public String getCommSortcode()
	{
		return commSortcode;
	}

	/**
	 * Sets the sort code.
	 *
	 * @param commSortcode the sort code
	 */
	public void setCommSortcode(String commSortcode)
	{
		this.commSortcode = commSortcode;
	}

	/**
	 * Returns the customer number.
	 *
	 * @return the customer number
	 */
	public String getCommCustno()
	{
		return commCustno;
	}

	/**
	 * Sets the customer number.
	 *
	 * @param commCustno the customer number
	 */
	public void setCommCustno(String commCustno)
	{
		this.commCustno = commCustno;
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
	public int getCommDateOfBirth()
	{
		return commDateOfBirth;
	}

	/**
	 * Sets the date of birth ({@code DDMMYYYY}).
	 *
	 * @param commDateOfBirth the date of birth
	 */
	public void setCommDateOfBirth(int commDateOfBirth)
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
	public int getCommCreditScoreReviewDate()
	{
		return commCreditScoreReviewDate;
	}

	/**
	 * Sets the credit-score review date ({@code DDMMYYYY}).
	 *
	 * @param commCreditScoreReviewDate the review date
	 */
	public void setCommCreditScoreReviewDate(int commCreditScoreReviewDate)
	{
		this.commCreditScoreReviewDate = commCreditScoreReviewDate;
	}

	/**
	 * Returns the success flag.
	 *
	 * @return the success flag
	 */
	public String getCommUpdateSuccess()
	{
		return commUpdateSuccess;
	}

	/**
	 * Sets the success flag.
	 *
	 * @param commUpdateSuccess the success flag
	 */
	public void setCommUpdateSuccess(String commUpdateSuccess)
	{
		this.commUpdateSuccess = commUpdateSuccess;
	}

	/**
	 * Returns the fail code.
	 *
	 * @return the fail code
	 */
	public String getCommUpdateFailCode()
	{
		return commUpdateFailCode;
	}

	/**
	 * Sets the fail code.
	 *
	 * @param commUpdateFailCode the fail code
	 */
	public void setCommUpdateFailCode(String commUpdateFailCode)
	{
		this.commUpdateFailCode = commUpdateFailCode;
	}

}
