/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.springboot.customerservices.jsonclasses.updatecustomer;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.ibm.cics.cip.bank.springboot.customerservices.JsonPropertyNamingStrategy;

@JsonNaming(JsonPropertyNamingStrategy.class)
public class UpdcustJson
{



	@JsonProperty("CommEye")
	private String commEye = "    ";

	@JsonProperty("CommScode")
	private String commSortcode = "";

	@JsonProperty("CommCustno")
	private String commCustno = " ";

	@JsonProperty("CommName")
	private String commName = " ";

	@JsonProperty("CommAddress")
	private String commAddress = " ";

	@JsonProperty("CommDob")
	private int commDateOfBirth = 0;

	@JsonProperty("CommCreditScore")
	private int commCreditScore = 0;

	@JsonProperty("CommCsReviewDate")
	private int commCreditScoreReviewDate = 0;

	@JsonProperty("CommUpdSuccess")
	private String commUpdateSuccess = " ";

	@JsonProperty("CommUpdFailCd")
	private String commUpdateFailCode = " ";


	public UpdcustJson(String commCustnoIn, String commNameIn,
			String commAddressIn, String commDateOfBirthIn,
			int commCreditScoreIn, String commCreditScoreReviewDateIn)
	{
		// Some values need to be padded out when not full
		commCustno = String.format("%10s", commCustnoIn).replace(" ", "0");
		if (!commNameIn.equals(" "))
			commName = String.format("%-60s", commNameIn);
		if (!commAddressIn.equals(" "))
			commAddress = String.format("%-160s", commAddressIn);

		// Convert the date strings to the numeric YYYYMMDD form the commarea
		// carries. parseDateToInt() strips any punctuation (so both the z/OS
		// numeric shape "20260620" and ISO / locale shapes such as "2026-06-20"
		// or "2026/06/20" are accepted) and falls back to 0 for blank, null, or
		// otherwise unparseable input, so a populated review date can never
		// raise a NumberFormatException that escapes the controller as an
		// HTTP 500 (QA Issue 3).
		commDateOfBirth = parseDateToInt(commDateOfBirthIn);
		commCreditScoreReviewDate = parseDateToInt(commCreditScoreReviewDateIn);

		// Doesn't need conversion as it isn't ever not an int
		commCreditScore = commCreditScoreIn;
	}


	public UpdcustJson()
	{

	}


	/**
	 * Convert a date supplied as a String into the numeric {@code YYYYMMDD}
	 * integer the commarea carries for {@code CommDob} / {@code CommCsReviewDate}.
	 *
	 * <p>The Customer Services form supplies these dates as Strings. The date of
	 * birth is already reduced to digits by the form, but the credit-score review
	 * date is taken verbatim from the input control and therefore arrives with
	 * separators (for example the ISO shape {@code 2026-06-20} produced by an
	 * HTML date picker). A naive {@link Integer#parseInt(String)} on such a value
	 * throws {@link NumberFormatException}, which previously escaped the
	 * controller as an HTTP 500 (QA Issue 3).</p>
	 *
	 * <p>This helper is intentionally defensive: it returns 0 for {@code null},
	 * blank, or non-numeric input, strips every non-digit character so that
	 * punctuated dates parse cleanly, and treats an out-of-range value (one that
	 * would overflow {@code int}) as 0 rather than propagating an exception.</p>
	 *
	 * @param value the raw date String from the request form; may be null or blank
	 * @return the digits of {@code value} parsed as an int, or 0 when the value
	 *         is null, blank, or cannot be represented as an int
	 */
	private static int parseDateToInt(String value)
	{
		if (value == null)
		{
			return 0;
		}
		String digits = value.replaceAll("\\D", "");
		if (digits.isEmpty())
		{
			return 0;
		}
		try
		{
			return Integer.parseInt(digits);
		}
		catch (NumberFormatException e)
		{
			// Too many digits to fit an int (or otherwise unparseable) - fall
			// back to 0 rather than failing the whole update request.
			return 0;
		}
	}


	public String getCommEye()
	{
		return commEye;
	}


	public void setCommEye(String commEyeIn)
	{
		commEye = commEyeIn;
	}


	public String getCommSortcode()
	{
		return commSortcode;
	}


	public void setCommSortcode(String commSortcodeIn)
	{
		commSortcode = commSortcodeIn;
	}


	public String getCommCustno()
	{
		return commCustno;
	}


	public void setCommCustno(String commCustnoIn)
	{
		commCustno = commCustnoIn;
	}


	public String getCommName()
	{
		return commName;
	}


	public void setCommName(String commNameIn)
	{
		commName = commNameIn;
	}


	public String getCommAddress()
	{
		return commAddress;
	}


	public void setCommAddress(String commAddressIn)
	{
		commAddress = commAddressIn;
	}


	public int getCommDateOfBirth()
	{
		return commDateOfBirth;
	}


	public void setCommDateOfBirth(int commDateOfBirthIn)
	{
		commDateOfBirth = commDateOfBirthIn;
	}


	public int getCommCreditScore()
	{
		return commCreditScore;
	}


	public void setCommCreditScore(int commCreditScoreIn)
	{
		commCreditScore = commCreditScoreIn;
	}


	public int getCommCreditScoreReviewDate()
	{
		return commCreditScoreReviewDate;
	}


	public void setCommCreditScoreReviewDate(int commCreditScoreReviewDateIn)
	{
		commCreditScoreReviewDate = commCreditScoreReviewDateIn;
	}


	public String getCommUpdateSuccess()
	{
		return commUpdateSuccess;
	}


	public void setCommUpdateSuccess(String commUpdateSuccessIn)
	{
		commUpdateSuccess = commUpdateSuccessIn;
	}


	public String getCommUpdateFailCode()
	{
		return commUpdateFailCode;
	}


	public void setCommUpdateFailCode(String commUpdateFailCodeIn)
	{
		commUpdateFailCode = commUpdateFailCodeIn;
	}


	@Override
	public String toString()
	{
		return "UpdcustJson [CommAddress=" + commAddress + ", CommCreditScore="
				+ commCreditScore + ", CommCsReviewDate="
				+ commCreditScoreReviewDate + ", CommCustno=" + commCustno
				+ ", CommDob=" + commDateOfBirth + ", CommEye=" + commEye
				+ ", CommName=" + commName + ", CommScode=" + commSortcode
				+ ", CommUpdFailCd=" + commUpdateFailCode
				+ ", CommmUpdSuccess=" + commUpdateSuccess + "]";
	}

}
