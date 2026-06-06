/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.core.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.ibm.cics.cip.bank.core.constants.BankConstants;
import com.ibm.cics.cip.bank.core.entity.Account;
import com.ibm.cics.cip.bank.core.entity.AccountId;
import com.ibm.cics.cip.bank.core.exception.BusinessRuleException;
import com.ibm.cics.cip.bank.core.repository.AccountRepository;
import com.ibm.cics.cip.bank.core.util.BankFormat;

/**
 * Funds-transfer service, reproducing the COBOL program {@code XFRFUN} (the
 * largest program in the suite) exactly &mdash; its validation order, the
 * same-account abend, the lower-account-first lock ordering, the deadlock retry
 * and the single transfer PROCTRAN row keyed on the source account.
 *
 * <p><strong>Atomicity (F-TXN-1).</strong> A successful transfer debits the
 * source account, credits the target account and appends one {@code TFR} audit
 * row, all within a single transaction. If any step fails the whole unit of
 * work rolls back, so the two balance changes and the audit row can never
 * diverge &mdash; reproducing CICS SYNCPOINT/ROLLBACK semantics.</p>
 *
 * <p><strong>Validation order.</strong></p>
 * <ol>
 *   <li>A transfer amount of zero or less fails with code {@code 4}
 *       ({@code IF COMM-AMT &lt;= ZERO}).</li>
 *   <li>A transfer to the same account abends with code {@code SAME}.</li>
 *   <li>A missing source account fails with code {@code 1}; a missing target
 *       account fails with code {@code 2}.</li>
 * </ol>
 *
 * <p><strong>No funds check.</strong> Unlike a payment, a transfer performs no
 * sufficiency check; the source balance may legitimately go negative.</p>
 *
 * <p><strong>Lock ordering and deadlock retry.</strong> The two account rows
 * are locked {@code PESSIMISTIC_WRITE} in ascending account-number order (the
 * lower-numbered account first), which is how {@code XFRFUN} avoids deadlocks.
 * Should the database still report a deadlock, the operation is retried up to
 * {@value #MAX_DEADLOCK_RETRIES} times at {@value #RETRY_INTERVAL_MILLIS}-ms
 * intervals, each attempt in a fresh transaction.</p>
 *
 * <p><strong>Balances.</strong> The source account has both its available and
 * actual balances reduced by the amount; the target account has both increased
 * by the amount.</p>
 */
@Service
public class TransferService
{

	/** Fail code: transfer amount is zero or negative. */
	private static final String FAIL_INVALID_AMOUNT = "4";

	/** Fail code: source (FROM) account not found. */
	private static final String FAIL_SOURCE_NOT_FOUND = "1";

	/** Fail code: target (TO) account not found. */
	private static final String FAIL_TARGET_NOT_FOUND = "2";

	/** Fail code: persistent deadlock/lock failure after exhausting retries. */
	private static final String FAIL_LOCK_FAILURE = "3";

	/** Abend code: transfer source and target are the same account. */
	private static final String ABEND_SAME_ACCOUNT = "SAME";

	/** Monetary scale for balances and amounts. */
	private static final int MONEY_SCALE = 2;

	/** Maximum number of attempts when a deadlock is reported. */
	private static final int MAX_DEADLOCK_RETRIES = 6;

	/** Interval between deadlock retries, in milliseconds. */
	private static final long RETRY_INTERVAL_MILLIS = 1000L;

	private final AccountRepository accountRepository;

	private final ProcessedTransactionAppender proctranAppender;

	private final TransactionTemplate transactionTemplate;

	/**
	 * Constructs the transfer service with its collaborators.
	 *
	 * @param accountRepository  repository for {@link Account} locking and
	 *                           persistence
	 * @param proctranAppender   atomic PROCTRAN audit-row appender
	 * @param transactionManager the platform transaction manager, used to run
	 *                           each retry attempt in its own READ_COMMITTED
	 *                           transaction
	 */
	public TransferService(AccountRepository accountRepository,
			ProcessedTransactionAppender proctranAppender,
			PlatformTransactionManager transactionManager)
	{
		this.accountRepository = accountRepository;
		this.proctranAppender = proctranAppender;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
		this.transactionTemplate.setIsolationLevel(
				TransactionDefinition.ISOLATION_READ_COMMITTED);
	}

	/**
	 * Transfers funds between two accounts, reproducing {@code XFRFUN}.
	 *
	 * <p>Amount and same-account validation happen before any transaction is
	 * started. The balance mutation, audit append and lock acquisition then run
	 * inside a transaction that is retried on deadlock.</p>
	 *
	 * @param fromAccount the source (debited) account number
	 * @param toAccount   the target (credited) account number
	 * @param amount      the transfer amount (must be strictly positive)
	 * @return a {@link TransferResult} carrying the updated source and target
	 *         accounts with their new balances
	 * @throws BusinessRuleException {@code 4} if the amount is not positive,
	 *                               {@code SAME} if the two accounts are the
	 *                               same, {@code 1} if the source account is not
	 *                               found, {@code 2} if the target account is
	 *                               not found, or {@code 3} if the transfer
	 *                               cannot acquire its locks after the maximum
	 *                               number of retries
	 */
	public TransferResult transfer(long fromAccount, long toAccount,
			BigDecimal amount)
	{
		// 1. Amount must be strictly positive (XFRFUN fail '4').
		BigDecimal scaledAmount = amount.setScale(MONEY_SCALE,
				RoundingMode.HALF_UP);
		if (scaledAmount.signum() <= 0)
		{
			throw new BusinessRuleException(FAIL_INVALID_AMOUNT,
					"Transfer amount must be greater than zero");
		}

		// 2. Source and target must differ (XFRFUN abend 'SAME').
		if (fromAccount == toAccount)
		{
			throw new BusinessRuleException(ABEND_SAME_ACCOUNT,
					"Cannot transfer to the same account");
		}

		// 3. Execute under a deadlock-retry loop, each attempt in its own
		//    transaction (XFRFUN DB2-DEADLOCK-RETRY with a one-second DELAY).
		int attempt = 0;
		while (true)
		{
			attempt++;
			try
			{
				return transactionTemplate.execute(status -> doTransfer(
						fromAccount, toAccount, scaledAmount));
			}
			catch (PessimisticLockingFailureException lockFailure)
			{
				if (attempt >= MAX_DEADLOCK_RETRIES)
				{
					throw new BusinessRuleException(FAIL_LOCK_FAILURE,
							"Transfer failed after " + MAX_DEADLOCK_RETRIES
									+ " deadlock retries",
							lockFailure);
				}
				pauseBeforeRetry();
			}
		}
	}

