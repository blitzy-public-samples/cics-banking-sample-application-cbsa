/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.core.dto.createaccount;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Outer wrapper for the create-account contract, nesting a {@link CreaccJson}
 * under the frozen {@code CreAcc} root key (request body
 * {@code POST /creacc/insert} and its response), reproduced from the interface
 * module's {@code CreateAccountJson} (feature F-019).
 */
public class CreateAccountJson
{

	/** The nested create-account envelope. */
	@JsonProperty("CreAcc")
	private CreaccJson creAcc;

	/**
	 * Default constructor for Jackson (de)serialisation.
	 */
	public CreateAccountJson()
	{
		// No-arg constructor required by Jackson.
	}

	/**
	 * Convenience constructor.
	 *
	 * @param creAcc the nested envelope
	 */
	public CreateAccountJson(CreaccJson creAcc)
	{
		this.creAcc = creAcc;
	}

	/**
	 * Returns the nested create-account envelope.
	 *
	 * @return the envelope
	 */
	public CreaccJson getCreAcc()
	{
		return creAcc;
	}

	/**
	 * Sets the nested create-account envelope.
	 *
	 * @param creAcc the envelope
	 */
	public void setCreAcc(CreaccJson creAcc)
	{
		this.creAcc = creAcc;
	}

}
