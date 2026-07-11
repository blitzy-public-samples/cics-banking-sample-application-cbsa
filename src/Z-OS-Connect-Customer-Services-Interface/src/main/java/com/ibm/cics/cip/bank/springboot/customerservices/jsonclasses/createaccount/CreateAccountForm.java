/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.springboot.customerservices.jsonclasses.createaccount;

import jakarta.validation.constraints.*;

public class CreateAccountForm
{


	@NotNull
	@Size(max = 8)
	private String custNumber;

	@NotNull
	private AccountType accountType;

	// SECURITY (V3, CWE-20 Missing Input Validation): reject negative overdraft
	// limits at the Spring boundary before any downstream mutation. The Db2
	// ACCOUNT_OVERDRAFT_LIMIT column is a non-negative INTEGER, so a negative
	// value is never valid. @PositiveOrZero is used (rather than @Min/@Max,
	// which the Bean Validation spec does not support on primitive numeric
	// fields the way the Positive/Negative family does) to enforce the >= 0
	// domain rule with reject-by-default semantics.
	@PositiveOrZero
	private int overdraftLimit;

	// SECURITY (V3, CWE-20 Missing Input Validation): reject negative interest
	// rates at the Spring boundary. The Db2 ACCOUNT_INTEREST_RATE column is a
	// non-negative DECIMAL(4,2); @PositiveOrZero (valid on float, unlike
	// @DecimalMax) enforces the >= 0 domain rule. The upper bound remains
	// enforced downstream in the Liberty account layer as defense-in-depth.
	@PositiveOrZero
	private float interestRate;


	public int getOverdraftLimit()
	{
		return overdraftLimit;
	}


	public void setOverdraftLimit(int overdraftLimit)
	{
		this.overdraftLimit = overdraftLimit;
	}


	public float getInterestRate()
	{
		return interestRate;
	}


	public void setInterestRate(float interestRate)
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
