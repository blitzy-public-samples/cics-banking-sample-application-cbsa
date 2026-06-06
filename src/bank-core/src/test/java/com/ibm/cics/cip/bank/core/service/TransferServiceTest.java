/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.core.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.transaction.PlatformTransactionManager;

import com.ibm.cics.cip.bank.core.constants.BankConstants;
import com.ibm.cics.cip.bank.core.domain.TransactionType;
import com.ibm.cics.cip.bank.core.entity.Account;
import com.ibm.cics.cip.bank.core.entity.AccountId;
import com.ibm.cics.cip.bank.core.exception.BusinessRuleException;
import com.ibm.cics.cip.bank.core.repository.AccountRepository;

/**
 * Parity unit test for {@link TransferService} &mdash; the Java rendering of the
 * COBOL program {@code XFRFUN} (the largest legacy program, feature
 * <strong>F-016</strong>), an account-to-account funds transfer with
 * deterministic lock ordering and deadlock retry. The COBOL is the authoritative
 * behavioural specification, so this suite pins behavioural <em>parity</em>
 * &mdash; exact fail codes, the lower-account-first lock order, and the movement
 * of <em>both</em> balances on <em>both</em> accounts &mdash; never "improved"
 * behaviour.
 *
 * <h2>What the COBOL specifies (the migration contract)</h2>
 * <ul>
 *   <li><strong>Amount guard.</strong> {@code IF COMM-AMT &lt;= ZERO} sets fail
 *       code {@code '4'} <em>before</em> any {@code ACCOUNT} row is read
 *       ({@code XFRFUN.cbl} L289-291), so both a zero and a negative amount are
 *       rejected with {@code '4'}.</li>
 *   <li><strong>Same-account abend.</strong> A transfer naming the same account
 *       twice abends with {@code ABCODE('SAME')} ({@code XFRFUN.cbl} L371).</li>
 *   <li><strong>Not-found codes.</strong> A missing source (FROM) account fails
 *       with {@code '1'}; a missing target (TO) account fails with
 *       {@code '2'}.</li>
 *   <li><strong>Dual-balance movement.</strong> The FROM account is debited on
 *       both balances ({@code AVAIL-BAL - COMM-AMT}, {@code ACTUAL-BAL -
 *       COMM-AMT}; L986-990) and the TO account is credited on both balances
 *       ({@code + COMM-AMT}; L1356-1360). The two balance columns are
 *       independent and are never collapsed.</li>
 *   <li><strong>Single TFR audit row.</strong> A successful transfer appends one
 *       {@code PROC-TY-TRANSFER} ({@code 'TFR'}) PROCTRAN row carrying the amount
 *       ({@code XFRFUN.cbl} L1607).</li>
 *   <li><strong>Lower-account-first locking + deadlock retry.</strong>
 *       {@code XFRFUN} locks the lower-numbered account first to avoid deadlock
 *       and retries on a reported deadlock ({@code DB2-DEADLOCK-RETRY}).</li>
 * </ul>
 *
 * <h2>Aligned with the finalized production code (not the suggested shape)</h2>
 * <p>The assertions below were reconciled against the finalized
 * {@link TransferService}. In particular the service acquires its pessimistic
 * locks through {@link AccountRepository#findByIdForUpdate(AccountId)} (the
 * {@code @Lock(PESSIMISTIC_WRITE)} finder), it appends the audit row through the
 * collaborating {@link ProcessedTransactionAppender#appendTransfer} (rather than
 * persisting a {@code ProcessedTransaction} directly), it runs each attempt
 * inside a {@code TransactionTemplate} built from an injected
 * {@link PlatformTransactionManager}, and the deadlock retry is a
 * <strong>manual loop</strong> inside {@code transfer(...)} (up to six attempts).
 * The three collaborators are therefore the {@link AccountRepository}, the
 * {@link ProcessedTransactionAppender} and the {@link PlatformTransactionManager}
 * &mdash; all supplied here as Mockito doubles and wired by {@code @InjectMocks}.
 * A plain mock {@code PlatformTransactionManager} causes
 * {@code TransactionTemplate.execute(...)} to run the callback synchronously and
 * propagate exceptions, so the real transfer logic executes under this pure
 * unit test.</p>
 *
 * <p>Because the audit append is delegated to the (mocked)
 * {@link ProcessedTransactionAppender}, the TFR type is asserted by verifying the
 * dedicated {@code appendTransfer(...)} call and cross-checking that the
 * appender's transfer-type constant equals {@link TransactionType#TFR}'s code;
 * capturing a fully-built {@code ProcessedTransaction} entity is the appender's
 * own unit-test concern.</p>
 *
 * <h2>Why this is a pure Mockito unit test (no Spring, no DB)</h2>
 * <p>No {@code @SpringBootTest}, {@code @DataJpaTest}, {@code MockMvc} or
 * {@code ApplicationContext} is bootstrapped; the collaborators are
 * {@code @Mock} doubles wired by {@code @InjectMocks}. All money is asserted with
 * {@link BigDecimal} {@code compareTo} semantics (never {@code equals}) at
 * scale&nbsp;2; no {@code double}/{@code float} appears anywhere.</p>
 */
