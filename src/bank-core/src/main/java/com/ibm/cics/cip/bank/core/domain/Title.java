/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.core.domain;

/**
 * Enumeration of the valid customer titles recognised by the CBSA banking core.
 *
 * <p>This enum is the pure-Java rendering of the title-validation rule encoded
 * in the legacy COBOL program {@code CRECUST.cbl}, which is the authoritative
 * behavioural specification for customer creation. {@code CRECUST} extracts the
 * first space-delimited word of the customer name ({@code COMM-NAME}) into the
 * working-storage field {@code WS-UNSTR-TITLE} (declared {@code PIC X(9)}) and
 * validates it against a fixed set of literals. An unrecognised value sets
 * {@code COMM-SUCCESS='N'} and {@code COMM-FAIL-CODE='T'} and the program
 * returns immediately:</p>
 *
 * <pre>
 *   EVALUATE WS-UNSTR-TITLE
 *      WHEN 'Professor'  MOVE 'Y' TO WS-TITLE-VALID
 *      WHEN 'Mr       '  MOVE 'Y' TO WS-TITLE-VALID
 *      WHEN 'Mrs      '  MOVE 'Y' TO WS-TITLE-VALID
 *      WHEN 'Miss     '  MOVE 'Y' TO WS-TITLE-VALID
 *      WHEN 'Ms       '  MOVE 'Y' TO WS-TITLE-VALID
 *      WHEN 'Dr       '  MOVE 'Y' TO WS-TITLE-VALID
 *      WHEN 'Drs      '  MOVE 'Y' TO WS-TITLE-VALID
 *      WHEN 'Lord     '  MOVE 'Y' TO WS-TITLE-VALID
 *      WHEN 'Sir      '  MOVE 'Y' TO WS-TITLE-VALID
 *      WHEN 'Lady     '  MOVE 'Y' TO WS-TITLE-VALID
 *      WHEN '         '  MOVE 'Y' TO WS-TITLE-VALID   *&gt; blank is ALSO valid
 *      WHEN OTHER        MOVE 'N' TO WS-TITLE-VALID   *&gt; fail code 'T'
 *   END-EVALUATE.
 * </pre>
 *
 * <h2>Exact-case parity</h2>
 * <p>The COBOL {@code EVALUATE} compares against mixed-case literals (for
 * example {@code 'Mr'}, not {@code 'MR'}), so the stored / wire representation
 * of a title is case-sensitive. Java enum constant names are conventionally
 * upper-case, so each constant additionally carries its <strong>exact</strong>
 * display string in {@link #displayValue}. The constant-to-display mapping is
 * therefore:</p>
 *
 * <pre>
 *   PROFESSOR -&gt; "Professor"    MR   -&gt; "Mr"    MRS  -&gt; "Mrs"
 *   MISS      -&gt; "Miss"         MS   -&gt; "Ms"    DR   -&gt; "Dr"
 *   DRS       -&gt; "Drs"          LORD -&gt; "Lord"  SIR  -&gt; "Sir"
 *   LADY      -&gt; "Lady"
 * </pre>
 *
 * <p>The COBOL literals are space-padded to width nine (for example
 * {@code 'Mr       '}); the meaningful value is the trimmed token, so the
 * trimmed form ({@code "Mr"}) is what is stored here.</p>
 *
 * <h2>Blank title is valid</h2>
 * <p>The COBOL {@code WHEN '         '} branch treats an all-spaces title as
 * <em>valid</em>: a customer may legitimately have no title. To preserve that
 * behaviour without polluting the enumeration with an empty constant, the blank
 * case is handled in {@link #isValidTitle(String)} (which returns {@code true}
 * for a {@code null}, empty, or whitespace-only value) rather than being modelled
 * as a {@code Title} constant. There are therefore exactly ten constants.</p>
 *
 * <h2>How this enum is consumed</h2>
 * <p>The service layer (notably {@code CustomerService}, which reproduces the
 * {@code CRECUST} and {@code UPDCUST} title checks) uses
 * {@link #isValidTitle(String)} to validate the title token parsed from the
 * customer name, raising the COBOL fail code {@code 'T'} (via
 * {@code BusinessRuleException}) when the token is rejected. This enum is a
 * framework-free domain type: the {@code Customer} entity persists the full
 * customer name as a single {@code String} column, so {@code Title} is
 * <strong>not</strong> a JPA column type and carries no persistence or
 * validation annotations.</p>
 */
public enum Title
{

	/** Academic title {@code "Professor"}. */
	PROFESSOR("Professor"),

