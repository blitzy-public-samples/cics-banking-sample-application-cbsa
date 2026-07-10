/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.springboot.paymentinterface.jsonclasses.paymentinterface;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class TransferForm
{



	// accno
	// Security fix (V3 CWE-20 Improper Input Validation - OWASP A03 Injection; QA finding
	// F-QA2): @NotNull alone accepts an empty string (""), which @Size(max=8) also permits
	// (length 0), so a blank account number bypassed validation and was forwarded to the
	// downstream money-movement call. @Size(min = 1, ...) enforces reject-by-default at the
	// controller boundary, mirroring the correct CustomerEnquiryForm pattern.
	@NotNull
	@Size(min = 1, max = 8)
	private String acctNumber;

	@NotNull
	private boolean debit = true;

	@NotNull
	private Float amount;

	// Security fix (V3 CWE-20 - QA finding F-QA2): reject a blank organisation (CommApplid)
	// at the boundary; @Size(min = 1, ...) closes the empty-string gap left by @NotNull.
	@NotNull
	@Size(min = 1, max = 16)
	private String organisation;


	public TransferForm()
	{

	}


	public TransferForm(@NotNull @Size(min = 1, max = 8) String acctNumber,
			@NotNull Float amount,
			@NotNull @Size(min = 1, max = 16) String organisation)
	{
		this.acctNumber = acctNumber;
		this.amount = amount;
		this.organisation = organisation;
	}


	public String getAcctNumber()
	{
		return acctNumber;
	}


	public void setAcctNumber(String acctNumber)
	{
		this.acctNumber = acctNumber;
	}


	public boolean isDebit()
	{
		return debit;
	}


	public void setDebit(boolean debit)
	{
		this.debit = debit;
	}


	public void setDebit(String type)
	{
		this.debit = type.equals("Debit");
	}


	public Float getAmount()
	{
		return amount;
	}


	public void setAmount(Float amount)
	{
		this.amount = amount;
	}


	public String getOrganisation()
	{
		return organisation;
	}


	public void setOrganisation(String organisation)
	{
		this.organisation = organisation;
	}


	@Override
	public String toString()
	{
		return "TransferForm [acctNumber=" + acctNumber + ", amount=" + amount
				+ ", organisation=" + organisation + "]";
	}
}
