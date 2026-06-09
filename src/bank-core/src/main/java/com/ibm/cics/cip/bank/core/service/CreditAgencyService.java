/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.core.service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.ibm.cics.cip.bank.core.config.AsyncConfig;

/**
 * Credit-agency scoring service &mdash; the Java rendering of the five identical
 * COBOL "dummy credit agency" programs {@code CRDTAGY1}&ndash;{@code CRDTAGY5}
 * (feature F-017).
 *
 * <p><strong>What the legacy programs do.</strong> Each {@code CRDTAGYn}
 * program is byte-identical apart from its program and container names. Driven
 * over the CICS Async API on channel {@code CIPCREDCHANN}, a single agency:
 * (1) computes a random delay of zero-to-three seconds
 * ({@code COMPUTE WS-DELAY-AMT = ((3 - 1) * FUNCTION RANDOM(WS-SEED)) + 1}) and
 * issues {@code EXEC CICS DELAY FOR SECONDS(WS-DELAY-AMT)} to emulate the
 * latency of a real bureau call, and (2) generates a random credit score in the
 * range 1&ndash;999
 * ({@code COMPUTE WS-NEW-CREDSCORE = ((999 - 1) * FUNCTION RANDOM) + 1}) and
 * returns it. The deliberate delay means an individual agency may or may not
 * reply inside the parent's fixed three-second window, emulating a bureau that
 * cannot always answer in time.</p>
 *
 * <p><strong>What survives the migration.</strong> Only the observable
 * behaviour is reproduced: the random delay and the random 1&ndash;999 score.
 * The CICS plumbing &mdash; the {@code CIPCREDCHANN} channel, the {@code CIPA}
 * container, the {@code EIBTASKN} random seed, and the abend handling &mdash; is
 * intentionally dropped because it has no place in the Spring Boot runtime.</p>
 *
 * <p><strong>Why this is the single-agency task only.</strong> The five legacy
 * programs collapse into this one service: every concurrent agency call is a
 * separate invocation of {@link #requestCreditScore()}. The orchestration that
 * surrounds those calls &mdash; fanning out five requests, enforcing the
 * three-second deadline, averaging the scores that replied in time, and raising
 * fail code {@code 'C'} when none reply &mdash; is owned by
 * {@code CustomerService} (the {@code CRECUST} analogue), <em>not</em> by this
 * bean. Keeping the aggregation out of here makes each agency a faithful,
 * single-responsibility analogue of one {@code CRDTAGYn} program and lets the
 * caller decide the fan-out and deadline policy.</p>
 *
 * <p><strong>Why {@link Async @Async} on the dedicated executor.</strong> The
 * method runs on the {@link AsyncConfig#CREDIT_AGENCY_EXECUTOR} pool, whose core
 * size is fixed at five (see {@link AsyncConfig}). Routing every agency call to
 * that pool guarantees that five concurrent invocations all start immediately
 * and run in parallel, so each gets its full chance to finish inside the
 * caller's three-second budget. Were these tasks to share a smaller or
 * contended pool they could be serialised, silently skewing the averaged result
 * and the "none replied" path. Spring's asynchronous-method support is enabled
 * once, on {@code BankCoreApplication} ({@code @EnableAsync}).</p>
 *
 * <p>This service holds no state and performs no database or transactional work;
 * the random score is an {@code int} (never a floating-point value), consistent
 * with the module-wide prohibition on floating-point types in any financial
 * path.</p>
 *
 * @see AsyncConfig
 */
@Service
public class CreditAgencyService
{

	/**
	 * Lower bound (inclusive) of the simulated agency processing delay, in
	 * milliseconds. Zero models an agency that replies effectively instantly.
	 */
	private static final int MIN_DELAY_MILLIS = 0;

	/**
	 * Upper bound (inclusive) of the simulated agency processing delay, in
	 * milliseconds &mdash; three seconds, the top of {@code CRDTAGY1}'s
	 * zero-to-three-second random delay. A delay at this bound coincides with
	 * the caller's three-second deadline, reproducing the legacy behaviour where
	 * the slowest agencies just miss the window.
	 */
	private static final int MAX_DELAY_MILLIS = 3000;

