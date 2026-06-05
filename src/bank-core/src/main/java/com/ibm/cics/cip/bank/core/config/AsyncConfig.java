/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.core.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Spring {@link Configuration @Configuration} that contributes the dedicated
 * {@link ThreadPoolTaskExecutor} used by the asynchronous credit-agency
 * fan-out.
 *
 * <p><strong>Why this executor exists.</strong> The legacy CBSA customer-create
 * flow ({@code CRECUST}) drives five dummy credit-agency programs
 * ({@code CRDTAGY1}&ndash;{@code CRDTAGY5}, all identical) over the CICS Async
 * API on channel {@code CIPCREDCHANN}. Each agency responds after a random
 * interval of zero to three seconds with a credit score between 1 and 999,
 * while the parent waits a fixed three seconds &mdash; so any individual agency
 * has roughly a one-in-four chance of replying inside the window. The Java
 * reimplementation reproduces this with a {@code CreditAgencyService} that fans
 * out five asynchronous tasks, averages whatever results arrive within a
 * three-second deadline, and falls back to fail code {@code 'C'} when none do
 * (feature F-017). This class supplies only the thread pool those tasks run on;
 * it deliberately contains <em>no</em> timing, rating, or orchestration logic
 * &mdash; that behaviour lives entirely in {@code CreditAgencyService}.</p>
 *
 * <p><strong>Why {@code corePoolSize == 5} is mandatory.</strong> All five
 * tasks are submitted simultaneously. Because the queue has capacity (see
 * {@link #QUEUE_CAPACITY}), a {@link ThreadPoolTaskExecutor} will <em>queue</em>
 * surplus tasks rather than grow toward {@link #MAX_POOL_SIZE} once the core
 * threads are busy. A core size below five would therefore force some agencies
 * to wait in the queue instead of starting immediately, and they could miss the
 * three-second deadline even when their random response interval was short
 * &mdash; silently skewing the averaged result and the "none completed" path.
 * Sizing the core at exactly five guarantees all five agencies start
 * concurrently and each gets its full chance inside the deadline.</p>
 *
 * <p><strong>Relationship to {@code application.yml}.</strong> The
 * {@code spring.task.execution.pool.*} block in {@code application.yml} mirrors
 * the very same values configured here (core 5 / max 10 / queue 50 / thread
 * name prefix {@code credit-agency-}). That mirroring is intentional: Spring
 * Boot's {@code TaskExecutionAutoConfiguration#applicationTaskExecutor} is
 * {@code @ConditionalOnMissingBean(Executor.class)}, so the moment this class
 * registers its own executor bean the auto-configured one backs off and the
 * YAML values are no longer auto-applied. Hard-coding the identical values here
 * keeps the dedicated pool consistent with the documented defaults regardless of
 * which executor a future unqualified {@code @Async} happens to resolve to. If
 * either side changes, keep this file and {@code application.yml} in sync.</p>
 *
 * <p><strong>Async support.</strong> Spring's asynchronous-method support (the
 * {@code EnableAsync} switch) is declared once, on {@code BankCoreApplication},
 * and is intentionally <em>not</em> repeated here to avoid a redundant (and
 * potentially conflicting) second declaration. This class only defines the
 * executor bean.</p>
 *
 * @see ThreadPoolTaskExecutor
 */
@Configuration
public class AsyncConfig
{

	/**
	 * Bean name of the dedicated credit-agency fan-out executor.
	 *
	 * <p>Published as a constant so the {@code CreditAgencyService} agent can
	 * bind to the same identifier in a typo-proof way &mdash; for example via
	 * {@code @Async(AsyncConfig.CREDIT_AGENCY_EXECUTOR)} or
	 * {@code @Qualifier(AsyncConfig.CREDIT_AGENCY_EXECUTOR)}, or by submitting
	 * work directly against the injected executor. Because it is also the
	 * module's only {@code Executor} bean, an unqualified {@code @Async} still
	 * resolves to it, but the named qualifier is the documented contract.</p>
	 */
	public static final String CREDIT_AGENCY_EXECUTOR = "creditAgencyExecutor";

	/**
	 * Core thread count: the number of threads kept alive and started on demand
	 * up to the pool's core size. Fixed at five so all five credit-agency tasks
	 * run concurrently within the three-second deadline (F-017). Mirrors
	 * {@code spring.task.execution.pool.core-size} in {@code application.yml}.
	 */
	private static final int CORE_POOL_SIZE = 5;

	/**
	 * Maximum thread count the pool may grow to once the core threads are busy
	 * and the queue is full. Mirrors
	 * {@code spring.task.execution.pool.max-size} in {@code application.yml}.
	 */
	private static final int MAX_POOL_SIZE = 10;

	/**
	 * Bounded work-queue capacity. Mirrors
	 * {@code spring.task.execution.pool.queue-capacity} in
	 * {@code application.yml}. Note that a non-zero queue means tasks beyond the
	 * core size queue before the pool grows toward {@link #MAX_POOL_SIZE}, which
	 * is exactly why {@link #CORE_POOL_SIZE} must be five.
	 */
	private static final int QUEUE_CAPACITY = 50;

	/**
	 * Thread-name prefix applied to every pooled thread, making credit-agency
	 * threads easy to spot in logs and thread dumps. Mirrors
	 * {@code spring.task.execution.thread-name-prefix} in
	 * {@code application.yml}.
	 */
	private static final String THREAD_NAME_PREFIX = "credit-agency-";

	/**
	 * Defines the dedicated {@link ThreadPoolTaskExecutor} for the five-way
	 * credit-agency fan-out.
	 *
	 * <p>The pool is sized so that all five agency tasks
	 * ({@code CRDTAGY1}&ndash;{@code CRDTAGY5}) start immediately and run
	 * concurrently, giving each its full chance to reply inside the parent's
	 * three-second deadline. {@link ThreadPoolTaskExecutor#initialize()} is
	 * invoked before the bean is returned so the backing
	 * {@code java.util.concurrent.ThreadPoolExecutor} is created eagerly and the
	 * pool is ready the first time {@code CreditAgencyService} submits work.</p>
	 *
	 * @return a fully initialised executor (core {@value #CORE_POOL_SIZE} / max
	 *         {@value #MAX_POOL_SIZE} / queue {@value #QUEUE_CAPACITY} / thread
	 *         name prefix {@value #THREAD_NAME_PREFIX}) registered under the bean
	 *         name {@link #CREDIT_AGENCY_EXECUTOR}
	 */
	@Bean(name = CREDIT_AGENCY_EXECUTOR)
	public ThreadPoolTaskExecutor creditAgencyExecutor()
	{
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(CORE_POOL_SIZE);
		executor.setMaxPoolSize(MAX_POOL_SIZE);
		executor.setQueueCapacity(QUEUE_CAPACITY);
		executor.setThreadNamePrefix(THREAD_NAME_PREFIX);
		executor.initialize();
		return executor;
	}

}
