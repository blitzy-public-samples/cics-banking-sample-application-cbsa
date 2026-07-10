/*                                                                        */
/* Copyright IBM Corp. 2025                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.springboot.paymentinterface.controllers;

import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.validation.ConstraintViolationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

/*
 * Centralized exception handling for the Payment Interface module.
 *
 * Security fix (V3 CWE-20 Improper Input Validation - OWASP A03 Injection):
 *   ParamsController is annotated @Validated at class level, which activates Spring's
 *   method-validation interceptor. When a @RequestParam constraint on /submit
 *   (@NotBlank / @Size / @Positive) is violated, that interceptor raises
 *   jakarta.validation.ConstraintViolationException. Spring MVC's
 *   DefaultHandlerExceptionResolver does NOT map ConstraintViolationException to a 400
 *   (it only maps MethodArgumentNotValidException / MissingServletRequestParameterException /
 *   MethodArgumentTypeMismatchException), so without this advice the violation propagated to a
 *   misleading HTTP 500. This handler maps constraint violations (and @Valid body-binding
 *   failures) to a reject-by-default HTTP 400 BEFORE any downstream money-movement WebClient
 *   call, restoring the intended API contract (QA finding F-QA1).
 *
 * Security fix (V4 CWE-209 Sensitive Data Exposure / CWE-532 Insertion of Sensitive
 *   Information into Log File - OWASP A09 Security Logging Failures / A02):
 *   The response body is a fixed, generic payload; the exception message, the offending
 *   input value, the exception class name and the stack trace are NEVER returned to the
 *   client. Only the exception TYPE name is logged server-side for diagnostics - the
 *   rejected value and any PII are never logged.
 *
 * Deliberately NARROW scope: this advice handles ONLY input-validation exceptions. It does
 * NOT declare a catch-all @ExceptionHandler(Exception.class). Spring Security's
 * AccessDeniedException (wrong role -> 403) and the authentication entry point
 * (unauthenticated -> 401) therefore continue to be handled by the security filter chain and
 * are NEVER swallowed here, preserving the V2 (OWASP A01 Broken Access Control / A07
 * Identification & Authentication Failures; CWE-306/CWE-862) authorization contract.
 */
@ControllerAdvice
public class GlobalExceptionHandler
{


	private static final Logger log = LoggerFactory
			.getLogger(GlobalExceptionHandler.class);

	// Fixed, generic client message - never echoes the offending input (V4 CWE-209).
	private static final String VALIDATION_ERROR_MSG = "Invalid request parameters.";


	// Method-level @Validated request-param constraints (ParamsController /submit) throw
	// ConstraintViolationException, which Spring MVC does not map to 400 by default. Map it
	// here so malformed input is rejected with a generic HTTP 400 (V3, QA finding F-QA1).
	@ExceptionHandler(ConstraintViolationException.class)
	public ResponseEntity<Map<String, Object>> handleConstraintViolation(
			ConstraintViolationException ex)
	{
		// Log only the exception TYPE (never the message or the offending value) to avoid
		// leaking submitted input or PII into the logs (V4 CWE-532).
		log.info("Rejected request: input validation failure ({})",
				ex.getClass().getSimpleName());
		return badRequest();
	}


	// @Valid on a bound object without an immediately-following BindingResult throws
	// MethodArgumentNotValidException; return the same generic 400 body so the whole module
	// answers validation failures consistently (V3/V4). Defensive: the existing controllers
	// pair @Valid with a BindingResult, so this is future-proofing, not a behavior change.
	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<Map<String, Object>> handleMethodArgumentNotValid(
			MethodArgumentNotValidException ex)
	{
		log.info("Rejected request: input validation failure ({})",
				ex.getClass().getSimpleName());
		return badRequest();
	}


	// Build a generic HTTP 400 body. No exception detail is included (V4 CWE-209).
	private static ResponseEntity<Map<String, Object>> badRequest()
	{
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("status", HttpStatus.BAD_REQUEST.value());
		body.put("error", HttpStatus.BAD_REQUEST.getReasonPhrase());
		body.put("message", VALIDATION_ERROR_MSG);
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
	}
}
