/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.core.dto.payment;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Frozen z/OS Connect <em>debit/credit</em> envelope ({@code DbcrJson}),
 * reproduced field-for-field from the Payment-Interface module's class of the
 * same name (feature F-019). Nested inside {@link PaymentInterfaceJson} under
 * the {@code PAYDBCR} key on both request and response.
 *
 * <p><strong>Fail-code semantics (critical for parity).</strong> The consumer
 * validates the response with
 * {@code Integer.parseInt(getPAYDBCR().getCommFailCode())}, so {@code bank-core}
 * <em>must</em> emit a numeric {@code CommFailCode} on every response:
 * {@code "0"} for success, {@code "1"} account-not-found, {@code "3"}
 * insufficient funds, {@code "4"} restricted/invalid account type. A blank value
 * would raise {@code NumberFormatException} on the consumer.</p>
 *
 * <p><strong>Sign convention.</strong> {@code CommAmt} is signed &mdash; a
 * negative amount is a debit (COBOL types {@code DEB}/{@code PDR}), a positive
 * amount a credit ({@code CRE}/{@code PCR}).</p>
 *
 * <p>Monetary fields ({@code CommAmt}, {@code CommAvBal}, {@code CommActBal}) are
 * typed {@link BigDecimal} (rule U1, replacing the legacy {@code float}/{@code int});
 * the response carries the post-transaction balances. The {@code mSortC} JSON
 * key is preserved exactly as in the legacy contract (it is <em>not</em> the
 * usual {@code CommSortcode} envelope name).</p>
 */
public class DbcrJson
{

	/** Account number, zero-padded to width 8. */
	@JsonProperty("CommAccno")
	private String commAccno;

	/** Signed amount; negative = debit, positive = credit. */
	@JsonProperty("CommAmt")
	private BigDecimal commAmt;

	/** Sort code (legacy JSON key {@code mSortC}). */
	@JsonProperty("mSortC")
	private int commSortC = 0;

	/** Available balance after the movement. */
	@JsonProperty("CommAvBal")
	private BigDecimal commAvBal = BigDecimal.ZERO;

	/** Actual balance after the movement. */
	@JsonProperty("CommActBal")
	private BigDecimal commActBal = BigDecimal.ZERO;

	/** Calling-channel origin (carries facility type and origin string). */
	@JsonProperty("CommOrigin")
	private OriginJson commOrigin;

	/** Success flag ({@code Y}/{@code N}); defaults to a space for wire parity. */
	@JsonProperty("CommSuccess")
	private String commSuccess = " ";

	/**
	 * Fail code; must be numeric on a response ({@code "0"} = success). Defaults
	 * to a space, matching the legacy request-side envelope.
	 */
	@JsonProperty("CommFailCode")
	private String commFailCode = " ";

	/**
	 * Default constructor for Jackson (de)serialisation.
	 */
	public DbcrJson()
	{
		// Field defaults preserve the legacy envelope's initial values.
	}

	/**
	 * Returns the account number (zero-padded to width 8).
	 *
	 * @return the account number
	 */
	public String getCommAccno()
	{
		return commAccno;
	}

	/**
	 * Sets the account number.
	 *
	 * @param commAccnoIn the account number
	 */
	public void setCommAccno(String commAccnoIn)
	{
		commAccno = commAccnoIn;
	}

	/**
	 * Returns the signed amount.
	 *
	 * @return the signed amount
	 */
	public BigDecimal getCommAmt()
	{
		return commAmt;
	}

	/**
	 * Sets the signed amount.
	 *
	 * @param commAmtIn the signed amount
	 */
	public void setCommAmt(BigDecimal commAmtIn)
	{
		commAmt = commAmtIn;
	}

	/**
	 * Returns the sort code.
	 *
	 * @return the sort code
	 */
	public int getCommSortC()
	{
		return commSortC;
	}

	/**
	 * Sets the sort code.
	 *
	 * @param commSortCodeIn the sort code
	 */
	public void setCommSortC(int commSortCodeIn)
	{
		commSortC = commSortCodeIn;
	}

	/**
	 * Returns the available balance after the movement.
	 *
	 * @return the available balance
	 */
	public BigDecimal getCommAvBal()
	{
		return commAvBal;
	}

	/**
	 * Sets the available balance after the movement.
	 *
	 * @param commAvBalIn the available balance
	 */
	public void setCommAvBal(BigDecimal commAvBalIn)
	{
		commAvBal = commAvBalIn;
	}

	/**
	 * Returns the actual balance after the movement.
	 *
	 * @return the actual balance
	 */
	public BigDecimal getCommActBal()
	{
		return commActBal;
	}

	/**
	 * Sets the actual balance after the movement.
	 *
	 * @param commActBalIn the actual balance
	 */
	public void setCommActBal(BigDecimal commActBalIn)
	{
		commActBal = commActBalIn;
	}

	/**
	 * Returns the calling-channel origin.
	 *
	 * @return the origin
	 */
	public OriginJson getCommOrigin()
	{
		return commOrigin;
	}

	/**
	 * Sets the calling-channel origin.
	 *
	 * @param commOriginIn the origin
	 */
	public void setCommOrigin(OriginJson commOriginIn)
	{
		commOrigin = commOriginIn;
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
	 * @param commSuccessIn the success flag
	 */
	public void setCommSuccess(String commSuccessIn)
	{
		commSuccess = commSuccessIn;
	}

	/**
	 * Returns the fail code (numeric on a response; {@code "0"} = success).
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
	 * @param commFailCodeIn the fail code
	 */
	public void setCommFailCode(String commFailCodeIn)
	{
		commFailCode = commFailCodeIn;
	}

}
