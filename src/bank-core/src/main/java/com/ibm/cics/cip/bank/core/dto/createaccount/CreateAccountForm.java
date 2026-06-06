/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.core.dto.createaccount;

import java.math.BigDecimal;

import com.ibm.cics.cip.bank.core.domain.AccountType;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request input form for the create-account flow (legacy COBOL program
 * {@code CREACC}, interface copybook {@code CREACC.cpy}), exposed by the
 * {@code creacc} (CREATE ACCOUNT) operation.
 *
 * <p>This is a plain, mutable JavaBean used for Spring MVC request binding and
 * Jakarta Bean Validation. It captures the four client-supplied inputs needed to
 * open an account &mdash; the owning customer number, the account type, the
 * overdraft limit and the interest rate &mdash; and is the object from which the
 * create-account wire envelope ({@code CreateAccountJson} / {@code CreaccJson})
 * is built. It is a faithful port of the legacy customer-services form of the
 * same name, retyped per AAP &sect;0.6 (see <em>Type changes</em> below).</p>
 *
 * <p><strong>This class is NOT part of the frozen wire contract.</strong> It is
 * a request input form, not a wire-envelope DTO, so it deliberately carries
 * <em>no</em> Jackson serialization annotations and is bound to no envelope
 * naming strategy &mdash; matching its sibling
 * {@link com.ibm.cics.cip.bank.core.dto.accountenquiry.AccountEnquiryForm
 * AccountEnquiryForm}. The frozen JSON envelope for the create-account operation
 * lives on the wire DTOs ({@code CreaccJson}); keeping Jackson annotations off
 * this form prevents accidental coupling to the wire contract. The class is
 * likewise not a JPA entity and is never persisted &mdash; a controller
 * validates an instance of this form and the service then performs the COBOL
 * {@code CREACC} create sequence from its values.</p>
 *
 * <h2>Type changes from the legacy form (AAP &sect;0.6)</h2>
 * <ul>
 *   <li>{@code accountType} is typed as the canonical {@link AccountType} enum
 *       ({@code com.ibm.cics.cip.bank.core.domain.AccountType}) rather than the
 *       legacy module-local enum; enum binding guarantees only one of
 *       {@code ISA, MORTGAGE, SAVING, CURRENT, LOAN} can be supplied, so an
 *       unknown value yields a bind / validation error &mdash; reproducing the
 *       {@code CREACC} fail code {@code 'A'} guard at the edge.</li>
 *   <li>{@code overdraftLimit} is a boxed {@link Integer} (legacy {@code int}),
 *       because the overdraft limit ({@code COMM-OVERDR-LIM PIC 9(8)}) has no
 *       decimal places; it is never a floating-point type.</li>
 *   <li>{@code interestRate} is a {@link BigDecimal} (a legacy
 *       floating-point field), honouring the AAP money rule that every monetary
 *       / fixed-point value is a {@code BigDecimal}; floating-point types are
 *       prohibited throughout the financial logic.</li>
 *   <li>{@code custNumber} remains a {@link String}, with its size constraint
 *       widened from the legacy eight to ten to match the copybook
 *       ({@code COMM-CUSTNO PIC 9(10)}) and the cross-cutting "customer number
 *       is ten digits" rule.</li>
 * </ul>
 *
 * <h2>Validation (feature F-021)</h2>
 * <p>The Bean Validation constraints reproduce the legacy / BMS field-validation
 * rules and are derived from the {@code CREACC.cpy} field widths. They are kept
 * faithful to the COBOL-acceptable ranges &mdash; deliberately no stricter
 * &mdash; so that the form never rejects a value the COBOL program would accept.
 * No sentinel handling applies here, because creating an account requires a
 * real, existing customer number.</p>
 */
public class CreateAccountForm
{

	/**
	 * Owning customer number that the new account is created against.
	 *
	 * <p>Maps to {@code COMM-CUSTNO PIC 9(10)} in the CREATE ACCOUNT interface.
	 * Held as a {@link String} to preserve the display-numeric leading zeros of
	 * the ten-digit customer number. The constraints require a non-{@code null}
	 * ({@link NotNull}) value of at most ten characters ({@link Size}) consisting
	 * of one to ten digits only ({@link Pattern} {@code \d{1,10}}); a blank or
	 * non-numeric value fails binding. The width is widened from the legacy
	 * eight to ten to match the copybook and the cross-cutting customer-number
	 * rule. There is no sentinel handling: a create requires a real, existing
	 * customer number.</p>
	 */
	@NotNull
	@Size(max = 10)
	@Pattern(regexp = "\\d{1,10}", message = "Customer number must be 1 to 10 digits")
	private String custNumber;

