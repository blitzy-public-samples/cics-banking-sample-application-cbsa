/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.core.dto.deleteaccount;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.ibm.cics.cip.bank.core.config.JacksonConfig;

/**
 * Inner commarea payload for the frozen z/OS Connect <em>delete-account</em>
 * ({@code delacc}) contract. It is the nested object wrapped by
 * {@link DeleteAccountJson} under the {@code DelAcc} key, reproducing the legacy
 * {@code DELACC-COMMAREA} (copybook {@code DELACC.cpy}) field-for-field so the
 * serialised JSON matches the frozen z/OS Connect schema verbatim (feature
 * F-019).
 *
 * <p><strong>Single class, request and response.</strong> Exactly as the legacy
 * interface-module class did, this single DTO serves both directions of the
 * contract: the request travels with the account number as the
 * {@code /delacc/remove/{accno}} path parameter (so {@code DelAccAccno} is
 * omitted from the request body), while the response echoes the full deleted
 * account including {@code DelAccAccno}. All twenty fields are therefore
 * declared so the class covers the response superset.</p>
 *
 * <p><strong>Terminal balances (F-013).</strong> {@code DELACC.cbl} captures the
 * account's closing state into an account-close PROCTRAN record before the row
 * is physically removed, so {@link #delaccAvailableBalance} and
 * {@link #delaccActualBalance} carry the account's <em>terminal</em> (closing)
 * available and actual balances at delete time. The two balances are
 * <strong>independent</strong> (cleared vs. pending funds, AAP &sect;0.6) and are
 * never collapsed into a single value.
 *
 * <h2>Wire-name strategy</h2>
 * <p>The class is annotated {@code @JsonNaming(}{@link
 * JacksonConfig.EnvelopeNamingStrategy}{@code .class)} to carry the frozen
 * envelope naming behaviour forward. Every field additionally declares an
 * explicit {@link JsonProperty}; an explicit {@code @JsonProperty} always
 * overrides the {@code substring(3)} naming strategy, so each wire name is
 * pinned verbatim (e.g. {@code DelAccEye}, {@code DelAccCustno},
 * {@code DelAccAccno}) regardless of the Java field name.</p>
 *
 * <h2>Documented representation divergences (AAP &sect;0.6)</h2>
 * <ul>
 *   <li><strong>Account number</strong> ({@code DelAccAccno}) &mdash; the raw
 *       z/OS Connect response schema types this as a JSON {@code integer}, but
 *       {@code bank-core} represents it as a left-zero-padded {@code String}
 *       (fixed-width identifier rule). The mapper pads to width eight before
 *       setting it.</li>
 *   <li><strong>Dates</strong> ({@code DelAccOpened}, {@code DelAccLastStmtDt},
 *       {@code DelAccNextStmtDt}) &mdash; the raw schema types these as
 *       {@code integer}, but {@code bank-core} carries them as the formatted
 *       account-date {@code String} produced by the populating mapper
 *       ({@code DtoFormat}); the legacy Java client likewise used
 *       {@code String}. No date/time object is stored here.</li>
 *   <li><strong>Fail codes</strong> ({@code DelAccFailCd},
 *       {@code DelAccDelFailCd}) &mdash; the schema types these
 *       {@code string(1)}; they carry the single-character COBOL fail codes
 *       (the legacy Java client incorrectly used {@code int}).</li>
 * </ul>
 *
 * <h2>Money and identifier handling</h2>
 * <p>Monetary fields ({@link #delaccInterestRate},
 * {@link #delaccAvailableBalance}, {@link #delaccActualBalance}) are typed
 * {@link BigDecimal} (the money-fidelity rule, AAP &sect;0.6 / ADR-005); the mapper
 * supplies values already normalised to scale&nbsp;2 with
 * {@code RoundingMode.HALF_UP} so the wire shows two decimal places. The
 * overdraft limit is a whole-pounds {@link Integer} (no decimals in COBOL),
 * <em>not</em> money. This DTO is a pure carrier: date formatting, zero-padding
 * and {@code BigDecimal} scale normalisation are performed by the
 * service/mapper that populates it, never inside this class.</p>
 *
 * <h2>PCB fields retained</h2>
 * <p>{@code DelAccDelPcb1}, {@code DelAccDelPcb2} and {@code DelAccDelPcb3} are
 * retained as {@code String} placeholders because the frozen {@code delacc}
 * swagger includes them as {@code string} ({@code maxLength 4}) in both the
 * request and response definitions (verified). The AAP &sect;0.6 PCB /
 * eye-catcher removal rule targets JPA <em>entities</em>, not this frozen wire
 * contract, so {@code DelAccEye} is likewise retained.</p>
 */
