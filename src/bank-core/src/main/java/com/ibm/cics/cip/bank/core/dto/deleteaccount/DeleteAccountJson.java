/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.core.dto.deleteaccount;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Top-level wrapper for the frozen <em>delete-account</em> contract. The legacy
 * z/OS Connect service nested the payload under the {@code DelAcc} key, so the
 * response envelope is {@code {"DelAcc": { ... }}} (feature F-019). The consumer
 * reads {@code getDelaccCommarea().getDelaccDelFailCode()} to detect a not-found
 * delete.
 */
public class DeleteAccountJson
{

	/** Nested delete-account commarea. */
	@JsonProperty("DelAcc")
	private DelaccJson delaccCommarea;

	/**
	 * Default constructor for Jackson (de)serialisation.
	 */
	public DeleteAccountJson()
	{
		super();
	}

	/**
	 * Convenience constructor wrapping an existing commarea.
	 *
	 * @param delaccCommarea the nested commarea
	 */
	public DeleteAccountJson(DelaccJson delaccCommarea)
	{
		this.delaccCommarea = delaccCommarea;
	}

	/**
	 * Returns the nested delete-account commarea.
	 *
	 * @return the commarea
	 */
	public DelaccJson getDelaccCommarea()
	{
		return delaccCommarea;
	}

	/**
	 * Sets the nested delete-account commarea.
	 *
	 * @param delaccCommareaIn the commarea
	 */
	public void setDelaccCommarea(DelaccJson delaccCommareaIn)
	{
		delaccCommarea = delaccCommareaIn;
	}

}
