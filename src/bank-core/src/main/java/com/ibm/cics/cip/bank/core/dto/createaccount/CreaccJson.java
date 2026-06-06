/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.core.dto.createaccount;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ibm.cics.cip.bank.core.dto.common.EnvelopeKeyJson;

/**
 * Frozen z/OS Connect <em>create-account</em> envelope ({@code CreaccJson}),
 * reproduced from the interface module's class of the same name so the
 * {@code bank-core} module honours the create-account contract byte-for-byte
 * (feature F-019).
 *
 * <p>Nested inside {@link CreateAccountJson} under the {@code CreAcc} key. On a
 * request the meaningful inputs are {@code CommCustno}, {@code CommAccType},
 * {@code CommIntRt} and {@code CommOverdrLim}. On a response {@code bank-core}
 * populates the allocated {@link #commKey key}, the opened/statement dates
 * (integer {@code DDMMYYYY}), the zeroed balances and the
 * {@code CommSuccess}/{@code CommFailCode} status pair.</p>
 *
 * <p><strong>Money types.</strong> Per rule U1 the monetary fields
 * ({@code CommIntRt}, {@code CommAvailBal}, {@code CommActBal}) and the
 * overdraft limit are typed {@link BigDecimal}; Jackson serialises them as JSON
 * numbers, which the legacy {@code float}-typed consumers parse without loss of
 * the contract. No {@code double}/{@code float} is used anywhere.</p>
 */
public class CreaccJson
{

	/** Account type ({@code ISA}/{@code MORTGAGE}/{@code SAVING}/etc.). */
	@JsonProperty("CommAccType")
	private String commAccType;

	/** Owning customer number. */
	@JsonProperty("CommCustno")
	private String commCustno;

	/** Eye-catcher (preserved for wire parity). */
	@JsonProperty("CommEyecatcher")
	private String commEyecatcher = "    ";

	/** Allocated identity (sort code + account number). */
	@JsonProperty("CommKey")
	private EnvelopeKeyJson commKey = new EnvelopeKeyJson();

	/** Interest rate. */
	@JsonProperty("CommIntRt")
	private BigDecimal commInterestRate;

	/** Date opened, integer {@code DDMMYYYY}. */
	@JsonProperty("CommOpened")
	private int commOpened;

	/** Overdraft limit (whole units). */
	@JsonProperty("CommOverdrLim")
	private BigDecimal commOverdraftLimit;

	/** Last-statement date, integer {@code DDMMYYYY}. */
	@JsonProperty("CommLastStmtDt")
	private int commLastStatementDate;

	/** Next-statement date, integer {@code DDMMYYYY}. */
	@JsonProperty("CommNextStmtDt")
	private int commNextStatementDate;

	/** Available balance. */
	@JsonProperty("CommAvailBal")
	private BigDecimal commAvailableBalance;

	/** Actual balance. */
	@JsonProperty("CommActBal")
	private BigDecimal commActualBalance;

	/** Success flag ({@code Y}/{@code N}). */
	@JsonProperty("CommSuccess")
	private String commSuccess;

	/** Single-character fail code; blank/"0" on success. */
	@JsonProperty("CommFailCode")
	private String commFailCode;

	/**
	 * Default constructor for Jackson (de)serialisation.
	 */
	public CreaccJson()
	{
		// Field defaults preserve the legacy envelope's initial values.
	}

	/**
	 * Returns the account type.
	 *
	 * @return the account type
	 */
	public String getCommAccType()
	{
		return commAccType;
	}

	/**
	 * Sets the account type.
	 *
	 * @param commAccType the account type
	 */
	public void setCommAccType(String commAccType)
	{
		this.commAccType = commAccType;
	}

	/**
	 * Returns the owning customer number.
	 *
	 * @return the customer number
	 */
	public String getCommCustno()
	{
		return commCustno;
	}

	/**
	 * Sets the owning customer number.
	 *
	 * @param commCustno the customer number
	 */
	public void setCommCustno(String commCustno)
	{
		this.commCustno = commCustno;
	}

	/**
	 * Returns the eye-catcher.
	 *
	 * @return the eye-catcher
	 */
	public String getCommEyecatcher()
	{
		return commEyecatcher;
	}

	/**
	 * Sets the eye-catcher.
	 *
	 * @param commEyecatcher the eye-catcher
	 */
	public void setCommEyecatcher(String commEyecatcher)
	{
		this.commEyecatcher = commEyecatcher;
	}

