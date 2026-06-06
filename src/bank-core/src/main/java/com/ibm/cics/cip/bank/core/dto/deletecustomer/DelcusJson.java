/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.core.dto.deletecustomer;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Frozen z/OS Connect <em>delete-customer</em> envelope ({@code DelcusJson}),
 * reproduced field-for-field from the interface module's class of the same name
 * (feature F-019). Nested inside {@link DeleteCustomerJson} under the
 * {@code DelCus} key.
 *
 * <p><strong>Fail-code semantics (critical for parity).</strong> The consumer
 * ({@code WebController}) treats {@code getCommDelFailCode() == 1} &mdash; the
 * field bound to JSON {@code CommDelFailCd} &mdash; as &quot;customer not
 * found&quot;. {@code bank-core} sets {@code CommDelFailCd = 1} on a not-found
 * delete and {@code 0} on success.</p>
 *
 * <p>Note the legacy typing carried here verbatim: {@code CommScode},
 * {@code CommCustno}, {@code CommDelFailCd} and {@code CommCreditScore} are
 * {@code int}; the review date and date-of-birth are {@code DDMMYYYY} strings.
 * There are no monetary fields, so no {@link java.math.BigDecimal} is required.</p>
 */
public class DelcusJson
{

	/** Customer address. */
	@JsonProperty("CommAddr")
	private String commAddress;

	/** Sort code. */
	@JsonProperty("CommScode")
	private int commSortcode;

	/** Customer name. */
	@JsonProperty("CommName")
	private String commName;

	/** Legacy delete-success slot. */
	@JsonProperty("CommDelSuccess")
	private String commDelSuccess;

	/** Eye-catcher (preserved for wire parity). */
	@JsonProperty("CommEye")
	private String commEye;

	/** Credit-score review date, {@code DDMMYYYY} string. */
	@JsonProperty("CommCsReviewDate")
	private String commCsReviewDate;

	/** Customer number. */
	@JsonProperty("CommCustno")
	private int commCustno;

	/**
	 * Fail code (JSON {@code CommDelFailCd}); {@code 1} signals &quot;customer
	 * not found&quot; to the consumer, {@code 0} signals success.
	 */
	@JsonProperty("CommDelFailCd")
	private int commDelFailCode;

	/** Credit score. */
	@JsonProperty("CommCreditScore")
	private int commCreditScore;

	/** Date of birth, {@code DDMMYYYY} string. */
	@JsonProperty("CommDob")
	private String commDateOfBirth;

	/**
	 * Default constructor for Jackson (de)serialisation.
	 */
	public DelcusJson()
	{
		super();
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
	 * @param commAddressIn the address
	 */
	public void setCommAddress(String commAddressIn)
	{
		commAddress = commAddressIn;
	}

	/**
	 * Returns the sort code.
	 *
	 * @return the sort code
	 */
	public int getCommSortcode()
	{
		return commSortcode;
	}

	/**
	 * Sets the sort code.
	 *
	 * @param commSortcodeIn the sort code
	 */
	public void setCommSortcode(int commSortcodeIn)
	{
		commSortcode = commSortcodeIn;
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
	 * @param commNameIn the name
	 */
	public void setCommName(String commNameIn)
	{
		commName = commNameIn;
	}

	/**
	 * Returns the legacy delete-success slot.
	 *
	 * @return the delete-success slot
	 */
	public String getCommDelSuccess()
	{
		return commDelSuccess;
	}

	/**
	 * Sets the legacy delete-success slot.
	 *
	 * @param commDelSuccessIn the delete-success slot
	 */
	public void setCommDelSuccess(String commDelSuccessIn)
	{
		commDelSuccess = commDelSuccessIn;
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
	 * @param commEyeIn the eye-catcher
	 */
	public void setCommEye(String commEyeIn)
	{
		commEye = commEyeIn;
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
	 * @param commCsReviewDateIn the review date
	 */
	public void setCommCsReviewDate(String commCsReviewDateIn)
	{
		commCsReviewDate = commCsReviewDateIn;
	}

	/**
	 * Returns the customer number.
	 *
	 * @return the customer number
	 */
	public int getCommCustno()
	{
		return commCustno;
	}

	/**
	 * Sets the customer number.
	 *
	 * @param commCustnoIn the customer number
	 */
	public void setCommCustno(int commCustnoIn)
	{
		commCustno = commCustnoIn;
	}

	/**
	 * Returns the fail code (JSON {@code CommDelFailCd}); {@code 1} means
	 * &quot;customer not found&quot;.
	 *
	 * @return the fail code
	 */
	public int getCommDelFailCode()
	{
		return commDelFailCode;
	}

	/**
	 * Sets the fail code (JSON {@code CommDelFailCd}).
	 *
	 * @param commDelFailCodeIn the fail code
	 */
	public void setCommDelFailCode(int commDelFailCodeIn)
	{
		commDelFailCode = commDelFailCodeIn;
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
	 * @param commCreditScoreIn the credit score
	 */
	public void setCommCreditScore(int commCreditScoreIn)
	{
		commCreditScore = commCreditScoreIn;
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
	 * @param commDateOfBirthIn the date of birth
	 */
	public void setCommDateOfBirth(String commDateOfBirthIn)
	{
		commDateOfBirth = commDateOfBirthIn;
	}

}