@JsonNaming(JacksonConfig.EnvelopeNamingStrategy.class)
public class DelaccJson
{

	/** Eye-catcher, {@code X(4)} (retained for wire parity). */
	@JsonProperty("DelAccEye")
	private String delaccEye;

	/** Owning customer number, {@code X(10)}; left-zero-padded to width 10 by the mapper. */
	@JsonProperty("DelAccCustno")
	private String delaccCustno;

	/** Sort code, {@code X(6)}; left-zero-padded to width 6 by the mapper. */
	@JsonProperty("DelAccScode")
	private String delaccSortcode;

	/**
	 * Account number, {@code 9(8)}; left-zero-padded to width 8 by the mapper.
	 * Represented as a {@code String} per AAP &sect;0.6 (the raw response schema
	 * types it as a JSON integer &mdash; documented divergence).
	 */
	@JsonProperty("DelAccAccno")
	private String delaccAccno;

	/** Account type, {@code X(8)}; plain {@code String} (e.g. {@code CURRENT}), never an enum. */
	@JsonProperty("DelAccAccType")
	private String delaccAccType;

	/** Interest rate, {@code 9(4)V99}; {@link BigDecimal} at scale 2. */
	@JsonProperty("DelAccIntRate")
	private BigDecimal delaccInterestRate;

	/**
	 * Date opened; formatted account-date {@code String} supplied by the mapper
	 * (raw schema types it integer &mdash; documented divergence).
	 */
	@JsonProperty("DelAccOpened")
	private String delaccOpened;

	/** Overdraft limit, {@code 9(8)}; whole pounds as an {@link Integer} (not money). */
	@JsonProperty("DelAccOverdraft")
	private Integer delaccOverdraft;

	/**
	 * Last-statement date; formatted account-date {@code String} supplied by the
	 * mapper (raw schema types it integer &mdash; documented divergence).
	 */
	@JsonProperty("DelAccLastStmtDt")
	private String delaccLastStatementDate;

	/**
	 * Next-statement date; formatted account-date {@code String} supplied by the
	 * mapper (raw schema types it integer &mdash; documented divergence).
	 */
	@JsonProperty("DelAccNextStmtDt")
	private String delaccNextStatementDate;

	/**
	 * Terminal (closing) available balance at delete time, {@code S9(10)V99};
	 * {@link BigDecimal} at scale 2. Independent from the actual balance.
	 */
	@JsonProperty("DelAccAvailBal")
	private BigDecimal delaccAvailableBalance;

	/**
	 * Terminal (closing) actual balance at delete time, {@code S9(10)V99};
	 * {@link BigDecimal} at scale 2. Independent from the available balance.
	 */
	@JsonProperty("DelAccActualBal")
	private BigDecimal delaccActualBalance;

	/** Success flag, {@code X(1)}. */
	@JsonProperty("DelAccSuccess")
	private String delaccSuccess;

	/**
	 * Primary fail code (single character), {@code X(1)}; carries the COBOL
	 * {@code DELACC} fail code. Bound to the wire name {@code DelAccFailCd}.
	 */
	@JsonProperty("DelAccFailCd")
	private String delaccFailCode;

	/** Secondary delete success flag, {@code X(1)}. */
	@JsonProperty("DelAccDelSuccess")
	private String delaccDelSuccess;

	/**
	 * Secondary delete fail code (single character), {@code X(1)}. Bound to the
	 * wire name {@code DelAccDelFailCd}.
	 */
	@JsonProperty("DelAccDelFailCd")
	private String delaccDelFailCode;

	/** Applid, {@code X(8)}. */
	@JsonProperty("DelAccDelApplid")
	private String delaccDelApplid;

	/** PCB pointer slot 1; {@code String} placeholder (frozen contract {@code string(4)}). */
	@JsonProperty("DelAccDelPcb1")
	private String delaccDelPcb1;

	/** PCB pointer slot 2; {@code String} placeholder (frozen contract {@code string(4)}). */
	@JsonProperty("DelAccDelPcb2")
	private String delaccDelPcb2;

	/** PCB pointer slot 3; {@code String} placeholder (frozen contract {@code string(4)}). */
	@JsonProperty("DelAccDelPcb3")
	private String delaccDelPcb3;

	/**
	 * Default constructor for Jackson (de)serialisation.
	 */
	public DelaccJson()
	{
		super();
	}

