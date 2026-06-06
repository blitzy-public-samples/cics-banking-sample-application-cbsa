/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.core.exception;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Centralised REST-boundary exception translation for the {@code bank-core}
 * controllers, reproducing the COBOL {@code ABNDPROC} role of turning a failure
 * into a structured outcome rather than letting it propagate raw.
 *
 * <h2>Division of responsibility with the controllers</h2>
 * <p>Each mutating controller already catches {@link BusinessRuleException}
 * itself and renders the failure onto its <em>own</em> frozen response envelope
 * (for example a create-account failure sets {@code CommSuccess="N"} and
 * {@code CommFailCode} inside the {@code CreAcc} envelope, while a delete sets an
 * integer {@code DelAccFailCd}). That per-endpoint handling is required because
 * the ten frozen z/OS Connect envelopes each carry their fail indicator in a
 * different field with a different type and a different "success" sentinel
 * (empty string, {@code "Y"}/{@code "N"}, numeric {@code "0"}, or an
 * {@code int}); a single advice cannot reproduce all of those shapes.</p>
 *
 * <p>This advice is therefore a <strong>defensive safety net</strong> for the
 * cases the controllers do not (and should not) shape themselves:</p>
 * <ul>
 *   <li>a {@link BusinessRuleException} that nonetheless escapes a controller
 *       &mdash; rendered as a minimal generic outcome so the fail code is never
 *       lost (HTTP 200, because consumers parse the body rather than the status
 *       for business outcomes);</li>
 *   <li>request <strong>validation</strong> failures
 *       ({@link MethodArgumentNotValidException}) and <strong>unreadable</strong>
 *       request bodies ({@link HttpMessageNotReadableException}) &mdash; genuine
 *       client errors, rendered as HTTP 400;</li>
 *   <li>any other unexpected exception &mdash; rendered as HTTP 500 without
 *       leaking stack traces into the response envelope.</li>
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler
{

	/** Logger for diagnostic context (failures are logged, not echoed verbatim). */
	private static final Logger LOG = LoggerFactory
			.getLogger(GlobalExceptionHandler.class);

	/** Response key carrying the success flag in the generic fallback body. */
	private static final String KEY_SUCCESS = "success";

	/** Response key carrying the fail code in the generic fallback body. */
	private static final String KEY_FAIL_CODE = "failCode";

	/** Response key carrying a human-readable message in error bodies. */
	private static final String KEY_ERROR = "error";

	/** Success-flag value denoting failure, matching the COBOL {@code COMM-SUCCESS='N'}. */
	private static final String FLAG_FAILURE = "N";

	/**
	 * Defensive fallback for a {@link BusinessRuleException} that escaped a
	 * controller's own envelope handling. Renders a minimal, generic outcome
	 * body carrying the verbatim fail code with HTTP 200, because consumers of
	 * the frozen contract inspect the response body (not the HTTP status) to
	 * detect a business rejection.
	 *
	 * @param ex the escaped business-rule exception
	 * @return an HTTP 200 response whose body reports failure and the fail code
	 */
	@ExceptionHandler(BusinessRuleException.class)
	public ResponseEntity<Map<String, Object>> handleBusinessRule(
			BusinessRuleException ex)
	{
		LOG.warn("Unhandled business-rule failure reached advice: failCode={}",
				ex.getFailCode());
		Map<String, Object> body = new LinkedHashMap<>();
		body.put(KEY_SUCCESS, FLAG_FAILURE);
		body.put(KEY_FAIL_CODE, ex.getFailCode());
		return ResponseEntity.ok(body);
	}

	/**
	 * Handles bean-validation failures on {@code @Valid} request bodies, mapping
	 * the first field error onto an HTTP 400 response.
	 *
	 * @param ex the validation exception
	 * @return an HTTP 400 response describing the first validation error
	 */
	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<Map<String, Object>> handleValidation(
			MethodArgumentNotValidException ex)
	{
		String message = "Validation failed";
		if (ex.getBindingResult().getFieldError() != null)
		{
			message = ex.getBindingResult().getFieldError().getField() + ": "
					+ ex.getBindingResult().getFieldError()
							.getDefaultMessage();
		}
		LOG.info("Request validation failed: {}", message);
		Map<String, Object> body = new LinkedHashMap<>();
		body.put(KEY_ERROR, message);
		return ResponseEntity.badRequest().body(body);
	}

	/**
	 * Handles an unreadable or malformed request body (for example invalid JSON),
	 * mapping it onto an HTTP 400 response.
	 *
	 * @param ex the message-not-readable exception
	 * @return an HTTP 400 response indicating a malformed request body
	 */
	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<Map<String, Object>> handleUnreadable(
			HttpMessageNotReadableException ex)
	{
		LOG.info("Malformed request body: {}", ex.getMostSpecificCause()
				.getMessage());
		Map<String, Object> body = new LinkedHashMap<>();
		body.put(KEY_ERROR, "Malformed request body");
		return ResponseEntity.badRequest().body(body);
	}

	/**
	 * Final catch-all for any other exception. Logs the full detail server-side
	 * and returns a sanitised HTTP 500 response that never exposes a stack trace
	 * or internal message to the caller.
	 *
	 * @param ex the unexpected exception
	 * @return an HTTP 500 response with a generic message
	 */
	@ExceptionHandler(Exception.class)
	public ResponseEntity<Map<String, Object>> handleUnexpected(Exception ex)
	{
		LOG.error("Unexpected error processing request", ex);
		Map<String, Object> body = new LinkedHashMap<>();
		body.put(KEY_ERROR, "Internal server error");
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(body);
	}

}
