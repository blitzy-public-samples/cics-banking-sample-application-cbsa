/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.core.dto.createcustomer;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Outer wrapper for the create-customer contract, nesting a {@link CrecustJson}
 * under the frozen {@code CreCust} root key (request body
 * {@code POST /crecust/insert} and its response), reproduced from the interface
 * module's {@code CreateCustomerJson} (feature F-019).
 */
public class CreateCustomerJson
{

	/** The nested create-customer envelope. */
	@JsonProperty("CreCust")
	private CrecustJson creCust;

	/**
	 * Default constructor for Jackson (de)serialisation.
	 */
	public CreateCustomerJson()
	{
		// No-arg constructor required by Jackson.
	}

	/**
	 * Convenience constructor.
	 *
	 * @param creCust the nested envelope
	 */
	public CreateCustomerJson(CrecustJson creCust)
	{
		this.creCust = creCust;
	}

	/**
	 * Returns the nested create-customer envelope.
	 *
	 * @return the envelope
	 */
	public CrecustJson getCreCust()
	{
		return creCust;
	}

	/**
	 * Sets the nested create-customer envelope.
	 *
	 * @param creCust the envelope
	 */
	public void setCreCust(CrecustJson creCust)
	{
		this.creCust = creCust;
	}

}
