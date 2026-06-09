/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.core.dto.accountenquiry;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.ibm.cics.cip.bank.core.config.JacksonConfig;

/**
 * Inner payload object of the frozen z/OS Connect {@code inqaccz}
 * (INQUIRE&nbsp;ACCOUNT) contract (feature F-019). This DTO is the object nested
 * under the outer envelope key {@code InqAcc} inside the wrapper
 * {@code AccountEnquiryJson}; it carries the result of an account enquiry back to
 * the preserved front ends and interface modules.
 *
 * <p><strong>This class IS the frozen contract surface.</strong> Its serialised
 * shape must match the z/OS Connect wire form VERBATIM so the preserved
 * Customer-Services interface module &mdash; whose own legacy {@code InqaccJson}
 * wire class in the interface package
 * deserialises with a default {@code new ObjectMapper()}
 * ({@code FAIL_ON_UNKNOWN_PROPERTIES = true}, no {@code @JsonIgnoreProperties})
 * &mdash; reads it with ZERO client change. Serialising a populated instance
 * therefore yields EXACTLY these fourteen keys and no others:
 * {@code InqAccEye}, {@code InqAccCustno}, {@code InqAccScode},
 * {@code InqAccAccno}, {@code InqAccAccType}, {@code InqAccIntRate},
 * {@code InqAccOpened}, {@code InqAccOverdraft}, {@code InqAccLastStmtDt},
 * {@code InqAccNextStmtDt}, {@code InqAccAvailBal}, {@code InqAccActualBal},
 * {@code InqAccSuccess}, {@code InqAccPcb1Pointer}. The field set is fixed by
 * {@code INQACC.cpy}/{@code INQACCZ.cpy}, the frozen swagger
 * ({@code inqaccz/api-docs/swagger.json}) and the frozen response schema
 * ({@code CSaccenq/schemas/CSaccenqResponse.json}); all three agree exactly.</p>
 *
 * <p><strong>Money is {@link BigDecimal}, never an inexact primitive numeric
 * type.</strong> The three monetary fields &mdash; {@code InqAccIntRate},
 * {@code InqAccAvailBal} and {@code InqAccActualBal} &mdash; are
 * {@link java.math.BigDecimal}, satisfying the binding rule to use
 * {@code BigDecimal} for all money, match the COBOL rounding exactly, and avoid
 * inexact primitive numeric types. This is the one deliberate, mandated deviation
 * from the legacy field types, which used an inexact primitive numeric type.
 * Values are carried at scale&nbsp;2 with {@code RoundingMode.HALF_UP} applied by
 * the populating service/controller; a scale-2 {@code BigDecimal} serialises as a
 * JSON decimal (for example {@code 100.00}), matching the swagger
 * {@code number}/{@code format:decimal}/{@code multipleOf:0.01} typing and read
 * cleanly by the legacy consumer's inexact-numeric fields. No inexact primitive
 * numeric type appears anywhere in this class. The available and actual balances
 * are independent (cleared vs. pending funds) and are NEVER collapsed into a
 * single value (&sect;0.6).</p>
 *
 * <p><strong>Identifiers and dates are JSON integers on this wire.</strong>
 * Unlike the sibling customer contract (whose swagger types its sort code and
 * customer number as {@code string}), the {@code inqaccz} swagger and schema type
 * {@code InqAccCustno}, {@code InqAccScode}, {@code InqAccAccno},
 * {@code InqAccOpened}, {@code InqAccOverdraft}, {@code InqAccLastStmtDt} and
 * {@code InqAccNextStmtDt} as {@code integer}. They are therefore modelled as
 * boxed integer types, NOT as zero-padded {@code String}s and NOT as
 * {@code LocalDate} values. {@code InqAccCustno} is widened to {@link Long}:
 * {@code INQACC-CUSTNO PIC 9(10)} permits up to {@code 9,999,999,999}, which
 * exceeds {@link Integer#MAX_VALUE}; this fixes a latent overflow in the legacy
 * consumer's {@code int} field while remaining wire-identical (a JSON integer)
 * for in-range values. The opened/statement dates are 8-digit {@code DDMMYYYY}
 * values carried as {@link Integer}; the entity/service layer converts between
 * the entity's {@code LocalDate} and this integer wire representation, so no date
 * formatting happens here.</p>
 *
 * <p><strong>Eye-catcher and PCB pointer are retained as wire fields.</strong>
 * Although &sect;0.6 drops the {@code ACCT}-style eye-catcher from the JPA
 * <em>entities</em>, the DTO keeps {@code InqAccEye} for contract fidelity.
 * {@code InqAccPcb1Pointer} is likewise retained as a {@link String}: the frozen
 * swagger and {@code CSaccenqResponse.json} both list it (string, maxLength&nbsp;4)
 * and the legacy consumer declares it, so omitting it would change the contract
 * surface. {@code INQACC.cpy} types it {@code POINTER} (a runtime-only construct)
 * but {@code INQACCZ.cpy} and the swagger flatten it to {@code PIC X(4)}/string,
 * the contract-facing form; the populating service may leave it blank.</p>
 *
 * <p><strong>Wire names are pinned per field.</strong> The class is annotated
 * {@link JsonNaming @JsonNaming} with {@link JacksonConfig.EnvelopeNamingStrategy}
 * for consistency with the other {@code core/dto} envelopes, but every field also
 * declares an explicit {@link JsonProperty @JsonProperty} with its verbatim wire
 * name. Because an explicit {@code @JsonProperty} always wins over the class-level
 * {@code substring(3)} naming strategy, the emitted keys are exactly the fourteen
 * names above &mdash; the strategy can never produce an unexpected key.</p>
 *
 * <p><strong>No business logic.</strong> This is a plain, mutable wire DTO. The
 * INQUIRE&nbsp;ACCOUNT behaviour (for example the {@code 99999999} sentinel that
 * resolves the highest account via the control-row {@code LAST-ACCOUNT-NUMBER}
 * rather than a {@code MAX()} scan, F-009) lives in {@code AccountService}, never
 * in this object. A required contract integration test confirms that an
 * {@code AccountEnquiryJson} wrapping this payload round-trips into the preserved
 * interface-module {@code InqaccJson} via a default {@code ObjectMapper} with zero
 * client change.</p>
 *
 * @see JacksonConfig.EnvelopeNamingStrategy
 */
