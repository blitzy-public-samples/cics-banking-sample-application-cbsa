/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.core.dto.payment;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.ibm.cics.cip.bank.core.config.JacksonConfig;

/**
 * Frozen z/OS Connect <em>debit/credit</em> payload ({@code DbcrJson}) &mdash;
 * the inner object of the {@code PAYDBCR} envelope on the make-payment endpoint.
 * It is reproduced field-for-field from the Payment-Interface module's class of
 * the same name (feature F-019) and maps the {@code PAYDBCR.cpy} copybook
 * record. Request and response share this exact shape: the single
 * {@code makepayment} {@code Pay} service uses the identical
 * {@code PayRequest.json} and {@code PayResponse.json} schemas, so one class
 * serves both directions. It is nested inside {@link PaymentInterfaceJson} under
 * the {@code PAYDBCR} key.
 *
 * <h2>Wire contract is frozen and reproduced verbatim</h2>
 * <p>The class is annotated {@link JsonNaming @JsonNaming} with
 * {@link JacksonConfig.EnvelopeNamingStrategy} (the {@code substring(3)} envelope
 * strategy) for consistency with the other {@code core/dto} envelopes; in
 * addition every field carries an explicit {@link JsonProperty @JsonProperty}
 * pinning its verbatim wire name ({@code CommAccno}, {@code CommAmt},
 * {@code mSortC}, {@code CommAvBal}, {@code CommActBal}, {@code CommOrigin},
 * {@code CommSuccess}, {@code CommFailCode}). Because the explicit
 * {@code @JsonProperty} always wins over the naming strategy, the serialised
 * object is byte-for-byte compatible with the frozen
 * {@code PayRequest.json}/{@code PayResponse.json} contract. Declaring the same
 * name on the field and on its standard getter/setter merges them into a single
 * Jackson property, avoiding any {@code substring(3)} phantom-property issue.</p>
 *
 * <h2>Money fidelity (binding rule &sect;0.6/&sect;0.7)</h2>
 * <p>The three monetary fields {@code CommAmt}, {@code CommAvBal}, and
 * {@code CommActBal} map the COBOL fixed-point {@code S9(10)V99} fields and are
 * therefore modelled as {@link BigDecimal} at scale&nbsp;2 with
 * {@link RoundingMode#HALF_UP}. The legacy interface module carried the amount
 * as a binary single-precision number and the balances as integers; this
 * migration <em>must</em> replace that binary numeric typing &mdash;
 * <strong>no binary fractional numeric type appears anywhere in this
 * file</strong>. The three fields are initialised to
 * {@code new BigDecimal("0.00")} so a
 * freshly-constructed payload serialises {@code 0.00} (matching the legacy
 * default-zero behaviour and the {@code multipleOf 0.01} schema rather than the
 * scale-0 {@code BigDecimal.ZERO}, which would emit a bare {@code 0}).</p>
 *
 * <h2>Two independent balances</h2>
 * <p>{@code CommAvBal} (available) and {@code CommActBal} (actual) are
 * deliberately separate fields modelling cleared versus pending funds; they must
 * never be collapsed into one column (&sect;0.6). On a successful response
 * {@code PaymentService} populates <em>both</em> with the post-movement
 * balances.</p>
 *
 * <h2>Sign convention</h2>
 * <p>{@code CommAmt} is signed: a <strong>negative</strong> amount is a debit
 * (COBOL transaction types {@code DEB}/{@code PDR}) and a <strong>positive</strong>
 * amount is a credit ({@code CRE}/{@code PCR}). The sign is applied once, in the
 * {@link #DbcrJson(TransferForm)} constructor (debit &rArr; {@code negate()}),
 * and must never be stripped downstream.</p>
 *
 * <h2>Sort code type fidelity ({@code mSortC})</h2>
 * <p>{@code COMM-SORTC PIC 9(6)} is modelled as an {@link Integer} (not a
 * zero-padded {@code String}) because the frozen schema declares {@code mSortC}
 * as {@code {"type":"integer","minimum":0,"maximum":999999}}. A {@code String}
 * would serialise as the quoted {@code "mSortC":"987654"} and fail integer
 * schema validation, whereas an {@code Integer} renders as the bare JSON number
 * the contract requires. The wire <em>name</em> remains literally
 * {@code "mSortC"} (lower-case {@code m}, capital {@code S}/{@code C}); the
 * explicit {@code @JsonProperty("mSortC")} pins it.</p>
 *
 * <h2>Responsibility boundary</h2>
 * <p>This is a plain, mutable POJO with no business logic and no Spring
 * stereotype. The debit/credit posting, the facility-type-496 channel
 * restrictions, the overdraft / insufficient-funds checks, and the population of
 * {@code CommSuccess}/{@code CommFailCode} are all the responsibility of
 * {@code PaymentService} (the {@code DBCRFUN} port); this DTO only carries the
 * values across the wire. Note that on a response the consumer parses
 * {@code CommFailCode} with {@code Integer.parseInt(...)}, so the payment
 * controller writes a numeric code ({@code "0"} = success); the request-side
 * default below is a single space, matching the legacy envelope.</p>
 *
 * @see JacksonConfig.EnvelopeNamingStrategy
 * @see OriginJson
 * @see TransferForm
 * @see PaymentInterfaceJson
 */
