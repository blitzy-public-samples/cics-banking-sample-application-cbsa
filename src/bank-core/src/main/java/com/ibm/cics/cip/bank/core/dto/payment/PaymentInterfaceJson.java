/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.core.dto.payment;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Top-level wrapper for the frozen <em>make-payment</em> (debit/credit)
 * contract. The legacy z/OS Connect service nested the payload under the
 * {@code PAYDBCR} key, so the wire envelope is {@code {"PAYDBCR": { ... }}} on
 * <em>both</em> request and response (feature F-019).
 */
public class PaymentInterfaceJson
{

	/** Nested debit/credit commarea. */
	@JsonProperty("PAYDBCR")
	private DbcrJson payDbCr;

	/**
	 * Default constructor for Jackson (de)serialisation.
	 */
	public PaymentInterfaceJson()
	{
		// No initialisation required.
	}

	/**
	 * Convenience constructor wrapping an existing commarea.
	 *
	 * @param payDbCr the nested commarea
	 */
	public PaymentInterfaceJson(DbcrJson payDbCr)
	{
		this.payDbCr = payDbCr;
	}

	/**
	 * Returns the nested debit/credit commarea.
	 *
	 * <p>The {@code @JsonProperty("PAYDBCR")} here is required: the getter name
	 * ({@code getPAYDBCR}) does not follow from the field name ({@code payDbCr})
	 * by JavaBean convention, so without the explicit annotation Jackson would
	 * treat the field and the getter as two separate properties and emit
	 * <em>two</em> keys. Annotating the field, getter, and setter with the same
	 * name merges them into the single {@code PAYDBCR} envelope key.</p>
	 *
	 * @return the commarea
	 */
	@JsonProperty("PAYDBCR")
	public DbcrJson getPAYDBCR()
	{
		return payDbCr;
	}

	/**
	 * Sets the nested debit/credit commarea.
	 *
	 * @param payDbCrIn the commarea
	 */
	@JsonProperty("PAYDBCR")
	public void setPAYDBCR(DbcrJson payDbCrIn)
	{
		this.payDbCr = payDbCrIn;
	}

}
