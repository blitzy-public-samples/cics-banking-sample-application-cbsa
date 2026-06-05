/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.core.domain;

/**
 * Enumeration of the five account types recognised by the CBSA banking core.
 *
 * <p>This enum is the pure-Java rendering of the account-type validation rule
 * encoded in the legacy COBOL program {@code CREACC.cbl}, which is the
 * authoritative behavioural specification for account creation. The
 * {@code ACCOUNT-TYPE-CHECK} section validates the supplied account type
 * ({@code COMM-ACC-TYPE}, declared {@code PIC X(8)}) against exactly five
 * literals; any other value sets {@code COMM-SUCCESS='N'} together with the
 * create-account fail code {@code COMM-FAIL-CODE='A'} and the program rejects
 * the request:</p>
 *
 * <pre>
 *   EVALUATE TRUE
 *      WHEN COMM-ACC-TYPE(1:3) = 'ISA'
 *      WHEN COMM-ACC-TYPE(1:8) = 'MORTGAGE'
 *      WHEN COMM-ACC-TYPE(1:6) = 'SAVING'
 *      WHEN COMM-ACC-TYPE(1:7) = 'CURRENT'
 *      WHEN COMM-ACC-TYPE(1:4) = 'LOAN'
 *         MOVE 'Y' TO COMM-SUCCESS
 *      WHEN OTHER
 *         MOVE 'N' TO COMM-SUCCESS
 *         MOVE 'A' TO COMM-FAIL-CODE    *&gt; invalid account type
 *   END-EVALUATE.
 * </pre>
 *
 * <h2>Name equals value</h2>
 * <p>The COBOL literals are upper-case ({@code 'ISA'}, {@code 'MORTGAGE'}, and so
 * on), which is also the conventional form for Java enum constant names. Each
 * constant's {@link #name()} therefore <strong>is</strong> the canonical stored
 * string, so &mdash; unlike {@link Title}, whose COBOL literals are mixed-case
 * and which carries a separate display field &mdash; this is a plain enum with
 * no per-constant state. The {@code Account} entity persists the account type as
 * a plain {@code VARCHAR(8)} {@code String} column holding exactly these
 * literals; this enum is the validation / typing authority, <strong>not</strong>
 * a JPA column type, and consequently declares no persistence or validation
 * annotations.</p>
 *
 * <h2>Exact-match parity</h2>
 * <p>The COBOL comparison is an exact, case-sensitive literal match (each
 * {@code WHEN} compares the precise prefix length of its literal, so for example
 * {@code 'SAVING'} is matched against positions 1-6 exactly). The
 * {@link #isValid(String)} and {@link #fromValue(String)} helpers reproduce this
 * faithfully: the input is only {@link String#trim() trimmed} (the field is
 * space-padded to width eight) and then matched case-sensitively. The input is
 * never up- or down-cased, because a permissive match would accept values the
 * COBOL rejects (for example {@code "isa"}), breaking behavioural parity.</p>
 *
 * <h2>How this enum is consumed</h2>
 * <p>The service layer (notably {@code AccountService}, which reproduces the
 * {@code CREACC} create-account validation) uses {@link #isValid(String)} to
 * decide whether a supplied account type is acceptable &mdash; raising the COBOL
 * fail code {@code 'A'} (via {@code BusinessRuleException}) when it is not
 * &mdash; and {@link #fromValue(String)} to resolve a raw string to a typed
 * constant. There are exactly five constants, declared in the canonical order
 * {@code ISA, MORTGAGE, SAVING, CURRENT, LOAN}.</p>
 *
 * @see Title
 */
public enum AccountType
{

	/** Individual Savings Account (COBOL literal {@code "ISA"}). */
	ISA,

	/** Mortgage account (COBOL literal {@code "MORTGAGE"}); the longest value, at eight characters. */
	MORTGAGE,

	/** Savings account (COBOL literal {@code "SAVING"}). */
	SAVING,

	/** Current account (COBOL literal {@code "CURRENT"}). */
	CURRENT,

	/** Loan account (COBOL literal {@code "LOAN"}). */
	LOAN;

	/**
	 * Reproduces the COBOL {@code ACCOUNT-TYPE-CHECK} {@code EVALUATE} exactly,
	 * indicating whether a raw account-type string is one of the five accepted
	 * types.
	 *
	 * <p>The check is null-safe and mirrors the COBOL exact, case-sensitive
	 * comparison:</p>
	 * <ul>
	 *   <li>a {@code null} value returns {@code false} &mdash; there is no
	 *       "blank is valid" branch for account type, so a missing value falls
	 *       into the COBOL {@code WHEN OTHER} case that drives fail code
	 *       {@code 'A'};</li>
	 *   <li>otherwise the value is {@link String#trim() trimmed} (the COBOL field
	 *       is space-padded to width eight) and compared
	 *       <strong>case-sensitively</strong> against each constant's
	 *       {@link #name()}, returning {@code true} on the first exact match.</li>
	 * </ul>
	 *
	 * <p>For example {@code isValid("MORTGAGE")} and {@code isValid(" ISA ")} are
	 * {@code true}, whereas {@code isValid("isa")} (wrong case),
	 * {@code isValid("CHECKING")} (unknown), {@code isValid("")} (blank) and
	 * {@code isValid(null)} are all {@code false}. The input case is never
	 * altered, preserving the COBOL exact-match semantics.</p>
	 *
	 * @param value the raw account-type string to validate; may be {@code null}
	 * @return {@code true} if the trimmed value exactly matches one of the five
	 *         account-type names, {@code false} otherwise (including for
	 *         {@code null})
	 */
	public static boolean isValid(String value)
	{
		if (value == null)
		{
			return false;
		}

		String trimmed = value.trim();
		for (AccountType type : values())
		{
			if (type.name().equals(trimmed))
			{
				return true;
			}
		}

		return false;
	}

	/**
	 * Resolves a raw account-type string to its matching {@code AccountType}
	 * constant.
	 *
	 * <p>The lookup is null- and whitespace-safe and mirrors the COBOL exact,
	 * case-sensitive comparison:</p>
	 * <ul>
	 *   <li>a {@code null} value resolves to {@code null};</li>
	 *   <li>otherwise the value is {@link String#trim() trimmed} and matched
	 *       <strong>case-sensitively</strong> against each constant's
	 *       {@link #name()}, returning the matching constant, or {@code null}
	 *       when none matches.</li>
	 * </ul>
	 *
	 * <p>This method deliberately returns {@code null} rather than throwing on an
	 * unknown value, so that the caller (typically {@code AccountService}) can map
	 * a miss onto the COBOL fail code {@code 'A'}. For example
	 * {@code fromValue("CURRENT")} returns {@link #CURRENT} and
	 * {@code fromValue(" loan ")} returns {@code null} (wrong case), while
	 * {@code fromValue("XYZ")} and {@code fromValue(null)} both return
	 * {@code null}.</p>
	 *
	 * @param value the raw account-type string to resolve; may be {@code null}
	 * @return the matching {@code AccountType}, or {@code null} if the value is
	 *         {@code null} or does not exactly match a known account type
	 */
	public static AccountType fromValue(String value)
	{
		if (value == null)
		{
			return null;
		}

		String trimmed = value.trim();
		for (AccountType type : values())
		{
			if (type.name().equals(trimmed))
			{
				return type;
			}
		}

		return null;
	}

}
