/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.core;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Executable entry point for the standalone {@code bank-core} module: the pure
 * Java, Spring Boot reimplementation of the CBSA (Bank Sample Application)
 * COBOL banking core, backed by PostgreSQL.
 *
 * <p>This class lives at the package root {@code com.ibm.cics.cip.bank.core} on
 * purpose. By anchoring {@link SpringBootApplication @SpringBootApplication}
 * here, Spring Boot's default component scanning together with the
 * auto-configured {@code @EntityScan} and {@code @EnableJpaRepositories} discover
 * every sibling subpackage &mdash; {@code config}, {@code constants},
 * {@code domain}, {@code entity}, {@code repository}, {@code service},
 * {@code controller}, {@code dto}, {@code exception}, and {@code bootstrap}
 * &mdash; without any explicit base-package configuration.</p>
 *
 * <p>{@link EnableAsync @EnableAsync} switches on Spring's asynchronous method
 * execution. It is required by the credit-agency fan-out, where
 * {@code CreditAgencyService} dispatches five {@code CompletableFuture} tasks
 * and aggregates their scores under a three-second deadline. The dedicated
 * {@code ThreadPoolTaskExecutor} backing those tasks is contributed by
 * {@code config/AsyncConfig} and tuned through the
 * {@code spring.task.execution.pool.*} properties in {@code application.yml};
 * this class only needs to enable async support. Without it, the
 * {@code @Async} work would execute on the calling thread and break the
 * deadline semantics.</p>
 *
 * <p>All datasource, JPA, Flyway, async-pool, and server settings are
 * externalized to {@code application.yml}, so the bootstrap deliberately stays
 * minimal and free of any mainframe-era startup mechanics.</p>
 */
@SpringBootApplication
@EnableAsync
public class BankCoreApplication
{

	/**
	 * Boots the Spring application context with embedded Tomcat, which in turn
	 * triggers Flyway schema migration, Hibernate validation, and registration
	 * of the REST controllers that expose the frozen banking API contract.
	 *
	 * @param args standard command-line arguments forwarded to Spring Boot;
	 *             all runtime configuration is supplied by {@code application.yml}
	 */
	public static void main(String[] args)
	{
		SpringApplication.run(BankCoreApplication.class, args);
	}

}
