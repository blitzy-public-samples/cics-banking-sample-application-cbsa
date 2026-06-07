/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.core.dto.updateaccount;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.ibm.cics.cip.bank.core.config.JacksonConfig;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * Inner commarea payload for the frozen z/OS Connect <em>update-account</em>
 * ({@code updacc}) contract. It is the nested object wrapped by
 * {@link UpdateAccountJson} under the {@code UpdAcc} key, reproducing the legacy
 * {@code UPDACC} commarea (copybook {@code UPDACC.cpy}) field-for-field so the
 * serialised JSON matches the frozen z/OS Connect schema verbatim (feature
 * F-019).
 *
 * <p><strong>Single class, request and response.</strong> The frozen
 * {@code updacc} request and response bodies are byte-identical (confirmed
 * against {@code CSaccupdRequest.json} and {@code CSaccupdResponse.json}: the
 * same thirteen keys with the same types), so this single DTO serves both
 * directions. On a request the meaningful inputs are {@code CommAccno},
 * {@code CommAccType}, {@code CommIntRate} and {@code CommOverdraft} (the only
 * attributes {@code UPDACC} changes); on a response {@code bank-core} echoes the
 * full account and sets {@code CommSuccess}.</p>
 *
 * <p><strong>No fail-code field.</strong> Neither {@code UPDACC.cpy} nor either
 * schema declares a fail-code element, so this envelope carries none. Success or
 * failure is signalled solely by {@code CommSuccess}; business failures surface
 * through {@code BusinessRuleException} / the {@code GlobalExceptionHandler}.</p>
 *
 * <h2>Wire-name strategy</h2>
 * <p>The class is annotated {@code @JsonNaming(}{@link
 * JacksonConfig.EnvelopeNamingStrategy}{@code .class)} to carry the frozen
 * envelope naming behaviour forward into the pure-Java module. Every field
 * additionally declares an explicit {@link JsonProperty}; an explicit
 * {@code @JsonProperty} always overrides the {@code substring(3)} naming
 * strategy, so each wire name is pinned verbatim (for example {@code CommEye},
 * {@code CommCustno}, {@code CommScode}, {@code CommAccno}) regardless of the
 * Java field name. The explicit annotations are the contract guarantee.</p>
 *
 * <h2>Wire field types (frozen {@code updacc} schema, F-019)</h2>
 * <ul>
 *   <li><strong>Account number</strong> ({@code CommAccno}) &mdash; serialised
 *       as a JSON {@code integer} ({@code 9(8)} in the copybook) to match the
 *       frozen schema verbatim. The eight-digit value always fits an
 *       {@link Integer}; the mapper supplies it via {@code Integer.parseInt}.</li>
 *   <li><strong>Dates</strong> ({@code CommOpened}, {@code CommLastStmtDt},
 *       {@code CommNextStmtDt}) &mdash; serialised as JSON {@code integer} to
 *       match the frozen schema, carried as the {@code DDMMYYYY} value supplied
 *       by the mapper via {@code DtoFormat.dateToInt}. No date/time object is
 *       stored here.</li>
 *   <li><strong>Customer number / sort code</strong> ({@code CommCustno},
 *       {@code CommScode}) are typed {@code string} in the schema, so they are
 *       carried as left-zero-padded {@code String}s (the AAP &sect;0.6
 *       fixed-width identifier rule applies to fields the contract types as
 *       {@code string}).</li>
 * </ul>
 *
 * <h2>Money and identifier handling</h2>
 * <p>Monetary and rate fields ({@link #commInterestRate},
 * {@link #commAvailableBalance}, {@link #commActualBalance}) are typed
 * {@link BigDecimal} (the money-fidelity rule, AAP &sect;0.6 / ADR-005); the
 * mapper supplies values already normalised to scale&nbsp;2 with
 * {@code RoundingMode.HALF_UP} so the wire shows two decimal places. The
 * overdraft limit is a whole-pounds {@link Integer} (no decimals in COBOL),
 * <em>not</em> money. The account number and the three dates are JSON
 * {@code integer}; the customer number, sort code, account type and success
 * flag are {@code String}.</p>
 *
 * <p><strong>Two independent balances (F-012).</strong>
 * {@link #commAvailableBalance} and {@link #commActualBalance} model cleared
 * versus pending funds and are always kept as two distinct values &mdash; never
 * collapsed into one. {@code UPDACC} itself never mutates either balance; they
 * are echoed for context only.</p>
 *
 * <p><strong>Eye-catcher retained on the wire.</strong> {@code CommEye} is kept
 * because the frozen contract includes it; the AAP &sect;0.6 eye-catcher-removal
 * rule applies to JPA <em>entities</em>, not to this wire DTO.</p>
 *
 * <p><strong>Pure carrier.</strong> This class only stores and returns values:
 * it performs no zero-padding, no date formatting, no {@code BigDecimal} scale
 * normalisation and applies no defaults. Those responsibilities belong to the
 * service/mapper that populates it.</p>
 */
@JsonNaming(JacksonConfig.EnvelopeNamingStrategy.class)
public class UpdaccJson
{

	/** Eye-catcher, {@code X(4)} (retained for wire parity). */
	@JsonProperty("CommEye")
	@Size(max = 4)
	private String commEye;

	/** Owning customer number, {@code X(10)}; left-zero-padded to width 10 by the mapper. */
	@JsonProperty("CommCustno")
	@Size(max = 10)
	private String commCustno;

	/** Sort code, {@code X(6)}; left-zero-padded to width 6 by the mapper. */
	@JsonProperty("CommScode")
	@Size(max = 6)
	private String commSortcode;

	/**
	 * Account number, {@code 9(8)}; serialised as a JSON integer to match the
	 * frozen {@code updacc} schema ({@code CommAccno: integer}, F-019). The
	 * eight-digit value always fits an {@link Integer}.
	 */
	@JsonProperty("CommAccno")
	@Min(0)
	@Max(99999999)
	private Integer commAccno;

	/** Account type, {@code X(8)}; plain {@code String} (e.g. {@code CURRENT}), never an enum. */
	@JsonProperty("CommAccType")
	@Size(max = 8)
	private String commAccountType;

	/** Interest rate, {@code 9(4)V99}; {@link BigDecimal} at scale 2. */
	@JsonProperty("CommIntRate")
	@Digits(integer = 4, fraction = 2)
	@DecimalMin("0")
	@DecimalMax("9999.99")
	private BigDecimal commInterestRate;

	/**
	 * Date opened; {@code DDMMYYYY} encoded as a JSON integer to match the
	 * frozen {@code updacc} schema ({@code CommOpened: integer}, F-019),
	 * supplied by the mapper via {@code DtoFormat.dateToInt}.
	 */
	@JsonProperty("CommOpened")
	@Min(0)
	@Max(99999999)
	private Integer commOpened;

	/** Overdraft limit, {@code 9(8)}; whole pounds as an {@link Integer} (not money). */
	@JsonProperty("CommOverdraft")
	@Min(0)
	@Max(99999999)
	private Integer commOverdraft;

	/**
	 * Last-statement date; {@code DDMMYYYY} encoded as a JSON integer to match
	 * the frozen {@code updacc} schema ({@code CommLastStmtDt: integer}, F-019),
	 * supplied by the mapper via {@code DtoFormat.dateToInt}.
	 */
	@JsonProperty("CommLastStmtDt")
	@Min(0)
	@Max(99999999)
	private Integer commLastStatementDate;

	/**
	 * Next-statement date; {@code DDMMYYYY} encoded as a JSON integer to match
	 * the frozen {@code updacc} schema ({@code CommNextStmtDt: integer}, F-019),
	 * supplied by the mapper via {@code DtoFormat.dateToInt}.
	 */
	@JsonProperty("CommNextStmtDt")
	@Min(0)
	@Max(99999999)
	private Integer commNextStatementDate;

	/**
	 * Available balance, {@code S9(10)V99}; {@link BigDecimal} at scale 2.
	 * Independent from the actual balance and not mutated by {@code UPDACC}
	 * (F-012); echoed for context only.
	 */
	@JsonProperty("CommAvailBal")
	@Digits(integer = 10, fraction = 2)
	@DecimalMin("-9999999999.99")
	@DecimalMax("9999999999.99")
	private BigDecimal commAvailableBalance;

	/**
	 * Actual balance, {@code S9(10)V99}; {@link BigDecimal} at scale 2.
	 * Independent from the available balance &mdash; the two are never collapsed.
	 */
	@JsonProperty("CommActualBal")
	@Digits(integer = 10, fraction = 2)
	@DecimalMin("-9999999999.99")
	@DecimalMax("9999999999.99")
	private BigDecimal commActualBalance;

	/** Success flag, {@code X(1)}. */
	@JsonProperty("CommSuccess")
	@Size(max = 1)
	private String commSuccess;

	/**
	 * Default constructor for Jackson (de)serialisation.
	 */
	public UpdaccJson()
	{
		super();
	}

	/**
	 * All-arguments constructor taking the thirteen fields in copybook
	 * ({@code UPDACC.cpy}) order. Performs simple field assignments only &mdash;
	 * no padding, formatting, scale normalisation or defaulting (the populating
	 * mapper owns those responsibilities).
	 *
	 * @param commEye               eye-catcher
	 * @param commCustno            owning customer number (zero-padded width 10)
	 * @param commSortcode          sort code (zero-padded width 6)
	 * @param commAccno             account number (integer)
	 * @param commAccountType       account type name
	 * @param commInterestRate      interest rate (scale 2)
	 * @param commOpened            date opened ({@code DDMMYYYY} integer)
	 * @param commOverdraft         overdraft limit (whole pounds)
	 * @param commLastStatementDate last-statement date ({@code DDMMYYYY} integer)
	 * @param commNextStatementDate next-statement date ({@code DDMMYYYY} integer)
	 * @param commAvailableBalance  available balance (scale 2)
	 * @param commActualBalance     actual balance (scale 2)
	 * @param commSuccess           success flag
	 */
	public UpdaccJson(String commEye, String commCustno, String commSortcode,
			Integer commAccno, String commAccountType,
			BigDecimal commInterestRate, Integer commOpened,
			Integer commOverdraft, Integer commLastStatementDate,
			Integer commNextStatementDate, BigDecimal commAvailableBalance,
			BigDecimal commActualBalance, String commSuccess)
	{
		this.commEye = commEye;
		this.commCustno = commCustno;
		this.commSortcode = commSortcode;
		this.commAccno = commAccno;
		this.commAccountType = commAccountType;
		this.commInterestRate = commInterestRate;
		this.commOpened = commOpened;
		this.commOverdraft = commOverdraft;
		this.commLastStatementDate = commLastStatementDate;
		this.commNextStatementDate = commNextStatementDate;
		this.commAvailableBalance = commAvailableBalance;
		this.commActualBalance = commActualBalance;
		this.commSuccess = commSuccess;
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
	 * Sets the owning customer number (expected left-zero-padded to width 10).
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
	 * Sets the sort code (expected left-zero-padded to width 6).
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
	 * @return the account number (integer per the frozen contract)
	 */
	public Integer getCommAccno()
	{
		return commAccno;
	}

	/**
	 * Sets the account number.
	 *
	 * @param commAccno the account number (integer per the frozen contract)
	 */
	public void setCommAccno(Integer commAccno)
	{
		this.commAccno = commAccno;
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
	 * Sets the account type (e.g. {@code ISA}, {@code MORTGAGE}, {@code SAVING},
	 * {@code CURRENT}, {@code LOAN}, or empty).
	 *
	 * @param commAccountType the account type
	 */
	public void setCommAccountType(String commAccountType)
	{
		this.commAccountType = commAccountType;
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
	 * Sets the interest rate (expected at scale 2, {@code RoundingMode.HALF_UP}).
	 *
	 * @param commInterestRate the interest rate
	 */
	public void setCommInterestRate(BigDecimal commInterestRate)
	{
		this.commInterestRate = commInterestRate;
	}

	/**
	 * Returns the date opened ({@code DDMMYYYY} integer).
	 *
	 * @return the date opened
	 */
	public Integer getCommOpened()
	{
		return commOpened;
	}

	/**
	 * Sets the date opened (expected as a {@code DDMMYYYY} integer produced by
	 * {@code DtoFormat.dateToInt}).
	 *
	 * @param commOpened the date opened
	 */
	public void setCommOpened(Integer commOpened)
	{
		this.commOpened = commOpened;
	}

	/**
	 * Returns the overdraft limit (whole pounds).
	 *
	 * @return the overdraft limit
	 */
	public Integer getCommOverdraft()
	{
		return commOverdraft;
	}

	/**
	 * Sets the overdraft limit (whole pounds).
	 *
	 * @param commOverdraft the overdraft limit
	 */
	public void setCommOverdraft(Integer commOverdraft)
	{
		this.commOverdraft = commOverdraft;
	}

	/**
	 * Returns the last-statement date ({@code DDMMYYYY} integer).
	 *
	 * @return the last-statement date
	 */
	public Integer getCommLastStatementDate()
	{
		return commLastStatementDate;
	}

	/**
	 * Sets the last-statement date (expected as a {@code DDMMYYYY} integer
	 * produced by {@code DtoFormat.dateToInt}).
	 *
	 * @param commLastStatementDate the last-statement date
	 */
	public void setCommLastStatementDate(Integer commLastStatementDate)
	{
		this.commLastStatementDate = commLastStatementDate;
	}

	/**
	 * Returns the next-statement date ({@code DDMMYYYY} integer).
	 *
	 * @return the next-statement date
	 */
	public Integer getCommNextStatementDate()
	{
		return commNextStatementDate;
	}

	/**
	 * Sets the next-statement date (expected as a {@code DDMMYYYY} integer
	 * produced by {@code DtoFormat.dateToInt}).
	 *
	 * @param commNextStatementDate the next-statement date
	 */
	public void setCommNextStatementDate(Integer commNextStatementDate)
	{
		this.commNextStatementDate = commNextStatementDate;
	}

	/**
	 * Returns the available balance. Independent from the actual balance.
	 *
	 * @return the available balance
	 */
	public BigDecimal getCommAvailableBalance()
	{
		return commAvailableBalance;
	}

	/**
	 * Sets the available balance (expected at scale 2). Independent from the
	 * actual balance &mdash; never collapse the two.
	 *
	 * @param commAvailableBalance the available balance
	 */
	public void setCommAvailableBalance(BigDecimal commAvailableBalance)
	{
		this.commAvailableBalance = commAvailableBalance;
	}

	/**
	 * Returns the actual balance. Independent from the available balance.
	 *
	 * @return the actual balance
	 */
	public BigDecimal getCommActualBalance()
	{
		return commActualBalance;
	}

	/**
	 * Sets the actual balance (expected at scale 2). Independent from the
	 * available balance &mdash; never collapse the two.
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
	 * Returns a diagnostic representation listing all thirteen fields in
	 * copybook order. Not part of the wire contract.
	 *
	 * @return a string representation of this payload
	 */
	@Override
	public String toString()
	{
		return "UpdaccJson [CommEye=" + commEye + ", CommCustno=" + commCustno
				+ ", CommScode=" + commSortcode + ", CommAccno=" + commAccno
				+ ", CommAccType=" + commAccountType + ", CommIntRate="
				+ commInterestRate + ", CommOpened=" + commOpened
				+ ", CommOverdraft=" + commOverdraft + ", CommLastStmtDt="
				+ commLastStatementDate + ", CommNextStmtDt="
				+ commNextStatementDate + ", CommAvailBal="
				+ commAvailableBalance + ", CommActualBal=" + commActualBalance
				+ ", CommSuccess=" + commSuccess + "]";
	}

}