	/**
	 * Performs a single transfer attempt within the caller's transaction.
	 *
	 * <p>The two accounts are locked in ascending account-number order to avoid
	 * deadlock, then the source is debited and the target credited on both
	 * balances, and finally a single {@code TFR} audit row keyed on the source
	 * account is appended.</p>
	 *
	 * @param fromAccount the source account number
	 * @param toAccount   the target account number
	 * @param amount      the (already scaled, positive) transfer amount
	 * @return the {@link TransferResult} for this attempt
	 */
	private TransferResult doTransfer(long fromAccount, long toAccount,
			BigDecimal amount)
	{
		// Acquire locks in ascending account-number order (XFRFUN locks the
		// lower-numbered account first to avoid deadlock).
		long lowerNumber = Math.min(fromAccount, toAccount);
		long higherNumber = Math.max(fromAccount, toAccount);
		Account lower = lockAccount(lowerNumber, lowerNumber == fromAccount);
		Account higher = lockAccount(higherNumber, higherNumber == fromAccount);

		// Resolve which locked row is the source and which is the target.
		Account source = (fromAccount == lowerNumber) ? lower : higher;
		Account target = (toAccount == lowerNumber) ? lower : higher;

		// Debit the source on both balances; no funds check (XFRFUN).
		source.setAvailableBalance(source.getAvailableBalance()
				.subtract(amount).setScale(MONEY_SCALE, RoundingMode.HALF_UP));
		source.setActualBalance(source.getActualBalance().subtract(amount)
				.setScale(MONEY_SCALE, RoundingMode.HALF_UP));

		// Credit the target on both balances.
		target.setAvailableBalance(target.getAvailableBalance().add(amount)
				.setScale(MONEY_SCALE, RoundingMode.HALF_UP));
		target.setActualBalance(target.getActualBalance().add(amount)
				.setScale(MONEY_SCALE, RoundingMode.HALF_UP));

		accountRepository.save(source);
		accountRepository.save(target);

		// Append the single transfer audit row, keyed on the source account
		// with the target encoded in the description (XFRFUN WRITE-TO-PROCTRAN).
		proctranAppender.appendTransfer(BankConstants.SORT_CODE, fromAccount,
				Long.parseLong(BankConstants.SORT_CODE), toAccount, amount);

		return new TransferResult(source, target);
	}

	/**
	 * Locks an account row for update, raising the role-appropriate fail code
	 * if it does not exist.
	 *
	 * @param accountNumber the account number to lock
	 * @param isSource      {@code true} if this is the source (FROM) account,
	 *                      {@code false} if it is the target (TO) account
	 * @return the locked {@link Account}
	 * @throws BusinessRuleException {@code 1} if a missing source account, or
	 *                               {@code 2} if a missing target account
	 */
	private Account lockAccount(long accountNumber, boolean isSource)
	{
		return accountRepository
				.findByIdForUpdate(new AccountId(BankConstants.SORT_CODE,
						BankFormat.accountNumber(accountNumber)))
				.orElseThrow(() -> new BusinessRuleException(
						isSource ? FAIL_SOURCE_NOT_FOUND : FAIL_TARGET_NOT_FOUND,
						(isSource ? "Source" : "Target")
								+ " account not found: " + accountNumber));
	}

	/**
	 * Sleeps for the deadlock-retry interval, restoring the interrupt flag and
	 * surfacing a lock-failure fail code if interrupted.
	 */
	private void pauseBeforeRetry()
	{
		try
		{
			Thread.sleep(RETRY_INTERVAL_MILLIS);
		}
		catch (InterruptedException interrupted)
		{
			Thread.currentThread().interrupt();
			throw new BusinessRuleException(FAIL_LOCK_FAILURE,
					"Transfer interrupted while waiting to retry after deadlock",
					interrupted);
		}
	}

	/**
	 * Immutable result of a successful transfer, exposing the updated source and
	 * target accounts so callers can read back both pairs of balances (the
	 * frozen contract returns the source and target available/actual balances).
	 */
	public static final class TransferResult
	{

		private final Account source;

		private final Account target;

		/**
		 * Constructs a transfer result.
		 *
		 * @param source the updated source (debited) account
		 * @param target the updated target (credited) account
		 */
		public TransferResult(Account source, Account target)
		{
			this.source = source;
			this.target = target;
		}

		/**
		 * Returns the updated source (debited) account.
		 *
		 * @return the source account
		 */
		public Account getSource()
		{
			return source;
		}

		/**
		 * Returns the updated target (credited) account.
		 *
		 * @return the target account
		 */
		public Account getTarget()
		{
			return target;
		}

	}

}
