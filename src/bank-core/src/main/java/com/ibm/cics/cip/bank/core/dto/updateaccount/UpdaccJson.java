/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.core.dto.updateaccount;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Frozen z/OS Connect <em>update-account</em> envelope ({@code UpdaccJson}),
 * reproduced from the interface module's class of the same name (feature
 * F-019). Nested inside {@link UpdateAccountJson} under the {@code UpdAcc} key.
 *
 * <p>On a request the meaningful inputs are {@code CommAccno},
 * {@code CommAccType}, {@code CommIntRate} and {@code CommOverdraft} (the only
 * fields {@code UPDACC} changes; balances are never altered). On a response
 * {@code bank-core} echoes the account and sets {@code CommSuccess}. This
 * envelope carries no fail-code field &mdash; success/failure is signalled
 * solely by {@code CommSuccess} ({@code Y}/{@code N}), matching the legacy
 * contract.</p>
 *
 * <p>Monetary fields are typed {@link BigDecimal} (rule U1); statement dates are
 * {@code DDMMYYYY} strings.</p>
 */
public class UpdaccJson
{

	/** Eye-catcher (preserved for wire parity). */
	@JsonProperty("CommEye")
	private String commEye = "    ";

	/** Owning customer number. */
	@JsonProperty("CommCustno")
	private String commCustno;

	/** Sort code. */
	@JsonProperty("CommScode")
	private String commSortcode;

	/** Account number. */
	@JsonProperty("CommAccno")
	private int commAccno;

	/** Interest rate. */
	@JsonProperty("CommIntRate")
	private BigDecimal commInterestRate;

	/** Date opened, {@code DDMMYYYY} string. */
	@JsonProperty("CommOpened")
	private String commOpened;

	/** Overdraft limit (whole units). */
	@JsonProperty("CommOverdraft")
	private int commOverdraft;

	/** Last-statement date, {@code DDMMYYYY} string. */
	@JsonProperty("CommLastStmtDt")
	private String commLastStatementDate;

	/** Next-statement date, {@code DDMMYYYY} string. */
	@JsonProperty("CommNextStmtDt")
	private String commNextStatementDate;

	/** Available balance. */
	@JsonProperty("CommAvailBal")
	private BigDecimal commAvailableBalance;

	/** Actual balance. */
	@JsonProperty("CommActualBal")
	private BigDecimal commActualBalance;

	/** Success flag ({@code Y}/{@code N}). */
	@JsonProperty("CommSuccess")
	private String commSuccess;

	/** Account type. */
	@JsonProperty("CommAccType")
	private String commAccountType = "        ";

	/**
	 * Default constructor for Jackson (de)serialisation.
	 */
	public UpdaccJson()
	{
		// Field defaults preserve the legacy envelope's initial values.
	}

	/**
	 * Returns the eye-catcher.
	 *
	 * @return the eye-catcher
	 */
	public String getCommEye()
	{
		return commEye;
	}

	/**
	 * Sets the eye-catcher.
	 *
	 * @param commEye the eye-catcher
	 */
	public void setCommEye(String commEye)
	{
		this.commEye = commEye;
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
	 * Returns the sort code.
	 *
	 * @return the sort code
	 */
	public String getCommSortcode()
	{
		return commSortcode;
	}

	/**
	 * Sets the sort code.
	 *
	 * @param commSortcode the sort code
	 */
	public void setCommSortcode(String commSortcode)
	{
		this.commSortcode = commSortcode;
	}

	/**
	 * Returns the account number.
	 *
	 * @return the account number
	 */
	public int getCommAccno()
	{
		return commAccno;
	}

	/**
	 * Sets the account number.
	 *
	 * @param commAccno the account number
	 */
	public void setCommAccno(int commAccno)
	{
		this.commAccno = commAccno;
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
	public String getCommOpened()
	{
		return commOpened;
	}

	/**
	 * Sets the date opened ({@code DDMMYYYY}).
	 *
	 * @param commOpened the date opened
	 */
	public void setCommOpened(String commOpened)
	{
		this.commOpened = commOpened;
	}

	/**
	 * Returns the overdraft limit.
	 *
	 * @return the overdraft limit
	 */
	public int getCommOverdraft()
	{
		return commOverdraft;
	}

	/**
	 * Sets the overdraft limit.
	 *
	 * @param commOverdraft the overdraft limit
	 */
	public void setCommOverdraft(int commOverdraft)
	{
		this.commOverdraft = commOverdraft;
	}

	/**
	 * Returns the last-statement date ({@code DDMMYYYY}).
	 *
	 * @return the last-statement date
	 */
	public String getCommLastStatementDate()
	{
		return commLastStatementDate;
	}

	/**
	 * Sets the last-statement date ({@code DDMMYYYY}).
	 *
	 * @param commLastStatementDate the last-statement date
	 */
	public void setCommLastStatementDate(String commLastStatementDate)
	{
		this.commLastStatementDate = commLastStatementDate;
	}

	/**
	 * Returns the next-statement date ({@code DDMMYYYY}).
	 *
	 * @return the next-statement date
	 */
	public String getCommNextStatementDate()
	{
		return commNextStatementDate;
	}

	/**
	 * Sets the next-statement date ({@code DDMMYYYY}).
	 *
	 * @param commNextStatementDate the next-statement date
	 */
	public void setCommNextStatementDate(String commNextStatementDate)
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
	 * Returns the account type.
	 *
	 * @return the account type
	 */
	public String getCommAccountType()
	{
		return commAccountType;
	}

	/**
	 * Sets the account type.
	 *
	 * @param commAccountType the account type
	 */
	public void setCommAccountType(String commAccountType)
	{
		this.commAccountType = commAccountType;
	}

}