@JsonNaming(JacksonConfig.EnvelopeNamingStrategy.class)
public class DbcrJson
{

	/**
	 * Account number the movement applies to. {@code COMM-ACCNO PIC X(8)};
	 * zero-padded to width&nbsp;8 by the {@link #DbcrJson(TransferForm)}
	 * constructor. Left {@code null} by the no-arg constructor.
	 */
	@JsonProperty("CommAccno")
	private String commAccno;

	/**
	 * Signed movement amount. {@code COMM-AMT PIC S9(10)V99} &rarr;
	 * {@link BigDecimal} at scale&nbsp;2. Negative denotes a debit, positive a
	 * credit. Initialised to {@code 0.00} so an unpopulated payload serialises a
	 * two-decimal zero.
	 */
	@JsonProperty("CommAmt")
	private BigDecimal commAmt = new BigDecimal("0.00");

	/**
	 * Sort code. {@code COMM-SORTC PIC 9(6)} &rarr; {@link Integer} (NOT a
	 * {@code String}): the frozen schema declares {@code mSortC} as
	 * {@code type=integer} in the range {@code 0..999999}, so only an integer
	 * round-trips against the contract. The wire name is literally
	 * {@code "mSortC"}.
	 */
	@JsonProperty("mSortC")
	private Integer commSortC = 0;

	/**
	 * Available (cleared) balance after the movement. {@code COMM-AV-BAL PIC
	 * S9(10)V99} &rarr; {@link BigDecimal} at scale&nbsp;2. Independent of
	 * {@link #commActBal}. Initialised to {@code 0.00}.
	 */
	@JsonProperty("CommAvBal")
	private BigDecimal commAvBal = new BigDecimal("0.00");

	/**
	 * Actual balance after the movement (includes pending funds).
	 * {@code COMM-ACT-BAL PIC S9(10)V99} &rarr; {@link BigDecimal} at
	 * scale&nbsp;2. Independent of {@link #commAvBal}. Initialised to
	 * {@code 0.00}.
	 */
	@JsonProperty("CommActBal")
	private BigDecimal commActBal = new BigDecimal("0.00");

	/**
	 * Calling-channel origin ({@code COMM-ORIGIN} group): carries the facility
	 * type and the origin string ({@code applid} + {@code userid}). Left
	 * {@code null} by the no-arg constructor; built from the inbound organisation
	 * by the {@link #DbcrJson(TransferForm)} constructor.
	 */
	@JsonProperty("CommOrigin")
	private OriginJson commOrigin;

	/**
	 * Success flag. {@code COMM-SUCCESS PIC X}. Defaults to a single space for
	 * wire parity with the legacy request-side envelope; set to the success/failure
	 * flag by {@code PaymentService} on a response.
	 */
	@JsonProperty("CommSuccess")
	private String commSuccess = " ";

	/**
	 * Fail code. {@code COMM-FAIL-CODE PIC X}. Defaults to a single space,
	 * matching the legacy request-side envelope; the payment controller writes a
	 * numeric value ({@code "0"} = success) on a response because the consumer
	 * parses it with {@code Integer.parseInt(...)}.
	 */
	@JsonProperty("CommFailCode")
	private String commFailCode = " ";

	/**
	 * No-argument constructor required for Jackson deserialisation. Leaves
	 * {@link #commAccno} and {@link #commOrigin} {@code null}, the three money
	 * fields at their {@code 0.00} defaults, {@link #commSortC} at {@code 0}, and
	 * {@link #commSuccess}/{@link #commFailCode} at a single space.
	 */
	public DbcrJson()
	{
		// Field defaults preserve the legacy envelope's initial values.
	}

