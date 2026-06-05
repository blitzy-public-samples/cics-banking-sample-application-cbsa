/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.core.dto.accountenquiry;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request input form for the inquire-account flow (legacy COBOL program
 * {@code INQACC}, interface copybooks {@code INQACC.cpy} / {@code INQACCZ.cpy}),
 * exposed by the {@code inqaccz} (INQUIRE ACCOUNT) operation.
 *
 * <p>This is a plain, mutable JavaBean used for Spring MVC request binding and
 * Jakarta Bean Validation. It captures the single user-supplied input for an
 * INQUIRE ACCOUNT request &mdash; the account <em>number</em> that identifies
 * the record to retrieve. It is a faithful 1:1 port of the legacy
 * customer-services form of the same name, carried forward with a single
 * deliberate tightening of validation (the digit pattern described below) and a
 * cosmetic fix to {@link #toString()}.</p>
 *
 * <p><strong>This class is NOT part of the frozen wire contract.</strong> It is
 * a request input form, not a wire-envelope DTO, so it deliberately carries
 * <em>no</em> Jackson serialization annotations and is bound to no envelope
 * naming strategy. The frozen JSON envelope for the inquire-account operation
 * lives on the wire DTOs; keeping Jackson annotations off this form prevents
 * accidental coupling to the wire contract. The class is likewise not a JPA
 * entity and is never persisted &mdash; the controller validates an instance of
 * this form, then the service builds the response payload from it.</p>
 *
 * <p>The {@code acctNumber} field is held as a {@link String} rather than a
 * numeric type so that the leading zeros of the display-numeric account number
 * (COBOL {@code INQACC-ACCNO PIC 9(8)}) are preserved and so that the
 * fixed-width sentinel value remains expressible.</p>
 *
 * <p>The Bean Validation constraints reproduce the legacy / BMS field-validation
 * rules (feature F-021): the value is required ({@link NotNull}), is at most 8
 * characters long ({@link Size}), and contains digits only ({@link Pattern}
 * {@code \d{1,8}}). The digit pattern carries forward the COBOL numeric field
 * rule while remaining <strong>sentinel-safe</strong>: the INQACC sentinel
 * {@code 99999999} (highest account), per feature F-009, satisfies the pattern.
 * No numeric-range ({@code @Min} / {@code @Max}) constraint is applied, because
 * such a bound would reject the all-nines sentinel or reject leading zeros and
 * thereby break behavioral parity.</p>
 */
public class AccountEnquiryForm
{

	/**
	 * Account number that identifies the record to inquire on.
	 *
	 * <p>Maps to {@code INQACC-ACCNO PIC 9(8)} in the INQUIRE ACCOUNT
	 * interface. Held as a {@link String} to preserve display-numeric leading
	 * zeros and the fixed-width sentinel value. The constraints require a
	 * non-{@code null} ({@link NotNull}) value of at most 8 characters
	 * ({@link Size}) consisting of digits only ({@link Pattern} {@code \d{1,8}}).
	 * The pattern is deliberately sentinel-safe: it accepts {@code 99999999}
	 * (highest-account sentinel) as well as any 1- to 8-digit value (including
	 * leading-zero values such as {@code 00000001}), while rejecting non-digit
	 * characters and values longer than eight digits.</p>
	 */
	@NotNull
	@Size(max = 8)
	@Pattern(regexp = "\\d{1,8}", message = "Account number must be 1 to 8 digits")
	private String acctNumber;

	/**
	 * Creates an empty form. Required for framework form binding and
	 * deserialization, which instantiate the object and then populate it through
	 * the setter.
	 */
	public AccountEnquiryForm()
	{

	}

	/**
	 * Creates a form with the supplied account number. Mirrors the legacy
	 * single-argument constructor and carries the same validation annotations on
	 * the parameter.
	 *
	 * @param acctNumber the account number to inquire on; 1 to 8 digits, with the
	 *                   highest-account sentinel {@code 99999999} permitted
	 */
	public AccountEnquiryForm(
			@NotNull @Size(max = 8) @Pattern(regexp = "\\d{1,8}") String acctNumber)
	{
		this.acctNumber = acctNumber;
	}

	/**
	 * @return the account number to inquire on
	 */
	public String getAcctNumber()
	{
		return acctNumber;
	}

	/**
	 * @param acctNumber the account number to inquire on
	 */
	public void setAcctNumber(String acctNumber)
	{
		this.acctNumber = acctNumber;
	}

	/**
	 * Renders the form's account number.
	 *
	 * <p>The label is {@code AccountEnquiryForm}, correcting a legacy copy-paste
	 * artifact in the source form whose {@code toString} returned a
	 * {@code "TransferForm [...]"} label. The fix is cosmetic and has no effect
	 * on the wire contract.</p>
	 *
	 * @return a human-readable representation of this form
	 */
	@Override
	public String toString()
	{
		return "AccountEnquiryForm [acctNumber=" + acctNumber + "]";
	}
}