	/**
	 * Lowest credit score an agency may return, matching the COBOL
	 * 1&ndash;999 range.
	 */
	private static final int MIN_CREDIT_SCORE = 1;

	/**
	 * Highest credit score an agency may return, matching the COBOL
	 * 1&ndash;999 range.
	 */
	private static final int MAX_CREDIT_SCORE = 999;

	/**
	 * Performs a single asynchronous credit-agency check, reproducing one
	 * {@code CRDTAGYn} program (feature F-017).
	 *
	 * <p>The task sleeps for a random interval of {@value #MIN_DELAY_MILLIS} to
	 * {@value #MAX_DELAY_MILLIS}&nbsp;ms (the {@code EXEC CICS DELAY} that
	 * emulates bureau latency) and then returns a random credit score between
	 * {@value #MIN_CREDIT_SCORE} and {@value #MAX_CREDIT_SCORE} inclusive
	 * (matching {@code COMPUTE WS-NEW-CREDSCORE = ((999 - 1) * FUNCTION RANDOM)
	 * + 1}). {@link ThreadLocalRandom} is used so the five concurrent agency
	 * threads never contend on a shared generator, replacing the COBOL
	 * {@code FUNCTION RANDOM} seeded from {@code EIBTASKN}.</p>
	 *
	 * <p>Because the method is annotated
	 * {@link Async @Async(AsyncConfig.CREDIT_AGENCY_EXECUTOR)}, Spring executes
	 * the entire body &mdash; including the {@link Thread#sleep(long) sleep}
	 * &mdash; on a {@link AsyncConfig#CREDIT_AGENCY_EXECUTOR} thread and adapts
	 * the returned {@link CompletableFuture} into the asynchronous result the
	 * caller observes. The caller ({@code CustomerService}) fans out five of
	 * these calls and enforces the overall three-second deadline; this method
	 * therefore never blocks longer than its own random delay.</p>
	 *
	 * <p><strong>Interruption.</strong> If the worker thread is interrupted
	 * while sleeping (for example during an executor shutdown), the interrupt
	 * status is restored and the call completes <em>exceptionally</em> rather
	 * than returning a fabricated score. An exceptionally-completed future is
	 * naturally excluded by the caller's "count only the agencies that completed
	 * normally" aggregation, so an interrupted agency simply does not contribute
	 * to the average &mdash; it never silently injects a bogus value.</p>
	 *
	 * @return a completed {@link CompletableFuture} carrying a random credit
	 *         score in the range
	 *         {@value #MIN_CREDIT_SCORE}&ndash;{@value #MAX_CREDIT_SCORE}; or an
	 *         exceptionally-completed future if the task was interrupted while
	 *         simulating its delay
	 */
	@Async(AsyncConfig.CREDIT_AGENCY_EXECUTOR)
	public CompletableFuture<Integer> requestCreditScore()
	{
		// 1. Emulate bureau latency: sleep a random 0-3 second interval.
		//    (COBOL: EXEC CICS DELAY FOR SECONDS(WS-DELAY-AMT), 0-3s.)
		int delayMillis = ThreadLocalRandom.current()
				.nextInt(MIN_DELAY_MILLIS, MAX_DELAY_MILLIS + 1);
		try
		{
			Thread.sleep(delayMillis);
		}
		catch (InterruptedException interruptedException)
		{
			// Restore the interrupt status so the pool/JVM can observe it, then
			// surface the interruption as an exceptional result the aggregator
			// ignores. Never swallow the interrupt.
			Thread.currentThread().interrupt();
			return CompletableFuture.failedFuture(interruptedException);
		}

		// 2. Generate the random credit score in 1-999 inclusive.
		//    (COBOL: COMPUTE WS-NEW-CREDSCORE = ((999 - 1) * FUNCTION RANDOM) + 1.)
		int creditScore = ThreadLocalRandom.current()
				.nextInt(MIN_CREDIT_SCORE, MAX_CREDIT_SCORE + 1);

		// 3. Hand the score back as the already-completed async result; the
		//    @Async proxy delivers it to the caller on the executor thread.
		return CompletableFuture.completedFuture(creditScore);
	}

}