@ExtendWith(MockitoExtension.class)
class TransferServiceTest
{

	/**
	 * The single institution sort code that keys every account. Sourced from the
	 * production {@link BankConstants#SORT_CODE} constant (rather than a
	 * re-hard-coded literal) so the parity test drifts with production if the
	 * sort code ever changes.
	 */
	private static final String SORT_CODE = BankConstants.SORT_CODE;

	/** COBOL fail code: transfer amount is zero or negative ({@code XFRFUN '4'}). */
	private static final String FAIL_INVALID_AMOUNT = "4";

	/** COBOL fail code: source (FROM) account not found ({@code XFRFUN '1'}). */
	private static final String FAIL_SOURCE_NOT_FOUND = "1";

	/** COBOL fail code: target (TO) account not found ({@code XFRFUN '2'}). */
	private static final String FAIL_TARGET_NOT_FOUND = "2";

	/** COBOL fail code: persistent lock failure after the retry budget ({@code '3'}). */
	private static final String FAIL_LOCK_FAILURE = "3";

	/** CICS abend marker: source and target are the same account ({@code ABCODE('SAME')}). */
	private static final String ABEND_SAME_ACCOUNT = "SAME";

	/** Mocked account repository (locked reads + persistence) injected into the service. */
	@Mock
	private AccountRepository accountRepository;

	/** Mocked PROCTRAN audit-row appender injected into the service. */
	@Mock
	private ProcessedTransactionAppender proctranAppender;

	/**
	 * Mocked platform transaction manager. The service wraps it in a
	 * {@code TransactionTemplate}; a plain mock makes {@code execute(...)} run the
	 * callback synchronously and propagate exceptions, so the transfer logic runs
	 * under this unit test without a real transaction.
	 */
	@Mock
	private PlatformTransactionManager transactionManager;

	/**
	 * System under test &mdash; constructed by Mockito via constructor injection
	 * with the mocked repository, appender and transaction manager
	 * ({@code new TransferService(accountRepository, proctranAppender,
	 * transactionManager)}).
	 */
	@InjectMocks
	private TransferService transferService;

	/** Shared FROM fixture: account {@code 00000001}, both balances {@code 500.00}. */
	private Account standardFrom;

	/** Shared TO fixture: account {@code 00000002}, both balances {@code 100.00}. */
	private Account standardTo;

	/**
	 * Builds the two most frequently reused account fixtures before each test.
	 * Individual cases that need different balances or account numbers build
	 * their own via {@link #account(String, String, String)}.
	 */
	@BeforeEach
	void setUp()
	{
		standardFrom = account("00000001", "500.00", "500.00");
		standardTo = account("00000002", "100.00", "100.00");
	}

	/**
	 * Builds an {@link Account} with the given eight-digit account number and the
	 * two independent balances, keyed under {@link #SORT_CODE}.
	 *
	 * @param accountNumber    the eight-digit, zero-padded account number
	 * @param availableBalance the available balance (scale-2 decimal string)
	 * @param actualBalance    the actual balance (scale-2 decimal string)
	 * @return the populated account
	 */
	private static Account account(String accountNumber,
			String availableBalance, String actualBalance)
	{
		Account account = new Account();
		account.setId(new AccountId(SORT_CODE, accountNumber));
		account.setAvailableBalance(new BigDecimal(availableBalance));
		account.setActualBalance(new BigDecimal(actualBalance));
		return account;
	}

