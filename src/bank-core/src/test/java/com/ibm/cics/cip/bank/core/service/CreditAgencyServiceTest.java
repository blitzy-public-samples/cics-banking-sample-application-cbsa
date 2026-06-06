/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.core.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

/**
 * Parity unit test for {@link CreditAgencyService} &mdash; the Java rendering of
 * the five identical COBOL "dummy credit agency" programs
 * {@code CRDTAGY1}&ndash;{@code CRDTAGY5} (feature F-017).
 *
 * <p><strong>What the COBOL specifies.</strong> Each {@code CRDTAGYn} program is
 * byte-identical apart from its program and container names
 * ({@code CIPA}&ndash;{@code CIPE}); the behaviour is the migration contract.
 * Two {@code COMPUTE} statements define everything observable:</p>
 * <ul>
 *   <li>{@code COMPUTE WS-DELAY-AMT = ((3 - 1) * FUNCTION RANDOM(WS-SEED)) + 1}
 *       &mdash; a random processing delay of zero-to-three seconds, issued as
 *       {@code EXEC CICS DELAY}, emulating bureau latency; and</li>
 *   <li>{@code COMPUTE WS-NEW-CREDSCORE = ((999 - 1) * FUNCTION RANDOM) + 1}
 *       &mdash; a random credit score in the <em>inclusive</em> range
 *       1&ndash;999 (never 0, never 1000).</li>
 * </ul>
 *
 * <p><strong>Parity invariants asserted here.</strong> Because both the delay
 * and the score are random, this suite pins only the invariants the COBOL
 * guarantees &mdash; never a specific score value and never an exact timing:</p>
 * <ol>
 *   <li>{@link CreditAgencyService#requestCreditScore()} returns a non-null,
 *       already-completed {@link CompletableFuture} whose value is non-null;</li>
 *   <li>every resolved score lies within the inclusive boundary
 *       {@code [1, 999]} (the core F-017 boundary), proven across many random
 *       samples so a single lucky pass cannot mask an off-by-one (0 or 1000)
 *       defect; and</li>
 *   <li>each call completes within the bounded zero-to-three-second delay
 *       window (asserted with a forgiving upper bound to stay green on slow
 *       CI).</li>
 * </ol>
 *
 * <p><strong>No collaborators, no Spring context.</strong>
 * {@code CreditAgencyService} models a <em>single</em> agency: it has no
 * repository and no sibling service, so the bean is instantiated directly with
 * {@code new CreditAgencyService()} &mdash; no Mockito {@code @Mock}/
 * {@code @InjectMocks} and no {@code ApplicationContext}. The wider
 * orchestration (fanning out five agencies, averaging the scores that reply
 * inside a three-second deadline, and raising fail code {@code 'C'} when none
 * reply) lives in {@code CustomerService} and is asserted by
 * {@code CustomerServiceTest}, <em>not</em> here.</p>
 *
 * <p><strong>Why {@code @Async} is inert here.</strong> No Spring proxy wraps
 * the directly-constructed bean, so {@link CreditAgencyService#requestCreditScore()}
 * runs <em>synchronously</em> on the calling thread &mdash; it performs its
 * random {@link Thread#sleep(long) sleep} (0&ndash;3000&nbsp;ms) inline and then
 * returns an already-completed future. Consequently a naive loop that builds a
 * list of <em>N</em> sequential calls would block for up to {@code N * 3000} ms.
 * To keep the suite fast and reliably green, the two multi-call tests
 * <em>launch</em> each invocation on a small test-owned
 * {@link ExecutorService} (via {@link CompletableFuture#supplyAsync}) so the
 * calls run concurrently &mdash; bounded by the aggregate
 * {@code allOf(...).get(...)} timeout &mdash; rather than serially. In a real
 * Spring context these would instead run on the {@code creditAgencyExecutor}
 * (core pool size 5) defined in {@code AsyncConfig}; the score-range invariant
 * is identical either way.</p>
 *
 * <p>The credit score is an {@code int} throughout &mdash; never a
 * floating-point value &mdash; consistent with the module-wide prohibition on
 * {@code double}/{@code float}.</p>
 *
 * @see CreditAgencyService
 */
class CreditAgencyServiceTest
{

	/**
	 * Lowest credit score the agency may return, matching the COBOL
	 * {@code COMPUTE WS-NEW-CREDSCORE = ((999 - 1) * FUNCTION RANDOM) + 1}
	 * lower bound. The score is never 0.
	 */
	private static final int MIN_CREDIT_SCORE = 1;

	/**
	 * Highest credit score the agency may return, matching the COBOL
	 * 1&ndash;999 range. The score is never 1000.
	 */
	private static final int MAX_CREDIT_SCORE = 999;

	/**
	 * Per-call resolution timeout, in seconds, applied to every single-call
	 * {@code future.get(...)}. Generously larger than the maximum three-second
	 * simulated delay so the assertion can never hang yet always absorbs the
	 * random latency.
	 */
	private static final long SINGLE_GET_TIMEOUT_SECONDS = 5L;

