/*                                                                        */
/* Copyright IBM Corp. 2025                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.springboot.paymentinterface.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import com.ibm.cics.cip.bank.springboot.paymentinterface.jsonclasses.paymentinterface.DbcrJson;
import com.ibm.cics.cip.bank.springboot.paymentinterface.jsonclasses.paymentinterface.OriginJson;
import com.ibm.cics.cip.bank.springboot.paymentinterface.jsonclasses.paymentinterface.TransferForm;

/*
 * SEC-R1 regression lock (closes QA test-coverage finding #2).
 *
 * Mandatory application-layer rule SEC-R1: the payment channel's facility type
 * (FACILTYPE = 496, encoded "0496") MUST be bound from the route context - the fixed
 * /makepayment/dbcr endpoint that both Payment controllers target - and NEVER from the
 * request body. This test regression-locks that guarantee at the (testable) Spring layer so a
 * future change that let a client supply a facility type through the body would fail here.
 *
 * The facility type is realized in {@code OriginJson}: the {@code commFaciltype} field is a
 * fixed literal "0496" and the {@code OriginJson(String organisation)} constructor - the only
 * path the controllers use - sets ONLY the applid/userid from the organisation, never the
 * facility type. {@code TransferForm} (the object bound from the inbound request) exposes no
 * facility field at all, so there is structurally no way for the body to carry one.
 *
 * Assertions:
 *   1. OriginJson always yields facility "0496", independent of the organisation input.
 *   2. The full request->payload path (TransferForm -> DbcrJson -> OriginJson) always yields
 *      "0496", even with an adversarial organisation value.
 *   3. TransferForm has NO facility-related field or accessor (structural proof the body
 *      cannot carry a facility type).
 *   4. A POST to /paydbcr that smuggles facility parameters in the body binds cleanly with NO
 *      field errors - the spurious parameters are ignored, confirming they cannot influence
 *      the route-bound facility type.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SecR1RouteBindingTest
{

	@Autowired
	private MockMvc mvc;

	// The route-bound facility type for the payment channel (FACILTYPE=496).
	private static final String ROUTE_BOUND_FACILITY = "0496";


	@BeforeAll
	static void stubZosConnectEndpoint()
	{
		System.setProperty("CBSA_ZOSCONN_HOST", "localhost");
		System.setProperty("CBSA_ZOSCONN_PORT", "38417");
		System.setProperty("CBSA_ZOSCONN_SCHEME", "http");
	}


	@AfterAll
	static void clearZosConnectEndpoint()
	{
		System.clearProperty("CBSA_ZOSCONN_HOST");
		System.clearProperty("CBSA_ZOSCONN_PORT");
		System.clearProperty("CBSA_ZOSCONN_SCHEME");
	}


	@Test
	void originJsonFacilityTypeIsFixedRegardlessOfOrganisation()
	{
		// The organisation value must never change the facility type - it is bound from the
		// route, not derived from any request input.
		assertThat(new OriginJson("ACME").getCommFacilType())
				.isEqualTo(ROUTE_BOUND_FACILITY);
		assertThat(new OriginJson("").getCommFacilType())
				.isEqualTo(ROUTE_BOUND_FACILITY);
		assertThat(new OriginJson("MORTGAGE-LOAN-999").getCommFacilType())
				.isEqualTo(ROUTE_BOUND_FACILITY);
	}


	@Test
	void requestToPayloadPathAlwaysBindsRouteFacility()
	{
		// The full inbound-form -> outbound-payload construction the controllers perform. Even
		// an adversarial organisation string cannot alter the route-bound facility type.
		TransferForm form =
				new TransferForm("12345678", 10.0f, "MORTGAGE-LOAN");
		DbcrJson payload = new DbcrJson(form);
		assertThat(payload.getCommOrigin().getCommFacilType())
				.as("facility type must be route-bound 0496, never from the body")
				.isEqualTo(ROUTE_BOUND_FACILITY);
	}


	@Test
	void transferFormExposesNoFacilityBinding()
	{
		// Structural proof: the object bound from the request body has no facility field or
		// accessor, so a client cannot supply a facility type through the body.
		assertThat(TransferForm.class.getDeclaredFields())
				.as("TransferForm must declare no facility-type field")
				.noneMatch(f -> f.getName().toLowerCase()
						.contains("facil"));
		assertThat(TransferForm.class.getMethods())
				.as("TransferForm must expose no facility-type accessor")
				.noneMatch(m -> m.getName().toLowerCase()
						.contains("facil"));
	}


	@Test
	@WithMockUser(username = "teller", roles = "TELLER")
	void bodySuppliedFacilityParametersAreIgnoredAtBoundary()
			throws Exception
	{
		// Smuggle facility parameters in the body. They must be silently ignored (no binding
		// error), proving they cannot override the route-bound facility type. The legitimate
		// fields bind normally.
		mvc.perform(post("/paydbcr").with(csrf())
				.param("acctNumber", "12345678")
				.param("amount", "10")
				.param("organisation", "ACME")
				.param("facilityType", "999")
				.param("FACILTYPE", "999")
				.param("commFaciltype", "999"))
				.andExpect(model().attributeHasNoErrors("transferForm"));
	}

}