	/**
	 * Builds the composite key for an account number under {@link #SORT_CODE},
	 * matching the key the service constructs for its locked read
	 * ({@code new AccountId(SORT_CODE, BankFormat.accountNumber(n))}). Relies on
	 * {@link AccountId#equals(Object)} for Mockito argument matching.
	 *
	 * @param accountNumber the eight-digit, zero-padded account number
	 * @return the composite account key
	 */
	private static AccountId accountId(String accountNumber)
	{
		return new AccountId(SORT_CODE, accountNumber);
	}

	/**
	 * Finds, among the accounts captured at {@code save(...)} time, the one whose
	 * account number matches (order-independent), failing the test if absent.
	 *
	 * @param savedAccounts the captured saved accounts
	 * @param accountNumber the account number to locate
	 * @return the matching saved account
	 */
	private static Account capturedAccount(List<Account> savedAccounts,
			String accountNumber)
	{
		return savedAccounts.stream()
				.filter(saved -> accountNumber.equals(saved.getAccountNumber()))
				.findFirst()
				.orElseThrow(() -> new AssertionError(
						"Expected a saved account " + accountNumber));
	}

	/**
	 * Amount of zero fails with code {@code '4'} and is rejected before any
	 * account is read &mdash; {@code XFRFUN} tests {@code COMM-AMT &lt;= ZERO}
	 * before touching the {@code ACCOUNT} rows. Nothing is persisted.
	 */
	@Test
	@DisplayName("Zero amount fails '4' before any account is read")
	void transferZeroAmount_failCode4()
	{
		assertThatThrownBy(() -> transferService.transfer("00000001",
				"00000002", new BigDecimal("0.00")))
				.isInstanceOf(BusinessRuleException.class)
				.hasFieldOrPropertyWithValue("failCode", FAIL_INVALID_AMOUNT);

		// The amount guard precedes account access: no locked read happened.
		verify(accountRepository, never()).findByIdForUpdate(any());
		verify(accountRepository, never()).save(any());
		verify(proctranAppender, never()).appendTransfer(any(), anyLong(),
				anyLong(), anyLong(), any());
	}

	/**
	 * Negative amount fails with code {@code '4'} (the guard rejects everything
	 * not strictly positive). Nothing is persisted.
	 */
	@Test
	@DisplayName("Negative amount fails '4'")
	void transferNegativeAmount_failCode4()
	{
		assertThatThrownBy(() -> transferService.transfer("00000001",
				"00000002", new BigDecimal("-10.00")))
				.isInstanceOf(BusinessRuleException.class)
				.hasFieldOrPropertyWithValue("failCode", FAIL_INVALID_AMOUNT);

		verify(accountRepository, never()).save(any());
		verify(proctranAppender, never()).appendTransfer(any(), anyLong(),
				anyLong(), anyLong(), any());
	}

	/**
	 * A transfer naming the same account twice abends with the marker
	 * {@code "SAME"} (an abend code, not a single-character fail code), matching
	 * {@code XFRFUN}'s {@code ABCODE('SAME')}. A positive amount is used so the
	 * same-account check is reached. Nothing is persisted.
	 */
	@Test
	@DisplayName("Same source and target account abends 'SAME'")
	void transferSameAccount_abendSame()
	{
		assertThatThrownBy(() -> transferService.transfer("00000005",
				"00000005", new BigDecimal("100.00")))
				.isInstanceOf(BusinessRuleException.class)
				.hasFieldOrPropertyWithValue("failCode", ABEND_SAME_ACCOUNT);

		verify(accountRepository, never()).save(any());
		verify(proctranAppender, never()).appendTransfer(any(), anyLong(),
				anyLong(), anyLong(), any());
	}

	/**
	 * A missing source (FROM) account fails with code {@code '1'}. The FROM
	 * account here ({@code 00000001}) is also the lower-numbered account and is
	 * therefore locked first; when it is absent the transfer aborts before the
	 * target is read, and nothing is persisted.
	 */
	@Test
	@DisplayName("Missing FROM account fails '1'")
	void fromAccountNotFound_failCode1()
	{
		when(accountRepository.findByIdForUpdate(accountId("00000001")))
				.thenReturn(Optional.empty());

		assertThatThrownBy(() -> transferService.transfer("00000001",
				"00000002", new BigDecimal("100.00")))
				.isInstanceOf(BusinessRuleException.class)
				.hasFieldOrPropertyWithValue("failCode", FAIL_SOURCE_NOT_FOUND);

		verify(accountRepository, never()).save(any());
		verify(proctranAppender, never()).appendTransfer(any(), anyLong(),
				anyLong(), anyLong(), any());
	}