	/**
	 * Returns the allocated identity key.
	 *
	 * @return the key
	 */
	public EnvelopeKeyJson getCommKey()
	{
		return commKey;
	}

	/**
	 * Sets the allocated identity key.
	 *
	 * @param commKey the key
	 */
	public void setCommKey(EnvelopeKeyJson commKey)
	{
		this.commKey = commKey;
	}

	/**
	 * Returns the interest rate.
	 *
	 * @return the interest rate
	 */
	public BigDecimal getCommInterestRate()
	{
		return commInterestRate;
	}

	/**
	 * Sets the interest rate.
	 *
	 * @param commInterestRate the interest rate
	 */
	public void setCommInterestRate(BigDecimal commInterestRate)
	{
		this.commInterestRate = commInterestRate;
	}

	/**
	 * Returns the date opened ({@code DDMMYYYY}).
	 *
	 * @return the date opened
	 */
	public int getCommOpened()
	{
		return commOpened;
	}

	/**
	 * Sets the date opened ({@code DDMMYYYY}).
	 *
	 * @param commOpened the date opened
	 */
	public void setCommOpened(int commOpened)
	{
		this.commOpened = commOpened;
	}

	/**
	 * Returns the overdraft limit.
	 *
	 * @return the overdraft limit
	 */
	public BigDecimal getCommOverdraftLimit()
	{
		return commOverdraftLimit;
	}

	/**
	 * Sets the overdraft limit.
	 *
	 * @param commOverdraftLimit the overdraft limit
	 */
	public void setCommOverdraftLimit(BigDecimal commOverdraftLimit)
	{
		this.commOverdraftLimit = commOverdraftLimit;
	}

	/**
	 * Returns the last-statement date ({@code DDMMYYYY}).
	 *
	 * @return the last-statement date
	 */
	public int getCommLastStatementDate()
	{
		return commLastStatementDate;
	}

	/**
	 * Sets the last-statement date ({@code DDMMYYYY}).
	 *
	 * @param commLastStatementDate the last-statement date
	 */
	public void setCommLastStatementDate(int commLastStatementDate)
	{
		this.commLastStatementDate = commLastStatementDate;
	}

	/**
	 * Returns the next-statement date ({@code DDMMYYYY}).
	 *
	 * @return the next-statement date
	 */
	public int getCommNextStatementDate()
	{
		return commNextStatementDate;
	}

	/**
	 * Sets the next-statement date ({@code DDMMYYYY}).
	 *
	 * @param commNextStatementDate the next-statement date
	 */
	public void setCommNextStatementDate(int commNextStatementDate)
	{
		this.commNextStatementDate = commNextStatementDate;
	}

	/**
	 * Returns the available balance.
	 *
	 * @return the available balance
	 */
	public BigDecimal getCommAvailableBalance()
	{
		return commAvailableBalance;
	}

	/**
	 * Sets the available balance.
	 *
	 * @param commAvailableBalance the available balance
	 */
	public void setCommAvailableBalance(BigDecimal commAvailableBalance)
	{
		this.commAvailableBalance = commAvailableBalance;
	}

	/**
	 * Returns the actual balance.
	 *
	 * @return the actual balance
	 */
	public BigDecimal getCommActualBalance()
	{
		return commActualBalance;
	}

	/**
	 * Sets the actual balance.
	 *
	 * @param commActualBalance the actual balance
	 */
	public void setCommActualBalance(BigDecimal commActualBalance)
	{
		this.commActualBalance = commActualBalance;
	}

	/**
	 * Returns the success flag.
	 *
	 * @return the success flag
	 */
	public String getCommSuccess()
	{
		return commSuccess;
	}

	/**
	 * Sets the success flag.
	 *
	 * @param commSuccess the success flag
	 */
	public void setCommSuccess(String commSuccess)
	{
		this.commSuccess = commSuccess;
	}

	/**
	 * Returns the fail code.
	 *
	 * @return the fail code
	 */
	public String getCommFailCode()
	{
		return commFailCode;
	}

	/**
	 * Sets the fail code.
	 *
	 * @param commFailCode the fail code
	 */
	public void setCommFailCode(String commFailCode)
	{
		this.commFailCode = commFailCode;
	}

}
