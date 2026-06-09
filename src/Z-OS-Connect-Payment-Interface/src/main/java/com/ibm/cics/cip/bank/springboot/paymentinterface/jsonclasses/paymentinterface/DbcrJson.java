/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.springboot.paymentinterface.jsonclasses.paymentinterface;

import java.math.BigDecimal;

import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.ibm.cics.cip.bank.springboot.paymentinterface.JsonPropertyNamingStrategy;

@JsonNaming(JsonPropertyNamingStrategy.class)
public class DbcrJson
{


	@JsonProperty("CommAccno")
	private String commAccno;

	// QA Issue 5: payment amount is COBOL S9(10)V99 money; AAP requires
	// BigDecimal (scale-2) and prohibits float/double to preserve exact
	// COBOL rounding.
	@JsonProperty("CommAmt")
	private BigDecimal commAmt;

	@JsonProperty("mSortC")
	private int commSortC = 0;

	// QA Issue 5: returned balances are monetary values with decimals. Binding
	// them into int silently truncates (and risks a parse failure); use
	// BigDecimal to carry the exact balance back from bank-core.
	@JsonProperty("CommAvBal")
	private BigDecimal commAvBal = new BigDecimal("0.00");

	@JsonProperty("CommActBal")
	private BigDecimal commActBal = new BigDecimal("0.00");

	@JsonProperty("CommOrigin")
	private OriginJson commOrigin;

	@JsonProperty("CommSuccess")
	private String commSuccess = " ";

	@JsonProperty("CommFailCode")
	private String commFailCode = " ";


	public DbcrJson()
	{

	}


	public DbcrJson(TransferForm transferForm)
	{
		commOrigin = new OriginJson(transferForm.getOrganisation());

		// accno
		// Pads out with zeroes to the length specified
		commAccno = String.format("%8s", transferForm.getAcctNumber())
				.replace(" ", "0");

		// Make the amount positive or negative based on wether debit or credit
		// is selected. QA Issue 5: negate via BigDecimal.negate() rather than
		// float arithmetic so the sign flip introduces no rounding error.
		commAmt = transferForm.isDebit() ? transferForm.getAmount().negate()
				: transferForm.getAmount();
	}


	public String getCommAccno()
	{
		return commAccno;
	}


	public void setCommAccno(String commAccnoIn)
	{
		commAccno = commAccnoIn;
	}


	public BigDecimal getCommAmt()
	{
		return commAmt;
	}


	public void setCommAmt(BigDecimal commAmtIn)
	{
		commAmt = commAmtIn;
	}


	public int getCommSortC()
	{
		return commSortC;
	}


	public void setCommC(int commSortCodeIn)
	{
		commSortC = commSortCodeIn;
	}


	public BigDecimal getCommAvBal()
	{
		return commAvBal;
	}


	public void setCommAvBal(BigDecimal commAvBalIn)
	{
		commAvBal = commAvBalIn;
	}


	public BigDecimal getCommActBal()
	{
		return commActBal;
	}


	public void setCommActBal(BigDecimal commActBalIn)
	{
		commActBal = commActBalIn;
	}


	public OriginJson getCommOrigin()
	{
		return commOrigin;
	}


	public void setCommOrigin(OriginJson commOriginIn)
	{
		commOrigin = commOriginIn;
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


	@Override
	public String toString()
	{
		return "DbcrJson [CommAccno=" + commAccno + ", CommActBal="
				+ commActBal + ", CommAmt=" + commAmt + ", CommAvBal="
				+ commAvBal + ", CommFailCode=" + commFailCode
				+ ", CommOrigin=" + commOrigin.toString() + ", CommSortC="
				+ commSortC + ", CommSuccess=" + commSuccess + "]";
	}

}
