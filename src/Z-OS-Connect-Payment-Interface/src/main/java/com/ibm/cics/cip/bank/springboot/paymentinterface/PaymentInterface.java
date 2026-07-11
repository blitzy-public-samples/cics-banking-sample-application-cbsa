/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.springboot.paymentinterface;

import com.beust.jcommander.JCommander;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// Security fix (V2 Missing Authentication/Authorization - OWASP A01 Broken Access Control / A07
// Identification & Authentication Failures, CWE-306/CWE-862; V6 Missing HTTP Security Controls -
// OWASP A05 Security Misconfiguration, CWE-352/CWE-942/CWE-693): include the config package in
// component scanning so SecurityConfig (SecurityFilterChain, CORS, CSRF, security headers) is
// registered. Without this, the security beans never load and the remediation is silently inert.
@SpringBootApplication(scanBasePackages = {
		"com.ibm.cics.cip.bank.springboot.paymentinterface.controllers",
		"com.ibm.cics.cip.bank.springboot.paymentinterface.config" })
public class PaymentInterface
{




	public static void main(String[] args)
	{
		final Logger log = LoggerFactory.getLogger(PaymentInterface.class);

		JCommander.newBuilder().build().parse(args);

		log.info("Running with address: {}",
				ConnectionInfo.getAddressAndPort());

		// Run the application. From here out, only the WebController and
		// ParamsController classes really matter.
		SpringApplication.run(PaymentInterface.class, args);
	}

}
