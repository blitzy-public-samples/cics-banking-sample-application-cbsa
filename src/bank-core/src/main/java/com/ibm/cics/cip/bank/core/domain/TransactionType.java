/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.core.domain;

/**
 * Enumeration of the eighteen processed-transaction type codes recognised by
 * the CBSA banking core.
 *
 * <p>This enum is the pure-Java rendering of the {@code PROC-TRAN-TYPE} field
 * defined in the legacy copybook {@code PROCTRAN.cpy}, which is the
 * authoritative structural specification for the processed-transaction (audit)
 * record. The field is declared {@code PIC X(3)} and constrained to exactly
 * eighteen three-character literals through a block of {@code 88}-level
 * condition names (copybook lines 30-47):</p>
 *
 * <pre>
 *   05 PROC-TRAN-TYPE               PIC X(3).
 *   88 PROC-TY-CHEQUE-ACKNOWLEDGED      VALUE 'CHA'.
 *   88 PROC-TY-CHEQUE-FAILURE           VALUE 'CHF'.
 *   88 PROC-TY-CHEQUE-PAID-IN           VALUE 'CHI'.
 *   88 PROC-TY-CHEQUE-PAID-OUT          VALUE 'CHO'.
 *   88 PROC-TY-CREDIT                   VALUE 'CRE'.
 *   88 PROC-TY-DEBIT                    VALUE 'DEB'.
 *   88 PROC-TY-WEB-CREATE-ACCOUNT       VALUE 'ICA'.
 *   88 PROC-TY-WEB-CREATE-CUSTOMER      VALUE 'ICC'.
 *   88 PROC-TY-WEB-DELETE-ACCOUNT       VALUE 'IDA'.
 *   88 PROC-TY-WEB-DELETE-CUSTOMER      VALUE 'IDC'.
 *   88 PROC-TY-BRANCH-CREATE-ACCOUNT    VALUE 'OCA'.
 *   88 PROC-TY-BRANCH-CREATE-CUSTOMER   VALUE 'OCC'.
 *   88 PROC-TY-BRANCH-DELETE-ACCOUNT    VALUE 'ODA'.
 *   88 PROC-TY-BRANCH-DELETE-CUSTOMER   VALUE 'ODC'.
 *   88 PROC-TY-CREATE-SODD              VALUE 'OCS'.
 *   88 PROC-TY-PAYMENT-CREDIT           VALUE 'PCR'.
 *   88 PROC-TY-PAYMENT-DEBIT            VALUE 'PDR'.
 *   88 PROC-TY-TRANSFER                 VALUE 'TFR'.
 * </pre>
 *
 * <h2>Constant name equals stored code</h2>
 * <p>Each enum constant's {@link #name()} <strong>is</strong> its canonical
 * three-character code (for example {@link #CRE} stores {@code "CRE"}). This is
 * a deliberate, load-bearing contract: the {@code ProcessedTransaction} entity
 * maps its {@code typeCode} field with {@code @Enumerated(EnumType.STRING)},
 * which persists {@code name()} directly into the {@code type_code CHAR(3)}
 * column. That column carries a {@code CHECK} constraint enumerating these exact
 * eighteen codes ({@code ck_proctran_type_code} in the Flyway {@code V1}
 * migration), so the constant names must be the codes verbatim &mdash; with no
 * {@code AttributeConverter} &mdash; for every persisted value to satisfy the
 * constraint. Using a descriptive constant name (such as
 * {@code CHEQUE_ACKNOWLEDGED}) would store the wrong string and violate the
 * {@code CHECK}; the human-readable meaning is therefore carried separately in
 * the {@link #description} field.</p>
 *
 * <h2>Exact set and order parity</h2>
 * <p>There are <strong>exactly eighteen</strong> constants, declared in the same
 * order as the copybook {@code 88}-levels and the database {@code CHECK} list
 * ({@code CHA, CHF, CHI, CHO, CRE, DEB, ICA, ICC, IDA, IDC, OCA, OCC, ODA, ODC,
 * OCS, PCR, PDR, TFR}). No code is added, removed, or renamed; in particular the
 * easily-overlooked {@code OCS} (create-SODD) is present, preserving behavioural
 * parity with the COBOL specification of record.</p>
 *
 * <h2>Exact-match parity</h2>
 * <p>The COBOL codes are upper-case three-character literals. The
 * {@link #fromCode(String)} helper reproduces an exact, case-sensitive match:
 * the input is only {@link String#trim() trimmed} (a {@code CHAR(3)} JDBC read or
 * a wire value may carry trailing spaces) and is never up- or down-cased,
 * because a permissive match would accept values that the COBOL and the database
 * {@code CHECK} constraint reject, breaking behavioural parity.</p>
 *
 * <h2>How this enum is consumed</h2>
 * <p>This is a framework-free domain type, referenced uniformly by the
 * {@code entity} layer (as the {@code @Enumerated(EnumType.STRING)} type of
 * {@code ProcessedTransaction.typeCode}), the {@code dto} layer (when mapping the
 * frozen z/OS Connect wire contract), and the {@code service} layer (which
 * selects the appropriate code when appending PROCTRAN audit records). It
 * therefore declares no persistence, validation, or serialization annotations
 * and pulls in no framework imports.</p>
 *
 * @see AccountType
 * @see Title
 */
