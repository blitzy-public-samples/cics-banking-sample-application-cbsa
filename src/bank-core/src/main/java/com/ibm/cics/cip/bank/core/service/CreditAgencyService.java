/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.core.service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.ibm.cics.cip.bank.core.config.AsyncConfig;
import com.ibm.cics.cip.bank.core.exception.BusinessRuleException;

/**
 * Reimplements the legacy asynchronous credit-agency check that the COBOL
 * customer-create flow ({@code CRECUST}) performs against five identical dummy
 * agency programs ({@code CRDTAGY1}&ndash;{@code CRDTAGY5}) over the CICS Async
 * API on channel {@code CIPCREDCHANN} (feature F-017).
 *
 * <p><strong>Legacy behaviour reproduced.</strong> {@code CRECUST} fans out
 * five asynchronous requests and then issues {@code EXEC CICS DELAY FOR
 * SECONDS(3)} before fetching whatever replies have arrived. Each agency
 * ({@code CRDTAGY1.cbl}) first computes a random delay of zero-to-three seconds
 * ({@code COMPUTE WS-DELAY-AMT = ((3 - 1) * FUNCTION RANDOM(...)) + 1}) and then
 * returns a credit score in the range 1&ndash;999. After the three-second
 * window the parent averages the scores of the agencies that replied
 * ({@code COMPUTE WS-ACTUAL-CS-SCR = WS-TOTAL-CS-SCR / WS-RETRIEVED-CNT}, an
 * integer division that truncates) and uses that as the customer credit score.
 * If <em>no</em> agency replied within the window the parent sets fail code
 * {@code 'C'} and abandons the create (CRECUST {@code NOTFINISHED} /
 * zero-retrieved path).</p>
 *
 * <p><strong>Java mapping.</strong> The five agencies become five
 * {@link CompletableFuture} tasks submitted to the dedicated
 * {@link AsyncConfig#CREDIT_AGENCY_EXECUTOR} pool, each sleeping a random
 * 0&ndash;3000&nbsp;ms and returning a 1&ndash;999 score. The parent waits up
 * to {@value #DEADLINE_SECONDS} seconds for them all; whichever have completed
 * by the deadline are averaged with truncating integer division, exactly
 * matching the COBOL. When none have completed a
 * {@link BusinessRuleException} carrying fail code {@value #FAIL_CODE_NO_AGENCY}
 * is thrown so the caller can reproduce the COBOL {@code 'C'} outcome and roll
 * the create back.</p>
 *
 * <p>This service holds no persistent state and performs no database work; it
 * is therefore deliberately free of any transactional annotation. The review
 * date that {@code CRECUST} derives on success (today plus a random 1&ndash;21
 * days) is intentionally <em>not</em> computed here &mdash; it is the
 * orchestrating {@code CustomerService}'s concern, keeping this bean a faithful,
 * single-responsibility analogue of the agency fan-out alone.</p>
 *
 * @see AsyncConfig
 */
@Service
public class CreditAgencyService
{

	/** Logger for diagnostic and deadline-miss reporting. */
	private static final Logger LOG = LoggerFactory
			.getLogger(CreditAgencyService.class);

	/**
	 * Number of credit agencies queried in parallel, reproducing the five
	 * identical COBOL programs {@code CRDTAGY1}&ndash;{@code CRDTAGY5}.
	 */
	private static final int NUMBER_OF_AGENCIES = 5;

	/**
	 * Lowest credit score an agency may return, matching the COBOL agencies'
	 * 1&ndash;999 range.
	 */
	private static final int MIN_SCORE = 1;

	/**
	 * Highest credit score an agency may return, matching the COBOL agencies'
	 * 1&ndash;999 range.
	 */
	private static final int MAX_SCORE = 999;

	/**
	 * Upper bound (exclusive of the extra millisecond) of an individual
	 * agency's simulated processing delay, in milliseconds &mdash; the Java
	 * analogue of {@code CRDTAGY1}'s zero-to-three-second random delay.
	 */
	private static final int MAX_DELAY_MILLIS = 3000;

	/**
	 * Fixed deadline, in seconds, the parent waits for replies before averaging
	 * whatever has arrived &mdash; the {@code EXEC CICS DELAY FOR SECONDS(3)} in
	 * {@code CRECUST}.
	 */
	private static final int DEADLINE_SECONDS = 3;

	/**
	 * COBOL fail code raised when not a single agency replies inside the
	 * deadline (the {@code CRECUST} {@code 'C'} path).
	 */
	private static final String FAIL_CODE_NO_AGENCY = "C";

	/** Dedicated executor that runs the five agency tasks concurrently. */
	private final Executor creditAgencyExecutor;