	/**
	 * Aggregate resolution timeout, in seconds, applied to the combined
	 * {@code CompletableFuture.allOf(...).get(...)} in the multi-call tests.
	 * Because those calls are launched concurrently on a dedicated test pool,
	 * the whole batch resolves within roughly the single three-second delay, so
	 * this bound keeps the suite green while still guaranteeing it cannot hang.
	 */
	private static final long AGGREGATE_GET_TIMEOUT_SECONDS = 10L;

	/**
	 * Forgiving upper bound, in milliseconds, for a single call's elapsed time:
	 * the three-second maximum delay plus a generous one-second slack for
	 * scheduling jitter on slow CI. The intent is to document the bounded-delay
	 * contract, not to micro-benchmark; no tight lower bound is asserted because
	 * the random delay may be effectively zero.
	 */
	private static final long MAX_DELAY_WITH_SLACK_MILLIS = 4_000L;

	/**
	 * Number of repeated invocations used to exercise the random score path.
	 * Kept modest (&le;30) so that, even launched concurrently, the test stays
	 * fast; large enough that repeated sampling routinely re-exercises the
	 * 1&ndash;999 boundary.
	 */
	private static final int REPEATED_CALL_COUNT = 24;

	/**
	 * Size of the concurrent fan-out, mirroring the five legacy agencies
	 * ({@code CRDTAGY1}&ndash;{@code CRDTAGY5}) and the
	 * {@code creditAgencyExecutor} core pool size of five.
	 */
	private static final int AGENCY_FAN_OUT = 5;

	/**
	 * Service under test. {@code CreditAgencyService} is stateless and
	 * dependency-free, so a single directly-constructed instance is shared by
	 * every test method &mdash; no Spring context, no mocks, and no setup
	 * fixture is required.
	 */
	private final CreditAgencyService service = new CreditAgencyService();

	/**
	 * Completion parity (F-017): a call returns a non-null future that, with
	 * {@code @Async} inert in this plain unit test, is already complete and
	 * carries a non-null score. Resolved with a generous timeout to absorb the
	 * random zero-to-three-second delay without ever hanging.
	 *
	 * @throws Exception if the future fails to resolve within the timeout
	 */
	@Test
	void requestCreditScore_returnsNonNullCompletedFuture() throws Exception
	{
		CompletableFuture<Integer> future = service.requestCreditScore();

		assertThat(future).isNotNull();
		// No Spring proxy wraps the bean, so the body ran synchronously on this
		// thread and the returned future is already complete.
		assertThat(future).isDone();

		Integer score = future.get(SINGLE_GET_TIMEOUT_SECONDS, TimeUnit.SECONDS);
		assertThat(score).isNotNull();
	}

	/**
	 * Core score-range parity (F-017): the resolved credit score lies within the
	 * inclusive boundary {@code [1, 999]} &mdash; never 0 and never 1000 &mdash;
	 * matching {@code COMPUTE WS-NEW-CREDSCORE = ((999 - 1) * FUNCTION RANDOM) + 1}.
	 *
	 * @throws Exception if the future fails to resolve within the timeout
	 */
	@Test
	void requestCreditScore_scoreWithinInclusiveRange1To999() throws Exception
	{
		Integer score = service.requestCreditScore()
				.get(SINGLE_GET_TIMEOUT_SECONDS, TimeUnit.SECONDS);

		assertThat(score).isBetween(MIN_CREDIT_SCORE, MAX_CREDIT_SCORE);
	}

	/**
	 * Randomness parity (F-017): across many repeated invocations every resolved
	 * score stays within the inclusive {@code [1, 999]} boundary, so a single
	 * lucky pass cannot hide an off-by-one defect at 0 or 1000.
	 *
	 * <p>In a real Spring context these calls would run on the
	 * {@code creditAgencyExecutor} (core pool size 5) concurrently; here they
	 * are launched on a test-owned pool so the batch completes in roughly one
	 * delay window instead of {@code REPEATED_CALL_COUNT} sequential delays. The
	 * range invariant is identical either way.</p>
	 *
	 * @throws Exception if the batch fails to resolve within the aggregate
	 *                   timeout
	 */
	@Test
	void requestCreditScore_repeatedCalls_allScoresInRange() throws Exception
	{
		List<Integer> scores = runConcurrentScoreRequests(REPEATED_CALL_COUNT);

		assertThat(scores).hasSize(REPEATED_CALL_COUNT);
		for (Integer score : scores)
		{
			assertThat(score).isBetween(MIN_CREDIT_SCORE, MAX_CREDIT_SCORE);
		}
	}

