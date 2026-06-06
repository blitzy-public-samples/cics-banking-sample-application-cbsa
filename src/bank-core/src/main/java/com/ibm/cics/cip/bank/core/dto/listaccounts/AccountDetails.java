/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.core.dto.listaccounts;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.ibm.cics.cip.bank.core.config.JacksonConfig;

/**
 * One account row of the frozen z/OS Connect <em>list-customer-accounts</em>
 * ({@code inqacccz}) {@code AccountDetails} OCCURS array. It is the repeating
 * element held in a {@code List<AccountDetails>} by the sibling
 * {@code InqAccczJson} (the {@code ACCOUNT-DETAILS OCCURS 1 TO 20} group of
 * copybook {@code INQACCCZ.cpy} / {@code INQACCCU.cpy}, capped at twenty rows),
 * reproducing the legacy account-detail layout field-for-field so the
 * serialised JSON of each array element matches the frozen z/OS Connect schema
 * verbatim (feature F-019).
 *
 * <h2>Wire-name strategy</h2>
 * <p>The class is annotated {@code @JsonNaming(}{@link
 * JacksonConfig.EnvelopeNamingStrategy}{@code .class)} to carry the frozen
 * envelope naming behaviour forward into {@code bank-core}. Every field
 * additionally declares an explicit {@link JsonProperty}; an explicit
 * {@code @JsonProperty} always overrides the {@code substring(3)} naming
 * strategy, so each wire name is pinned verbatim (e.g. {@code CommEye},
 * {@code CommCustno}, {@code CommAccno}, {@code CommIntRate},
 * {@code CommAvailBal}) regardless of the Java field name.</p>
 *
 * <h2>Field count and the dropped per-row sort code</h2>
 * <p>The element carries exactly <strong>eleven</strong> wire fields. The COBOL
 * copybook also defines a per-row sort code ({@code COMM-SCODE}, {@code X(6)}),
 * but it is intentionally <strong>omitted</strong> here: it is commented out in
 * the legacy interface-module Java client and is absent from the frozen
 * {@code inqacccz} swagger {@code AccountDetails} item schema (verified). Adding
 * such a per-row sort-code field would inject a non-contract property into every
 * account row, so it is dropped.</p>
 *
 * <h2>Eye-catcher retained</h2>
 * <p>{@code CommEye} is retained as a {@code String} wire field because the
 * frozen contract includes it ({@code string}, {@code maxLength 4}). The AAP
 * &sect;0.6 eye-catcher-removal rule targets the JPA <em>entities</em>, not this
 * frozen wire contract.</p>
 *
 * <h2>Documented representation divergences (AAP &sect;0.6)</h2>
 * <ul>
 *   <li><strong>Account number</strong> ({@code CommAccno}) &mdash; the raw
 *       z/OS Connect schema types this as a JSON {@code integer}, but
 *       {@code bank-core} represents it as a left-zero-padded {@code String}
 *       (fixed-width identifier rule). The mapper pads to width eight before
 *       setting it.</li>
 *   <li><strong>Dates</strong> ({@code CommOpened}, {@code CommLastStmtDt},
 *       {@code CommNextStmtDt}) &mdash; the raw schema types these as
 *       {@code integer}, but {@code bank-core} carries them as the formatted
 *       account-date {@code String} ({@code DD/MM/YYYY}) supplied by the
 *       populating mapper. No date/time object is stored here.</li>
 *   <li><strong>Customer number</strong> ({@code CommCustno}) is already
 *       {@code string} in the schema &mdash; no type divergence, only a
 *       width-ten left-zero-pad applied by the mapper.</li>
 * </ul>
 *
 * <h2>Money and identifier handling</h2>
 * <p>Monetary fields ({@link #commInterestRate}, {@link #commAvailableBalance},
 * {@link #commActualBalance}) are typed {@link BigDecimal} (the money-fidelity
 * rule, AAP &sect;0.6 / ADR-005); the mapper supplies values already normalised
 * to scale&nbsp;2 with {@code RoundingMode.HALF_UP} so the wire shows two
 * decimal places. The available and actual balances are <strong>independent</strong>
 * (cleared vs. pending funds) and are never collapsed into a single value. The
 * overdraft limit is a whole-pounds {@link Integer} (no decimals in COBOL),
 * <em>not</em> money. This DTO is a pure carrier: date formatting, zero-padding
 * and {@code BigDecimal} scale normalisation are performed by the
 * service/mapper that populates it, never inside this class.</p>
 */