	/**
	 * Constructs the service with the dedicated credit-agency fan-out executor.
	 *
	 * @param creditAgencyExecutor the {@link AsyncConfig#CREDIT_AGENCY_EXECUTOR}
	 *                             thread pool sized so all five agency tasks
	 *                             start immediately
	 */
	public CreditAgencyService(
			@Qualifier(AsyncConfig.CREDIT_AGENCY_EXECUTOR) Executor creditAgencyExecutor)
	{
		this.creditAgencyExecutor = creditAgencyExecutor;
	}

	/**
	 * Performs the asynchronous five-agency credit check and returns the
	 * averaged credit score, reproducing the {@code CRECUST} credit-check
	 * section.
	 *
	 * <p>Five tasks are submitted concurrently; the method then waits up to
	 * {@value #DEADLINE_SECONDS} seconds for them to finish. The scores of the
	 * agencies that completed within the deadline are averaged using truncating
	 * integer division (matching the COBOL {@code COMPUTE ... / WS-RETRIEVED-CNT}).
	 * If none completed in time, a {@link BusinessRuleException} carrying fail
	 * code {@value #FAIL_CODE_NO_AGENCY} is thrown.</p>
	 *
	 * @return the averaged credit score (1&ndash;999) of the agencies that
	 *         replied within the deadline
	 * @throws BusinessRuleException with fail code {@value #FAIL_CODE_NO_AGENCY}
	 *                               if no agency replied within the deadline
	 */
	public int requestCreditScore()
	{
		List<CompletableFuture<Integer>> futures = new ArrayList<>(
				NUMBER_OF_AGENCIES);
		for (int agency = 0; agency < NUMBER_OF_AGENCIES; agency++)
		{
			futures.add(CompletableFuture.supplyAsync(this::queryAgency,
					creditAgencyExecutor));
		}

		// Wait for the fixed deadline, mirroring EXEC CICS DELAY FOR SECONDS(3).
		// A timeout is expected and benign: it simply means one or more agencies
		// did not reply in time, so we proceed to average whatever did arrive.
		CompletableFuture<Void> all = CompletableFuture
				.allOf(futures.toArray(new CompletableFuture[0]));
		try
		{
			all.get(DEADLINE_SECONDS, TimeUnit.SECONDS);
		}
		catch (TimeoutException timeout)
		{
			LOG.debug("Credit-agency deadline of {}s reached before all "
					+ "agencies replied; averaging those that did",
					DEADLINE_SECONDS);
		}
		catch (InterruptedException interrupted)
		{
			Thread.currentThread().interrupt();
			throw new BusinessRuleException(FAIL_CODE_NO_AGENCY,
					"Interrupted while awaiting credit-agency replies");
		}
		catch (java.util.concurrent.ExecutionException execution)
		{
			// An individual task failing is tolerated here; the per-future
			// inspection below counts only the agencies that completed normally.
			LOG.debug("A credit-agency task completed exceptionally: {}",
					execution.getMessage());
		}

		long total = 0L;
		int retrieved = 0;
		for (CompletableFuture<Integer> future : futures)
		{
			if (future.isDone() && !future.isCompletedExceptionally())
			{
				Integer score = future.getNow(null);
				if (score != null)
				{
					total += score;
					retrieved++;
				}
			}
		}

		if (retrieved == 0)
		{
			LOG.warn("No credit agency replied within {}s; failing with "
					+ "code '{}'", DEADLINE_SECONDS, FAIL_CODE_NO_AGENCY);
			throw new BusinessRuleException(FAIL_CODE_NO_AGENCY,
					"No credit agency responded within the deadline");
		}

		// Truncating integer division, exactly as the COBOL COMPUTE does.
		return (int) (total / retrieved);
	}

	/**
	 * Simulates a single credit agency ({@code CRDTAGY1}&ndash;{@code CRDTAGY5}):
	 * sleeps for a random zero-to-three-second interval and then returns a
	 * random credit score between {@value #MIN_SCORE} and {@value #MAX_SCORE}.
	 *
	 * @return a random credit score in the range
	 *         {@value #MIN_SCORE}&ndash;{@value #MAX_SCORE}
	 */
	private int queryAgency()
	{
		int delayMillis = ThreadLocalRandom.current()
				.nextInt(MAX_DELAY_MILLIS + 1);
		try
		{
			Thread.sleep(delayMillis);
		}
		catch (InterruptedException interrupted)
		{
			Thread.currentThread().interrupt();
			throw new IllegalStateException(
					"Credit-agency task interrupted", interrupted);
		}
		return ThreadLocalRandom.current().nextInt(MIN_SCORE, MAX_SCORE + 1);
	}

}