	/**
	 * All-arguments constructor taking the twenty fields in copybook
	 * ({@code DELACC.cpy}) order.
	 *
	 * @param delaccEye               eye-catcher
	 * @param delaccCustno            owning customer number (zero-padded width 10)
	 * @param delaccSortcode          sort code (zero-padded width 6)
	 * @param delaccAccno             account number (zero-padded width 8)
	 * @param delaccAccType           account type name
	 * @param delaccInterestRate      interest rate (scale 2)
	 * @param delaccOpened            date opened (formatted string)
	 * @param delaccOverdraft         overdraft limit (whole pounds)
	 * @param delaccLastStatementDate last-statement date (formatted string)
	 * @param delaccNextStatementDate next-statement date (formatted string)
	 * @param delaccAvailableBalance  terminal available balance (scale 2)
	 * @param delaccActualBalance     terminal actual balance (scale 2)
	 * @param delaccSuccess           success flag
	 * @param delaccFailCode          primary fail code (wire {@code DelAccFailCd})
	 * @param delaccDelSuccess        secondary delete success flag
	 * @param delaccDelFailCode       secondary delete fail code (wire {@code DelAccDelFailCd})
	 * @param delaccDelApplid         applid
	 * @param delaccDelPcb1           PCB pointer slot 1
	 * @param delaccDelPcb2           PCB pointer slot 2
	 * @param delaccDelPcb3           PCB pointer slot 3
	 */
	public DelaccJson(String delaccEye, String delaccCustno,
			String delaccSortcode, String delaccAccno, String delaccAccType,
			BigDecimal delaccInterestRate, String delaccOpened,
			Integer delaccOverdraft, String delaccLastStatementDate,
			String delaccNextStatementDate, BigDecimal delaccAvailableBalance,
			BigDecimal delaccActualBalance, String delaccSuccess,
			String delaccFailCode, String delaccDelSuccess,
			String delaccDelFailCode, String delaccDelApplid,
			String delaccDelPcb1, String delaccDelPcb2, String delaccDelPcb3)
	{
		this.delaccEye = delaccEye;
		this.delaccCustno = delaccCustno;
		this.delaccSortcode = delaccSortcode;
		this.delaccAccno = delaccAccno;
		this.delaccAccType = delaccAccType;
		this.delaccInterestRate = delaccInterestRate;
		this.delaccOpened = delaccOpened;
		this.delaccOverdraft = delaccOverdraft;
		this.delaccLastStatementDate = delaccLastStatementDate;
		this.delaccNextStatementDate = delaccNextStatementDate;
		this.delaccAvailableBalance = delaccAvailableBalance;
		this.delaccActualBalance = delaccActualBalance;
		this.delaccSuccess = delaccSuccess;
		this.delaccFailCode = delaccFailCode;
		this.delaccDelSuccess = delaccDelSuccess;
		this.delaccDelFailCode = delaccDelFailCode;
		this.delaccDelApplid = delaccDelApplid;
		this.delaccDelPcb1 = delaccDelPcb1;
		this.delaccDelPcb2 = delaccDelPcb2;
		this.delaccDelPcb3 = delaccDelPcb3;
	}

	/**
	 * Returns the eye-catcher.
	 *
	 * @return the eye-catcher
	 */
	public String getDelaccEye()
	{
		return delaccEye;
	}

	/**
	 * Sets the eye-catcher.
	 *
	 * @param delaccEye the eye-catcher
	 */
	public void setDelaccEye(String delaccEye)
	{
		this.delaccEye = delaccEye;
	}

	/**
	 * Returns the owning customer number.
	 *
	 * @return the customer number
	 */
	public String getDelaccCustno()
	{
		return delaccCustno;
	}

	/**
	 * Sets the owning customer number (expected left-zero-padded to width 10).
	 *
	 * @param delaccCustno the customer number
	 */
	public void setDelaccCustno(String delaccCustno)
	{
		this.delaccCustno = delaccCustno;
	}

	/**
	 * Returns the sort code.
	 *
	 * @return the sort code
	 */
	public String getDelaccSortcode()
	{
		return delaccSortcode;
	}

	/**
	 * Sets the sort code (expected left-zero-padded to width 6).
	 *
	 * @param delaccSortcode the sort code
	 */
	public void setDelaccSortcode(String delaccSortcode)
	{
		this.delaccSortcode = delaccSortcode;
	}

	/**
	 * Returns the account number.
	 *
	 * @return the account number
	 */
	public String getDelaccAccno()
	{
		return delaccAccno;
	}

	/**
	 * Sets the account number (expected left-zero-padded to width 8).
	 *
	 * @param delaccAccno the account number
	 */
	public void setDelaccAccno(String delaccAccno)
	{
		this.delaccAccno = delaccAccno;
	}

