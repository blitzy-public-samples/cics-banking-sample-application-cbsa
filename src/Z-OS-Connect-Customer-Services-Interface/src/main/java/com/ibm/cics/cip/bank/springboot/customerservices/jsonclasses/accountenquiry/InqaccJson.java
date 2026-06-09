/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.springboot.customerservices.jsonclasses.accountenquiry;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.ibm.cics.cip.bank.springboot.customerservices.JsonPropertyNamingStrategy;

@JsonNaming(JsonPropertyNamingStrategy.class)
public class InqaccJson
{


	@JsonProperty("InqAccPcb1Pointer")
	private String inqaccPcb1Pointer;

	@JsonProperty("InqAccOverdraft")
	private int inqaccOverdraft;

	@JsonProperty("InqAccLastStmtDt")
	private int inqaccLastStatementDate;

	@JsonProperty("InqAccScode")
	private int inqaccSortcode;

	// QA Issue 5: money fields carry COBOL S9(10)V99 values; AAP requires
	// BigDecimal (scale-2) and prohibits float/double to preserve exact
	// rounding. bank-core emits these as JSON numbers bound losslessly here.
	@JsonProperty("InqAccActualBal")
	private BigDecimal inqaccActualBalance;

	@JsonProperty("InqAccAccno")
	private int inqaccAccno;

	@JsonProperty("InqAccOpened")
	private int inqaccOpened;

	// QA Issues 4 and 5: customer number is a fixed-width 10-digit COBOL
	// identifier. int overflows for valid upper-bound values and discards
	// leading zeroes, so bind into String; bank-core emits InqAccCustno as a
	// JSON string for this contract.
	@JsonProperty("InqAccCustno")
	private String inqaccCustno;

	@JsonProperty("InqAccAccType")
	private String inqaccAccType;

	@JsonProperty("InqAccNextStmtDt")
	private int inqaccNextStatementDate;

	@JsonProperty("InqAccAvailBal")
	private BigDecimal inqaccAvailableBalance;

	@JsonProperty("InqAccEye")
	private String inqaccEyecatcher;

	@JsonProperty("InqAccSuccess")
	private String inqaccSuccess;

	@JsonProperty("InqAccIntRate")
	private BigDecimal inqaccInterestRate;


	public String getInqaccPcb1Pointer()
	{
		return inqaccPcb1Pointer;
	}


	public void setInqaccPcb1Pointer(String inqaccPcb1PointerIn)
	{
		inqaccPcb1Pointer = inqaccPcb1PointerIn;
	}


	public int getInqaccOverdraft()
	{
		return inqaccOverdraft;
	}


	public void setInqaccOverdraft(int inqaccOverdraftIn)
	{
		inqaccOverdraft = inqaccOverdraftIn;
	}


	public int getInqaccLastStatementDate()
	{
		return inqaccLastStatementDate;
	}


	public void setInqaccLastStatementDate(int inqaccLastStatementDateIn)
	{
		inqaccLastStatementDate = inqaccLastStatementDateIn;
	}


	public int getInqaccSortcode()
	{
		return inqaccSortcode;
	}


	public void setInqaccSortcode(int inqaccSortcodeIn)
	{
		inqaccSortcode = inqaccSortcodeIn;
	}


	public BigDecimal getInqaccActualBalance()
	{
		return inqaccActualBalance;
	}


	public void setInqaccActualBalance(BigDecimal inqaccActualBalanceIn)
	{
		inqaccActualBalance = inqaccActualBalanceIn;
	}


	public int getInqaccAccno()
	{
		return inqaccAccno;
	}


	public void setInqaccAccno(int inqaccAccnoIn)
	{
		inqaccAccno = inqaccAccnoIn;
	}


	public int getInqaccOpened()
	{
		return inqaccOpened;
	}


	public void setInqaccOpened(int inqaccOpenedIn)
	{
		inqaccOpened = inqaccOpenedIn;
	}


	public String getInqaccCustno()
	{
		return inqaccCustno;
	}


	public void setInqaccCustno(String inqaccCustnoIn)
	{
		inqaccCustno = inqaccCustnoIn;
	}


	public String getInqaccAccType()
	{
		return inqaccAccType;
	}


	public void setInqaccAccType(String inaccAccTypeIn)
	{
		inqaccAccType = inaccAccTypeIn;
	}


	public int getInqaccNextStatementDate()
	{
		return inqaccNextStatementDate;
	}


	public void setInqaccNextStatementDate(int inqaccNextStatementDateIn)
	{
		inqaccNextStatementDate = inqaccNextStatementDateIn;
	}


	public BigDecimal getInqaccAvailableBalance()
	{
		return inqaccAvailableBalance;
	}


	public void setInqaccAvailableBalance(BigDecimal inqaccAvailableBalanceIn)
	{
		inqaccAvailableBalance = inqaccAvailableBalanceIn;
	}


	public String getInqaccEyecatcher()
	{
		return inqaccEyecatcher;
	}


	public void setInqaccEyecatcher(String inqaccEyecatcherIn)
	{
		inqaccEyecatcher = inqaccEyecatcherIn;
	}


	public String getInaccSuccess()
	{
		return inqaccSuccess;
	}


	public void setInqaccSuccess(String inqaccSuccessIn)
	{
		inqaccSuccess = inqaccSuccessIn;
	}


	public BigDecimal getInqaccInterestRate()
	{
		return inqaccInterestRate;
	}


	public void setInqaccInterestRate(BigDecimal inqaccInterestRateIn)
	{
		inqaccInterestRate = inqaccInterestRateIn;
	}


	@Override
	public String toString()
	{
		return "InqaccJson [InqAccAccno=" + inqaccAccno + ", InqAccAccType="
				+ inqaccAccType + ", InqAccActualBal=" + inqaccActualBalance
				+ ", InqAccAvailBal=" + inqaccAvailableBalance
				+ ", InqAccCustno=" + inqaccCustno + ", InqAccEye="
				+ inqaccEyecatcher + ", InqAccIntRate=" + inqaccInterestRate
				+ ", InqAccLastStmtDt=" + inqaccLastStatementDate
				+ ", InqAccNextStmtDt=" + inqaccNextStatementDate
				+ ", InqAccOpened=" + inqaccOpened + ", InqAccOverdraft="
				+ inqaccOverdraft + ", InqAccPcb1Pointer=" + inqaccPcb1Pointer
				+ ", InqAccScode=" + inqaccSortcode + ", InqAccSuccess="
				+ inqaccSuccess + "]";
	}

}