@JsonNaming(JacksonConfig.EnvelopeNamingStrategy.class)
public class InqaccJson
{

	/**
	 * Eye-catcher field. {@code INQACC-EYE PIC X(4)} &rarr; {@link String} (frozen
	 * schema {@code type=string}, {@code maxLength=4}). Retained as a wire field
	 * for contract fidelity even though the eye-catcher is dropped from the JPA
	 * entities (&sect;0.6). Serialised verbatim as {@code InqAccEye}.
	 */
	@JsonProperty("InqAccEye")
	private String inqaccEyecatcher;

	/**
	 * Customer number that owns the account. {@code INQACC-CUSTNO PIC 9(10)}
	 * &rarr; {@link Long} (frozen schema {@code type=integer}, range
	 * {@code 0..9999999999}). Widened to {@code Long} because the maximum exceeds
	 * {@link Integer#MAX_VALUE}; serialises as a JSON integer. Serialised verbatim
	 * as {@code InqAccCustno}.
	 */
	@JsonProperty("InqAccCustno")
	private Long inqaccCustno;

	/**
	 * Bank sort code. {@code INQACC-SCODE PIC 9(6)} &rarr; {@link Integer} (frozen
	 * schema {@code type=integer}, range {@code 0..999999}). Typed as a JSON
	 * integer by this contract &mdash; NOT a zero-padded string. Serialised
	 * verbatim as {@code InqAccScode}.
	 */
	@JsonProperty("InqAccScode")
	private Integer inqaccSortcode;

	/**
	 * Account number. {@code INQACC-ACCNO PIC 9(8)} &rarr; {@link Integer} (frozen
	 * schema {@code type=integer}, range {@code 0..99999999}). Serialised verbatim
	 * as {@code InqAccAccno}.
	 */
	@JsonProperty("InqAccAccno")
	private Integer inqaccAccno;

	/**
	 * Account type, for example {@code ISA}, {@code MORTGAGE}, {@code SAVING},
	 * {@code CURRENT} or {@code LOAN}. {@code INQACC-ACC-TYPE PIC X(8)} &rarr;
	 * {@link String} (frozen schema {@code type=string}, {@code maxLength=8}).
	 * Serialised verbatim as {@code InqAccAccType}.
	 */
	@JsonProperty("InqAccAccType")
	private String inqaccAccType;

	/**
	 * Interest rate. {@code INQACC-INT-RATE PIC 9(4)V99} &rarr;
	 * {@link java.math.BigDecimal} (frozen schema {@code type=number},
	 * {@code format=decimal}, {@code multipleOf=0.01}, range {@code 0..9999.99}).
	 * Carried at scale&nbsp;2 with {@code RoundingMode.HALF_UP} (applied by the
	 * populating service); the legacy inexact primitive numeric type is
	 * deliberately replaced by {@code BigDecimal} per the binding money rule.
	 * Serialised verbatim as {@code InqAccIntRate}.
	 */
	@JsonProperty("InqAccIntRate")
	private BigDecimal inqaccInterestRate;

