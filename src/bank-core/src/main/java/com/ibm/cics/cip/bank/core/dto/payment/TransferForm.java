/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.core.dto.payment;

import java.math.BigDecimal;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Inbound request / input form for the debit-credit payment operation
 * (legacy COBOL program {@code DBCRFUN}, copybook {@code PAYDBCR.cpy}).
 *
 * <p>This is a plain, mutable POJO that captures the user-supplied input for a
 * single debit or credit movement before it is mapped onto the frozen
 * {@code PAYDBCR} wire payload. It is a faithful 1:1 port of the legacy
 * payment-interface form {@code TransferForm} of the same name, carried forward
 * with a single, mandatory modernization: the monetary {@code amount} field is now
 * {@link java.math.BigDecimal} rather than the legacy single-precision binary
 * numeric type. This satisfies the binding money rule that requires fixed-point
 * decimal arithmetic for all money and forbids binary numeric types &mdash; the
 * magnitude captured here flows downstream into the scale-2 /
 * {@code RoundingMode.HALF_UP} money pipeline.</p>
 *
 * <p><strong>This class is NOT part of the frozen wire contract.</strong> It is
 * an input form, not a {@code PAYDBCR} envelope DTO, and therefore it carries
 * <em>no</em> Jackson annotations and is <em>not</em> bound to the envelope
 * naming strategy. The envelope strategy strips the first three characters of
 * each member name and would corrupt these form fields (for example
 * {@code acctNumber} would become {@code tNumber}); it is confined to the wire
 * envelope DTOs ({@code PaymentJson}, {@code DbcrJson}, {@code OriginJson}). The
 * plain field names ({@code acctNumber}, {@code debit}, {@code amount},
 * {@code organisation}) are preserved so that framework form binding behaves
 * exactly as it did in the legacy module.</p>
 *
 * <p>The Bean Validation constraints reproduce the legacy / BMS field-validation
 * rules (feature F-021) and mirror the COBOL field widths declared in
 * {@code PAYDBCR.cpy}: {@code acctNumber} maps to {@code COMM-ACCNO PIC X(8)},
 * {@code amount} maps to {@code COMM-AMT PIC S9(10)V99}, and
 * {@code organisation} maps to {@code COMM-ORIGIN}'s
 * {@code COMM-APPLID PIC X(8)} plus {@code COMM-USERID PIC X(8)} (16
 * characters combined). They preserve parity with the legacy behavior and do
 * not introduce new business rules.</p>
 *
 * <p>The {@code debit} flag follows the sign convention documented in the
 * migration plan: {@code true} denotes a debit and {@code false} denotes a
 * credit. The sign is <em>not</em> applied here &mdash; the amount magnitude is
 * captured as-is, and the debit negation is performed downstream when this form
 * is consumed by the {@code DbcrJson(TransferForm)} constructor.</p>
 */
public class TransferForm
{

	/**
	 * Account number the movement applies to.
	 *
	 * <p>Maps to {@code COMM-ACCNO PIC X(8)} in {@code PAYDBCR.cpy}; the
	 * {@link Size} ceiling of 8 reproduces that fixed COBOL width. The value is
	 * zero-padded to its declared width downstream when it is packed onto the
	 * wire payload.</p>
	 */
	@NotNull
	@Size(max = 8)
	private String acctNumber;

	/**
	 * Debit / credit discriminator. {@code true} (the default) denotes a debit;
	 * {@code false} denotes a credit.
	 *
	 * <p>Kept as a primitive {@code boolean} defaulting to {@code true} to match
	 * the legacy form. It deliberately carries no {@link NotNull} annotation: a
	 * primitive can never be {@code null}, so the constraint would be a no-op
	 * (the legacy annotation on this field was vestigial). The sign implied by
	 * this flag is applied later, when the form is transformed into the wire
	 * payload &mdash; not within this form.</p>
	 */
	private boolean debit = true;

	/**
	 * Monetary amount of the movement, as a positive magnitude.
	 *
	 * <p>Maps to {@code COMM-AMT PIC S9(10)V99} in {@code PAYDBCR.cpy}. Modeled
	 * as a {@link java.math.BigDecimal} (never a binary numeric type) so that the
	 * value enters the fixed-point money pipeline without binary rounding error.
	 * The {@link Digits} constraint of
	 * {@code integer = 10, fraction = 2} faithfully mirrors the COBOL
	 * {@code S9(10)V99} capacity &mdash; ten integer digits and two fractional
	 * digits &mdash; preserving parity rather than tightening the rule.</p>
	 */
	@NotNull
	@Digits(integer = 10, fraction = 2)
	private BigDecimal amount;

