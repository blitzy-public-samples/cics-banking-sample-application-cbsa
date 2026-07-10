/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.springboot.customerservices.jsonclasses.accountenquiry;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class AccountEnquiryForm
{



	// Security fix (V3 CWE-20 Improper Input Validation - OWASP A03 Injection; QA finding
	// F-QA2): @NotNull alone accepts an empty string (""), which @Size(max=8) also permits
	// (length 0), so a blank account number bypassed validation and was forwarded downstream
	// on both the /enqacct read and the state-changing /delacct delete. @Size(min = 1, ...)
	// enforces reject-by-default at the controller boundary, mirroring the correct
	// CustomerEnquiryForm pattern (custNumber = @NotNull @Size(min = 1, max = 10)).
	@NotNull
	@Size(min = 1, max = 8)
	private String acctNumber;


	public AccountEnquiryForm()
	{

	}


	public AccountEnquiryForm(@NotNull @Size(min = 1, max = 8) String acctNumber)
	{
		this.acctNumber = acctNumber;
	}


	public String getAcctNumber()
	{
		return acctNumber;
	}


	public void setAcctNumber(String acctNumber)
	{
		this.acctNumber = acctNumber;
	}


	@Override
	public String toString()
	{
		return "TransferForm [acctNumber=" + acctNumber + "]";
	}
}