	/**
	 * Account opened date as an 8-digit {@code DDMMYYYY}-packed value.
	 * {@code INQACC-OPENED PIC 9(8)} &rarr; {@link Integer} (frozen schema
	 * {@code type=integer}, range {@code 0..99999999}). The wire form is an
	 * integer, exactly as the legacy behaved; no {@code DD/MM/YYYY} string
	 * formatting occurs here. Serialised verbatim as {@code InqAccOpened}.
	 */
	@JsonProperty("InqAccOpened")
	private Integer inqaccOpened;

	/**
	 * Overdraft limit. {@code INQACC-OVERDRAFT PIC 9(8)} &rarr; {@link Integer}
	 * (frozen schema {@code type=integer}, range {@code 0..99999999}). Per
	 * &sect;0.6 the overdraft limit maps to {@code Integer} (no decimals).
	 * Serialised verbatim as {@code InqAccOverdraft}.
	 */
	@JsonProperty("InqAccOverdraft")
	private Integer inqaccOverdraft;

	/**
	 * Last statement date as an 8-digit {@code DDMMYYYY}-packed value.
	 * {@code INQACC-LAST-STMT-DT PIC 9(8)} &rarr; {@link Integer} (frozen schema
	 * {@code type=integer}, range {@code 0..99999999}). Serialised verbatim as
	 * {@code InqAccLastStmtDt}.
	 */
	@JsonProperty("InqAccLastStmtDt")
	private Integer inqaccLastStatementDate;

	/**
	 * Next statement date as an 8-digit {@code DDMMYYYY}-packed value.
	 * {@code INQACC-NEXT-STMT-DT PIC 9(8)} &rarr; {@link Integer} (frozen schema
	 * {@code type=integer}, range {@code 0..99999999}). Serialised verbatim as
	 * {@code InqAccNextStmtDt}.
	 */
	@JsonProperty("InqAccNextStmtDt")
	private Integer inqaccNextStatementDate;

	/**
	 * Available (cleared) balance. {@code INQACC-AVAIL-BAL PIC S9(10)V99} &rarr;
	 * {@link java.math.BigDecimal} (frozen schema {@code type=number},
	 * {@code format=decimal}, {@code multipleOf=0.01}, range
	 * {@code -9999999999.99..9999999999.99}). Carried at scale&nbsp;2 with
	 * {@code RoundingMode.HALF_UP}; independent from the actual balance and never
	 * collapsed with it. Serialised verbatim as {@code InqAccAvailBal}.
	 */
	@JsonProperty("InqAccAvailBal")
	private BigDecimal inqaccAvailableBalance;

	/**
	 * Actual (pending) balance. {@code INQACC-ACTUAL-BAL PIC S9(10)V99} &rarr;
	 * {@link java.math.BigDecimal} (frozen schema {@code type=number},
	 * {@code format=decimal}, {@code multipleOf=0.01}, range
	 * {@code -9999999999.99..9999999999.99}). Carried at scale&nbsp;2 with
	 * {@code RoundingMode.HALF_UP}; independent from the available balance and
	 * never collapsed with it. Serialised verbatim as {@code InqAccActualBal}.
	 */
	@JsonProperty("InqAccActualBal")
	private BigDecimal inqaccActualBalance;

	/**
	 * Single-character success flag. {@code INQACC-SUCCESS PIC X} &rarr;
	 * {@link String} (frozen schema {@code type=string}, {@code maxLength=1}).
	 * Serialised verbatim as {@code InqAccSuccess}.
	 */
	@JsonProperty("InqAccSuccess")
	private String inqaccSuccess;

	/**
	 * PCB&nbsp;1 pointer, retained for contract fidelity.
	 * {@code INQACC-PCB1-POINTER} is {@code POINTER} in {@code INQACC.cpy} (runtime
	 * only) but {@code PIC X(4)} in {@code INQACCZ.cpy} and {@code string}
	 * ({@code maxLength=4}) in the frozen swagger/schema &mdash; the contract-facing
	 * form &mdash; so it is modelled as {@link String}. The populating service may
	 * leave it blank; emitting it (even as {@code ""}) is contract-safe, whereas
	 * omitting it would change the contract surface. Serialised verbatim as
	 * {@code InqAccPcb1Pointer}.
	 */
	@JsonProperty("InqAccPcb1Pointer")
	private String inqaccPcb1Pointer;

