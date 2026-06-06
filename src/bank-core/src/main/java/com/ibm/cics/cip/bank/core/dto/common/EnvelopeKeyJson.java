/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.core.dto.common;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Shared nested key object used by the create-customer and create-account
 * envelopes, reproducing the legacy z/OS Connect {@code CommKey} structure
 * (the interface module's {@code CreaccKeyJson}) verbatim.
 *
 * <p>It carries the two-part identity of the created record: the bank sort code
 * and the allocated record number, both serialised as JSON integers under the
 * frozen wire names {@code CommSortcode} and {@code CommNumber} (feature
 * F-019).</p>
 */
public class EnvelopeKeyJson
{

	/** Sort code, serialised as {@code CommSortcode}. */
	@JsonProperty("CommSortcode")
	private int commSortcode;

	/** Record (customer or account) number, serialised as {@code CommNumber}. */
	@JsonProperty("CommNumber")
	private int commNumber;

	/**
	 * Default constructor for Jackson (de)serialisation.
	 */
	public EnvelopeKeyJson()
	{
		// Defaults of 0/0 match the legacy CreaccKeyJson initial values.
	}

	/**
	 * Convenience constructor.
	 *
	 * @param commSortcode the sort code
	 * @param commNumber   the record number
	 */
	public EnvelopeKeyJson(int commSortcode, int commNumber)
	{
		this.commSortcode = commSortcode;
		this.commNumber = commNumber;
	}

	/**
	 * Returns the sort code.
	 *
	 * @return the sort code
	 */
	public int getCommSortcode()
	{
		return commSortcode;
	}

	/**
	 * Sets the sort code.
	 *
	 * @param commSortcode the sort code
	 */
	public void setCommSortcode(int commSortcode)
	{
		this.commSortcode = commSortcode;
	}

	/**
	 * Returns the record number.
	 *
	 * @return the record number
	 */
	public int getCommNumber()
	{
		return commNumber;
	}

	/**
	 * Sets the record number.
	 *
	 * @param commNumber the record number
	 */
	public void setCommNumber(int commNumber)
	{
		this.commNumber = commNumber;
	}

}
