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

	// SECURITY (V3, CWE-20 Missing Input Validation - OWASP A03; reject-by-default
	// at every controller boundary): bound the interest rate on BOTH sides at the
	// Spring boundary before the value is serialised into a CreateAccountJson and
	// forwarded to the downstream z/OS Connect /creacc call.
	//   - @PositiveOrZero rejects a negative rate (the Db2 ACCOUNT_INTEREST_RATE
	//     column is non-negative).
	//   - @DecimalMax adds the previously-missing upper bound as defense-in-depth,
	//     rejecting a grossly excessive rate at the Spring tier. The authoritative
	//     fine-grained bound (reject > 9999.99) is enforced in the Liberty account
	//     layer (AccountsResource); this guard stops clearly-invalid magnitudes
	//     before any downstream mutation.
	// The bound is intentionally expressed as "< 10000.00" (inclusive = false)
	// rather than "<= 9999.99": interestRate is a primitive float and 9999.99 has
	// no exact float representation (9999.99f is stored as ~9999.9902), so a
	// "<= 9999.99" constraint would spuriously REJECT the legitimate maximum and
	// break behaviour for an authorised teller (AAP zero-functional-change rule).
	// "< 10000.00" accepts the entire legitimate [0, 9999.99] range (Jackson
	// serialises 9999.99f back to "9999.99" for the exact downstream check) while
	// still rejecting excessive input. Hibernate Validator 8.x supports @DecimalMax
	// on float via DecimalMaxValidatorForFloat.
	@PositiveOrZero
	@DecimalMax(value = "10000.00", inclusive = false)
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
