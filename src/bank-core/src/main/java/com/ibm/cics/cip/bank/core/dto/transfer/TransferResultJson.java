/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.core.dto.transfer;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response envelope for the {@code bank-core} <em>transfer</em> endpoint
 * (reproducing {@code XFRFUN}). A flat result carrying the outcome flag, the
 * single-character fail code, and the post-transfer balances of both accounts.
 *
 * <p>Like {@link TransferJson} this is a purpose-built internal contract (not a
 * frozen z/OS Connect envelope). The fail code mirrors the {@code XFRFUN}
 * codes surfaced by {@code TransferService}: {@code "4"} (amount not positive),
 * {@code "1"} (source not found), {@code "2"} (target not found), {@code "3"}
 * (deadlock/lock failure) and {@code "SAME"} (transfer to the same account).
 * On success {@code success} is {@code "Y"} and {@code failCode} is empty.</p>
 *
 * <p>Both balances of both accounts are returned (rule: the available and actual
 * balances are independent and are each moved by a transfer), typed
 * {@link BigDecimal} (rule U1).</p>
 */
public class TransferResultJson
{

	/** Outcome flag ({@code Y} on success, {@code N} on failure). */
	@JsonProperty("success")
	private String success;

	/** Single-character fail code (or {@code SAME}); empty on success. */
	@JsonProperty("failCode")
	private String failCode;

	/** Source account number, zero-padded. */
	@JsonProperty("fromAccountNumber")
	private String fromAccountNumber;

	/** Source available balance after the transfer. */
	@JsonProperty("fromAvailableBalance")
	private BigDecimal fromAvailableBalance;

	/** Source actual balance after the transfer. */
	@JsonProperty("fromActualBalance")
	private BigDecimal fromActualBalance;

	/** Target account number, zero-padded. */
	@JsonProperty("toAccountNumber")
	private String toAccountNumber;

	/** Target available balance after the transfer. */
	@JsonProperty("toAvailableBalance")
	private BigDecimal toAvailableBalance;

	/** Target actual balance after the transfer. */
	@JsonProperty("toActualBalance")
	private BigDecimal toActualBalance;

	/**
	 * Default constructor for Jackson (de)serialisation.
	 */
	public TransferResultJson()
	{
		// No initialisation required.
	}

	/**
	 * Returns the outcome flag.
	 *
	 * @return the outcome flag
	 */
	public String getSuccess()
	{
		return success;
	}

	/**
	 * Sets the outcome flag.
	 *
	 * @param success the outcome flag
	 */
	public void setSuccess(String success)
	{
		this.success = success;
	}

	/**
	 * Returns the fail code.
	 *
	 * @return the fail code
	 */
	public String getFailCode()
	{
		return failCode;
	}

	/**
	 * Sets the fail code.
	 *
	 * @param failCode the fail code
	 */
	public void setFailCode(String failCode)
	{
		this.failCode = failCode;
	}

	/**
	 * Returns the source account number.
	 *
	 * @return the source account number
	 */
	public String getFromAccountNumber()
	{
		return fromAccountNumber;
	}

	/**
	 * Sets the source account number.
	 *
	 * @param fromAccountNumber the source account number
	 */
	public void setFromAccountNumber(String fromAccountNumber)
	{
		this.fromAccountNumber = fromAccountNumber;
	}

	/**
	 * Returns the source available balance after the transfer.
	 *
	 * @return the source available balance
	 */
	public BigDecimal getFromAvailableBalance()
	{
		return fromAvailableBalance;
	}

	/**
	 * Sets the source available balance after the transfer.
	 *
	 * @param fromAvailableBalance the source available balance
	 */
	public void setFromAvailableBalance(BigDecimal fromAvailableBalance)
	{
		this.fromAvailableBalance = fromAvailableBalance;
	}

	/**
	 * Returns the source actual balance after the transfer.
	 *
	 * @return the source actual balance
	 */
	public BigDecimal getFromActualBalance()
	{
		return fromActualBalance;
	}

	/**
	 * Sets the source actual balance after the transfer.
	 *
	 * @param fromActualBalance the source actual balance
	 */
	public void setFromActualBalance(BigDecimal fromActualBalance)
	{
		this.fromActualBalance = fromActualBalance;
	}

	/**
	 * Returns the target account number.
	 *
	 * @return the target account number
	 */
	public String getToAccountNumber()
	{
		return toAccountNumber;
	}

	/**
	 * Sets the target account number.
	 *
	 * @param toAccountNumber the target account number
	 */
	public void setToAccountNumber(String toAccountNumber)
	{
		this.toAccountNumber = toAccountNumber;
	}

	/**
	 * Returns the target available balance after the transfer.
	 *
	 * @return the target available balance
	 */
	public BigDecimal getToAvailableBalance()
	{
		return toAvailableBalance;
	}

	/**
	 * Sets the target available balance after the transfer.
	 *
	 * @param toAvailableBalance the target available balance
	 */
	public void setToAvailableBalance(BigDecimal toAvailableBalance)
	{
		this.toAvailableBalance = toAvailableBalance;
	}

	/**
	 * Returns the target actual balance after the transfer.
	 *
	 * @return the target actual balance
	 */
	public BigDecimal getToActualBalance()
	{
		return toActualBalance;
	}

	/**
	 * Sets the target actual balance after the transfer.
	 *
	 * @param toActualBalance the target actual balance
	 */
	public void setToActualBalance(BigDecimal toActualBalance)
	{
		this.toActualBalance = toActualBalance;
	}

}