	/**
	 * Bounded-delay parity (F-017): a single call completes within the
	 * zero-to-three-second delay window. The elapsed time is asserted against a
	 * forgiving upper bound ({@value #MAX_DELAY_WITH_SLACK_MILLIS}&nbsp;ms = 3s
	 * max delay + 1s scheduling slack) to document the bounded-delay contract
	 * without flaking on slow CI. No tight lower bound is asserted, because the
	 * random delay may be effectively zero.
	 *
	 * @throws Exception if the future fails to resolve within the timeout
	 */
	@Test
	void requestCreditScore_completesWithinDeadlineWindow() throws Exception
	{
		long start = System.nanoTime();
		Integer score = service.requestCreditScore()
				.get(SINGLE_GET_TIMEOUT_SECONDS, TimeUnit.SECONDS);
		long elapsedMillis = (System.nanoTime() - start) / 1_000_000L;

		// Parity guard: the call still produced a valid in-range score...
		assertThat(score).isBetween(MIN_CREDIT_SCORE, MAX_CREDIT_SCORE);
		// ...and it returned within the bounded 0-3s delay (+ generous slack).
		assertThat(elapsedMillis).isLessThanOrEqualTo(MAX_DELAY_WITH_SLACK_MILLIS);
	}

	/**
	 * Fan-out parity (F-017): five concurrent agency calls &mdash; mirroring the
	 * five legacy {@code CRDTAGYn} programs and the {@code creditAgencyExecutor}
	 * core pool size of five &mdash; all complete with an in-range score.
	 *
	 * <p>This asserts only the per-agency invariants. The orchestration that
	 * surrounds the fan-out (averaging the scores that reply within the
	 * three-second deadline via {@code CompletableFuture.allOf(...).orTimeout(3,
	 * SECONDS)} and falling back to fail code {@code 'C'} when none reply) lives
	 * in {@code CustomerService} and is asserted by {@code CustomerServiceTest},
	 * not here.</p>
	 *
	 * @throws Exception if the batch fails to resolve within the aggregate
	 *                   timeout
	 */
	@Test
	void fiveConcurrentRequests_allCompleteInRange() throws Exception
	{
		List<Integer> scores = runConcurrentScoreRequests(AGENCY_FAN_OUT);

		assertThat(scores).hasSize(AGENCY_FAN_OUT);
		for (Integer score : scores)
		{
			assertThat(score).isBetween(MIN_CREDIT_SCORE, MAX_CREDIT_SCORE);
		}
	}

	/**
	 * Launches {@code count} {@link CreditAgencyService#requestCreditScore()}
	 * calls concurrently on a dedicated, right-sized test executor and returns
	 * their resolved scores once all have completed.
	 *
	 * <p>Each invocation is submitted with {@link CompletableFuture#supplyAsync}
	 * so that the production method's synchronous {@link Thread#sleep(long)}
	 * (the inert-{@code @Async} behaviour described on the class) runs on a pool
	 * thread rather than blocking the test thread. The pool is sized to
	 * {@code count} so all calls start immediately and the whole batch resolves
	 * within roughly a single three-second delay window. The inner already-
	 * completed future is unwrapped with {@link CompletableFuture#join()}, which
	 * surfaces any exceptional completion (for example a simulated interruption)
	 * as an {@code allOf(...).get(...)} failure rather than silently passing.</p>
	 *
	 * <p>The combined wait uses the bounded {@link #AGGREGATE_GET_TIMEOUT_SECONDS}
	 * so the suite can never hang, and the executor is always shut down in a
	 * {@code finally} block so no test threads leak between cases.</p>
	 *
	 * @param count the number of concurrent score requests to launch; also the
	 *              size of the temporary thread pool
	 * @return the list of resolved credit scores, one per launched request, in
	 *         submission order
	 * @throws Exception if the aggregate future fails to resolve within
	 *                   {@link #AGGREGATE_GET_TIMEOUT_SECONDS}
	 */
	private List<Integer> runConcurrentScoreRequests(int count) throws Exception
	{
		ExecutorService pool = Executors.newFixedThreadPool(count);
		try
		{
			List<CompletableFuture<Integer>> futures = new ArrayList<>(count);
			for (int i = 0; i < count; i++)
			{
				// supplyAsync launches the (synchronous, blocking) production
				// call on a pool thread; join() unwraps the already-completed
				// inner future without checked-exception plumbing.
				futures.add(CompletableFuture
						.supplyAsync(() -> service.requestCreditScore().join(), pool));
			}

			// Bounded wait: guarantees the batch resolves (or fails) promptly and
			// the suite cannot hang despite the random per-call delay.
			CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
					.get(AGGREGATE_GET_TIMEOUT_SECONDS, TimeUnit.SECONDS);

			List<Integer> scores = new ArrayList<>(count);
			for (CompletableFuture<Integer> future : futures)
			{
				assertThat(future).isDone();
				scores.add(future.join());
			}
			return scores;
		}
		finally
		{
			// Reclaim the temporary pool threads regardless of assertion outcome.
			pool.shutdownNow();
		}
	}

}