	/**
	 * Returns the account type.
	 *
	 * @return the account type
	 */
	public String getDelaccAccType()
	{
		return delaccAccType;
	}

	/**
	 * Sets the account type (e.g. {@code ISA}, {@code MORTGAGE}, {@code SAVING},
	 * {@code CURRENT}, {@code LOAN}, or empty).
	 *
	 * @param delaccAccType the account type
	 */
	public void setDelaccAccType(String delaccAccType)
	{
		this.delaccAccType = delaccAccType;
	}

	/**
	 * Returns the interest rate.
	 *
	 * @return the interest rate
	 */
	public BigDecimal getDelaccInterestRate()
	{
		return delaccInterestRate;
	}

	/**
	 * Sets the interest rate (expected scale 2).
	 *
	 * @param delaccInterestRate the interest rate
	 */
	public void setDelaccInterestRate(BigDecimal delaccInterestRate)
	{
		this.delaccInterestRate = delaccInterestRate;
	}

	/**
	 * Returns the date opened (formatted account-date string).
	 *
	 * @return the date opened
	 */
	public String getDelaccOpened()
	{
		return delaccOpened;
	}

	/**
	 * Sets the date opened (formatted account-date string).
	 *
	 * @param delaccOpened the date opened
	 */
	public void setDelaccOpened(String delaccOpened)
	{
		this.delaccOpened = delaccOpened;
	}

	/**
	 * Returns the overdraft limit (whole pounds).
	 *
	 * @return the overdraft limit
	 */
	public Integer getDelaccOverdraft()
	{
		return delaccOverdraft;
	}

	/**
	 * Sets the overdraft limit (whole pounds).
	 *
	 * @param delaccOverdraft the overdraft limit
	 */
	public void setDelaccOverdraft(Integer delaccOverdraft)
	{
		this.delaccOverdraft = delaccOverdraft;
	}

	/**
	 * Returns the last-statement date (formatted account-date string).
	 *
	 * @return the last-statement date
	 */
	public String getDelaccLastStatementDate()
	{
		return delaccLastStatementDate;
	}

	/**
	 * Sets the last-statement date (formatted account-date string).
	 *
	 * @param delaccLastStatementDate the last-statement date
	 */
	public void setDelaccLastStatementDate(String delaccLastStatementDate)
	{
		this.delaccLastStatementDate = delaccLastStatementDate;
	}

	/**
	 * Returns the next-statement date (formatted account-date string).
	 *
	 * @return the next-statement date
	 */
	public String getDelaccNextStatementDate()
	{
		return delaccNextStatementDate;
	}

	/**
	 * Sets the next-statement date (formatted account-date string).
	 *
	 * @param delaccNextStatementDate the next-statement date
	 */
	public void setDelaccNextStatementDate(String delaccNextStatementDate)
	{
		this.delaccNextStatementDate = delaccNextStatementDate;
	}

	/**
	 * Returns the terminal available balance at deletion.
	 *
	 * @return the available balance
	 */
	public BigDecimal getDelaccAvailableBalance()
	{
		return delaccAvailableBalance;
	}

	/**
	 * Sets the terminal available balance at deletion (expected scale 2).
	 *
	 * @param delaccAvailableBalance the available balance
	 */
	public void setDelaccAvailableBalance(BigDecimal delaccAvailableBalance)
	{
		this.delaccAvailableBalance = delaccAvailableBalance;
	}

	/**
	 * Returns the terminal actual balance at deletion.
	 *
	 * @return the actual balance
	 */
	public BigDecimal getDelaccActualBalance()
	{
		return delaccActualBalance;
	}

	/**
	 * Sets the terminal actual balance at deletion (expected scale 2).
	 *
	 * @param delaccActualBalance the actual balance
	 */
	public void setDelaccActualBalance(BigDecimal delaccActualBalance)
	{
		this.delaccActualBalance = delaccActualBalance;
	}

	/**
	 * Returns the success flag.
	 *
	 * @return the success flag
	 */
	public String getDelaccSuccess()
	{
		return delaccSuccess;
	}

	/**
	 * Sets the success flag.
	 *
	 * @param delaccSuccess the success flag
	 */
	public void setDelaccSuccess(String delaccSuccess)
	{
		this.delaccSuccess = delaccSuccess;
	}

	/**
	 * Returns the primary fail code (wire {@code DelAccFailCd}).
	 *
	 * @return the primary fail code
	 */
	public String getDelaccFailCode()
	{
		return delaccFailCode;
	}