	/**
	 * A missing target (TO) account fails with code {@code '2'}. The source
	 * ({@code 00000001}) is present and locked first, but the target
	 * ({@code 00000002}) is absent; the transfer aborts, the source is never
	 * debited (no {@code save}) and no audit row is appended.
	 */
	@Test
	@DisplayName("Missing TO account fails '2'; source not debited")
	void toAccountNotFound_failCode2()
	{
		when(accountRepository.findByIdForUpdate(accountId("00000001")))
				.thenReturn(Optional.of(standardFrom));
		when(accountRepository.findByIdForUpdate(accountId("00000002")))
				.thenReturn(Optional.empty());

		assertThatThrownBy(() -> transferService.transfer("00000001",
				"00000002", new BigDecimal("100.00")))
				.isInstanceOf(BusinessRuleException.class)
				.hasFieldOrPropertyWithValue("failCode", FAIL_TARGET_NOT_FOUND);

		verify(accountRepository, never()).save(any());
		verify(proctranAppender, never()).appendTransfer(any(), anyLong(),
				anyLong(), anyLong(), any());
	}

	/**
	 * Happy path: the FROM account is debited and the TO account credited on
	 * <em>both</em> independent balances, and a single {@code TFR} audit row is
	 * appended. From {@code 500.00/500.00} and {@code 100.00/100.00} a
	 * {@code 200.00} transfer leaves both accounts at {@code 300.00/300.00}. The
	 * persisted accounts are captured and asserted with scale-2
	 * {@code compareTo}; the {@code appendTransfer(...)} call is verified for the
	 * source key, target key and amount; and the appender's transfer type is
	 * cross-checked against {@link TransactionType#TFR}.
	 */
	@Test
	@DisplayName("Happy path debits FROM and credits TO on both balances and appends a TFR row")
	void happyPath_debitsFromAndCreditsTo_bothBalances_andAppendsTfr()
	{
		when(accountRepository.findByIdForUpdate(accountId("00000001")))
				.thenReturn(Optional.of(standardFrom));
		when(accountRepository.findByIdForUpdate(accountId("00000002")))
				.thenReturn(Optional.of(standardTo));

		TransferService.TransferResult result = transferService.transfer(
				"00000001", "00000002", new BigDecimal("200.00"));

		// Both rows were persisted (source then target); capture and locate each.
		ArgumentCaptor<Account> accountCaptor =
				ArgumentCaptor.forClass(Account.class);
		verify(accountRepository, times(2)).save(accountCaptor.capture());
		List<Account> savedAccounts = accountCaptor.getAllValues();
		Account savedFrom = capturedAccount(savedAccounts, "00000001");
		Account savedTo = capturedAccount(savedAccounts, "00000002");

		// FROM debited by 200.00 on BOTH independent balances (500.00 -> 300.00).
		assertThat(savedFrom.getAvailableBalance())
				.isEqualByComparingTo(new BigDecimal("300.00"));
		assertThat(savedFrom.getActualBalance())
				.isEqualByComparingTo(new BigDecimal("300.00"));

		// TO credited by 200.00 on BOTH independent balances (100.00 -> 300.00).
		assertThat(savedTo.getAvailableBalance())
				.isEqualByComparingTo(new BigDecimal("300.00"));
		assertThat(savedTo.getActualBalance())
				.isEqualByComparingTo(new BigDecimal("300.00"));

		// The public result echoes the same updated balances (frozen contract).
		assertThat(result.getSource().getAvailableBalance())
				.isEqualByComparingTo(new BigDecimal("300.00"));
		assertThat(result.getTarget().getActualBalance())
				.isEqualByComparingTo(new BigDecimal("300.00"));

		// Exactly one PROCTRAN row: keyed on the source account, target sort code
		// and account passed through, amount preserved.
		ArgumentCaptor<BigDecimal> amountCaptor =
				ArgumentCaptor.forClass(BigDecimal.class);
		verify(proctranAppender).appendTransfer(eq(SORT_CODE), eq(1L),
				eq(Long.parseLong(SORT_CODE)), eq(2L), amountCaptor.capture());
		assertThat(amountCaptor.getValue())
				.isEqualByComparingTo(new BigDecimal("200.00"));

		// Parity cross-check: appendTransfer is the TFR-specific writer, so the
		// appended row carries TransactionType.TFR (COBOL PROC-TY-TRANSFER 'TFR').
		assertThat(ProcessedTransactionAppender.TYPE_TRANSFER)
				.isEqualTo(TransactionType.TFR.getCode());
	}