	/**
	 * Type of account to create.
	 *
	 * <p>Maps to {@code COMM-ACC-TYPE PIC X(8)} in the CREATE ACCOUNT interface
	 * and is typed as the canonical {@link AccountType} enum, so only one of
	 * {@code ISA, MORTGAGE, SAVING, CURRENT, LOAN} can be bound. The value is
	 * required ({@link NotNull}); a {@code null} or otherwise unbindable value
	 * fails validation, reproducing the {@code CREACC} fail code {@code 'A'}
	 * (invalid account type) guard at the edge of the application.</p>
	 */
	@NotNull
	private AccountType accountType;

	/**
	 * Overdraft limit requested for the new account, in whole currency units.
	 *
	 * <p>Maps to {@code COMM-OVERDR-LIM PIC 9(8)} &mdash; an unsigned, eight-digit
	 * field with no decimal places &mdash; and is therefore a boxed
	 * {@link Integer}, never a floating-point type. The constraints require a
	 * non-{@code null} ({@link NotNull}) value in the inclusive range
	 * {@code 0 .. 99999999} ({@link Min} / {@link Max}), matching the copybook
	 * width.</p>
	 */
	@NotNull
	@Min(0)
	@Max(99999999)
	private Integer overdraftLimit;

	/**
	 * Interest rate to apply to the new account.
	 *
	 * <p>Maps to {@code COMM-INT-RT PIC 9(4)V99} &mdash; four integer digits and
	 * two fractional digits &mdash; and is held as a {@link BigDecimal} in
	 * accordance with the AAP money rule (no floating-point types). The
	 * constraints require a non-{@code null} ({@link NotNull}) value in the
	 * inclusive range {@code 0.00 .. 9999.99} ({@link DecimalMin} /
	 * {@link DecimalMax}) with at most four integer and two fraction digits
	 * ({@link Digits}).</p>
	 */
	@NotNull
	@DecimalMin(value = "0.00")
	@DecimalMax(value = "9999.99")
	@Digits(integer = 4, fraction = 2)
	private BigDecimal interestRate;

	/**
	 * Creates an empty form. Required for framework form binding and
	 * deserialization, which instantiate the object and then populate it through
	 * the setters.
	 */
	public CreateAccountForm()
	{
		super();
	}

	/**
	 * Creates a fully populated form. Mirrors the legacy convenience constructor
	 * (retyped per AAP &sect;0.6) and is primarily useful for tests and for
	 * programmatic construction of a request.
	 *
	 * @param custNumber     the owning customer number; one to ten digits
	 * @param accountType    the account type; one of
	 *                       {@code ISA, MORTGAGE, SAVING, CURRENT, LOAN}
	 * @param overdraftLimit the overdraft limit in whole units;
	 *                       {@code 0 .. 99999999}
	 * @param interestRate   the interest rate; {@code 0.00 .. 9999.99} with at
	 *                       most two fraction digits
	 */
	public CreateAccountForm(String custNumber, AccountType accountType,
			Integer overdraftLimit, BigDecimal interestRate)
	{
		this.custNumber = custNumber;
		this.accountType = accountType;
		this.overdraftLimit = overdraftLimit;
		this.interestRate = interestRate;
	}

	/**
	 * Returns the owning customer number.
	 *
	 * @return the customer number (one to ten digits)
	 */
	public String getCustNumber()
	{
		return custNumber;
	}

	/**
	 * Sets the owning customer number.
	 *
	 * @param custNumber the customer number (one to ten digits)
	 */
	public void setCustNumber(String custNumber)
	{
		this.custNumber = custNumber;
	}

	/**
	 * Returns the account type.
	 *
	 * @return the account type
	 */
	public AccountType getAccountType()
	{
		return accountType;
	}

	/**
	 * Sets the account type.
	 *
	 * @param accountType the account type
	 */
	public void setAccountType(AccountType accountType)
	{
		this.accountType = accountType;
	}

	/**
	 * Returns the requested overdraft limit.
	 *
	 * @return the overdraft limit in whole units
	 */
	public Integer getOverdraftLimit()
	{
		return overdraftLimit;
	}

	/**
	 * Sets the requested overdraft limit.
	 *
	 * @param overdraftLimit the overdraft limit in whole units
	 */
	public void setOverdraftLimit(Integer overdraftLimit)
	{
		this.overdraftLimit = overdraftLimit;
	}

	/**
	 * Returns the interest rate.
	 *
	 * @return the interest rate
	 */
	public BigDecimal getInterestRate()
	{
		return interestRate;
	}

	/**
	 * Sets the interest rate.
	 *
	 * @param interestRate the interest rate
	 */
	public void setInterestRate(BigDecimal interestRate)
	{
		this.interestRate = interestRate;
	}

	/**
	 * Renders the form's four fields for logging and debugging. Uses no external
	 * dependencies.
	 *
	 * @return a human-readable representation of this form
	 */
	@Override
	public String toString()
	{
		return "CreateAccountForm [accountType=" + accountType + ", custNumber="
				+ custNumber + ", interestRate=" + interestRate
				+ ", overdraftLimit=" + overdraftLimit + "]";
	}

}
