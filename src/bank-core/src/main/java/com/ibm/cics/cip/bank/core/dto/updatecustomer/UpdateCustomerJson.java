/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.core.dto.updatecustomer;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Outer wrapper for the update-customer contract, nesting an {@link UpdcustJson}
 * under the frozen {@code UpdCust} root key (request body
 * {@code PUT /updcust/update} and its response), reproduced from the interface
 * module's {@code UpdateCustomerJson} (feature F-019).
 */
public class UpdateCustomerJson
{

	/** The nested update-customer envelope. */
	@JsonProperty("UpdCust")
	private UpdcustJson updCust;

	/**
	 * Default constructor for Jackson (de)serialisation.
	 */
	public UpdateCustomerJson()
	{
		// No-arg constructor required by Jackson.
	}

	/**
	 * Convenience constructor.
	 *
	 * @param updCust the nested envelope
	 */
	public UpdateCustomerJson(UpdcustJson updCust)
	{
		this.updCust = updCust;
	}

	/**
	 * Returns the nested update-customer envelope.
	 *
	 * @return the envelope
	 */
	@JsonProperty("UpdCust")
	public UpdcustJson getUpdcust()
	{
		return updCust;
	}

	/**
	 * Sets the nested update-customer envelope.
	 *
	 * @param updCust the envelope
	 */
	@JsonProperty("UpdCust")
	public void setUpdcust(UpdcustJson updCust)
	{
		this.updCust = updCust;
	}

}
