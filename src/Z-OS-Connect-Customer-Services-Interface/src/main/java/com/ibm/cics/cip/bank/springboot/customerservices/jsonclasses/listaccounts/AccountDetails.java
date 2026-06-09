/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.springboot.customerservices.jsonclasses.listaccounts;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.ibm.cics.cip.bank.springboot.customerservices.JsonPropertyNamingStrategy;
import com.ibm.cics.cip.bank.springboot.customerservices.OutputFormatUtils;

@JsonNaming(JsonPropertyNamingStrategy.class)
public class AccountDetails
{



	private static final String FLOAT_FORMAT = "%.02f";

	// QA Issue 5: money fields carry COBOL S9(10)V99 monetary values; the AAP
	// invariant requires BigDecimal (scale-2) end-to-end and prohibits float/
	// double to preserve exact COBOL rounding. bank-core emits these as JSON
	// numbers that Jackson binds losslessly into BigDecimal.
	@JsonProperty("CommActualBal")
	private BigDecimal commActualBalance;

	@JsonProperty("CommAvailBal")
	private BigDecimal commAvailableBalance;

	// @JsonProperty("COMM_SCODE")
	// private int commSortcode;

	@JsonProperty("CommIntRate")
	private BigDecimal commInterestRate;

	@JsonProperty("CommEye")
	private String commEye;

	@JsonProperty("CommOpened")
	private int commOpened;

	// QA Issues 4 and 5: customer number is a fixed-width 10-digit COBOL
	// identifier. Binding it into int overflows for valid values near the
	// upper bound (e.g. 9999999998) and discards leading zeroes. Bind into
	// String to keep the zero-padded identifier intact end-to-end; bank-core
	// emits CommCustno as a JSON string for this contract.
	@JsonProperty("CommCustno")
	private String commCustno;

	@JsonProperty("CommNextStmtDt")
	private int commNextStatementDate;

	@JsonProperty("CommAccType")
	private String commAccType;

	@JsonProperty("CommOverdraft")
	private int commOverdraft;

	@JsonProperty("CommAccno")
	private int commAccno;

	@JsonProperty("CommLastStmtDt")
	private int commLastStatementDate;


	public BigDecimal getCommActualBalance()
	{
		return commActualBalance;
	}


	public void setCommActualBalance(BigDecimal commActualBalanceIn)
	{
		commActualBalance = commActualBalanceIn;
	}


	public BigDecimal getCommAvailableBalance()
	{
		return commAvailableBalance;
	}


	public void setCommAvailableBalance(BigDecimal commAvailableBalanceIn)
	{
		commAvailableBalance = commAvailableBalanceIn;
	}


	// public int getCommSortcode()
	// {
	// 	return commSortcode;
	// }


	// public void setCommSortcode(int commSortcodeIn)
	// {
	// 	commSortcode = commSortcodeIn;
	// }


	public BigDecimal getCommInterestRate()
	{
		return commInterestRate;
	}


	public void setCommInterestRate(BigDecimal commInterestRateIn)
	{
		commInterestRate = commInterestRateIn;
	}


	public String getCommEye()
	{
		return commEye;
	}


	public void setCommEye(String commEyeIn)
	{
		commEye = commEyeIn;
	}


	public int getCommOpened()
	{
		return commOpened;
	}


	public void setCommOpened(int commOpenedIn)
	{
		commOpened = commOpenedIn;
	}


	public String getCommCustno()
	{
		return commCustno;
	}


	public void setCommCustno(String commCustnoIn)
	{
		commCustno = commCustnoIn;
	}


	public int getCommNextStatementDate()
	{
		return commNextStatementDate;
	}


	public void setCommNextStatementDate(int commNextStatementDateIn)
	{
		commNextStatementDate = commNextStatementDateIn;
	}


	public String getCommAccType()
	{
		return commAccType;
	}


	public void setCommAccType(String commAccTypeIn)
	{
		commAccType = commAccTypeIn;
	}


	public int getCommOverdraft()
	{
		return commOverdraft;
	}


	public void setCommOverdraft(int commOverdraftIn)
	{
		commOverdraft = commOverdraftIn;
	}


	public int getCommAccno()
	{
		return commAccno;
	}


	public void setCommAccno(int commAccnoIn)
	{
		commAccno = commAccnoIn;
	}


	public int getCommLastStatementDate()
	{
		return commLastStatementDate;
	}


	public void setCommLastStatementDate(int commLastStatementDateIn)
	{
		commLastStatementDate = commLastStatementDateIn;
	}


	@Override
	public String toString()
	{
		return "AccountDetails [CommAccno=" + commAccno + ", CommAccType="
				+ commAccType + ", CommActualBal=" + commActualBalance
				+ ", CommCustno=" + commCustno + ", CommEye=" + commEye
				+ ", CommIntRate=" + commInterestRate + ", CommLastStmtDt="
				+ commLastStatementDate + ", CommNextStmtDt="
				+ commNextStatementDate + ", CommOpened=" + commOpened
				+ ", CommOverdraft=" + commOverdraft;
				//  + ", CommScode="
				// + commSortcode + "]";
	}


	public String toPrettyString()
	{
		String output = "";
		output += "Account Number:       "
				+ OutputFormatUtils.leadingZeroes(8, commAccno) + "\n"
				// + "Sort Code:            " + String.format("%06d", commSortcode)
				+ "\n" + "Customer Number:      "
				+ OutputFormatUtils.leadingZeroes(10, commCustno) + "\n"
				+ "Account Type:         " + commAccType + "\n"
				+ "Available Balance:    "
				+ String.format(FLOAT_FORMAT, commAvailableBalance) + "\n"
				+ "Actual Balance:       "
				+ String.format(FLOAT_FORMAT, commActualBalance) + "\n"
				+ "Interest Rate:        "
				+ String.format(FLOAT_FORMAT, commInterestRate) + "\n"
				+ "Overdraft:            " + commOverdraft + "\n"
				+ "Account Opened: " + OutputFormatUtils.date(commOpened) + "\n"
				+ "Next Statement Date:  "
				+ OutputFormatUtils.date(commNextStatementDate) + "\n"
				+ "Last Statement Date:  "
				+ OutputFormatUtils.date(commLastStatementDate) + "\n";
		return output;
	}

}