public enum TransactionType
{

	/** Cheque acknowledged (COBOL {@code PROC-TY-CHEQUE-ACKNOWLEDGED}, code {@code "CHA"}). */
	CHA("Cheque Acknowledged"),

	/** Cheque failure (COBOL {@code PROC-TY-CHEQUE-FAILURE}, code {@code "CHF"}). */
	CHF("Cheque Failure"),

	/** Cheque paid in (COBOL {@code PROC-TY-CHEQUE-PAID-IN}, code {@code "CHI"}). */
	CHI("Cheque Paid In"),

	/** Cheque paid out (COBOL {@code PROC-TY-CHEQUE-PAID-OUT}, code {@code "CHO"}). */
	CHO("Cheque Paid Out"),

	/** Credit movement (COBOL {@code PROC-TY-CREDIT}, code {@code "CRE"}). */
	CRE("Credit"),

	/** Debit movement (COBOL {@code PROC-TY-DEBIT}, code {@code "DEB"}). */
	DEB("Debit"),

	/** Web (internet-channel) account creation (COBOL {@code PROC-TY-WEB-CREATE-ACCOUNT}, code {@code "ICA"}). */
	ICA("Web Create Account"),

	/** Web (internet-channel) customer creation (COBOL {@code PROC-TY-WEB-CREATE-CUSTOMER}, code {@code "ICC"}). */
	ICC("Web Create Customer"),

	/** Web (internet-channel) account deletion (COBOL {@code PROC-TY-WEB-DELETE-ACCOUNT}, code {@code "IDA"}). */
	IDA("Web Delete Account"),

	/** Web (internet-channel) customer deletion (COBOL {@code PROC-TY-WEB-DELETE-CUSTOMER}, code {@code "IDC"}). */
	IDC("Web Delete Customer"),

	/** Branch (counter-channel) account creation (COBOL {@code PROC-TY-BRANCH-CREATE-ACCOUNT}, code {@code "OCA"}). */
	OCA("Branch Create Account"),

	/** Branch (counter-channel) customer creation (COBOL {@code PROC-TY-BRANCH-CREATE-CUSTOMER}, code {@code "OCC"}). */
	OCC("Branch Create Customer"),

	/** Branch (counter-channel) account deletion (COBOL {@code PROC-TY-BRANCH-DELETE-ACCOUNT}, code {@code "ODA"}). */
	ODA("Branch Delete Account"),