	/**
	 * Sets the primary fail code (wire {@code DelAccFailCd}).
	 *
	 * @param delaccFailCode the primary fail code
	 */
	public void setDelaccFailCode(String delaccFailCode)
	{
		this.delaccFailCode = delaccFailCode;
	}

	/**
	 * Returns the secondary delete success flag.
	 *
	 * @return the delete success flag
	 */
	public String getDelaccDelSuccess()
	{
		return delaccDelSuccess;
	}

	/**
	 * Sets the secondary delete success flag.
	 *
	 * @param delaccDelSuccess the delete success flag
	 */
	public void setDelaccDelSuccess(String delaccDelSuccess)
	{
		this.delaccDelSuccess = delaccDelSuccess;
	}

	/**
	 * Returns the secondary delete fail code (wire {@code DelAccDelFailCd}).
	 *
	 * @return the secondary delete fail code
	 */
	public String getDelaccDelFailCode()
	{
		return delaccDelFailCode;
	}

	/**
	 * Sets the secondary delete fail code (wire {@code DelAccDelFailCd}).
	 *
	 * @param delaccDelFailCode the secondary delete fail code
	 */
	public void setDelaccDelFailCode(String delaccDelFailCode)
	{
		this.delaccDelFailCode = delaccDelFailCode;
	}

	/**
	 * Returns the applid.
	 *
	 * @return the applid
	 */
	public String getDelaccDelApplid()
	{
		return delaccDelApplid;
	}

	/**
	 * Sets the applid.
	 *
	 * @param delaccDelApplid the applid
	 */
	public void setDelaccDelApplid(String delaccDelApplid)
	{
		this.delaccDelApplid = delaccDelApplid;
	}

	/**
	 * Returns PCB pointer slot 1.
	 *
	 * @return PCB slot 1
	 */
	public String getDelaccDelPcb1()
	{
		return delaccDelPcb1;
	}

	/**
	 * Sets PCB pointer slot 1.
	 *
	 * @param delaccDelPcb1 PCB slot 1
	 */
	public void setDelaccDelPcb1(String delaccDelPcb1)
	{
		this.delaccDelPcb1 = delaccDelPcb1;
	}

	/**
	 * Returns PCB pointer slot 2.
	 *
	 * @return PCB slot 2
	 */
	public String getDelaccDelPcb2()
	{
		return delaccDelPcb2;
	}

	/**
	 * Sets PCB pointer slot 2.
	 *
	 * @param delaccDelPcb2 PCB slot 2
	 */
	public void setDelaccDelPcb2(String delaccDelPcb2)
	{
		this.delaccDelPcb2 = delaccDelPcb2;
	}

	/**
	 * Returns PCB pointer slot 3.
	 *
	 * @return PCB slot 3
	 */
	public String getDelaccDelPcb3()
	{
		return delaccDelPcb3;
	}

	/**
	 * Sets PCB pointer slot 3.
	 *
	 * @param delaccDelPcb3 PCB slot 3
	 */
	public void setDelaccDelPcb3(String delaccDelPcb3)
	{
		this.delaccDelPcb3 = delaccDelPcb3;
	}

	/**
	 * Returns a diagnostic representation listing all twenty fields in copybook
	 * order. Not part of the wire contract.
	 *
	 * @return a string representation of this payload
	 */
	@Override
	public String toString()
	{
		return "DelaccJson [DelAccEye=" + delaccEye + ", DelAccCustno="
				+ delaccCustno + ", DelAccScode=" + delaccSortcode
				+ ", DelAccAccno=" + delaccAccno + ", DelAccAccType="
				+ delaccAccType + ", DelAccIntRate=" + delaccInterestRate
				+ ", DelAccOpened=" + delaccOpened + ", DelAccOverdraft="
				+ delaccOverdraft + ", DelAccLastStmtDt="
				+ delaccLastStatementDate + ", DelAccNextStmtDt="
				+ delaccNextStatementDate + ", DelAccAvailBal="
				+ delaccAvailableBalance + ", DelAccActualBal="
				+ delaccActualBalance + ", DelAccSuccess=" + delaccSuccess
				+ ", DelAccFailCd=" + delaccFailCode + ", DelAccDelSuccess="
				+ delaccDelSuccess + ", DelAccDelFailCd=" + delaccDelFailCode
				+ ", DelAccDelApplid=" + delaccDelApplid + ", DelAccDelPcb1="
				+ delaccDelPcb1 + ", DelAccDelPcb2=" + delaccDelPcb2
				+ ", DelAccDelPcb3=" + delaccDelPcb3 + "]";
	}

}
