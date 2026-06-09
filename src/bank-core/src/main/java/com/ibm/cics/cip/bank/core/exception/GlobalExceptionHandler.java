/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.core.exception;

import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;

/**
 * Centralised REST-boundary exception translation for the {@code bank-core}
 * module &mdash; the Java analogue of the legacy {@code ABNDPROC} COBOL program,
 * whose stated purpose was to process abends "from one place, without having to
 * go hunting for them". This {@code @RestControllerAdvice} is that single,
 * centralised place: it converts an exception thrown anywhere beneath the
 * controllers into a structured, contract-faithful HTTP response rather than
 * letting it propagate raw.
 *
 * <h2>The non-obvious rule: a business fail code is HTTP 200, not 4xx/5xx</h2>
 * <p>Every legacy CBSA business program reports its outcome through two trailing
 * commarea fields &mdash; {@code COMM-SUCCESS PIC X} ({@code "Y"}/{@code "N"})
 * and {@code COMM-FAIL-CODE PIC X}. Under the frozen z/OS Connect contract those
 * <em>business</em> outcomes (both success and failure) ride inside the
 * <strong>HTTP&nbsp;200 JSON body</strong>; consumers inspect the body, not the
 * HTTP status, to decide whether a request was rejected. The existing consumer
 * {@code WebController} proves this: it calls
 * {@code WebClient ... .retrieve().bodyToMono(String.class).block()} and only
 * runs its {@code checkIfResponseValid*} fail-code logic on a successful (2xx)
 * body; a non-2xx status instead makes {@code WebClient} throw a
 * {@code WebClientResponseException}, which the consumer treats as a generic
 * connection/request error rather than as a fail code.</p>
 *
 * <p>Consequently a {@link BusinessRuleException} that reaches this advice MUST
 * be rendered as HTTP&nbsp;200 with a body that flags failure and carries the
 * <em>exact</em> fail code. Returning 4xx/5xx for a normal business fail code
 * would break both the React/Carbon UI and the interface modules that branch on
 * the body, defeating the "re-point only, no rewrite" promise of the migration.</p>
 *
 * <h2>Why this advice is deliberately decoupled from per-endpoint envelopes</h2>
 * <p>Primary envelope fidelity belongs to the controller layer: each
 * {@code controller/*Controller} catches {@link BusinessRuleException} (or
 * otherwise sets {@code COMM-SUCCESS="N"} + {@code COMM-FAIL-CODE}) and returns
 * its <em>own</em> endpoint-specific response DTO so the per-endpoint envelope is
 * byte-for-byte correct. Those DTO types live in sibling packages and each
 * carries its fail indicator in a different field, with a different type and a
 * different "success" sentinel; a single advice cannot reproduce all of those
 * shapes. This class is therefore a <strong>cross-cutting safety net</strong>
 * for (a) any {@link BusinessRuleException} that escapes a controller uncaught,
 * (b) Bean Validation failures (F-021), and (c) genuinely unexpected exceptions.
 * It must never depend on an endpoint DTO, so it emits a small, generic,
 * JSON-serialisable {@link ErrorResponse} body instead.</p>
 *
 * <p>Component scanning discovers this advice automatically: {@code
 * BankCoreApplication} sits at the package root
 * {@code com.ibm.cics.cip.bank.core} with default scanning, so this
 * {@code exception} subpackage is picked up with no extra configuration.</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler
{

	/**
	 * Logger for this advice. Genuinely unexpected exceptions (those reaching the
	 * {@link #handleUnexpected(Exception) catch-all}) are logged server-side at
	 * {@code ERROR} so an HTTP&nbsp;500 is always traceable to its root cause in
	 * the application log, while the client still receives only the generic,
	 * sanitised {@link #UNEXPECTED_ERROR_MESSAGE} body (no stack trace, SQL, or
	 * exception class name is ever exposed &mdash; CWE-209 safe). This is the
	 * Java analogue of {@code ABNDPROC} recording an abend "from one place".
	 */
	private static final Logger LOG = LoggerFactory
			.getLogger(GlobalExceptionHandler.class);

	/**
	 * Fail-code value used when no COBOL business fail code applies (for example
	 * for malformed-input or unexpected-error responses). An empty string is the
	 * contract-faithful "no failure code" marker: the legacy consumer treats an
	 * empty {@code COMM-FAIL-CODE} as "no failure" (see
	 * {@code WebController.checkIfResponseValidCreateCust}). A new code is never
	 * invented.
	 */
	private static final String NO_FAIL_CODE = "";

	/**
	 * Generic, user-safe message returned for any unexpected server-side error.
	 * Deliberately free of stack traces, SQL, exception class names, and any
	 * internal or mainframe detail.
	 */
	private static final String UNEXPECTED_ERROR_MESSAGE = "An unexpected error occurred.";

	/**
	 * Fallback message used when a validation failure carries no inspectable
	 * field error or constraint violation to aggregate.
	 */
	private static final String VALIDATION_FAILED_MESSAGE = "Validation failed.";

	/**
	 * User-safe message returned when a request body cannot be read or parsed
	 * (for example malformed JSON, an unexpected end of input, a wrong content
	 * type, or a value that cannot be bound to the target type). Deliberately
	 * generic so it never echoes the offending payload, the parser's internal
	 * detail, or any exception class name (avoiding CWE-209 information exposure).
	 */
	private static final String MALFORMED_BODY_MESSAGE = "Malformed request body.";

	/**
	 * User-safe message returned when the HTTP method is not supported for the
	 * target route (HTTP&nbsp;405). Generic so it leaks no routing internals.
	 */
	private static final String METHOD_NOT_SUPPORTED_MESSAGE = "Request method not supported.";

	/**
	 * User-safe message returned when the request's content type is not
	 * supported (HTTP&nbsp;415). Generic so it leaks no internals.
	 */
	private static final String MEDIA_TYPE_NOT_SUPPORTED_MESSAGE = "Unsupported media type.";

	/**
	 * User-safe message returned when a request parameter (for example a path
	 * variable) cannot be parsed or bound to its target type (HTTP&nbsp;400).
	 * Generic so it never echoes the offending value or any internal detail.
	 */
	private static final String INVALID_PARAMETER_MESSAGE = "Invalid request parameter.";

	/**
	 * User-safe message returned when no route matches the requested path
	 * (HTTP&nbsp;404). Generic so it never echoes the offending URL or any
	 * routing internal.
	 */
	private static final String NOT_FOUND_MESSAGE = "The requested resource was not found.";

	/**
	 * User-safe message returned when a persistence-layer data-integrity
	 * constraint rejects the request &mdash; for example a monetary value whose
	 * magnitude exceeds the column precision ({@code NUMERIC(12,2)}), a
	 * {@code NOT NULL} column left unset, or a {@code CHECK}/unique constraint
	 * violation (HTTP&nbsp;400). Deliberately generic so it never echoes the SQL
	 * statement, constraint name, column, table, or any database/Hibernate
	 * internal (CWE-209 safe).
	 */
	private static final String DATA_INTEGRITY_MESSAGE = "The request could not be completed because a submitted value is invalid or out of the permitted range.";

	/** Separator used when aggregating multiple validation messages into one. */
	private static final String MESSAGE_DELIMITER = "; ";

	/**
	 * Defensive translation of a {@link BusinessRuleException} that escaped a
	 * controller's own envelope handling. Renders a minimal, generic outcome body
	 * that carries the verbatim COBOL fail code.
	 *
	 * <p><strong>Status is HTTP&nbsp;200 by design.</strong> Frozen-contract
	 * consumers detect a business rejection by reading the response body (the
	 * success flag and fail code), not the HTTP status; returning 4xx/5xx here
	 * would be misread as a transport error and break those consumers.</p>
	 *
	 * @param ex the escaped business-rule exception (never {@code null})
	 * @return an HTTP&nbsp;200 response whose body reports {@code success=false}
	 *         and the exact {@link BusinessRuleException#getFailCode() fail code}
	 */
	@ExceptionHandler(BusinessRuleException.class)
	public ResponseEntity<ErrorResponse> handleBusinessRuleException(
			BusinessRuleException ex)
	{
		// HTTP 200 (not 4xx/5xx): the fail code is a business outcome carried in
		// the body, exactly as the legacy z/OS Connect contract delivers it.
		ErrorResponse body = new ErrorResponse(false, ex.getFailCode(),
				ex.getMessage());
		return ResponseEntity.ok(body);
	}

	/**
	 * Translates a Bean Validation failure raised for a {@code @Valid}
	 * {@code @RequestBody} DTO into an HTTP&nbsp;400 response. The individual
	 * field errors are aggregated into a single concise, user-safe message; no
	 * business fail code applies, so the fail code is left empty (never invented).
	 *
	 * <p>HTTP&nbsp;400 is correct here: the request never reached business logic,
	 * so this is a genuine client error, distinct from a COBOL business fail
	 * code.</p>
	 *
	 * @param ex the binding/validation exception for the request body
	 * @return an HTTP&nbsp;400 response describing the field-level errors
	 */
	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(
			MethodArgumentNotValidException ex)
	{
		String message = ex.getBindingResult().getFieldErrors().stream()
				.map(fieldError -> fieldError.getField() + ": "
						+ fieldError.getDefaultMessage())
				.collect(Collectors.joining(MESSAGE_DELIMITER));
		if (message.isEmpty())
		{
			message = VALIDATION_FAILED_MESSAGE;
		}
		ErrorResponse body = new ErrorResponse(false, NO_FAIL_CODE, message);
		return ResponseEntity.badRequest().body(body);
	}

	/**
	 * Translates a Jakarta Bean Validation failure raised for {@code @Validated}
	 * method parameters (path / query parameters, F-021) into an HTTP&nbsp;400
	 * response. The constraint violations are aggregated into a single concise,
	 * user-safe message; no business fail code applies, so the fail code is left
	 * empty.
	 *
	 * @param ex the constraint-violation exception for the request parameters
	 * @return an HTTP&nbsp;400 response describing the constraint violations
	 */
	@ExceptionHandler(ConstraintViolationException.class)
	public ResponseEntity<ErrorResponse> handleConstraintViolation(
			ConstraintViolationException ex)
	{
		String message = ex.getConstraintViolations().stream()
				.map((ConstraintViolation<?> violation) -> violation
						.getPropertyPath() + ": " + violation.getMessage())
				.collect(Collectors.joining(MESSAGE_DELIMITER));
		if (message.isEmpty())
		{
			message = VALIDATION_FAILED_MESSAGE;
		}
		ErrorResponse body = new ErrorResponse(false, NO_FAIL_CODE, message);
		return ResponseEntity.badRequest().body(body);
	}

	/**
	 * Translates a failure to read or parse the HTTP request body &mdash; for
	 * example malformed JSON, an unexpected end of input, a wrong content type,
	 * or a value that cannot be bound to the target field type &mdash; into an
	 * HTTP&nbsp;400 response.
	 *
	 * <p>Without this dedicated handler such failures would fall through to the
	 * {@link #handleUnexpected(Exception) catch-all} and be reported as
	 * HTTP&nbsp;500, mislabelling a client-side input mistake as a server fault.
	 * Because the request never reached business logic, no COBOL business fail
	 * code applies and the fail code is left empty (never invented), exactly as
	 * for the Bean Validation handlers above. The body is a generic, sanitised
	 * message that never echoes the offending payload or any parser/exception
	 * internal, so no internal detail is leaked (CWE-209 safe).</p>
	 *
	 * @param ex the message-not-readable exception (intentionally not surfaced to
	 *           the client)
	 * @return an HTTP&nbsp;400 response with a generic, safe message
	 */
	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ErrorResponse> handleHttpMessageNotReadable(
			HttpMessageNotReadableException ex)
	{
		// HTTP 400 (not 500): a malformed / unreadable body is a client input
		// error, not a server fault. No business fail code applies; the body is
		// sanitised so no payload or parser internal is exposed (CWE-209 safe).
		ErrorResponse body = new ErrorResponse(false, NO_FAIL_CODE,
				MALFORMED_BODY_MESSAGE);
		return ResponseEntity.badRequest().body(body);
	}

	/**
	 * Translates an unsupported HTTP method on an otherwise-valid route into an
	 * HTTP&nbsp;405 (Method Not Allowed) response, populating the {@code Allow}
	 * header with the methods the route does support.
	 *
	 * <p>Without this dedicated handler Spring's
	 * {@link HttpRequestMethodNotSupportedException} would fall through to the
	 * {@link #handleUnexpected(Exception) catch-all} and be mislabelled as
	 * HTTP&nbsp;500. A wrong method is a client error, not a server fault; no
	 * COBOL business fail code applies, so the fail code is left empty.</p>
	 *
	 * @param ex the method-not-supported exception
	 * @return an HTTP&nbsp;405 response with the {@code Allow} header set
	 */
	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	public ResponseEntity<ErrorResponse> handleMethodNotSupported(
			HttpRequestMethodNotSupportedException ex)
	{
		ErrorResponse body = new ErrorResponse(false, NO_FAIL_CODE,
				METHOD_NOT_SUPPORTED_MESSAGE);
		ResponseEntity.BodyBuilder builder = ResponseEntity
				.status(HttpStatus.METHOD_NOT_ALLOWED);
		if (ex.getSupportedHttpMethods() != null)
		{
			builder.allow(ex.getSupportedHttpMethods()
					.toArray(new org.springframework.http.HttpMethod[0]));
		}
		return builder.body(body);
	}

	/**
	 * Translates an unsupported request content type into an HTTP&nbsp;415
	 * (Unsupported Media Type) response.
	 *
	 * <p>Without this dedicated handler Spring's
	 * {@link HttpMediaTypeNotSupportedException} (raised, for example, when a
	 * {@code POST}/{@code PUT} arrives with no or a wrong {@code Content-Type})
	 * would fall through to the {@link #handleUnexpected(Exception) catch-all}
	 * and be mislabelled as HTTP&nbsp;500. It is a client error; no COBOL
	 * business fail code applies, so the fail code is left empty.</p>
	 *
	 * @param ex the media-type-not-supported exception
	 * @return an HTTP&nbsp;415 response with a generic, safe message
	 */
	@ExceptionHandler(HttpMediaTypeNotSupportedException.class)
	public ResponseEntity<ErrorResponse> handleMediaTypeNotSupported(
			HttpMediaTypeNotSupportedException ex)
	{
		ErrorResponse body = new ErrorResponse(false, NO_FAIL_CODE,
				MEDIA_TYPE_NOT_SUPPORTED_MESSAGE);
		return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
				.body(body);
	}

	/**
	 * Translates a request-parameter binding failure &mdash; a
	 * {@link MethodArgumentTypeMismatchException} (Spring cannot coerce a path or
	 * query parameter to its declared type) or a {@link NumberFormatException}
	 * (a controller's own {@code BankFormat.parse*} guard rejected a blank,
	 * non-numeric or over-width identifier path variable) &mdash; into an
	 * HTTP&nbsp;400 (Bad Request) response.
	 *
	 * <p>Without this handler such failures would reach the
	 * {@link #handleUnexpected(Exception) catch-all} and be reported as
	 * HTTP&nbsp;500, mislabelling a client input mistake as a server fault
	 * (QA&nbsp;F-009-B/F-010-B/F-013-B/F-009-C). The request never reached
	 * business logic, so no COBOL business fail code applies and the fail code is
	 * left empty; the body is generic so it never echoes the offending value
	 * (CWE-209 safe).</p>
	 *
	 * @param ex the type-mismatch or number-format exception (not surfaced)
	 * @return an HTTP&nbsp;400 response with a generic, safe message
	 */
	@ExceptionHandler({ MethodArgumentTypeMismatchException.class,
			NumberFormatException.class })
	public ResponseEntity<ErrorResponse> handleParameterTypeMismatch(
			Exception ex)
	{
		ErrorResponse body = new ErrorResponse(false, NO_FAIL_CODE,
				INVALID_PARAMETER_MESSAGE);
		return ResponseEntity.badRequest().body(body);
	}

	/**
	 * Translates a request for a path that matches no route &mdash; Spring MVC's
	 * {@link NoResourceFoundException} (raised since Spring&nbsp;6.1 when no
	 * handler and no static resource match the path) or the legacy
	 * {@link NoHandlerFoundException} &mdash; into an HTTP&nbsp;404 (Not Found)
	 * response.
	 *
	 * <p>Without this dedicated handler an unknown route would fall through to
	 * the {@link #handleUnexpected(Exception) catch-all} and be mislabelled as
	 * HTTP&nbsp;500, because that catch-all is declared for {@code Exception} and
	 * would otherwise intercept these "no route" exceptions. A missing resource
	 * is a client addressing mistake, not a server fault, so the correct status
	 * is&nbsp;404. Spring resolves the most specific {@code @ExceptionHandler}
	 * first, so declaring these types here takes precedence over the broad
	 * catch-all. No COBOL business fail code applies (the request never reached a
	 * business program), so the fail code is left empty (never invented), and the
	 * body is generic so it never echoes the offending URL or any routing
	 * internal (CWE-209 safe).</p>
	 *
	 * @param ex the no-route exception (intentionally not surfaced to the client)
	 * @return an HTTP&nbsp;404 response with a generic, safe message
	 */
	@ExceptionHandler({ NoResourceFoundException.class,
			NoHandlerFoundException.class })
	public ResponseEntity<ErrorResponse> handleNotFound(Exception ex)
	{
		ErrorResponse body = new ErrorResponse(false, NO_FAIL_CODE,
				NOT_FOUND_MESSAGE);
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
	}

	/**
	 * Translates a persistence-layer {@link DataIntegrityViolationException} into
	 * an HTTP&nbsp;400 (Bad Request) response.
	 *
	 * <p>Some invalid inputs pass Bean Validation yet are still rejected by the
	 * database when the work is flushed &mdash; most notably a monetary value
	 * whose <em>computed result</em> overflows the column precision
	 * ({@code NUMERIC(12,2)}; QA Issue&nbsp;6, an extreme payment amount), or a
	 * {@code NOT NULL} column left unset by a request that omitted a
	 * contract-required field (QA Issue&nbsp;7, the defence-in-depth backstop
	 * behind the DTO {@code @NotNull} guards). Without this dedicated handler
	 * such failures would reach the {@link #handleUnexpected(Exception)
	 * catch-all} and be mislabelled as HTTP&nbsp;500 with a full Hibernate/SQL
	 * stack trace logged as though it were a server fault.</p>
	 *
	 * <p>A data-integrity violation driven by request content is a client input
	 * error, so HTTP&nbsp;400 is correct; no COBOL business fail code applies, so
	 * the fail code is left empty (never invented). The response body is the
	 * generic {@link #DATA_INTEGRITY_MESSAGE} and never exposes the SQL,
	 * constraint name, column, table, or any database internal (CWE-209 safe).
	 * The root cause is logged server-side at {@code WARN} (a concise
	 * most-specific-cause message, <em>not</em> a full {@code ERROR} stack trace)
	 * so the condition stays traceable without polluting the log with
	 * server-fault noise for what is really a client mistake.</p>
	 *
	 * @param ex the data-integrity exception (intentionally not surfaced to the
	 *           client)
	 * @return an HTTP&nbsp;400 response with a generic, safe message
	 */
	@ExceptionHandler(DataIntegrityViolationException.class)
	public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(
			DataIntegrityViolationException ex)
	{
		// HTTP 400 (not 500): a constraint rejection driven by request content is
		// a client input error. Log the concise root cause at WARN (not a full
		// ERROR stack) so it stays traceable without server-fault log noise; the
		// client body stays generic so no SQL/constraint detail leaks (CWE-209).
		LOG.warn("Data integrity violation translated to HTTP 400: {}",
				ex.getMostSpecificCause().getMessage());
		ErrorResponse body = new ErrorResponse(false, NO_FAIL_CODE,
				DATA_INTEGRITY_MESSAGE);
		return ResponseEntity.badRequest().body(body);
	}

	/**
	 * Translates a content-negotiation failure
	 * ({@link HttpMediaTypeNotAcceptableException}, HTTP&nbsp;406) into a clean,
	 * <strong>body-less</strong> response.
	 *
	 * <p>This exception is raised when the request's {@code Accept} header cannot
	 * be satisfied by any representation the endpoint can produce (for example
	 * {@code Accept: text/plain} against a JSON-only endpoint; QA Issue&nbsp;8).
	 * The subtlety is that the advice <em>must not</em> return a serialised body
	 * here: any {@link ErrorResponse} would itself have to be content-negotiated,
	 * the same unsatisfiable {@code Accept} header would reject it again, and the
	 * advice would fail recursively (Spring's "Failure in &#64;ExceptionHandler"
	 * &mdash; exactly the nested-handler noise QA observed). Returning a body-less
	 * HTTP&nbsp;406 via {@link ResponseEntity#build()} sidesteps content
	 * negotiation entirely and resolves cleanly.</p>
	 *
	 * <p>The condition is logged once at {@code WARN} (a client header mistake,
	 * not a server fault) and no body is emitted, so nothing is leaked.</p>
	 *
	 * @param ex the not-acceptable exception (intentionally not surfaced)
	 * @return a body-less HTTP&nbsp;406 response
	 */
	@ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
	public ResponseEntity<Void> handleMediaTypeNotAcceptable(
			HttpMediaTypeNotAcceptableException ex)
	{
		// Body-less by design: emitting any body would be re-negotiated against
		// the same unsatisfiable Accept header and fail the handler recursively.
		LOG.warn("Request Accept header not satisfiable, returning HTTP 406: {}",
				ex.getMessage());
		return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE).build();
	}

	/**
	 * Final catch-all for any other (unexpected) exception. Returns a sanitised
	 * HTTP&nbsp;500 response that never exposes a stack trace, SQL, exception
	 * class name, or any internal / mainframe detail to the caller. The original
	 * exception is intentionally not echoed into the body.
	 *
	 * <p>The exception <em>is</em> logged server-side at {@code ERROR} (with its
	 * stack trace) so that every HTTP&nbsp;500 is traceable to its root cause in
	 * the application log &mdash; the catch-all is deliberately a last resort, and
	 * a 500 reaching it signals a condition that should be investigated. Logging
	 * happens only in the server log; the client response body remains the
	 * generic {@link #UNEXPECTED_ERROR_MESSAGE} (CWE-209 safe).</p>
	 *
	 * @param ex the unexpected exception (intentionally not surfaced to the client)
	 * @return an HTTP&nbsp;500 response with a generic, safe message
	 */
	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex)
	{
		// Record the genuine root cause server-side (never to the client) so an
		// unexpected 500 is always traceable, not silent.
		LOG.error("Unexpected error handling request", ex);
		ErrorResponse body = new ErrorResponse(false, NO_FAIL_CODE,
				UNEXPECTED_ERROR_MESSAGE);
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(body);
	}

	/**
	 * Compact, contract-safe error body emitted by this advice. It exposes only
	 * the fields a frozen-contract consumer looks for and is deliberately
	 * decoupled from every endpoint-specific DTO, so the advice compiles and
	 * behaves correctly in isolation.
	 *
	 * <p>Serialises to {@code {"success":false,"failCode":"...","message":"..."}}.
	 * The {@code failCode} carries the verbatim {@link BusinessRuleException}
	 * code for a business rejection and an empty string when no business code
	 * applies.</p>
	 *
	 * @param success  the success flag; always {@code false} for an error body
	 * @param failCode the verbatim COBOL fail code, or empty when none applies
	 * @param message  a user-safe description of the failure
	 */
	private static record ErrorResponse(boolean success, String failCode,
			String message)
	{
	}
}
