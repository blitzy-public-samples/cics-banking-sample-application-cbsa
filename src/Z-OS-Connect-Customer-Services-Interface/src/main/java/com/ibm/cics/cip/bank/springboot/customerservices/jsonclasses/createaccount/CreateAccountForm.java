/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.springboot.customerservices.jsonclasses.createaccount;

import java.math.BigDecimal;

import jakarta.validation.constraints.*;

public class CreateAccountForm
{


	@NotNull
	@Size(max = 8)
	private String custNumber;

	@NotNull
	private AccountType accountType;

	private int overdraftLimit;

	// QA Issue 5: interest rate is monetary precision; AAP requires BigDecimal
	// (scale-2) and prohibits float/double. Spring MVC binds the form value
	// straight into BigDecimal.
	private BigDecimal interestRate;


	public int getOverdraftLimit()
	{
		return overdraftLimit;
	}


	public void setOverdraftLimit(int overdraftLimit)
	{
		this.overdraftLimit = overdraftLimit;
	}


	public BigDecimal getInterestRate()
	{
		return interestRate;
	}


	public void setInterestRate(BigDecimal interestRate)
	{
		this.interestRate = interestRate;
	}


	public CreateAccountForm()
	{
		super();
	}


	public String getCustNumber()
	{
		return custNumber;
	}


	public void setCustNumber(String custNumber)
	{
		this.custNumber = custNumber;
	}


	public AccountType getAccountType()
	{
		return accountType;
	}


	public void setAccountType(AccountType accountType)
	{
		this.accountType = accountType;
	}


	@Override
	public String toString()
	{
		return "CreateAccountForm [accountType=" + accountType + ", custNumber="
				+ custNumber + ", interestRate=" + interestRate
				+ ", overdraftLimit=" + overdraftLimit + "]";
	}

}
