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
 * reimplementation reproduces this with {@code CustomerService} fanning out five
 * asynchronous {@code CreditAgencyService.requestCreditScore()} tasks, averaging
 * whatever results arrive within a three-second deadline, and falling back to
 * fail code {@code 'C'} when none do (feature F-017). Each
 * {@code CreditAgencyService} call models one agency &mdash; a random delay plus
 * a random 1&ndash;999 score. This class supplies only the thread pool those
 * agency tasks run on; it deliberately contains <em>no</em> timing, rating, or
 * orchestration logic.</p>
 *
 * <p><strong>Why {@code corePoolSize == 5} is mandatory.</strong> All five
 * tasks of a single customer-create are submitted simultaneously. Sizing the
 * core at exactly five guarantees those five agencies all start immediately on
 * resident core threads &mdash; each getting its full chance to reply inside the
 * three-second deadline &mdash; without even having to grow the pool. A core
 * size below five would force some agencies of a single create to wait for a
 * thread and could make them miss the deadline even when their random response
 * interval was short, silently skewing the averaged result and the "none
 * completed" path.</p>
 *
 * <p><strong>Why the queue is zero and the max is fifty (F2-01).</strong> The
 * work-queue capacity is fixed at zero (a direct-handoff
 * {@link java.util.concurrent.SynchronousQueue}) and the maximum pool size at
 * fifty. A standard {@link ThreadPoolTaskExecutor} grows past its core size only
 * <em>after</em> the work queue is full, so a non-zero queue would absorb the
 * agency tasks of concurrent customer-creates and never let the pool grow,
 * capping effective parallelism at five threads regardless of load. Under even
 * three simultaneous creates the surplus agencies would queue behind the five
 * core threads and miss the three-second deadline, yielding a spurious fail code
 * {@code 'C'} ("no agency responded") although every simulated agency is
 * healthy. With a zero-capacity queue the executor instead hands each task
 * straight to a thread, growing from five toward fifty on demand, so up to ten
 * concurrent creates (five agency tasks each) all run their fan-out at once. The
 * fan-out caller ({@code CustomerService.performCreditCheck}) already treats a
 * {@code RejectedExecutionException} beyond the ceiling as a non-reply, so the
 * executor's default abort policy degrades gracefully past fifty active agency
 * threads.</p>
 *
 * <p><strong>Relationship to {@code application.yml}.</strong> The
 * {@code spring.task.execution.pool.*} block in {@code application.yml} mirrors
 * the very same values configured here (core 5 / max 50 / queue 0 / thread
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
	 * Maximum thread count the pool may grow to once the core threads are busy.
	 * Because {@link #QUEUE_CAPACITY} is zero (a direct-handoff
	 * {@code SynchronousQueue}), every task that arrives while all core threads
	 * are busy triggers the pool to grow &mdash; up to this ceiling &mdash;
	 * instead of waiting in a queue. Sized at fifty so up to ten concurrent
	 * customer-creates (five agency tasks each) can all run their fan-out
	 * simultaneously without thread starvation (F2-01). Mirrors
	 * {@code spring.task.execution.pool.max-size} in {@code application.yml}.
	 */
	private static final int MAX_POOL_SIZE = 50;

	/**
	 * Work-queue capacity, fixed at zero so the backing
	 * {@code ThreadPoolExecutor} uses a direct-handoff
	 * {@link java.util.concurrent.SynchronousQueue}. With a zero-capacity queue a
	 * {@link ThreadPoolTaskExecutor} never parks a submitted task behind the core
	 * threads: if no core thread is idle it immediately starts a new thread (up to
	 * {@link #MAX_POOL_SIZE}). This is what lets concurrent customer-creates each
	 * obtain their five agency threads instead of the surplus tasks filling a
	 * bounded queue and missing the three-second deadline &mdash; the defect a
	 * non-zero queue caused (F2-01). Mirrors
	 * {@code spring.task.execution.pool.queue-capacity} in
	 * {@code application.yml}.
	 */
	private static final int QUEUE_CAPACITY = 0;

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