	/**
	 * Lock ordering when the FROM account is the higher-numbered one: the service
	 * still locks the lower-numbered account ({@code 00000002}, here the TO
	 * account) <em>before</em> the higher-numbered account ({@code 00000009}, the
	 * FROM account). This proves the lock order follows the account number, not
	 * the FROM/TO role &mdash; the deadlock-avoidance rule of {@code XFRFUN}.
	 */
	@Test
	@DisplayName("Lock order: lower account locked first even when FROM is higher")
	void lockOrder_lowerAccountLockedFirst_whenFromIsHigher()
	{
		Account from = account("00000009", "500.00", "500.00");
		Account to = account("00000002", "100.00", "100.00");
		when(accountRepository.findByIdForUpdate(accountId("00000009")))
				.thenReturn(Optional.of(from));
		when(accountRepository.findByIdForUpdate(accountId("00000002")))
				.thenReturn(Optional.of(to));

		transferService.transfer("00000009", "00000002",
				new BigDecimal("100.00"));

		InOrder inOrder = inOrder(accountRepository);
		inOrder.verify(accountRepository)
				.findByIdForUpdate(accountId("00000002"));
		inOrder.verify(accountRepository)
				.findByIdForUpdate(accountId("00000009"));
	}

	/**
	 * Lock ordering when the FROM account is the lower-numbered one: the lower
	 * account ({@code 00000002}, here the FROM account) is locked before the
	 * higher account ({@code 00000009}). Together with
	 * {@link #lockOrder_lowerAccountLockedFirst_whenFromIsHigher()} this proves
	 * the ordering is purely by account number in both directions.
	 */
	@Test
	@DisplayName("Lock order: lower account locked first when FROM is lower")
	void lockOrder_lowerAccountLockedFirst_whenFromIsLower()
	{
		Account from = account("00000002", "500.00", "500.00");
		Account to = account("00000009", "100.00", "100.00");
		when(accountRepository.findByIdForUpdate(accountId("00000002")))
				.thenReturn(Optional.of(from));
		when(accountRepository.findByIdForUpdate(accountId("00000009")))
				.thenReturn(Optional.of(to));

		transferService.transfer("00000002", "00000009",
				new BigDecimal("100.00"));

		InOrder inOrder = inOrder(accountRepository);
		inOrder.verify(accountRepository)
				.findByIdForUpdate(accountId("00000002"));
		inOrder.verify(accountRepository)
				.findByIdForUpdate(accountId("00000009"));
	}

	/**
	 * Deadlock retry (manual loop in {@code transfer(...)}): the lower account's
	 * locked read fails once with a transient {@link CannotAcquireLockException}
	 * and then succeeds on the retry, so the transfer ultimately completes
	 * (XFRFUN {@code DB2-DEADLOCK-RETRY}). The lower account is therefore read
	 * twice, and the successful attempt persists both rows and appends one TFR
	 * row.
	 */
	@Test
	@DisplayName("Deadlock retry: a transient lock failure is retried and the transfer completes")
	void deadlockRetry_succeedsWithinSixAttempts()
	{
		when(accountRepository.findByIdForUpdate(accountId("00000001")))
				.thenThrow(new CannotAcquireLockException(
						"simulated transient deadlock"))
				.thenReturn(Optional.of(standardFrom));
		when(accountRepository.findByIdForUpdate(accountId("00000002")))
				.thenReturn(Optional.of(standardTo));

		TransferService.TransferResult result = transferService.transfer(
				"00000001", "00000002", new BigDecimal("200.00"));

		// The transfer completed on the second attempt: no exception, balances
		// moved exactly once (500.00 -> 300.00, not double-applied).
		assertThat(result).isNotNull();
		assertThat(result.getSource().getAvailableBalance())
				.isEqualByComparingTo(new BigDecimal("300.00"));

		// The lower account was read twice (one failure + one success); the
		// successful attempt persisted both rows and appended the TFR row once.
		verify(accountRepository, times(2))
				.findByIdForUpdate(accountId("00000001"));
		verify(accountRepository, times(2)).save(any());
		verify(proctranAppender).appendTransfer(eq(SORT_CODE), eq(1L),
				eq(Long.parseLong(SORT_CODE)), eq(2L), any());
	}