@JsonNaming(JacksonConfig.EnvelopeNamingStrategy.class)
public class AccountDetails
{

	/** Eye-catcher, {@code X(4)} (retained for wire parity). */
	@JsonProperty("CommEye")
	private String commEye;

	/** Owning customer number, {@code X(10)}; left-zero-padded to width 10 by the mapper. */
	@JsonProperty("CommCustno")
	private String commCustno;

	/**
	 * Account number, {@code 9(8)}; left-zero-padded to width 8 by the mapper.
	 * Represented as a {@code String} per AAP &sect;0.6 (the raw schema types it
	 * as a JSON integer &mdash; documented divergence).
	 */
	@JsonProperty("CommAccno")
	private String commAccno;

	/** Account type, {@code X(8)}; plain {@code String} (e.g. {@code CURRENT}), never an enum. */
	@JsonProperty("CommAccType")
	private String commAccType;

	/** Interest rate, {@code 9(4)V99}; {@link BigDecimal} at scale 2. */
	@JsonProperty("CommIntRate")
	private BigDecimal commInterestRate;

	/**
	 * Date opened; formatted account-date {@code String} ({@code DD/MM/YYYY})
	 * supplied by the mapper (raw schema types it integer &mdash; documented
	 * divergence).
	 */
	@JsonProperty("CommOpened")
	private String commOpened;

	/** Overdraft limit, {@code 9(8)}; whole pounds as an {@link Integer} (not money). */
	@JsonProperty("CommOverdraft")
	private Integer commOverdraft;

	/**
	 * Last-statement date; formatted account-date {@code String}
	 * ({@code DD/MM/YYYY}) supplied by the mapper (raw schema types it integer
	 * &mdash; documented divergence).
	 */
	@JsonProperty("CommLastStmtDt")
	private String commLastStatementDate;

	/**
	 * Next-statement date; formatted account-date {@code String}
	 * ({@code DD/MM/YYYY}) supplied by the mapper (raw schema types it integer
	 * &mdash; documented divergence).
	 */
	@JsonProperty("CommNextStmtDt")
	private String commNextStatementDate;

	/**
	 * Available (cleared) balance, {@code S9(10)V99}; {@link BigDecimal} at
	 * scale 2. Independent from the actual balance.
	 */
	@JsonProperty("CommAvailBal")
	private BigDecimal commAvailableBalance;

	/**
	 * Actual balance, {@code S9(10)V99}; {@link BigDecimal} at scale 2.
	 * Independent from the available balance.
	 */
	@JsonProperty("CommActualBal")
	private BigDecimal commActualBalance;

	/**
	 * Default constructor for Jackson (de)serialisation.
	 */
	public AccountDetails()
	{
		super();
	}

