/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.core.dto.customerenquiry;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request input form for the inquire-customer flow (legacy COBOL program
 * {@code INQCUST}, interface copybooks {@code INQCUST.cpy} / {@code INQCUSTZ.cpy}),
 * exposed by the {@code inqcustz} (INQUIRE CUSTOMER) operation.
 *
 * <p>This is a plain, mutable JavaBean used for Spring MVC request binding and
 * Jakarta Bean Validation. It captures the single user-supplied input for an
 * INQUIRE CUSTOMER request &mdash; the customer <em>number</em> that identifies
 * the record to retrieve. It is a faithful 1:1 port of the legacy
 * customer-services form of the same name, carried forward with a single
 * deliberate tightening of validation (the digit pattern described below) and a
 * cosmetic fix to {@link #toString()}.</p>
 *
 * <p><strong>This class is NOT part of the frozen wire contract.</strong> It is a
 * request input form, not a wire-envelope DTO, so it deliberately carries
 * <em>no</em> Jackson serialization annotations and is bound to no envelope
 * naming strategy. The frozen JSON envelope for the inquire-customer operation
 * lives on the wire DTOs; keeping Jackson annotations off this form prevents
 * accidental coupling to the wire contract. The class is likewise not a JPA
 * entity and is never persisted &mdash; the controller validates an instance of
 * this form, then the service builds the response payload from it.</p>
 *
 * <p>The {@code custNumber} field is held as a {@link String} rather than a
 * numeric type so that the leading zeros of the display-numeric customer number
 * (COBOL {@code INQCUST-CUSTNO PIC 9(10)}) are preserved and so that the
 * fixed-width sentinel values remain expressible.</p>
 *
 * <p>The Bean Validation constraints reproduce the legacy / BMS field-validation
 * rules (feature F-021): the value is required ({@link NotNull}), is 1 to 10
 * characters long ({@link Size}), and contains digits only ({@link Pattern}
 * {@code \d{1,10}}). The digit pattern carries forward the COBOL numeric field
 * rule while remaining <strong>sentinel-safe</strong>: the INQCUST sentinels
 * {@code 0000000000} (random pick) and {@code 9999999999} (highest customer),
 * per feature F-008, both satisfy the pattern. No numeric-range
 * ({@code @Min} / {@code @Max}) constraint is applied, because such a bound
 * would reject the all-zeros sentinel and break behavioral parity.</p>
 */
public class CustomerEnquiryForm
{

	/**
	 * Customer number that identifies the record to inquire on.
	 *
	 * <p>Maps to {@code INQCUST-CUSTNO PIC 9(10)} in the INQUIRE CUSTOMER
	 * interface. Held as a {@link String} to preserve display-numeric leading
	 * zeros and the fixed-width sentinel values. The constraints require a
	 * non-{@code null} ({@link NotNull}) value of 1 to 10 characters
	 * ({@link Size}) consisting of digits only ({@link Pattern}
	 * {@code \d{1,10}}). The pattern is deliberately sentinel-safe: it accepts
	 * {@code 0000000000} (random-pick sentinel) and {@code 9999999999}
	 * (highest-customer sentinel), as well as any 1- to 10-digit value, while
	 * rejecting non-digit characters and values longer than ten digits.</p>
	 */
	@NotNull
	@Size(min = 1, max = 10)
	@Pattern(regexp = "\\d{1,10}", message = "Customer number must be 1 to 10 digits")
	private String custNumber;

	/**
	 * Creates an empty form. Required for framework form binding and
	 * deserialization, which instantiate the object and then populate it through
	 * the setter.
	 */
	public CustomerEnquiryForm()
	{

	}

	/**
	 * Creates a form with the supplied customer number. Mirrors the legacy
	 * single-argument constructor and carries the same validation annotations on
	 * the parameter.
	 *
	 * @param custNumber the customer number to inquire on; 1 to 10 digits, with
	 *                   the sentinel values {@code 0000000000} and
	 *                   {@code 9999999999} permitted
	 */
	public CustomerEnquiryForm(
			@NotNull @Size(min = 1, max = 10) @Pattern(regexp = "\\d{1,10}") String custNumber)
	{
		this.custNumber = custNumber;
	}

	/**
	 * @return the customer number to inquire on
	 */
	public String getCustNumber()
	{
		return custNumber;
	}

	/**
	 * @param custNumber the customer number to inquire on
	 */
	public void setCustNumber(String custNumber)
	{
		this.custNumber = custNumber;
	}

	/**
	 * Renders the form's customer number.
	 *
	 * <p>The label is {@code CustomerEnquiryForm}, correcting a legacy
	 * copy-paste artifact in the source form whose {@code toString} returned a
	 * {@code "TransferForm [...]"} label. The fix is cosmetic and has no effect
	 * on the wire contract.</p>
	 *
	 * @return a human-readable representation of this form
	 */
	@Override
	public String toString()
	{
		return "CustomerEnquiryForm [custNumber=" + custNumber + "]";
	}
}
