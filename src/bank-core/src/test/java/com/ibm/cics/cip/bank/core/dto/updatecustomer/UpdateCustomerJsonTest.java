/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.core.dto.updatecustomer;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Unit test for {@link UpdateCustomerJson} pinning the empty-body validation
 * behaviour of the inner {@code UpdCust} payload (QA finding Issue&nbsp;2,
 * feature F-021).
 *
 * <p>Mirroring the {@code UpdateAccountJson} / {@code PaymentJson} siblings that
 * "got it right", the no-argument constructor leaves the inner
 * {@link UpdcustJson} payload {@code null} (no eager initialisation). With the
 * {@code @NotNull} guard on the nested field, an empty {@code {}} request body
 * &mdash; or an explicit {@code {"UpdCust": null}} &mdash; is therefore rejected
 * with HTTP&nbsp;{@code 400} by the {@code @Valid}+{@code @NotNull} cascade
 * BEFORE the controller dereferences the payload, instead of degrading into a
 * business-fail envelope at HTTP&nbsp;{@code 200}.</p>
 *
 * <p>This supersedes the earlier eager-initialisation expectation: leaving the
 * field {@code null} cannot raise a {@link NullPointerException}&rarr;{@code 500}
 * because Bean Validation runs before the controller body executes (proven by
 * the {@code updateaccount} / {@code payment} endpoints, which already leave the
 * field {@code null} and return {@code 400} for an empty body). A populated
 * response built via {@link UpdateCustomerJson#UpdateCustomerJson(UpdcustJson)}
 * still serialises its inner payload under the {@code UpdCust} key, so the wire
 * shape of a real response is unchanged.</p>
 */
@DisplayName("UpdateCustomerJson — empty-body @NotNull validation (Issue 2, F-021)")
class UpdateCustomerJsonTest
{

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	@DisplayName("no-arg constructor leaves the inner UpdCust payload null (so @NotNull can reject an empty body)")
	void noArgConstructorLeavesInnerPayloadNull()
	{
		UpdateCustomerJson envelope = new UpdateCustomerJson();

		assertThat(envelope.getUpdcust()).isNull();
	}

	@Test
	@DisplayName("deserialising an empty {} body leaves the inner payload null (drives the @NotNull -> HTTP 400 path)")
	void deserialisingEmptyBodyLeavesInnerPayloadNull() throws Exception
	{
		UpdateCustomerJson envelope = objectMapper.readValue("{}",
				UpdateCustomerJson.class);

		// No UpdCust key in the body and no eager initialisation, so the inner
		// payload stays null. At the controller, the @NotNull guard on the
		// nested field then rejects the request with HTTP 400 (via
		// MethodArgumentNotValidException -> GlobalExceptionHandler) BEFORE the
		// payload is dereferenced — this is the heart of the Issue-2 fix and
		// matches PUT /updacc/update and PUT /makepayment/dbcr.
		assertThat(envelope.getUpdcust()).isNull();
	}

	@Test
	@DisplayName("deserialising an explicit {\"UpdCust\":null} body leaves the inner payload null (HTTP 400 path)")
	void deserialisingExplicitNullLeavesInnerPayloadNull() throws Exception
	{
		UpdateCustomerJson envelope = objectMapper.readValue("{\"UpdCust\":null}",
				UpdateCustomerJson.class);

		assertThat(envelope.getUpdcust()).isNull();
	}

	@Test
	@DisplayName("deserialising a populated {\"UpdCust\":{...}} body yields a non-null inner payload (round-trip preserved)")
	void deserialisingPopulatedBodyYieldsNonNullInnerPayload() throws Exception
	{
		UpdateCustomerJson envelope = objectMapper.readValue(
				"{\"UpdCust\":{\"CommCustno\":\"0000000123\","
						+ "\"CommName\":\"Mr Alan Turing\","
						+ "\"CommAddress\":\"1 Maths Lane\"}}",
				UpdateCustomerJson.class);

		// A present UpdCust key populates the inner payload as before; the
		// empty-body fix does not alter the real request/response wire shape.
		assertThat(envelope.getUpdcust()).isNotNull();
		assertThat(envelope.getUpdcust().getCommCustno()).isEqualTo("0000000123");
		assertThat(envelope.getUpdcust().getCommName())
				.isEqualTo("Mr Alan Turing");
		assertThat(envelope.getUpdcust().getCommAddress())
				.isEqualTo("1 Maths Lane");
	}
}
