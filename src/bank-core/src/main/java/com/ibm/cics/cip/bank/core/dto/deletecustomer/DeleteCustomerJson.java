/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.core.dto.deletecustomer;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Top-level wrapper for the frozen <em>delete-customer</em> contract. The legacy
 * z/OS Connect service nested the payload under the {@code DelCus} key, so the
 * response envelope is {@code {"DelCus": { ... }}} (feature F-019). The consumer
 * reads {@code getDelcus().getCommDelFailCode()} to detect a not-found delete.
 */
public class DeleteCustomerJson
{

	/** Nested delete-customer commarea. */
	@JsonProperty("DelCus")
	private DelcusJson delcus;

	/**
	 * Default constructor for Jackson (de)serialisation.
	 */
	public DeleteCustomerJson()
	{
		super();
	}

	/**
	 * Convenience constructor wrapping an existing commarea.
	 *
	 * @param delcus the nested commarea
	 */
	public DeleteCustomerJson(DelcusJson delcus)
	{
		this.delcus = delcus;
	}

	/**
	 * Returns the nested delete-customer commarea.
	 *
	 * @return the commarea
	 */
	public DelcusJson getDelcus()
	{
		return delcus;
	}

	/**
	 * Sets the nested delete-customer commarea.
	 *
	 * @param delcusIn the commarea
	 */
	public void setDelcus(DelcusJson delcusIn)
	{
		delcus = delcusIn;
	}

}
