/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.core.dto.transfer;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request envelope for the {@code bank-core} <em>transfer</em> endpoint, which
 * reproduces the legacy {@code XFRFUN} program.
 *
 * <p><strong>Not a frozen z/OS Connect contract.</strong> {@code XFRFUN} is one
 * of the thirteen business programs but is <em>not</em> among the ten frozen
 * z/OS Connect REST endpoints (feature F-019), so there is no externally-frozen
 * wire shape to preserve. This is therefore a purpose-built, self-contained
 * internal contract consumed by the {@code webui} adapter: the adapter maps its
 * own caller request onto this envelope and maps {@link TransferResultJson} back
 * to the {@code /webui-1.0/banking/*} response.</p>
 *
 * <p>The amount is a positive {@link BigDecimal} (rule U1); the sign convention
 * of {@code DBCRFUN} does not apply here because a transfer always debits the
 * source and credits the target.</p>
 */
public class TransferJson
{

	/** Source account number (debited). */
	@JsonProperty("fromAccount")
	private long fromAccount;

	/** Target account number (credited). */
	@JsonProperty("toAccount")
	private long toAccount;

	/** Transfer amount; must be strictly positive. */
	@JsonProperty("amount")
	private BigDecimal amount;

	/**
	 * Default constructor for Jackson (de)serialisation.
	 */
	public TransferJson()
	{
		// No initialisation required.
	}

	/**
	 * Returns the source account number.
	 *
	 * @return the source account number
	 */
	public long getFromAccount()
	{
		return fromAccount;
	}

	/**
	 * Sets the source account number.
	 *
	 * @param fromAccount the source account number
	 */
	public void setFromAccount(long fromAccount)
	{
		this.fromAccount = fromAccount;
	}

	/**
	 * Returns the target account number.
	 *
	 * @return the target account number
	 */
	public long getToAccount()
	{
		return toAccount;
	}

	/**
	 * Sets the target account number.
	 *
	 * @param toAccount the target account number
	 */
	public void setToAccount(long toAccount)
	{
		this.toAccount = toAccount;
	}

	/**
	 * Returns the transfer amount.
	 *
	 * @return the transfer amount
	 */
	public BigDecimal getAmount()
	{
		return amount;
	}

	/**
	 * Sets the transfer amount.
	 *
	 * @param amount the transfer amount
	 */
	public void setAmount(BigDecimal amount)
	{
		this.amount = amount;
	}

}
