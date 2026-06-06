/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.core.dto.updateaccount;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

import com.ibm.cics.cip.bank.core.domain.AccountType;

/**
 * Request input form for the update-account flow (legacy COBOL program
 * {@code UPDACC}, interface copybook {@code UPDACC.cpy}), exposed by the
 * {@code updacc} (UPDATE ACCOUNT) operation.
 *
 * <p>This is a plain, mutable JavaBean used for Spring MVC request binding and
 * Jakarta Bean Validation. It is a faithful port of the legacy customer-services
 * form of the same name, retyped to the new module's idioms: the COBOL
 * fixed-point interest rate is a {@link BigDecimal} (never an imprecise
 * IEEE-754 primitive), the overdraft limit is an {@link Integer}, and the
 * account-type validation is driven by the new-module domain enum
 * {@link AccountType} rather than the legacy {@code createaccount.AccountType}.</p>
 *
 * <h2>Restricted update parity (F-012)</h2>
 * <p>{@code UPDACC.cbl} is the authoritative behavioural specification, and the
 * UPDATE ACCOUNT operation is deliberately <strong>RESTRICTED</strong>: it
 * changes only the account <em>type</em>, <em>interest rate</em>, and
 * <em>overdraft limit</em>. It <strong>never</strong> changes either balance and
 * it writes <strong>no</strong> {@code PROCTRAN} record. This form therefore
 * carries only the two identifiers needed to locate the account
 * ({@link #custNumber}, {@link #acctNumber}) plus the three updatable fields
 * ({@link #acctType}, {@link #acctInterestRate}, {@link #acctOverdraft}). The
 * legacy form's opened / last-statement / next-statement dates and its available
 * / actual balances are intentionally omitted, because they are not updatable;
 * the response balances are sourced from the entity by {@code UpdaccJson}, never
 * from this form.</p>
 *
 * <p><strong>This class is NOT part of the frozen wire contract.</strong> It is
 * a request input form, not a wire-envelope DTO, so it deliberately carries
 * <em>no</em> Jackson serialization annotations and is bound to no envelope
 * naming strategy. Keeping Jackson annotations off this form prevents accidental
 * coupling to the wire contract, which lives on the update-account wire DTOs. The
 * class is likewise not a JPA entity and is never persisted &mdash; the
 * controller validates an instance of this form, then the service applies the
 * three updatable values to the located account.</p>
 *
 * <p>The Bean Validation constraints carry the legacy / BMS field-validation
 * rules forward as annotations (feature F-021). All constraints are
 * {@code jakarta.validation.constraints}; the legacy Hibernate
 * {@code @Range(1, 99999999)} on the account number is replaced by the
 * equivalent {@link Min}{@code (1)} / {@link Max}{@code (99999999)} pair.</p>
 */
public class UpdateAccountForm
{

	/**
	 * Customer number that, together with {@link #acctNumber}, identifies the
	 * account to update. Maps to {@code COMM-CUSTNO PIC X(10)} in
	 * {@code UPDACC.cpy}. Held as a {@link String} so the display-numeric leading
	 * zeros of the fixed-width identifier are preserved. Constrained to at most
	 * ten characters ({@link Size}); the legacy form applied no constraint here,
	 * so this is a light, safe tightening that does not reject any previously
	 * accepted in-range value.
	 */
	@Size(max = 10)
	private String custNumber;

	/**
	 * Account number identifying which account to update. Maps to
	 * {@code COMM-ACCNO PIC 9(8)} in {@code UPDACC.cpy}. This is a whole-number
	 * identifier (never money), so it is a primitive {@code int}. The bounds
	 * {@link Min}{@code (1)} / {@link Max}{@code (99999999)} replace the legacy
	 * Hibernate {@code @Range(1, 99999999)} (off the new module's import
	 * allow-list) with the equivalent Jakarta constraints. {@code @NotNull} is
	 * intentionally omitted: it is meaningless on a primitive, and the
	 * {@code @Min(1)} lower bound already rejects an unset / zero value.
	 */
	@Min(1)
	@Max(99999999)
	private int acctNumber;

