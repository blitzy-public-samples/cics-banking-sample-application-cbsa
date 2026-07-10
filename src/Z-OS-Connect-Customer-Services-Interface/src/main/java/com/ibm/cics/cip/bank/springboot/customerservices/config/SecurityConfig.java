/*                                                                        */
/* Copyright IBM Corp. 2025                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.springboot.customerservices.config;

import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import static org.springframework.security.config.Customizer.withDefaults;

/*
 * Application-layer security for the Customer Services module.
 *
 * Security fix summary (OWASP-aligned, config-first, minimal & non-invasive):
 *   V2 Missing Authentication/Authorization - OWASP A01 Broken Access Control /
 *      A07 Identification & Authentication Failures (CWE-306 Missing Authentication,
 *      CWE-862 Missing Authorization): deny-by-default + HTTP Basic auth + method security.
 *   V6 Missing HTTP Security Controls - OWASP A05 Security Misconfiguration:
 *      CWE-352 (CSRF) cookie token for the SPA; CWE-942 (permissive CORS) explicit
 *      origin allowlist; CWE-693 (missing security headers) explicit CSP header.
 *
 * Spring Security version is governed by spring-boot-starter-parent 3.5.11
 * (Spring Security 6.x) - NEVER pin a Spring Security version here.
 */
@Configuration
@EnableWebSecurity
// @EnableMethodSecurity activates @PreAuthorize("hasRole('TELLER')") on the
// money-movement handlers in the controllers package
// (V2 - OWASP A01 Broken Access Control, CWE-862 Missing Authorization).
@EnableMethodSecurity
public class SecurityConfig
{

	// V6 (CWE-942 permissive CORS): explicit origin allowlist read from configuration
	// (cbsa.security.cors.allowed-origins in application.properties). Comma-separated
	// list. NEVER a wildcard '*'. Key is a byte-for-byte cross-file contract.
	@Value("${cbsa.security.cors.allowed-origins}")
	private String allowedOrigins;


	// Security fix (F-001 / V2 CWE-306 Missing Authentication, CWE-862 Missing Authorization -
	// OWASP A01 Broken Access Control / A07 Identification & Authentication Failures; V7 CWE-798
	// Hardcoded Credentials - OWASP A05 Security Misconfiguration): externalized TELLER role
	// credentials. @PreAuthorize("hasRole('TELLER')") on the state-changing handlers requires a
	// principal that holds ROLE_TELLER; without a role source EVERY authenticated caller is denied
	// with 403 (finding F-001). Username and password are read from configuration (env-backed via
	// application.properties) so NO secret is committed to source control.
	@Value("${cbsa.security.teller.username:teller}")
	private String tellerUsername;

	@Value("${cbsa.security.teller.password:}")
	private String tellerPassword;

	private static final Logger LOG = LoggerFactory
			.getLogger(SecurityConfig.class);


	@Bean
	SecurityFilterChain filterChain(HttpSecurity http) throws Exception
	{
		// V2 (OWASP A01 Broken Access Control / A07 Auth Failures; CWE-306/CWE-862):
		// deny-by-default - every request must be authenticated; HTTP Basic authentication.
		http.authorizeHttpRequests(a -> a.anyRequest().authenticated())
				.httpBasic(withDefaults())
				// V6 (CWE-352 CSRF): cookie-based CSRF token for the JavaScript SPA -
				// writes the XSRF-TOKEN cookie, read back from the X-XSRF-TOKEN header
				// (Axios convention). CSRF protection is NOT disabled.
				.csrf(c -> c.csrfTokenRepository(
						CookieCsrfTokenRepository.withHttpOnlyFalse()))
				// V6 (CWE-942 permissive CORS): wire the restrictive allowlist bean below.
				.cors(withDefaults())
				// V6 (CWE-693 missing security headers): Content-Security-Policy is the
				// ONLY header not added by Spring Security defaults, so declare it
				// explicitly. X-Content-Type-Options: nosniff, X-Frame-Options: DENY and
				// HSTS remain enabled via Spring Security defaults (do NOT remove them).
				.headers(h -> h.contentSecurityPolicy(
						csp -> csp.policyDirectives("default-src 'self'")));
		return http.build();
	}


	@Bean
	CorsConfigurationSource corsConfigurationSource()
	{
		// V6 (CWE-942 Permissive Cross-domain Policy): restrictive CORS allowlist.
		CorsConfiguration config = new CorsConfiguration();
		// Explicit origin allowlist from configuration (comma-separated); NEVER '*'.
		config.setAllowedOrigins(Arrays.stream(allowedOrigins.split(","))
				.map(String::trim).toList());
		// Explicit methods and headers (no wildcards on the contract surface).
		config.setAllowedMethods(
				List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
		config.setAllowedHeaders(List.of("Authorization", "Content-Type",
				"X-XSRF-TOKEN", "X-Requested-With"));
		// Credentials permitted ONLY because origins are explicitly allowlisted
		// (never combined with a wildcard origin - that would be CWE-942).
		config.setAllowCredentials(true);
		UrlBasedCorsConfigurationSource source =
				new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", config);
		return source;
	}


	@Bean
	PasswordEncoder passwordEncoder()
	{
		// Security fix (V7 CWE-798 Hardcoded Credentials - OWASP A05 Security Misconfiguration):
		// hash the externalized TELLER credential with the Spring Security delegating encoder
		// (bcrypt by default) - never store or compare a plain-text password.
		return PasswordEncoderFactories.createDelegatingPasswordEncoder();
	}


	@Bean
	UserDetailsService userDetailsService(PasswordEncoder passwordEncoder)
	{
		// Security fix (F-001 / V2 CWE-862 Missing Authorization - OWASP A01 Broken Access Control):
		// provision the TELLER authority source so @PreAuthorize("hasRole('TELLER')") admits an
		// authorized teller (authority ROLE_TELLER) and denies non-teller principals (403), keeping
		// behavior identical for authorized users. Deny-by-default: when no TELLER password is
		// externally provisioned, register NO user - mirroring the Liberty externalized
		// ${zosconnect.registry.*} pattern; the deployer MUST supply CBSA_TELLER_PASSWORD.
		// NEVER hardcode a credential here (V7 CWE-798).
		if (tellerPassword == null || tellerPassword.isBlank())
		{
			LOG.warn(
					"No TELLER credential provisioned (set CBSA_TELLER_PASSWORD); no in-memory user "
							+ "registered - authenticated access is denied by default until a teller "
							+ "credential is supplied.");
			return new InMemoryUserDetailsManager();
		}
		UserDetails teller = User.withUsername(tellerUsername)
				.password(passwordEncoder.encode(tellerPassword))
				.roles("TELLER")
				.build();
		return new InMemoryUserDetailsManager(teller);
	}

}
