/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.springboot.customerservices.jsonclasses.listaccounts;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.ibm.cics.cip.bank.springboot.customerservices.JsonPropertyNamingStrategy;

@JsonNaming(JsonPropertyNamingStrategy.class)
public class InqAccczJson
{



	@JsonProperty("CommFailCode")
	private int commFailCode;

	// QA Issues 4 and 5: customer number is a fixed-width 10-digit COBOL
	// identifier. int overflows for valid upper-bound values and discards
	// leading zeroes, so bind into String to preserve the zero-padded
	// identifier; bank-core emits CustomerNumber as a JSON string here.
	@JsonProperty("CustomerNumber")
	private String customerNumber;

	@JsonProperty("AccountDetails")
	private List<AccountDetails> accountDetails;

	@JsonProperty("CommPcbPointer")
	private String commPcbPointer;

	@JsonProperty("CustomerFound")
	private String customerFound;

	@JsonProperty("CommSuccess")
	private String commSuccess;


	public int getCommFailCode()
	{
		return commFailCode;
	}


	public void setCommFailCode(int commFailCodeIn)
	{
		commFailCode = commFailCodeIn;
	}


	public String getCustomerNumber()
	{
		return customerNumber;
	}


	public void setCustomerNumber(String customerNumberIn)
	{
		customerNumber = customerNumberIn;
	}


	public List<AccountDetails> getAccountDetails()
	{
		return accountDetails;
	}


	public void setAccountDetails(List<AccountDetails> accountDetailsIn)
	{
		accountDetails = accountDetailsIn;
	}


	public String getCommPcbPointer()
	{
		return commPcbPointer;
	}


	public void setCommPcbPointer(String commPcbPointerIn)
	{
		commPcbPointer = commPcbPointerIn;
	}


	public String getCustomerFound()
	{
		return customerFound;
	}


	public void setCustomerFound(String customerFoundIn)
	{
		customerFound = customerFoundIn;
	}


	public String getCommSuccess()
	{
		return commSuccess;
	}


	public void setCommSuccess(String commSuccessIn)
	{
		commSuccess = commSuccessIn;
	}


	@Override
	public String toString()
	{
		return "InqAccczJson [AccountDetails=" + accountDetails
				+ ", CommFailCode=" + commFailCode + ", CommPcbPointer="
				+ commPcbPointer + ", CommSuccess=" + commSuccess
				+ ", CustomerFound=" + customerFound + ", CustomerNumber="
				+ customerNumber + "]";
	}
}