	/**
	 * No-argument constructor required by Jackson for deserialisation. Leaves all
	 * fields {@code null} until populated by the setters or by deserialisation.
	 */
	public InqaccJson()
	{
		super();
	}

	/**
	 * Returns the eye-catcher field.
	 *
	 * @return the eye-catcher ({@code InqAccEye})
	 */
	public String getInqaccEyecatcher()
	{
		return inqaccEyecatcher;
	}

	/**
	 * Sets the eye-catcher field.
	 *
	 * @param inqaccEyecatcherIn the eye-catcher
	 */
	public void setInqaccEyecatcher(String inqaccEyecatcherIn)
	{
		inqaccEyecatcher = inqaccEyecatcherIn;
	}

	/**
	 * Returns the owning customer number.
	 *
	 * @return the customer number ({@code InqAccCustno})
	 */
	public Long getInqaccCustno()
	{
		return inqaccCustno;
	}

	/**
	 * Sets the owning customer number.
	 *
	 * @param inqaccCustnoIn the customer number
	 */
	public void setInqaccCustno(Long inqaccCustnoIn)
	{
		inqaccCustno = inqaccCustnoIn;
	}

	/**
	 * Returns the bank sort code.
	 *
	 * @return the sort code ({@code InqAccScode})
	 */
	public Integer getInqaccSortcode()
	{
		return inqaccSortcode;
	}

	/**
	 * Sets the bank sort code.
	 *
	 * @param inqaccSortcodeIn the sort code
	 */
	public void setInqaccSortcode(Integer inqaccSortcodeIn)
	{
		inqaccSortcode = inqaccSortcodeIn;
	}

	/**
	 * Returns the account number.
	 *
	 * @return the account number ({@code InqAccAccno})
	 */
	public Integer getInqaccAccno()
	{
		return inqaccAccno;
	}

	/**
	 * Sets the account number.
	 *
	 * @param inqaccAccnoIn the account number
	 */
	public void setInqaccAccno(Integer inqaccAccnoIn)
	{
		inqaccAccno = inqaccAccnoIn;
	}

	/**
	 * Returns the account type.
	 *
	 * @return the account type ({@code InqAccAccType})
	 */
	public String getInqaccAccType()
	{
		return inqaccAccType;
	}

	/**
	 * Sets the account type.
	 *
	 * @param inqaccAccTypeIn the account type
	 */
	public void setInqaccAccType(String inqaccAccTypeIn)
	{
		inqaccAccType = inqaccAccTypeIn;
	}

	/**
	 * Returns the interest rate.
	 *
	 * @return the interest rate ({@code InqAccIntRate}), a scale-2
	 *         {@link java.math.BigDecimal}
	 */
	public BigDecimal getInqaccInterestRate()
	{
		return inqaccInterestRate;
	}

	/**
	 * Sets the interest rate. The value is expected at scale&nbsp;2 with
	 * {@code RoundingMode.HALF_UP} already applied by the populating service.
	 *
	 * @param inqaccInterestRateIn the interest rate
	 */
	public void setInqaccInterestRate(BigDecimal inqaccInterestRateIn)
	{
		inqaccInterestRate = inqaccInterestRateIn;
	}

	/**
	 * Returns the account opened date as an 8-digit {@code DDMMYYYY}-packed
	 * integer.
	 *
	 * @return the opened date ({@code InqAccOpened})
	 */
	public Integer getInqaccOpened()
	{
		return inqaccOpened;
	}

	/**
	 * Sets the account opened date as an 8-digit {@code DDMMYYYY}-packed integer.
	 *
	 * @param inqaccOpenedIn the opened date
	 */
	public void setInqaccOpened(Integer inqaccOpenedIn)
	{
		inqaccOpened = inqaccOpenedIn;
	}

	/**
	 * Returns the overdraft limit.
	 *
	 * @return the overdraft limit ({@code InqAccOverdraft})
	 */
	public Integer getInqaccOverdraft()
	{
		return inqaccOverdraft;
	}

	/**
	 * Sets the overdraft limit.
	 *
	 * @param inqaccOverdraftIn the overdraft limit
	 */
	public void setInqaccOverdraft(Integer inqaccOverdraftIn)
	{
		inqaccOverdraft = inqaccOverdraftIn;
	}

	/**
	 * Returns the last statement date as an 8-digit {@code DDMMYYYY}-packed
	 * integer.
	 *
	 * @return the last statement date ({@code InqAccLastStmtDt})
	 */
	public Integer getInqaccLastStatementDate()
	{
		return inqaccLastStatementDate;
	}

