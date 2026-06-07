/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.core.dto.updatecustomer;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Unit test for {@link UpdateCustomerJson} pinning the eager-initialisation of
 * the inner {@code UpdCust} payload hardened in response to QA checkpoint CP3.
 *
 * <p>Mirroring the {@code CreateCustomerJson} sibling, the no-argument
 * constructor must instantiate the inner {@link UpdcustJson} so that
 * {@link UpdateCustomerJson#getUpdcust()} is never {@code null}. This is what
 * lets a request body with no {@code UpdCust} key (for example an empty
 * {@code {}} object) degrade gracefully through the normal business path
 * instead of triggering a {@link NullPointerException} that the global advice
 * would mislabel as HTTP&nbsp;500.</p>
 */
@DisplayName("UpdateCustomerJson — CP3 eager initialisation of the inner payload")
class UpdateCustomerJsonTest
{

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	@DisplayName("no-arg constructor eagerly initialises a non-null inner UpdCust payload")
	void noArgConstructorEagerlyInitialisesInnerPayload()
	{
		UpdateCustomerJson envelope = new UpdateCustomerJson();

		assertThat(envelope.getUpdcust()).isNotNull();
	}

	@Test
	@DisplayName("deserialising an empty {} body still yields a non-null inner payload (graceful fail-'1' path)")
	void deserialisingEmptyBodyYieldsNonNullInnerPayload() throws Exception
	{
		UpdateCustomerJson envelope = objectMapper.readValue("{}",
				UpdateCustomerJson.class);

		// No UpdCust key in the body, yet — thanks to the eager-init no-arg
		// constructor — the inner payload is present so the controller can copy
		// it into the service form WITHOUT a NullPointerException (the root
		// cause of the QA HTTP-500). This is the heart of the Issue-2 fix.
		assertThat(envelope.getUpdcust()).isNotNull();

		// The inner payload carries the legacy envelope's default wire shape:
		// string fields default to a single space " " (NOT null), preserving the
		// frozen z/OS Connect default document shape. Crucially, the customer
		// number " " drives a graceful business rejection downstream:
		// CustomerService.parseCustomerNumber(" ") -> Long.parseLong("") ->
		// NumberFormatException -> BusinessRuleException(fail '1'), which the
		// controller catches and renders as HTTP 200 / CommUpdSuccess="N" /
		// CommUpdFailCd="1" — consistent with POST /crecust {} degrading to a
		// fail code rather than HTTP 500.
		assertThat(envelope.getUpdcust().getCommCustno()).isEqualTo(" ");
		assertThat(envelope.getUpdcust().getCommName()).isEqualTo(" ");
		assertThat(envelope.getUpdcust().getCommAddress()).isEqualTo(" ");
	}
}