	/**
	 * Builds the wire payload from an inbound {@link TransferForm}, porting the
	 * legacy transformation exactly while modernising the money arithmetic to
	 * {@link BigDecimal}:
	 * <ul>
	 *   <li>the {@link #commOrigin} is constructed from the form's organisation
	 *       ({@code new OriginJson(transferForm.getOrganisation())});</li>
	 *   <li>the {@link #commAccno} is left-zero-padded to width&nbsp;8
	 *       (for example {@code "123"} &rarr; {@code "00000123"});</li>
	 *   <li>the {@link #commAmt} carries the sign convention &mdash; the amount is
	 *       negated for a debit and kept as-is for a credit &mdash; and is then
	 *       normalised to scale&nbsp;2 with {@link RoundingMode#HALF_UP},
	 *       replacing the legacy {@code amount * -1} binary arithmetic.</li>
	 * </ul>
	 *
	 * @param transferForm the inbound payment form supplying the account number,
	 *                      debit/credit flag, amount, and originating organisation
	 */
	public DbcrJson(TransferForm transferForm)
	{
		commOrigin = new OriginJson(transferForm.getOrganisation());

		// Account number: left-pad with zeroes to the fixed COBOL width of 8.
		commAccno = String.format("%8s", transferForm.getAcctNumber())
				.replace(" ", "0");

		// Amount: negate for a debit, keep positive for a credit, then normalise
		// to scale 2 (HALF_UP) so the value enters the fixed-point money pipeline
		// without any binary rounding error.
		BigDecimal amt = transferForm.getAmount();
		commAmt = (transferForm.isDebit() ? amt.negate() : amt)
				.setScale(2, RoundingMode.HALF_UP);
	}

	/**
	 * Returns the account number (zero-padded to width&nbsp;8 when built from a
	 * form).
	 *
	 * @return the account number, or {@code null} if not set
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
	 * Returns the signed movement amount (negative = debit, positive = credit).
	 *
	 * @return the amount as a scale-2 {@link BigDecimal}
	 */
	public BigDecimal getCommAmt()
	{
		return commAmt;
	}

	/**
	 * Sets the signed movement amount.
	 *
	 * @param commAmtIn the amount as a {@link BigDecimal}
	 */
	public void setCommAmt(BigDecimal commAmtIn)
	{
		commAmt = commAmtIn;
	}

	/**
	 * Returns the sort code as an {@link Integer} (wire name {@code mSortC}).
	 *
	 * @return the sort code
	 */
	public Integer getCommSortC()
	{
		return commSortC;
	}

	/**
	 * Sets the sort code.
	 *
	 * @param commSortCIn the sort code as an {@link Integer}
	 */
	public void setCommSortC(Integer commSortCIn)
	{
		commSortC = commSortCIn;
	}

	/**
	 * Returns the available (cleared) balance after the movement.
	 *
	 * @return the available balance as a scale-2 {@link BigDecimal}
	 */
	public BigDecimal getCommAvBal()
	{
		return commAvBal;
	}

	/**
	 * Sets the available (cleared) balance after the movement.
	 *
	 * @param commAvBalIn the available balance as a {@link BigDecimal}
	 */
	public void setCommAvBal(BigDecimal commAvBalIn)
	{
		commAvBal = commAvBalIn;
	}

	/**
	 * Returns the actual balance after the movement (includes pending funds).
	 *
	 * @return the actual balance as a scale-2 {@link BigDecimal}
	 */
	public BigDecimal getCommActBal()
	{
		return commActBal;
	}

	/**
	 * Sets the actual balance after the movement.
	 *
	 * @param commActBalIn the actual balance as a {@link BigDecimal}
	 */
	public void setCommActBal(BigDecimal commActBalIn)
	{
		commActBal = commActBalIn;
	}

	/**
	 * Returns the calling-channel origin.
	 *
	 * @return the origin, or {@code null} if not set
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
	 * @return the success flag (defaults to a single space)
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
	 * @return the fail code (defaults to a single space)
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

	/**
	 * Renders all eight fields of the payload. Null-safe on {@link #commOrigin}
	 * so that a request-side payload built without an origin does not throw.
	 *
	 * @return a human-readable representation of this payload
	 */
	@Override
	public String toString()
	{
		return "DbcrJson [CommAccno=" + commAccno + ", CommAmt=" + commAmt
				+ ", mSortC=" + commSortC + ", CommAvBal=" + commAvBal
				+ ", CommActBal=" + commActBal + ", CommOrigin="
				+ (commOrigin == null ? "null" : commOrigin.toString())
				+ ", CommSuccess=" + commSuccess + ", CommFailCode="
				+ commFailCode + "]";
	}

}
