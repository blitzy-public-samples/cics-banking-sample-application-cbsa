/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.core.dto.updateaccount;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Top-level wrapper for the frozen <em>update-account</em> contract. The legacy
 * z/OS Connect service nested the payload under the {@code UpdAcc} key, so the
 * wire envelope is {@code {"UpdAcc": { ... }}} on both request and response
 * (feature F-019).
 */
public class UpdateAccountJson
{

	/** Nested update-account commarea. */
	@JsonProperty("UpdAcc")
	private UpdaccJson updacc;

	/**
	 * Default constructor for Jackson (de)serialisation.
	 */
	public UpdateAccountJson()
	{
		// No initialisation required.
	}

	/**
	 * Convenience constructor wrapping an existing commarea.
	 *
	 * @param updacc the nested commarea
	 */
	public UpdateAccountJson(UpdaccJson updacc)
	{
		this.updacc = updacc;
	}

	/**
	 * Returns the nested update-account commarea.
	 *
	 * @return the commarea
	 */
	public UpdaccJson getUpdacc()
	{
		return updacc;
	}

	/**
	 * Sets the nested update-account commarea.
	 *
	 * @param updacc the commarea
	 */
	public void setUpdacc(UpdaccJson updacc)
	{
		this.updacc = updacc;
	}

}
