/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.springboot.customerservices;

import com.beust.jcommander.JCommander;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// Security fix - enables V2 Missing Authentication/Authorization
// (OWASP A01 Broken Access Control / A07 Identification & Authentication Failures;
// CWE-306 Missing Authentication, CWE-862 Missing Authorization) and
// V6 Missing HTTP Security Controls (OWASP A05 Security Misconfiguration;
// CWE-352 CSRF, CWE-942 Permissive CORS, CWE-693 Missing Security Headers):
// include the config package in component scanning so SecurityConfig
// (SecurityFilterChain, CORS, CSRF, security headers) is registered. Without this,
// the security beans never load and the entire remediation is silently inert.
@SpringBootApplication(scanBasePackages = {
    "com.ibm.cics.cip.bank.springboot.customerservices.controllers",
    "com.ibm.cics.cip.bank.springboot.customerservices.config" })
public class CustomerServices
{




	public static void main(String[] args)
	{
		final Logger log = LoggerFactory.getLogger(CustomerServices.class);
		JCommander.newBuilder().build().parse(args);

		log.info("Running with address: {}",
				ConnectionInfo.getAddressAndPort());

		// Run the application. From here out, only the WebController and
		// ParamsController classes really matter.
		SpringApplication.run(CustomerServices.class, args);
	}

}