	/**
	 * Originating organisation identifier.
	 *
	 * <p>Maps to {@code COMM-ORIGIN} in {@code PAYDBCR.cpy}; the
	 * {@link Size} ceiling of 16 reflects the combined width of
	 * {@code COMM-APPLID PIC X(8)} and {@code COMM-USERID PIC X(8)}, into which
	 * this value is split when packed onto the wire payload.</p>
	 */
	@NotNull
	@Size(max = 16)
	private String organisation;

	/**
	 * Creates an empty form. Required for framework form binding and
	 * deserialization, which instantiate the object and then populate it through
	 * the setters. The {@link #debit} flag retains its default of {@code true}.
	 */
	public TransferForm()
	{

	}

	/**
	 * Creates a form with the three caller-supplied values, leaving the
	 * {@link #debit} flag at its default of {@code true} (a debit). Mirrors the
	 * legacy three-argument constructor.
	 *
	 * @param acctNumber   the account number (maximum 8 characters)
	 * @param amount       the movement amount as a {@link java.math.BigDecimal}
	 *                     (maximum 10 integer and 2 fractional digits)
	 * @param organisation the originating organisation identifier (maximum 16
	 *                     characters)
	 */
	public TransferForm(@NotNull @Size(max = 8) String acctNumber,
			@NotNull @Digits(integer = 10, fraction = 2) BigDecimal amount,
			@NotNull @Size(max = 16) String organisation)
	{
		this.acctNumber = acctNumber;
		this.amount = amount;
		this.organisation = organisation;
	}

	/**
	 * @return the account number the movement applies to
	 */
	public String getAcctNumber()
	{
		return acctNumber;
	}

	/**
	 * @param acctNumber the account number the movement applies to
	 */
	public void setAcctNumber(String acctNumber)
	{
		this.acctNumber = acctNumber;
	}

	/**
	 * @return {@code true} if the movement is a debit, {@code false} if it is a
	 *         credit
	 */
	public boolean isDebit()
	{
		return debit;
	}

	/**
	 * Sets the debit / credit flag directly.
	 *
	 * @param debit {@code true} for a debit, {@code false} for a credit
	 */
	public void setDebit(boolean debit)
	{
		this.debit = debit;
	}

	/**
	 * Sets the debit / credit flag from a textual movement type. The flag is set
	 * to {@code true} only when the supplied type is exactly {@code "Debit"};
	 * any other value (including {@code null}) sets it to {@code false}.
	 *
	 * <p>The comparison is expressed as {@code "Debit".equals(type)} so that a
	 * {@code null} argument is handled safely without throwing a
	 * {@link NullPointerException}, while preserving the legacy literal mapping.</p>
	 *
	 * @param type the movement type; {@code "Debit"} maps to {@code true}, all
	 *             other values (and {@code null}) map to {@code false}
	 */
	public void setDebit(String type)
	{
		this.debit = "Debit".equals(type);
	}

	/**
	 * @return the movement amount as a {@link java.math.BigDecimal}
	 */
	public BigDecimal getAmount()
	{
		return amount;
	}

	/**
	 * @param amount the movement amount as a {@link java.math.BigDecimal}
	 */
	public void setAmount(BigDecimal amount)
	{
		this.amount = amount;
	}

	/**
	 * @return the originating organisation identifier
	 */
	public String getOrganisation()
	{
		return organisation;
	}

	/**
	 * @param organisation the originating organisation identifier
	 */
	public void setOrganisation(String organisation)
	{
		this.organisation = organisation;
	}

	/**
	 * Renders the form's account number, amount, and organisation. The
	 * {@link #debit} flag is intentionally omitted to match the legacy
	 * {@code toString} output.
	 *
	 * @return a human-readable representation of this form
	 */
	@Override
	public String toString()
	{
		return "TransferForm [acctNumber=" + acctNumber + ", amount=" + amount
				+ ", organisation=" + organisation + "]";
	}
}