	/**
	 * New account type &mdash; updatable field #1 (F-012). Maps to
	 * {@code COMM-ACC-TYPE PIC X(8)} in {@code UPDACC.cpy} and is typed as the
	 * new-module domain enum {@link AccountType}
	 * ({@code ISA, MORTGAGE, SAVING, CURRENT, LOAN}), replacing the legacy
	 * {@code createaccount.AccountType} reference. Required ({@link NotNull}); the
	 * validation message is preserved verbatim from the legacy form.
	 */
	@NotNull(message = "You must choose an account type")
	private AccountType acctType;

	/**
	 * New interest rate &mdash; updatable field #2 (F-012). Maps to
	 * {@code COMM-INT-RATE PIC 9(4)V99} in {@code UPDACC.cpy}: up to four integer
	 * digits and exactly two fraction digits (range 0&ndash;9999.99, swagger
	 * {@code multipleOf 0.01}). Modelled as a {@link BigDecimal} at scale 2 per
	 * the money / rate rule (exact decimal arithmetic, not an imprecise IEEE-754
	 * primitive), replacing the legacy {@code String}-plus-parse pattern; Spring
	 * MVC binds the typed value directly. Required ({@link NotNull}) and
	 * shape-checked by {@link Digits}{@code (integer = 4, fraction = 2)}.
	 */
	@NotNull
	@Digits(integer = 4, fraction = 2)
	private BigDecimal acctInterestRate;

	/**
	 * New overdraft limit &mdash; updatable field #3 (F-012). Maps to
	 * {@code COMM-OVERDRAFT PIC 9(8)} in {@code UPDACC.cpy}: a non-negative whole
	 * number of pounds with no decimal places. It is therefore an
	 * {@link Integer} (deliberately not {@code BigDecimal}, because the overdraft
	 * limit is not money), replacing the legacy
	 * {@code String}-plus-{@code Integer.parseInt} pattern. Required
	 * ({@link NotNull}) and constrained non-negative by {@link Min}{@code (0)}.
	 */
	@NotNull
	@Min(0)
	private Integer acctOverdraft;

	/**
	 * Creates an empty form. Required for framework form binding and
	 * deserialization, which instantiate the object and then populate it through
	 * the setters.
	 */
	public UpdateAccountForm()
	{

	}

	/**
	 * @return the customer number that identifies the account to update
	 */
	public String getCustNumber()
	{
		return custNumber;
	}

	/**
	 * @param custNumber the customer number that identifies the account to update
	 */
	public void setCustNumber(String custNumber)
	{
		this.custNumber = custNumber;
	}

	/**
	 * @return the account number that identifies the account to update
	 */
	public int getAcctNumber()
	{
		return acctNumber;
	}

	/**
	 * @param acctNumber the account number that identifies the account to update
	 */
	public void setAcctNumber(int acctNumber)
	{
		this.acctNumber = acctNumber;
	}

	/**
	 * @return the new account type (updatable field #1)
	 */
	public AccountType getAcctType()
	{
		return acctType;
	}

	/**
	 * @param acctType the new account type (updatable field #1)
	 */
	public void setAcctType(AccountType acctType)
	{
		this.acctType = acctType;
	}

	/**
	 * @return the new interest rate (updatable field #2), scale-2 {@link BigDecimal}
	 */
	public BigDecimal getAcctInterestRate()
	{
		return acctInterestRate;
	}

	/**
	 * @param acctInterestRate the new interest rate (updatable field #2)
	 */
	public void setAcctInterestRate(BigDecimal acctInterestRate)
	{
		this.acctInterestRate = acctInterestRate;
	}

	/**
	 * @return the new overdraft limit (updatable field #3), in whole pounds
	 */
	public Integer getAcctOverdraft()
	{
		return acctOverdraft;
	}

	/**
	 * @param acctOverdraft the new overdraft limit (updatable field #3)
	 */
	public void setAcctOverdraft(Integer acctOverdraft)
	{
		this.acctOverdraft = acctOverdraft;
	}

	/**
	 * Renders the form's five fields &mdash; the two identifiers and the three
	 * updatable values. Balances and dates are absent by design (F-012).
	 *
	 * @return a human-readable representation of this form
	 */
	@Override
	public String toString()
	{
		return "UpdateAccountForm [custNumber=" + custNumber + ", acctNumber="
				+ acctNumber + ", acctType=" + acctType + ", acctInterestRate="
				+ acctInterestRate + ", acctOverdraft=" + acctOverdraft + "]";
	}

}
