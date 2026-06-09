/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.core.constants;

/**
 * Centralized, COBOL-derived constant holder for the CBSA banking core.
 *
 * <p>This class is the single, authoritative home for the handful of literal
 * values that the legacy IBM CICS Bank Sample Application (CBSA) hard-codes in
 * its COBOL sources and that the pure-Java {@code bank-core} reimplementation
 * must reproduce <strong>exactly</strong>. Behavioural parity with the COBOL is
 * the migration contract, so none of these values is "improvable" &mdash; each
 * is traceable to a specific line in a specific COBOL artifact, cited in the
 * Javadoc of the field that carries it.</p>
 *
 * <p>It is a non-instantiable utility holder: it declares only
 * {@code public static final} fields, holds no instance state, exposes no static
 * helper methods, and contains no business logic. Downstream service beans
 * reference these fields by their exact names, for example:</p>
 * <ul>
 *   <li>{@code ReferenceDataService} (GETCOMPY / GETSCODE) &rarr;
 *       {@link #COMPANY_NAME} and {@link #SORT_CODE};</li>
 *   <li>{@code AccountService} (CREACC create-account guard) &rarr;
 *       {@link #MAX_ACCOUNTS_PER_CUSTOMER}, rejecting a new account when the
 *       customer's existing account count is {@code >=} this value;</li>
 *   <li>{@code PaymentService} (DBCRFUN debit / credit) &rarr;
 *       {@link #PAYMENT_FACILITY_TYPE}, used to detect the PAYMENT channel and
 *       apply the MORTGAGE/LOAN, insufficient-funds, and PDR/PCR-vs-DEB/CRE
 *       rules.</li>
 * </ul>
 *
 * <p>The class deliberately carries no framework annotations: it is a plain
 * static holder rather than a Spring {@code @Component} or
 * {@code @Configuration} bean, and Spring's default component scan from the
 * package-root application class simply ignores it. It also carries no imports
 * &mdash; every value is expressed with {@code java.lang} types only
 * ({@link String} and {@code int}). Consistent with the wider migration, no IBM
 * mainframe library is referenced and no floating-point type is ever used.</p>
 */
public final class BankConstants
{

	/**
	 * Bank sort code for the CBSA institution.
	 *
	 * <p>COBOL source: {@code SORTCODE.cpy} L7 &mdash;
	 * {@code 77 SORTCODE PIC 9(6) VALUE 987654.} The same literal is surfaced by
	 * {@code GETSCODE.cbl}, which moves it into the response commarea.</p>
	 *
	 * <p>Although the COBOL field is display-numeric, the value is held here as a
	 * fixed-width six-character {@link String} so that the COBOL leading-zero
	 * semantics of display-numeric identifiers are preserved (AAP &sect;0.6,
	 * fixed-width character identifiers). Holding it as an {@code int} would
	 * silently discard any leading zero and break wire-format parity.</p>
	 */
	public static final String SORT_CODE = "987654";

	/**
	 * Human-readable company name returned by the reference-data lookup.
	 *
	 * <p>COBOL source: {@code GETCOMPY.cbl} L38 &mdash;
	 * {@code move 'CICS Bank Sample Application' to COMPANY-NAME.} The target
	 * field {@code COMPANY-NAME} is declared {@code PIC X(40)} in
	 * {@code GETCOMPY.cpy}; the 40-character right-padding is a
	 * DTO/response-serialization concern and is deliberately <strong>not</strong>
	 * baked into this literal, which is the exact 28-character name.</p>
	 */
	public static final String COMPANY_NAME = "CICS Bank Sample Application";

	/**
	 * Maximum number of accounts a single customer may hold.
	 *
	 * <p>COBOL source: {@code CREACC.cbl} L347 &mdash;
	 * {@code IF NUMBER-OF-ACCOUNTS IN INQACCCU-COMMAREA > 9} sets the
	 * create-account fail code {@code '8'} and rejects the request. An existing
	 * count strictly greater than {@code 9} (that is, {@code >= 10}) blocks a new
	 * account, so the maximum a customer can actually hold is {@code 10}.</p>
	 *
	 * <p>The Java equivalent of the COBOL {@code > 9} guard is therefore to
	 * reject when {@code existingAccountCount >= MAX_ACCOUNTS_PER_CUSTOMER}.</p>
	 */
	public static final int MAX_ACCOUNTS_PER_CUSTOMER = 10;

	/**
	 * Payment facility-type sentinel that distinguishes the PAYMENT channel from
	 * the Teller channel during debit/credit processing.
	 *
	 * <p>COBOL source: {@code DBCRFUN.cbl} (field {@code PAYDBCR.cpy} L17 &mdash;
	 * {@code 05 COMM-FACILTYPE PIC S9(8) COMP}). The comment on
	 * {@code DBCRFUN.cbl} L362 documents {@code COMM-FACILTYPE(496 = NONE)}: a
	 * value of {@code 496} means there is no terminal facility, i.e. the request
	 * arrived via the PAYMENT link rather than a Teller.</p>
	 *
	 * <p>When {@code COMM-FACILTYPE = 496}, DBCRFUN: (a) rejects a debit or
	 * credit against a {@code MORTGAGE} or {@code LOAN} account with fail code
	 * {@code '4'} (L330-338, L368-376); (b) fails an insufficient-funds debit
	 * with {@code '3'} (L344-350); and (c) types the movement as {@code PDR} /
	 * {@code PCR} instead of {@code DEB} / {@code CRE}. The Teller channel
	 * (facility type other than {@code 496}) bypasses the MORTGAGE/LOAN and
	 * insufficient-funds checks.</p>
	 *
	 * <p>{@code PIC S9(8) COMP} is a signed binary integer that fits comfortably
	 * within a Java {@code int}.</p>
	 */
	public static final int PAYMENT_FACILITY_TYPE = 496;

	/**
	 * Private constructor &mdash; {@code BankConstants} is a non-instantiable
	 * constant holder.
	 *
	 * <p>It is never invoked in normal operation; it exists only to suppress the
	 * compiler-synthesized public default constructor and to defend against
	 * reflection-driven instantiation, which it answers with an
	 * {@link AssertionError}.</p>
	 */
	private BankConstants()
	{
		throw new AssertionError("Utility class - do not instantiate");
	}

}
