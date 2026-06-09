/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.springboot.paymentinterface.jsonclasses.paymentinterface;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class TransferForm
{



	// accno
	@NotNull
	@Size(max = 8)
	private String acctNumber;

	@NotNull
	private boolean debit = true;

	// QA Issue 5: transfer amount is COBOL S9(10)V99 money; AAP requires
	// BigDecimal (scale-2) and prohibits float/double to preserve exact
	// COBOL rounding through the payment path.
	@NotNull
	private BigDecimal amount;

	@NotNull
	@Size(max = 16)
	private String organisation;


	public TransferForm()
	{

	}


	public TransferForm(@NotNull @Size(max = 8) String acctNumber,
			@NotNull BigDecimal amount,
			@NotNull @Size(max = 16) String organisation)
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


	public BigDecimal getAmount()
	{
		return amount;
	}


	public void setAmount(BigDecimal amount)
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
