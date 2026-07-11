/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.springboot.paymentinterface.controllers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibm.cics.cip.bank.springboot.paymentinterface.ConnectionInfo;
import com.ibm.cics.cip.bank.springboot.paymentinterface.jsonclasses.paymentinterface.PaymentInterfaceJson;
import com.ibm.cics.cip.bank.springboot.paymentinterface.jsonclasses.paymentinterface.TransferForm;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClient.ResponseSpec;

// Security fix (V3 CWE-20 Improper Input Validation - OWASP A03 Injection): reject
// malformed request input at the controller boundary; violations short-circuit with 400.
@Validated
@RestController
public class ParamsController
{



	private static final Logger log = LoggerFactory
			.getLogger(ParamsController.class);


	// This follows a very similar format to the form submitting equivalents in
	// WebController.java
	// Instead of a form object, parameters required in the url
	@PostMapping("/submit")
	// Security fix (V2 CWE-306 Missing Authentication / CWE-862 Missing Authorization -
	// OWASP A01 Broken Access Control, A07 Identification & Authentication Failures):
	// restrict this money-movement operation to the TELLER role.
	@PreAuthorize("hasRole('TELLER')")
	public PaymentInterfaceJson submit(
			@RequestParam(name = "acctnum", required = true) @NotBlank @Size(max = 8) String acctNumber,
			@RequestParam(name = "amount", required = true) @Positive float amount,
			@RequestParam(name = "organisation", required = true) @NotBlank @Size(max = 16) String organisation)
			throws JsonProcessingException
	{
		log.info("AcctNumber: {}, Amount {}, Organisation {}", acctNumber,
				amount, organisation);
		TransferForm transferForm = new TransferForm(acctNumber, amount,
				organisation);

		PaymentInterfaceJson transferJson = new PaymentInterfaceJson(
				transferForm);

		String jsonString = new ObjectMapper().writeValueAsString(transferJson);
		log.info(jsonString);

		WebClient client = WebClient.create(
				ConnectionInfo.getAddressAndPort() + "/makepayment/dbcr");
		PaymentInterfaceJson responseObj;

		try
		{
			ResponseSpec response = client.put()
					.header("content-type", "application/json")
					.accept(MediaType.APPLICATION_JSON)
					.body(BodyInserters.fromValue(jsonString)).retrieve();
			String responseBody = response.bodyToMono(String.class).block();
			log.info(responseBody);
			responseObj = new ObjectMapper().readValue(responseBody,
					PaymentInterfaceJson.class);
			log.info("{}", responseObj);
			return responseObj;
		}
		catch (Exception e)
		{
			log.info(e.toString());
		}

		return null;
	}
}