	/**
	 * Sets the last statement date as an 8-digit {@code DDMMYYYY}-packed integer.
	 *
	 * @param inqaccLastStatementDateIn the last statement date
	 */
	public void setInqaccLastStatementDate(Integer inqaccLastStatementDateIn)
	{
		inqaccLastStatementDate = inqaccLastStatementDateIn;
	}

	/**
	 * Returns the next statement date as an 8-digit {@code DDMMYYYY}-packed
	 * integer.
	 *
	 * @return the next statement date ({@code InqAccNextStmtDt})
	 */
	public Integer getInqaccNextStatementDate()
	{
		return inqaccNextStatementDate;
	}

	/**
	 * Sets the next statement date as an 8-digit {@code DDMMYYYY}-packed integer.
	 *
	 * @param inqaccNextStatementDateIn the next statement date
	 */
	public void setInqaccNextStatementDate(Integer inqaccNextStatementDateIn)
	{
		inqaccNextStatementDate = inqaccNextStatementDateIn;
	}

	/**
	 * Returns the available (cleared) balance.
	 *
	 * @return the available balance ({@code InqAccAvailBal}), a scale-2
	 *         {@link java.math.BigDecimal}
	 */
	public BigDecimal getInqaccAvailableBalance()
	{
		return inqaccAvailableBalance;
	}

	/**
	 * Sets the available (cleared) balance. The value is expected at scale&nbsp;2
	 * with {@code RoundingMode.HALF_UP} already applied by the populating service.
	 *
	 * @param inqaccAvailableBalanceIn the available balance
	 */
	public void setInqaccAvailableBalance(BigDecimal inqaccAvailableBalanceIn)
	{
		inqaccAvailableBalance = inqaccAvailableBalanceIn;
	}

	/**
	 * Returns the actual (pending) balance.
	 *
	 * @return the actual balance ({@code InqAccActualBal}), a scale-2
	 *         {@link java.math.BigDecimal}
	 */
	public BigDecimal getInqaccActualBalance()
	{
		return inqaccActualBalance;
	}

	/**
	 * Sets the actual (pending) balance. The value is expected at scale&nbsp;2
	 * with {@code RoundingMode.HALF_UP} already applied by the populating service.
	 *
	 * @param inqaccActualBalanceIn the actual balance
	 */
	public void setInqaccActualBalance(BigDecimal inqaccActualBalanceIn)
	{
		inqaccActualBalance = inqaccActualBalanceIn;
	}

	/**
	 * Returns the single-character success flag.
	 *
	 * @return the success flag ({@code InqAccSuccess})
	 */
	public String getInqaccSuccess()
	{
		return inqaccSuccess;
	}

	/**
	 * Sets the single-character success flag.
	 *
	 * @param inqaccSuccessIn the success flag
	 */
	public void setInqaccSuccess(String inqaccSuccessIn)
	{
		inqaccSuccess = inqaccSuccessIn;
	}

	/**
	 * Returns the PCB&nbsp;1 pointer.
	 *
	 * @return the PCB&nbsp;1 pointer ({@code InqAccPcb1Pointer})
	 */
	public String getInqaccPcb1Pointer()
	{
		return inqaccPcb1Pointer;
	}

	/**
	 * Sets the PCB&nbsp;1 pointer.
	 *
	 * @param inqaccPcb1PointerIn the PCB&nbsp;1 pointer
	 */
	public void setInqaccPcb1Pointer(String inqaccPcb1PointerIn)
	{
		inqaccPcb1Pointer = inqaccPcb1PointerIn;
	}

	/**
	 * Renders all fourteen fields for diagnostics and logging, labelled with their
	 * verbatim wire names. This is not a Jackson getter and therefore does not
	 * affect the serialised wire form.
	 *
	 * @return a diagnostic string containing every field
	 */
	@Override
	public String toString()
	{
		return "InqaccJson [InqAccEye=" + inqaccEyecatcher + ", InqAccCustno="
				+ inqaccCustno + ", InqAccScode=" + inqaccSortcode
				+ ", InqAccAccno=" + inqaccAccno + ", InqAccAccType="
				+ inqaccAccType + ", InqAccIntRate=" + inqaccInterestRate
				+ ", InqAccOpened=" + inqaccOpened + ", InqAccOverdraft="
				+ inqaccOverdraft + ", InqAccLastStmtDt="
				+ inqaccLastStatementDate + ", InqAccNextStmtDt="
				+ inqaccNextStatementDate + ", InqAccAvailBal="
				+ inqaccAvailableBalance + ", InqAccActualBal="
				+ inqaccActualBalance + ", InqAccSuccess=" + inqaccSuccess
				+ ", InqAccPcb1Pointer=" + inqaccPcb1Pointer + "]";
	}

}
