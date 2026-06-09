/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.springboot.customerservices.jsonclasses.createaccount;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.ibm.cics.cip.bank.springboot.customerservices.JsonPropertyNamingStrategy;

@JsonNaming(JsonPropertyNamingStrategy.class)
public class CreaccJson
{



	@JsonProperty("CommAccType")
	private String commAccType;

	@JsonProperty("CommCustno")
	private String commCustno;

	@JsonProperty("CommEyecatcher")
	private String commEyecatcher;

	@JsonProperty("CommKey")
	private CreaccKeyJson commKey;

	// QA Issue 5: interest rate is COBOL 9(4)V99 money; AAP requires BigDecimal
	// (scale-2) and prohibits float/double to preserve exact rounding.
	@JsonProperty("CommIntRt")
	private BigDecimal commInterestRate;

	@JsonProperty("CommOpened")
	private int commOpened;

	// QA Issue 5: overdraft limit is COBOL 9(8) with no decimals; bank-core
	// expects an Integer. Use Integer (not float) to match the contract and
	// avoid floating-point representation of a whole-pound limit.
	@JsonProperty("CommOverdrLim")
	private Integer commOverdraftLimit;

	@JsonProperty("CommLastStmtDt")
	private int commLastStatementDate;

	@JsonProperty("CommNextStmtDt")
	private int commNextStatementDate;

	@JsonProperty("CommAvailBal")
	private BigDecimal commAvailableBalance;

	@JsonProperty("CommActBal")
	private BigDecimal commActualBalance;

	@JsonProperty("CommSuccess")
	private String commSuccess;

	@JsonProperty("CommFailCode")
	private String commFailCode;


	public CreaccJson(String accountType, String accountNumber,
			Integer overdraftLimit, BigDecimal interestRate)
	{
		commAccType = String.format("%-8s", accountType);
		commCustno = String.format("%8s", accountNumber).replace(" ", "0");

		commOverdraftLimit = overdraftLimit;
		commInterestRate = interestRate;

		commEyecatcher = "    ";
		commKey = new CreaccKeyJson();

	}


	public CreaccJson()
	{

	}


	public String getCommEyecatcher()
	{
		return commEyecatcher;
	}


	public void setCommEyecatcher(String commEyecatcherIn)
	{
		commEyecatcher = commEyecatcherIn;
	}


	public String getCommCustno()
	{
		return commCustno;
	}


	public void setCommCustno(String commCustnoIn)
	{
		commCustno = commCustnoIn;
	}


	public CreaccKeyJson getCommKey()
	{
		return commKey;
	}


	public void setCommKey(CreaccKeyJson commKeyIn)
	{
		commKey = commKeyIn;
	}


	public String getCommAccType()
	{
		return commAccType;
	}


	public void setCommAccType(String commAccTypeIn)
	{
		commAccType = commAccTypeIn;
	}


	public BigDecimal getCommInterestRate()
	{
		return commInterestRate;
	}


	public void setCommInterestRate(BigDecimal commInterestRateIn)
	{
		commInterestRate = commInterestRateIn;
	}


	public int getCommOpened()
	{
		return commOpened;
	}


	public void setCommOpened(int commOpenedIn)
	{
		commOpened = commOpenedIn;
	}


	public Integer getCommOverdraftLimit()
	{
		return commOverdraftLimit;
	}


	public void setCommOverdraftLimit(Integer commOverdraftLimitIn)
	{
		commOverdraftLimit = commOverdraftLimitIn;
	}


	public int getCommLastStatementDate()
	{
		return commLastStatementDate;
	}


	public void setCommLastStatementDate(int setCommLastStatementDateIn)
	{
		commLastStatementDate = setCommLastStatementDateIn;
	}


	public int getCommNextStatementDate()
	{
		return commNextStatementDate;
	}


	public void setCommNextStatementDate(int commNextStatementDateIn)
	{
		commNextStatementDate = commNextStatementDateIn;
	}


	public BigDecimal getCommAvailableBalance()
	{
		return commAvailableBalance;
	}


	public void setCommAvailableBalance(BigDecimal commAvailableBalanceIn)
	{
		commAvailableBalance = commAvailableBalanceIn;
	}


	public BigDecimal getCommActualBalance()
	{
		return commActualBalance;
	}


	public void setCommActualBalance(BigDecimal commActualBalanceIn)
	{
		commActualBalance = commActualBalanceIn;
	}


	public String getCommSuccess()
	{
		return commSuccess;
	}


	public void setCommSuccess(String commSuccessIn)
	{
		commSuccess = commSuccessIn;
	}


	public String getCommFailCode()
	{
		return commFailCode;
	}


	public void setCommFailCode(String commFailCodeIn)
	{
		commFailCode = commFailCodeIn;
	}

}