	/**
	 * All-arguments constructor taking the eleven fields in copybook
	 * ({@code INQACCCZ.cpy} {@code ACCOUNT-DETAILS}) order.
	 *
	 * @param commEye               eye-catcher
	 * @param commCustno            owning customer number (zero-padded width 10)
	 * @param commAccno             account number (zero-padded width 8)
	 * @param commAccType           account type name
	 * @param commInterestRate      interest rate (scale 2)
	 * @param commOpened            date opened (formatted {@code DD/MM/YYYY} string)
	 * @param commOverdraft         overdraft limit (whole pounds)
	 * @param commLastStatementDate last-statement date (formatted {@code DD/MM/YYYY} string)
	 * @param commNextStatementDate next-statement date (formatted {@code DD/MM/YYYY} string)
	 * @param commAvailableBalance  available balance (scale 2)
	 * @param commActualBalance     actual balance (scale 2)
	 */
	public AccountDetails(String commEye, String commCustno, String commAccno,
			String commAccType, BigDecimal commInterestRate, String commOpened,
			Integer commOverdraft, String commLastStatementDate,
			String commNextStatementDate, BigDecimal commAvailableBalance,
			BigDecimal commActualBalance)
	{
		this.commEye = commEye;
		this.commCustno = commCustno;
		this.commAccno = commAccno;
		this.commAccType = commAccType;
		this.commInterestRate = commInterestRate;
		this.commOpened = commOpened;
		this.commOverdraft = commOverdraft;
		this.commLastStatementDate = commLastStatementDate;
		this.commNextStatementDate = commNextStatementDate;
		this.commAvailableBalance = commAvailableBalance;
		this.commActualBalance = commActualBalance;
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
	 * Returns the account number.
	 *
	 * @return the account number
	 */
	public String getCommAccno()
	{
		return commAccno;
	}

	/**
	 * Sets the account number (expected left-zero-padded to width 8).
	 *
	 * @param commAccno the account number
	 */
	public void setCommAccno(String commAccno)
	{
		this.commAccno = commAccno;
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
	 * Sets the account type (e.g. {@code ISA}, {@code MORTGAGE}, {@code SAVING},
	 * {@code CURRENT}, {@code LOAN}, or empty).
	 *
	 * @param commAccType the account type
	 */
	public void setCommAccType(String commAccType)
	{
		this.commAccType = commAccType;
	}

	/**
	 * Returns the interest rate.
	 *
	 * @return the interest rate (scale 2)
	 */
	public BigDecimal getCommInterestRate()
	{
		return commInterestRate;
	}

	/**
	 * Sets the interest rate (expected at scale 2 with {@code HALF_UP}).
	 *
	 * @param commInterestRate the interest rate
	 */
	public void setCommInterestRate(BigDecimal commInterestRate)
	{
		this.commInterestRate = commInterestRate;
	}

	/**
	 * Returns the date opened (formatted {@code DD/MM/YYYY} string).
	 *
	 * @return the date opened
	 */
	public String getCommOpened()
	{
		return commOpened;
	}

	/**
	 * Sets the date opened (expected formatted {@code DD/MM/YYYY}).
	 *
	 * @param commOpened the date opened
	 */
	public void setCommOpened(String commOpened)
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
	 * Sets the overdraft limit (whole pounds, not money).
	 *
	 * @param commOverdraft the overdraft limit
	 */
	public void setCommOverdraft(Integer commOverdraft)
	{
		this.commOverdraft = commOverdraft;
	}

	/**
	 * Returns the last-statement date (formatted {@code DD/MM/YYYY} string).
	 *
	 * @return the last-statement date
	 */
	public String getCommLastStatementDate()
	{
		return commLastStatementDate;
	}

	/**
	 * Sets the last-statement date (expected formatted {@code DD/MM/YYYY}).
	 *
	 * @param commLastStatementDate the last-statement date
	 */
	public void setCommLastStatementDate(String commLastStatementDate)
	{
		this.commLastStatementDate = commLastStatementDate;
	}

	/**
	 * Returns the next-statement date (formatted {@code DD/MM/YYYY} string).
	 *
	 * @return the next-statement date
	 */
	public String getCommNextStatementDate()
	{
		return commNextStatementDate;
	}

	/**
	 * Sets the next-statement date (expected formatted {@code DD/MM/YYYY}).
	 *
	 * @param commNextStatementDate the next-statement date
	 */
	public void setCommNextStatementDate(String commNextStatementDate)
	{
		this.commNextStatementDate = commNextStatementDate;
	}

	/**
	 * Returns the available (cleared) balance.
	 *
	 * @return the available balance (scale 2)
	 */
	public BigDecimal getCommAvailableBalance()
	{
		return commAvailableBalance;
	}

	/**
	 * Sets the available (cleared) balance (expected at scale 2 with
	 * {@code HALF_UP}). Independent from the actual balance.
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
	 * @return the actual balance (scale 2)
	 */
	public BigDecimal getCommActualBalance()
	{
		return commActualBalance;
	}

	/**
	 * Sets the actual balance (expected at scale 2 with {@code HALF_UP}).
	 * Independent from the available balance.
	 *
	 * @param commActualBalance the actual balance
	 */
	public void setCommActualBalance(BigDecimal commActualBalance)
	{
		this.commActualBalance = commActualBalance;
	}

	/**
	 * Returns a diagnostic representation listing all eleven fields in copybook
	 * order. Not part of the wire contract.
	 *
	 * @return a string representation of this account row
	 */
	@Override
	public String toString()
	{
		return "AccountDetails [CommEye=" + commEye + ", CommCustno="
				+ commCustno + ", CommAccno=" + commAccno + ", CommAccType="
				+ commAccType + ", CommIntRate=" + commInterestRate
				+ ", CommOpened=" + commOpened + ", CommOverdraft="
				+ commOverdraft + ", CommLastStmtDt=" + commLastStatementDate
				+ ", CommNextStmtDt=" + commNextStatementDate
				+ ", CommAvailBal=" + commAvailableBalance + ", CommActualBal="
				+ commActualBalance + "]";
	}

}