	/** Title {@code "Mr"}. */
	MR("Mr"),

	/** Title {@code "Mrs"}. */
	MRS("Mrs"),

	/** Title {@code "Miss"}. */
	MISS("Miss"),

	/** Title {@code "Ms"}. */
	MS("Ms"),

	/** Title {@code "Dr"}. */
	DR("Dr"),

	/** Title {@code "Drs"}. */
	DRS("Drs"),

	/** Title {@code "Lord"}. */
	LORD("Lord"),

	/** Title {@code "Sir"}. */
	SIR("Sir"),

	/** Title {@code "Lady"}. */
	LADY("Lady");

	/**
	 * The exact, case-sensitive display value of this title as recognised by the
	 * legacy {@code CRECUST} validation (for example {@code "Mr"}). This is the
	 * trimmed form of the corresponding COBOL literal and is the value that flows
	 * across the frozen API contract; it is deliberately <em>not</em> the enum
	 * constant name, because the constant name is upper-case whereas the wire
	 * value is mixed-case.
	 */
	private final String displayValue;

	/**
	 * Binds a title constant to its exact mixed-case display value.
	 *
	 * @param displayValue the case-sensitive display string for this title
	 */
	private Title(String displayValue)
	{
		this.displayValue = displayValue;
	}

	/**
	 * Returns the exact, case-sensitive display value of this title (for example
	 * {@code "Mr"} for {@link #MR}).
	 *
	 * @return the mixed-case display value, exactly as matched by {@code CRECUST}
	 */
	public String getDisplayValue()
	{
		return displayValue;
	}

	/**
	 * Resolves a raw title token to its matching {@code Title} constant.
	 *
	 * <p>The lookup is null- and whitespace-safe and mirrors the COBOL
	 * exact-case comparison:</p>
	 * <ul>
	 *   <li>a {@code null} value, or a value that is empty or whitespace-only
	 *       after {@link String#trim()}, resolves to {@code null} (no title);</li>
	 *   <li>otherwise the value is trimmed and matched <strong>case-sensitively</strong>
	 *       against each constant's {@link #displayValue}, returning the matching
	 *       constant, or {@code null} when none matches.</li>
	 * </ul>
	 *
	 * <p>For example {@code fromDisplayValue("Professor")} returns
	 * {@link #PROFESSOR}, {@code fromDisplayValue(" Dr ")} returns {@link #DR},
	 * while {@code fromDisplayValue("mr")} (wrong case) and
	 * {@code fromDisplayValue("King")} (unknown) both return {@code null}. The
	 * input case is never altered, preserving the COBOL exact-case semantics.</p>
	 *
	 * @param value the raw title token to resolve; may be {@code null}
	 * @return the matching {@code Title}, or {@code null} if the value is
	 *         blank/{@code null} or does not match a known title
	 */
	public static Title fromDisplayValue(String value)
	{
		if (value == null)
		{
			return null;
		}

		String trimmed = value.trim();
		if (trimmed.isEmpty())
		{
			return null;
		}

		for (Title title : values())
		{
			if (title.displayValue.equals(trimmed))
			{
				return title;
			}
		}

		return null;
	}

	/**
	 * Reproduces the result of the COBOL {@code EVALUATE WS-UNSTR-TITLE} block
	 * exactly, indicating whether a raw title token is acceptable.
	 *
	 * <p>A title is considered valid when:</p>
	 * <ul>
	 *   <li>the value is {@code null}, empty, or whitespace-only after
	 *       {@link String#trim()} &mdash; the COBOL {@code WHEN '         '}
	 *       branch, which accepts a missing title; or</li>
	 *   <li>the value matches one of the ten recognised titles
	 *       (case-sensitively), i.e. {@link #fromDisplayValue(String)} returns a
	 *       non-{@code null} constant.</li>
	 * </ul>
	 *
	 * <p>Any other value is rejected (the COBOL {@code WHEN OTHER} branch, which
	 * drives fail code {@code 'T'}). The comparison is case-sensitive, so
	 * {@code isValidTitle("Mr")} is {@code true} while {@code isValidTitle("mr")}
	 * is {@code false}; the input is never up- or down-cased.</p>
	 *
	 * @param value the raw title token to validate; may be {@code null}
	 * @return {@code true} if the token is blank/{@code null} or a recognised
	 *         title, {@code false} otherwise
	 */
	public static boolean isValidTitle(String value)
	{
		if (value == null || value.trim().isEmpty())
		{
			return true;
		}

		return fromDisplayValue(value) != null;
	}

}
