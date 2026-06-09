/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.core.dto.createaccount;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.ibm.cics.cip.bank.core.config.JacksonConfig;
import com.ibm.cics.cip.bank.core.dto.common.CommKey;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * Inner commarea payload of the frozen z/OS Connect <em>create-account</em>
 * ({@code creacc}) contract (feature F-019). This DTO is the object nested under
 * the outer envelope key {@code CreAcc} inside the wrapper
 * {@link CreateAccountJson}; it carries the create-account commarea in both
 * directions of the contract.
 *
 * <p><strong>This class IS the frozen contract surface.</strong> Its serialised
 * shape must match the z/OS Connect wire form VERBATIM so the preserved
 * Customer-Services interface module &mdash; whose own legacy {@code CreaccJson}
 * wire class deserialises with a default {@code new ObjectMapper()}
 * ({@code FAIL_ON_UNKNOWN_PROPERTIES = true}, no {@code @JsonIgnoreProperties})
 * &mdash; reads it with ZERO client change. Serialising a populated instance
 * therefore yields EXACTLY these thirteen keys and no others:
 * {@code CommEyecatcher}, {@code CommCustno}, {@code CommKey},
 * {@code CommAccType}, {@code CommIntRt}, {@code CommOpened},
 * {@code CommOverdrLim}, {@code CommLastStmtDt}, {@code CommNextStmtDt},
 * {@code CommAvailBal}, {@code CommActBal}, {@code CommSuccess} and
 * {@code CommFailCode}. The field set is fixed by {@code CREACC.cpy}, the frozen
 * request schema ({@code CSacccre/schemas/CSacccreRequest.json}) and the frozen
 * response schema ({@code CSacccreResponse.json}); all three agree exactly (the
 * request and response schemas are byte-for-byte identical).</p>
 *
 * <p><strong>Request vs. response usage.</strong> On a request the meaningful
 * inputs are {@code CommCustno}, {@code CommAccType}, {@code CommIntRt} and
 * {@code CommOverdrLim}. On a response {@code bank-core} populates the allocated
 * {@link #commKey key} (sort code + account number), the opened/statement dates
 * (integer {@code DDMMYYYY}), both balances and the
 * {@code CommSuccess}/{@code CommFailCode} status pair. The behavioural driver
 * &mdash; {@code CREACC.cbl}'s ordered five-step create with fail codes
 * {@code '1'} (customer not found), {@code '8'} (max ten accounts) and
 * {@code 'A'} (invalid account type) &mdash; lives in {@code AccountService},
 * never in this object. This class is a plain, mutable wire carrier with no
 * business logic (behavioural parity over enhancement).</p>
 *
 * <p><strong>Money is {@link BigDecimal}, never an inexact primitive numeric
 * type.</strong> The three monetary fields &mdash; {@code CommIntRt}
 * ({@code COMM-INT-RT PIC 9(4)V99}), {@code CommAvailBal}
 * ({@code COMM-AVAIL-BAL PIC S9(10)V99}) and {@code CommActBal}
 * ({@code COMM-ACT-BAL PIC S9(10)V99}) &mdash; are {@link java.math.BigDecimal},
 * satisfying the binding rule "use {@code BigDecimal} for all money; match COBOL
 * rounding exactly; avoid inexact binary numeric types". Values are carried at
 * scale&nbsp;2
 * with {@code RoundingMode.HALF_UP} applied by the populating service/controller
 * (following the {@code accountenquiry} sibling convention; this DTO performs no
 * scaling itself). A scale-2 {@code BigDecimal} serialises as a JSON decimal,
 * matching the swagger {@code number}/{@code format:decimal}/{@code multipleOf:
 * 0.01} typing and read cleanly by the legacy consumer's inexact-numeric fields.
 * No inexact primitive numeric type appears anywhere in this class. The available
 * and actual balances are independent (cleared vs. pending funds) and are NEVER
 * collapsed into a single value (&sect;0.6).</p>
 *
 * <p><strong>Identifiers and dates are JSON integers/strings on this wire, not
 * date objects.</strong> The opened and statement dates ({@code CommOpened},
 * {@code CommLastStmtDt}, {@code CommNextStmtDt}; each {@code PIC 9(8)}
 * {@code DDMMYYYY}) and the overdraft limit ({@code CommOverdrLim};
 * {@code PIC 9(8)} with no decimals) are modelled as boxed {@link Integer} to
 * match BOTH the frozen swagger ({@code type:integer}) AND the legacy client's
 * {@code int} fields. They are deliberately NOT modelled as date objects and
 * are NOT serialised as {@code DD/MM/YYYY} strings: the AAP's "{@code DD/MM/YYYY}"
 * note describes the human-readable DISPLAY format the legacy front end renders
 * from the integer, not the wire form. Serialising a string here would break the
 * frozen schema's integer typing and the re-pointed interface module's {@code int}
 * deserialisation. {@code CommCustno} ({@code COMM-CUSTNO PIC 9(10)}) is a
 * {@link String}: the legacy interface-module {@code CreaccJson} (the frozen
 * consumer this module interoperates with) declares it {@code String}, SENDS it
 * as a zero-padded JSON string and READS it via {@code getCommCustno()} +
 * {@code Integer.parseInt(...)} (see {@code WebController.checkIfResponseValidCreateAcc}).
 * Jackson coerces a JSON number&harr;String, so a {@code String} is wire-safe in
 * both directions even though the swagger nominally types it {@code integer}; the
 * contract-faithful choice is to match the frozen consumer DTO field type.</p>
 *
 * <p><strong>Wire names are pinned per field.</strong> The class is annotated
 * {@link JsonNaming @JsonNaming} with {@link JacksonConfig.EnvelopeNamingStrategy}
 * for consistency with the other {@code core/dto} envelopes, but every field also
 * declares an explicit {@link JsonProperty @JsonProperty} with its verbatim wire
 * name. Because an explicit {@code @JsonProperty} always wins over the class-level
 * {@code substring(3)} naming strategy, the emitted keys are exactly the thirteen
 * names above &mdash; the strategy can never produce an unexpected key.</p>
 *
 * <p><strong>Composite key.</strong> The {@code CommKey} group
 * ({@code COMM-SORTCODE PIC 9(6)} + {@code COMM-NUMBER PIC 9(8)}) is the shared
 * {@link CommKey} from {@code dto.common}, which supersedes the legacy nested
 * per-envelope key class. The field is initialised to a non-{@code null}
 * {@code new CommKey()} so a freshly-constructed envelope reproduces the legacy
 * default {@code "CommKey":{"CommSortcode":0,"CommNumber":0}} rather than emitting
 * {@code null}.</p>
 *
 * @see JacksonConfig.EnvelopeNamingStrategy
 * @see CommKey
 * @see CreateAccountJson
 */
@JsonNaming(JacksonConfig.EnvelopeNamingStrategy.class)
public class CreaccJson
{

	/**
	 * Eye-catcher field. {@code COMM-EYECATCHER PIC X(4)} &rarr; {@link String}
	 * (frozen schema {@code type=string}, {@code maxLength=4}). Defaults to four
	 * spaces to reproduce the legacy envelope's initial value. Serialised
	 * verbatim as {@code CommEyecatcher}.
	 */
	@JsonProperty("CommEyecatcher")
	@Size(max = 4)
	private String commEyecatcher = "    ";

	/**
	 * Owning customer number. {@code COMM-CUSTNO PIC 9(10)} &rarr; {@link String}.
	 * Although the frozen swagger nominally types this {@code integer}, the legacy
	 * interface-module consumer declares it {@code String}, sends it zero-padded
	 * and reads it with {@code Integer.parseInt(getCommCustno())}; matching the
	 * frozen consumer DTO field type is the contract-faithful choice, and Jackson
	 * coerces JSON number&harr;String either way. Serialised verbatim as
	 * {@code CommCustno}.
	 */
	@JsonProperty("CommCustno")
	@Size(max = 10)
	private String commCustno;

	/**
	 * Allocated composite identity (sort code + account number), the shared
	 * {@link CommKey} that replaces the legacy nested per-envelope key class. Models
	 * the COBOL {@code COMM-KEY} group ({@code COMM-SORTCODE PIC 9(6)} +
	 * {@code COMM-NUMBER PIC 9(8)}). Initialised to a non-{@code null}
	 * {@code new CommKey()} so the default envelope is
	 * {@code {"CommSortcode":0,"CommNumber":0}}. Serialised verbatim as
	 * {@code CommKey}.
	 */
	@JsonProperty("CommKey")
	@Valid
	private CommKey commKey = new CommKey();

	/**
	 * Account type, for example {@code ISA}, {@code MORTGAGE}, {@code SAVING},
	 * {@code CURRENT} or {@code LOAN}. {@code COMM-ACC-TYPE PIC X(8)} &rarr;
	 * {@link String} (frozen schema {@code type=string}, {@code maxLength=8}),
	 * left-justified and space-padded to width 8 by the convenience constructor.
	 * Serialised verbatim as {@code CommAccType}.
	 */
	@JsonProperty("CommAccType")
	@Size(max = 8)
	private String commAccType;

	/**
	 * Interest rate. {@code COMM-INT-RT PIC 9(4)V99} &rarr;
	 * {@link java.math.BigDecimal} (frozen schema {@code type=number},
	 * {@code format=decimal}, {@code multipleOf=0.01}, range {@code 0..9999.99}).
	 * Carried at scale&nbsp;2 with {@code RoundingMode.HALF_UP} applied by the
	 * populating service/controller; the legacy inexact primitive numeric type is
	 * deliberately replaced by {@code BigDecimal} per the binding money rule.
	 * Serialised verbatim as {@code CommIntRt}.
	 */
	@JsonProperty("CommIntRt")
	@Digits(integer = 4, fraction = 2)
	@DecimalMin("0")
	@DecimalMax("9999.99")
	private BigDecimal commInterestRate;

	/**
	 * Account opened date as an 8-digit {@code DDMMYYYY}-packed value.
	 * {@code COMM-OPENED PIC 9(8)} &rarr; {@link Integer} (frozen schema
	 * {@code type=integer}, range {@code 0..99999999}). The wire form is an
	 * integer, exactly as the legacy behaved; no {@code DD/MM/YYYY} string
	 * formatting occurs here (that is a display concern of the front end).
	 * Serialised verbatim as {@code CommOpened}.
	 */
	@JsonProperty("CommOpened")
	@Min(0)
	@Max(99999999)
	private Integer commOpened;

	/**
	 * Overdraft limit (whole units, no decimals). {@code COMM-OVERDR-LIM PIC 9(8)}
	 * &rarr; {@link Integer} (frozen schema {@code type=integer}, range
	 * {@code 0..99999999}). Per &sect;0.6 the overdraft limit maps to
	 * {@code Integer}, NOT {@code BigDecimal}. Serialised verbatim as
	 * {@code CommOverdrLim}.
	 */
	@JsonProperty("CommOverdrLim")
	@Min(0)
	@Max(99999999)
	private Integer commOverdraftLimit;

	/**
	 * Last-statement date as an 8-digit {@code DDMMYYYY}-packed value.
	 * {@code COMM-LAST-STMT-DT PIC 9(8)} &rarr; {@link Integer} (frozen schema
	 * {@code type=integer}, range {@code 0..99999999}). Serialised verbatim as
	 * {@code CommLastStmtDt}.
	 */
	@JsonProperty("CommLastStmtDt")
	@Min(0)
	@Max(99999999)
	private Integer commLastStatementDate;

	/**
	 * Next-statement date as an 8-digit {@code DDMMYYYY}-packed value.
	 * {@code COMM-NEXT-STMT-DT PIC 9(8)} &rarr; {@link Integer} (frozen schema
	 * {@code type=integer}, range {@code 0..99999999}). Serialised verbatim as
	 * {@code CommNextStmtDt}.
	 */
	@JsonProperty("CommNextStmtDt")
	@Min(0)
	@Max(99999999)
	private Integer commNextStatementDate;

	/**
	 * Available (cleared) balance. {@code COMM-AVAIL-BAL PIC S9(10)V99} &rarr;
	 * {@link java.math.BigDecimal} (frozen schema {@code type=number},
	 * {@code format=decimal}, {@code multipleOf=0.01}, range
	 * {@code -9999999999.99..9999999999.99}). Carried at scale&nbsp;2 with
	 * {@code RoundingMode.HALF_UP}; independent from the actual balance and never
	 * collapsed with it (&sect;0.6). Serialised verbatim as {@code CommAvailBal}.
	 */
	@JsonProperty("CommAvailBal")
	@Digits(integer = 10, fraction = 2)
	@DecimalMin("-9999999999.99")
	@DecimalMax("9999999999.99")
	private BigDecimal commAvailableBalance;

	/**
	 * Actual (pending) balance. {@code COMM-ACT-BAL PIC S9(10)V99} &rarr;
	 * {@link java.math.BigDecimal} (frozen schema {@code type=number},
	 * {@code format=decimal}, {@code multipleOf=0.01}, range
	 * {@code -9999999999.99..9999999999.99}). Carried at scale&nbsp;2 with
	 * {@code RoundingMode.HALF_UP}; independent from the available balance and
	 * never collapsed with it (&sect;0.6). Serialised verbatim as
	 * {@code CommActBal}.
	 */
	@JsonProperty("CommActBal")
	@Digits(integer = 10, fraction = 2)
	@DecimalMin("-9999999999.99")
	@DecimalMax("9999999999.99")
	private BigDecimal commActualBalance;

	/**
	 * Single-character success flag ({@code Y}/{@code N}). {@code COMM-SUCCESS
	 * PIC X} &rarr; {@link String} (frozen schema {@code type=string},
	 * {@code maxLength=1}). Serialised verbatim as {@code CommSuccess}.
	 */
	@JsonProperty("CommSuccess")
	@Size(max = 1)
	private String commSuccess;

	/**
	 * Single-character fail code carrying the COBOL {@code CREACC} outcome &mdash;
	 * for example {@code '1'} (customer not found), {@code '8'} (max ten accounts)
	 * or {@code 'A'} (invalid account type). {@code COMM-FAIL-CODE PIC X} &rarr;
	 * {@link String} (frozen schema {@code type=string}, {@code maxLength=1}).
	 * Serialised verbatim as {@code CommFailCode}.
	 */
	@JsonProperty("CommFailCode")
	@Size(max = 1)
	private String commFailCode;

	/**
	 * No-argument constructor required by Jackson for deserialisation. Leaves the
	 * field-level defaults in place: {@link #commEyecatcher} is four spaces and
	 * {@link #commKey} is a non-{@code null} {@code new CommKey()}, reproducing the
	 * legacy envelope's initial wire shape.
	 */
	public CreaccJson()
	{
		// Field-level initialisers preserve the legacy envelope defaults
		// (eye-catcher = 4 spaces, CommKey = {"CommSortcode":0,"CommNumber":0}).
	}

	/**
	 * Convenience constructor mirroring the legacy four-argument analog, retyped
	 * to the AAP-mandated wire types (overdraft &rarr; {@link Integer}, interest
	 * rate &rarr; {@link BigDecimal}). Builds a request-shaped envelope from the
	 * caller-supplied fields, applying the COBOL fixed-width formatting rules:
	 * the account type is left-justified and space-padded to width 8
	 * ({@code COMM-ACC-TYPE PIC X(8)}) and the customer number is left-zero-padded
	 * to width 10 ({@code COMM-CUSTNO PIC 9(10)}). The eye-catcher and key are
	 * reset to their default wire values.
	 *
	 * @param accountType    the account type (left-justified, padded to width 8);
	 *                       serialised under {@code CommAccType}
	 * @param custNumber     the owning customer number; when non-{@code null} it
	 *                       is left-zero-padded to width 10 (the consumer's
	 *                       {@code Integer.parseInt} is padding-agnostic), and is
	 *                       left {@code null} when {@code null} is supplied;
	 *                       serialised under {@code CommCustno}
	 * @param overdraftLimit the whole-number overdraft limit; serialised under
	 *                       {@code CommOverdrLim}
	 * @param interestRate   the interest rate as a {@link BigDecimal} (scaling to
	 *                       2 with {@code RoundingMode.HALF_UP} is applied by the
	 *                       populating service/controller); serialised under
	 *                       {@code CommIntRt}
	 */
	public CreaccJson(String accountType, String custNumber,
			Integer overdraftLimit, BigDecimal interestRate)
	{
		this.commAccType = String.format("%-8s", accountType);
		this.commCustno = (custNumber == null) ? null
				: String.format("%10s", custNumber).replace(' ', '0');
		this.commOverdraftLimit = overdraftLimit;
		this.commInterestRate = interestRate;
		this.commEyecatcher = "    ";
		this.commKey = new CommKey();
	}

	/**
	 * Returns the eye-catcher.
	 *
	 * @return the eye-catcher ({@code CommEyecatcher})
	 */
	public String getCommEyecatcher()
	{
		return commEyecatcher;
	}

	/**
	 * Sets the eye-catcher.
	 *
	 * @param commEyecatcher the eye-catcher ({@code CommEyecatcher})
	 */
	public void setCommEyecatcher(String commEyecatcher)
	{
		this.commEyecatcher = commEyecatcher;
	}

	/**
	 * Returns the owning customer number.
	 *
	 * @return the customer number ({@code CommCustno})
	 */
	public String getCommCustno()
	{
		return commCustno;
	}

	/**
	 * Sets the owning customer number.
	 *
	 * @param commCustno the customer number ({@code CommCustno})
	 */
	public void setCommCustno(String commCustno)
	{
		this.commCustno = commCustno;
	}

	/**
	 * Returns the allocated composite identity key.
	 *
	 * @return the key ({@code CommKey}); never {@code null} for a default instance
	 */
	public CommKey getCommKey()
	{
		return commKey;
	}

	/**
	 * Sets the allocated composite identity key.
	 *
	 * @param commKey the key ({@code CommKey})
	 */
	public void setCommKey(CommKey commKey)
	{
		this.commKey = commKey;
	}

	/**
	 * Returns the account type.
	 *
	 * @return the account type ({@code CommAccType})
	 */
	public String getCommAccType()
	{
		return commAccType;
	}

	/**
	 * Sets the account type.
	 *
	 * @param commAccType the account type ({@code CommAccType})
	 */
	public void setCommAccType(String commAccType)
	{
		this.commAccType = commAccType;
	}

	/**
	 * Returns the interest rate.
	 *
	 * @return the interest rate ({@code CommIntRt})
	 */
	public BigDecimal getCommInterestRate()
	{
		return commInterestRate;
	}

	/**
	 * Sets the interest rate.
	 *
	 * @param commInterestRate the interest rate ({@code CommIntRt})
	 */
	public void setCommInterestRate(BigDecimal commInterestRate)
	{
		this.commInterestRate = commInterestRate;
	}

	/**
	 * Returns the date opened as an 8-digit {@code DDMMYYYY} integer.
	 *
	 * @return the date opened ({@code CommOpened})
	 */
	public Integer getCommOpened()
	{
		return commOpened;
	}

	/**
	 * Sets the date opened as an 8-digit {@code DDMMYYYY} integer.
	 *
	 * @param commOpened the date opened ({@code CommOpened})
	 */
	public void setCommOpened(Integer commOpened)
	{
		this.commOpened = commOpened;
	}

	/**
	 * Returns the overdraft limit.
	 *
	 * @return the overdraft limit ({@code CommOverdrLim})
	 */
	public Integer getCommOverdraftLimit()
	{
		return commOverdraftLimit;
	}

	/**
	 * Sets the overdraft limit.
	 *
	 * @param commOverdraftLimit the overdraft limit ({@code CommOverdrLim})
	 */
	public void setCommOverdraftLimit(Integer commOverdraftLimit)
	{
		this.commOverdraftLimit = commOverdraftLimit;
	}

	/**
	 * Returns the last-statement date as an 8-digit {@code DDMMYYYY} integer.
	 *
	 * @return the last-statement date ({@code CommLastStmtDt})
	 */
	public Integer getCommLastStatementDate()
	{
		return commLastStatementDate;
	}

	/**
	 * Sets the last-statement date as an 8-digit {@code DDMMYYYY} integer.
	 *
	 * @param commLastStatementDate the last-statement date ({@code CommLastStmtDt})
	 */
	public void setCommLastStatementDate(Integer commLastStatementDate)
	{
		this.commLastStatementDate = commLastStatementDate;
	}

	/**
	 * Returns the next-statement date as an 8-digit {@code DDMMYYYY} integer.
	 *
	 * @return the next-statement date ({@code CommNextStmtDt})
	 */
	public Integer getCommNextStatementDate()
	{
		return commNextStatementDate;
	}

	/**
	 * Sets the next-statement date as an 8-digit {@code DDMMYYYY} integer.
	 *
	 * @param commNextStatementDate the next-statement date ({@code CommNextStmtDt})
	 */
	public void setCommNextStatementDate(Integer commNextStatementDate)
	{
		this.commNextStatementDate = commNextStatementDate;
	}

	/**
	 * Returns the available (cleared) balance.
	 *
	 * @return the available balance ({@code CommAvailBal})
	 */
	public BigDecimal getCommAvailableBalance()
	{
		return commAvailableBalance;
	}

	/**
	 * Sets the available (cleared) balance.
	 *
	 * @param commAvailableBalance the available balance ({@code CommAvailBal})
	 */
	public void setCommAvailableBalance(BigDecimal commAvailableBalance)
	{
		this.commAvailableBalance = commAvailableBalance;
	}

	/**
	 * Returns the actual (pending) balance.
	 *
	 * @return the actual balance ({@code CommActBal})
	 */
	public BigDecimal getCommActualBalance()
	{
		return commActualBalance;
	}

	/**
	 * Sets the actual (pending) balance.
	 *
	 * @param commActualBalance the actual balance ({@code CommActBal})
	 */
	public void setCommActualBalance(BigDecimal commActualBalance)
	{
		this.commActualBalance = commActualBalance;
	}

	/**
	 * Returns the success flag.
	 *
	 * @return the success flag ({@code CommSuccess})
	 */
	public String getCommSuccess()
	{
		return commSuccess;
	}

	/**
	 * Sets the success flag.
	 *
	 * @param commSuccess the success flag ({@code CommSuccess})
	 */
	public void setCommSuccess(String commSuccess)
	{
		this.commSuccess = commSuccess;
	}

	/**
	 * Returns the fail code.
	 *
	 * @return the fail code ({@code CommFailCode})
	 */
	public String getCommFailCode()
	{
		return commFailCode;
	}

	/**
	 * Sets the fail code.
	 *
	 * @param commFailCode the fail code ({@code CommFailCode})
	 */
	public void setCommFailCode(String commFailCode)
	{
		this.commFailCode = commFailCode;
	}

	/**
	 * Returns a diagnostic string representation of this envelope. Not part of the
	 * JSON wire contract; intended for logging and debugging only and built
	 * entirely from local fields with no external dependencies.
	 *
	 * @return a human-readable representation of the create-account commarea
	 */
	@Override
	public String toString()
	{
		return "CreaccJson [CommEyecatcher=" + commEyecatcher + ", CommCustno="
				+ commCustno + ", CommKey=" + commKey + ", CommAccType="
				+ commAccType + ", CommIntRt=" + commInterestRate
				+ ", CommOpened=" + commOpened + ", CommOverdrLim="
				+ commOverdraftLimit + ", CommLastStmtDt="
				+ commLastStatementDate + ", CommNextStmtDt="
				+ commNextStatementDate + ", CommAvailBal="
				+ commAvailableBalance + ", CommActBal=" + commActualBalance
				+ ", CommSuccess=" + commSuccess + ", CommFailCode="
				+ commFailCode + "]";
	}

}
