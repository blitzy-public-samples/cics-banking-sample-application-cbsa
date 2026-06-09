/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.core.exception;

/**
 * Unchecked exception that carries a single COBOL-faithful fail code (or a CICS
 * abend code) out of the {@code bank-core} service layer up to the REST
 * boundary, where {@code GlobalExceptionHandler} translates it onto the frozen
 * z/OS Connect response envelope.
 *
 * <p>This type is the consolidated Java rendering of the legacy CBSA failure
 * signalling mechanism. The COBOL is the authoritative behavioural
 * specification, so the codes carried here are reproduced verbatim and are
 * never re-numbered, re-worded, or "improved".</p>
 *
 * <h2>What this models (the specification of record)</h2>
 * <p>Every legacy CBSA business program reports its outcome through two trailing
 * fields in its commarea copybook: a success flag ({@code 03 COMM-SUCCESS
 * PIC X}, holding {@code "Y"} or {@code "N"}) and a reason indicator
 * ({@code 03 COMM-FAIL-CODE PIC X}) that is populated when the flag is
 * {@code "N"}. That reason indicator is the authoritative way a program signals
 * <em>why</em> a request was rejected, and it is program-scoped &mdash; the same
 * value can mean different things in different programs. For example:</p>
 * <ul>
 *   <li>{@code CREACC} sets {@code "1"} for an unknown customer, {@code "8"}
 *       when a customer already holds the maximum of ten accounts, and
 *       {@code "A"} for an invalid account type.</li>
 *   <li>{@code CRECUST} sets {@code "T"} for an invalid title and {@code "C"}
 *       when no credit-agency reply arrived inside the deadline.</li>
 *   <li>{@code XFRFUN} sets {@code "4"} for a transfer amount of zero or less.</li>
 *   <li>{@code DBCRFUN} sets {@code "3"} for insufficient funds and {@code "4"}
 *       for an account-type / facility-type-496 channel rule violation.</li>
 *   <li>{@code DELACC}, {@code DELCUS}, {@code INQCUST}, and {@code UPDCUST} set
 *       {@code "1"} when the target record is not found.</li>
 * </ul>
 * <p>A single instance of this class is the universal Java carrier for that
 * indicator, mirroring {@code COMM-FAIL-CODE} semantics without inventing a
 * taxonomy.</p>
 *
 * <h2>Why the code is a {@link String}, never a single-position primitive</h2>
 * <p>Besides the one-position {@code COMM-FAIL-CODE} values, the COBOL also
 * abends with CICS {@code ABCODE} values that are up to four positions wide. For
 * example {@code XFRFUN} issues {@code ABCODE('SAME')} when a transfer names the
 * same account twice, and other programs raise codes such as {@code "HNCS"},
 * {@code "HBNK"}, and {@code "PLOP"} (see {@code ABNDPROC.cbl}, whose
 * {@code COMM-CODE} field is declared {@code PIC X(4)}). Storing the code as a
 * {@code String} lets one type hold both the one-position fail codes and the
 * four-position abend codes. The value is stored exactly as supplied by the
 * caller &mdash; it is never trimmed, padded, upper-cased, or otherwise
 * normalised.</p>
 *
 * <h2>Why it is unchecked</h2>
 * <p>On failure the COBOL performs {@code EXEC CICS SYNCPOINT ROLLBACK} to undo
 * its unit of work. In the Java target the service beans execute inside Spring
 * {@code @Transactional} boundaries (propagation {@code REQUIRED}, isolation
 * {@code READ_COMMITTED}), which roll back automatically only for unchecked
 * (runtime) exceptions. Extending {@link RuntimeException} therefore reproduces
 * the COBOL rollback-on-failure behaviour without scattering {@code throws}
 * declarations across the service layer, and in particular it makes the
 * control-row identity allocation roll back implicitly when the enclosing
 * operation fails.</p>
 *
 * <h2>Why the codes are NOT a fixed set of typed constants</h2>
 * <p>Because the same one-position value means different things in different
 * programs &mdash; and because new programs may introduce their own codes
 * &mdash; pinning the codes into a closed, typed set would risk omitting or
 * inventing values and would break exact behavioural parity with the COBOL. Each
 * service instead passes the precise code {@code String} that its COBOL
 * counterpart would have placed in {@code COMM-FAIL-CODE} (or the {@code ABCODE}
 * it would have abended with).</p>
 */
public class BusinessRuleException extends RuntimeException {

	/**
	 * Serialisation identifier. {@link RuntimeException} is {@code Serializable},
	 * so a fixed, stable value is declared (matching the repository convention)
	 * to keep the serialised form deterministic across builds.
	 */
	private static final long serialVersionUID = 7841596302145768934L;

	/**
	 * The COBOL fail code or CICS abend code that explains the failure, stored
	 * verbatim (for example {@code "1"}, {@code "A"}, {@code "T"}, {@code "C"},
	 * or {@code "SAME"}). Immutable once set; read by the REST-boundary advice
	 * via {@link #getFailCode()} to populate the response envelope.
	 */
	private final String failCode;

	/**
	 * Creates an exception for the supplied fail code, deriving a useful default
	 * detail message so that logs identify the code even when the caller does
	 * not provide bespoke context.
	 *
	 * @param failCode the COBOL fail code or CICS abend code, stored verbatim
	 */
	public BusinessRuleException(String failCode) {
		super("Business rule violation, fail code=" + failCode);
		this.failCode = failCode;
	}

	/**
	 * Creates an exception for the supplied fail code with a caller-provided
	 * detail message. The message is passed through to {@link RuntimeException}
	 * unchanged so that COBOL-faithful context is preserved exactly.
	 *
	 * @param failCode the COBOL fail code or CICS abend code, stored verbatim
	 * @param message  the detail message, used as-is for {@link #getMessage()}
	 */
	public BusinessRuleException(String failCode, String message) {
		super(message);
		this.failCode = failCode;
	}

	/**
	 * Creates an exception for the supplied fail code with a caller-provided
	 * detail message and an underlying cause, for wrapping a lower-level failure
	 * (such as a data-access error) while still surfacing the COBOL fail code.
	 *
	 * @param failCode the COBOL fail code or CICS abend code, stored verbatim
	 * @param message  the detail message, used as-is for {@link #getMessage()}
	 * @param cause    the underlying cause, retained for {@link #getCause()}
	 */
	public BusinessRuleException(String failCode, String message, Throwable cause) {
		super(message, cause);
		this.failCode = failCode;
	}

	/**
	 * Returns the carried COBOL fail code or CICS abend code exactly as it was
	 * supplied at construction time. The REST-boundary advice reads this to put
	 * the precise code onto the response envelope.
	 *
	 * @return the stored fail code (or abend code), never altered
	 */
	public String getFailCode() {
		return failCode;
	}
}