	/**
	 * Deadlock budget exhausted: every locked read reports a deadlock (here a
	 * {@link DeadlockLoserDataAccessException}), so all six attempts fail and the
	 * service surfaces the COBOL lock-failure fail code {@code '3'} as a
	 * {@link BusinessRuleException}. Both {@code CannotAcquireLockException} and
	 * {@code DeadlockLoserDataAccessException} are sub-types of
	 * {@code PessimisticLockingFailureException}, so either drives the retry path.
	 * Nothing is persisted.
	 */
	@Test
	@DisplayName("Deadlock budget exhausted (6 attempts) propagates fail code '3'")
	void deadlockExceedsRetryBudget_propagates()
	{
		when(accountRepository.findByIdForUpdate(accountId("00000001")))
				.thenThrow(new DeadlockLoserDataAccessException(
						"simulated persistent deadlock",
						new RuntimeException("SQLSTATE 40001")));

		assertThatThrownBy(() -> transferService.transfer("00000001",
				"00000002", new BigDecimal("200.00")))
				.isInstanceOf(BusinessRuleException.class)
				.hasFieldOrPropertyWithValue("failCode", FAIL_LOCK_FAILURE);

		// Six attempts were made (the maximum retry budget) before giving up.
		verify(accountRepository, times(6))
				.findByIdForUpdate(accountId("00000001"));
		verify(accountRepository, never()).save(any());
		verify(proctranAppender, never()).appendTransfer(any(), anyLong(),
				anyLong(), anyLong(), any());
	}

	/**
	 * The two balances are independent: starting from differing
	 * available/actual values ({@code 500.00/480.00} on the FROM and
	 * {@code 100.00/90.00} on the TO), a {@code 50.00} transfer moves each column
	 * by its own delta &mdash; FROM to {@code 450.00/430.00}, TO to
	 * {@code 150.00/140.00} &mdash; and the columns are never collapsed onto one
	 * another.
	 */
	@Test
	@DisplayName("Both balances are independent: each column moves by its own delta")
	void bothBalancesIndependent()
	{
		Account from = account("00000001", "500.00", "480.00");
		Account to = account("00000002", "100.00", "90.00");
		when(accountRepository.findByIdForUpdate(accountId("00000001")))
				.thenReturn(Optional.of(from));
		when(accountRepository.findByIdForUpdate(accountId("00000002")))
				.thenReturn(Optional.of(to));

		transferService.transfer("00000001", "00000002",
				new BigDecimal("50.00"));

		ArgumentCaptor<Account> accountCaptor =
				ArgumentCaptor.forClass(Account.class);
		verify(accountRepository, times(2)).save(accountCaptor.capture());
		List<Account> savedAccounts = accountCaptor.getAllValues();
		Account savedFrom = capturedAccount(savedAccounts, "00000001");
		Account savedTo = capturedAccount(savedAccounts, "00000002");

		// FROM: avail 500.00 - 50.00 = 450.00 ; actual 480.00 - 50.00 = 430.00.
		assertThat(savedFrom.getAvailableBalance())
				.isEqualByComparingTo(new BigDecimal("450.00"));
		assertThat(savedFrom.getActualBalance())
				.isEqualByComparingTo(new BigDecimal("430.00"));

		// TO: avail 100.00 + 50.00 = 150.00 ; actual 90.00 + 50.00 = 140.00.
		assertThat(savedTo.getAvailableBalance())
				.isEqualByComparingTo(new BigDecimal("150.00"));
		assertThat(savedTo.getActualBalance())
				.isEqualByComparingTo(new BigDecimal("140.00"));
	}
}