	/** Branch (counter-channel) customer deletion (COBOL {@code PROC-TY-BRANCH-DELETE-CUSTOMER}, code {@code "ODC"}). */
	ODC("Branch Delete Customer"),

	/** Create SODD (COBOL {@code PROC-TY-CREATE-SODD}, code {@code "OCS"}); the easily-missed code. */
	OCS("Create SODD"),

	/** Payment credit (COBOL {@code PROC-TY-PAYMENT-CREDIT}, code {@code "PCR"}). */
	PCR("Payment Credit"),

	/** Payment debit (COBOL {@code PROC-TY-PAYMENT-DEBIT}, code {@code "PDR"}). */
	PDR("Payment Debit"),

	/** Transfer between accounts (COBOL {@code PROC-TY-TRANSFER}, code {@code "TFR"}). */
	TFR("Transfer");

	/**
	 * Human-readable description of this transaction type, derived from the
	 * corresponding COBOL {@code 88}-level condition name in {@code PROCTRAN.cpy}.
	 *
	 * <p>This text exists purely to preserve the semantic meaning recorded by the
	 * copybook; it is documentation / parity metadata and is <strong>not</strong>
	 * persisted &mdash; the entity stores only the three-character
	 * {@link #name()}.</p>
	 */
	private final String description;

	/**
	 * Binds a transaction-type constant to its human-readable description.
	 *
	 * @param description the human-readable meaning of this type code
	 */
	private TransactionType(String description)
	{
		this.description = description;
	}

	/**
	 * Returns the human-readable description of this transaction type (for
	 * example {@code "Credit"} for {@link #CRE}).
	 *
	 * @return the human-readable description; never {@code null}
	 */
	public String getDescription()
	{
		return description;
	}

	/**
	 * Returns the canonical three-character transaction-type code &mdash; the
	 * exact value stored in the {@code type_code} column.
	 *
	 * <p>This is identical to the enum constant {@link #name()} (for example
	 * {@code "CRE"} for {@link #CRE}); the accessor exists to make the intent
	 * explicit at call sites in the DTO and service layers, which reason in terms
	 * of the three-character code rather than the enum constant.</p>
	 *
	 * @return the three-character code, identical to {@link #name()}
	 */
	public String getCode()
	{
		return name();
	}

	/**
	 * Resolves a raw three-character string to its matching
	 * {@code TransactionType} constant.
	 *
	 * <p>The lookup is null- and whitespace-safe and mirrors the COBOL exact,
	 * case-sensitive comparison:</p>
	 * <ul>
	 *   <li>a {@code null} value resolves to {@code null}, so the caller can
	 *       decide how to treat an absent code;</li>
	 *   <li>otherwise the value is {@link String#trim() trimmed} (a
	 *       {@code CHAR(3)} JDBC read or a wire value may carry trailing spaces)
	 *       and matched <strong>case-sensitively</strong> against each constant's
	 *       {@link #getCode() code}, returning the matching constant, or
	 *       {@code null} when none matches.</li>
	 * </ul>
	 *
	 * <p>The input is deliberately never up-cased: auto-uppercasing would mask
	 * malformed data by accepting values that the COBOL and the database
	 * {@code CHECK} constraint reject. For example {@code fromCode(" TFR ")}
	 * returns {@link #TFR}, whereas {@code fromCode("tfr")} (wrong case),
	 * {@code fromCode("ZZZ")} (unknown) and {@code fromCode(null)} all return
	 * {@code null}.</p>
	 *
	 * @param code the raw transaction-type code to resolve; may be {@code null}
	 * @return the matching {@code TransactionType}, or {@code null} if the value
	 *         is {@code null} or does not exactly match one of the eighteen codes
	 */
	public static TransactionType fromCode(String code)
	{
		if (code == null)
		{
			return null;
		}

		String trimmed = code.trim();
		for (TransactionType type : values())
		{
			if (type.getCode().equals(trimmed))
			{
				return type;
			}
		}

		return null;
	}

}
