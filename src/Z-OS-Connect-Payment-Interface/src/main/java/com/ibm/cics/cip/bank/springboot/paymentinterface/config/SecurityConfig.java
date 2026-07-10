/*                                                                        */
/* Copyright IBM Corp. 2025                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.springboot.paymentinterface.config;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import static org.springframework.security.config.Customizer.withDefaults;

/*
 * Application-layer security for the Payment Interface module.
 * V2 Missing Authentication/Authorization - OWASP A01 Broken Access Control /
 *    A07 Identification & Authentication Failures (CWE-306, CWE-862): deny-by-default
 *    + HTTP Basic auth + method security.
 * V6 Missing HTTP Security Controls - OWASP A05 Security Misconfiguration:
 *    CWE-352 (CSRF) cookie token for the SPA; CWE-942 (permissive CORS) explicit
 *    origin allowlist; CWE-693 (missing security headers) explicit CSP header.
 * Spring Security version is governed by spring-boot-starter-parent 3.5.11
 * (Spring Security 6.x) - NEVER pin a Spring Security version here.
 */
@Configuration
@EnableWebSecurity
// @EnableMethodSecurity activates @PreAuthorize("hasRole('TELLER')") on the
// money-movement handlers (V2 - OWASP A01, CWE-862). Do NOT omit it.
@EnableMethodSecurity
public class SecurityConfig
{

	// V6 (CWE-942 permissive CORS): explicit origin allowlist from configuration
	// (cbsa.security.cors.allowed-origins in application.properties). NEVER '*'.
	@Value("${cbsa.security.cors.allowed-origins}")
	private String allowedOrigins;


	@Bean
	SecurityFilterChain filterChain(HttpSecurity http) throws Exception
	{
		// V2 (OWASP A01/A07; CWE-306/CWE-862): deny-by-default, all requests authenticated; HTTP Basic auth.
		http.authorizeHttpRequests(a -> a.anyRequest().authenticated())
				.httpBasic(withDefaults())
				// V6 (CWE-352 CSRF): cookie-based CSRF token for the SPA (XSRF-TOKEN cookie /
				// X-XSRF-TOKEN header, Axios convention). CSRF is NOT disabled.
				.csrf(c -> c.csrfTokenRepository(
						CookieCsrfTokenRepository.withHttpOnlyFalse()))
				// V6 (CWE-942 permissive CORS): wire the restrictive allowlist bean below.
				.cors(withDefaults())
				// V6 (CWE-693 missing headers): CSP is the ONLY header not added by Spring
				// Security defaults, so declare it explicitly. X-Content-Type-Options: nosniff,
				// X-Frame-Options: DENY and HSTS remain enabled via defaults - do NOT remove them.
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

}
