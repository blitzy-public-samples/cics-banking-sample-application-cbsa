/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.core.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
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
import org.springframework.dao.DataIntegrityViolationException;

import com.ibm.cics.cip.bank.core.constants.BankConstants;
import com.ibm.cics.cip.bank.core.domain.TransactionType;
import com.ibm.cics.cip.bank.core.dto.payment.DbcrJson;
import com.ibm.cics.cip.bank.core.dto.payment.OriginJson;
import com.ibm.cics.cip.bank.core.dto.payment.PaymentJson;
import com.ibm.cics.cip.bank.core.entity.Account;
import com.ibm.cics.cip.bank.core.entity.AccountControl;
import com.ibm.cics.cip.bank.core.entity.AccountId;
import com.ibm.cics.cip.bank.core.entity.ProcessedTransaction;
import com.ibm.cics.cip.bank.core.exception.BusinessRuleException;
import com.ibm.cics.cip.bank.core.repository.AccountControlRepository;
import com.ibm.cics.cip.bank.core.repository.AccountRepository;
import com.ibm.cics.cip.bank.core.repository.ProcessedTransactionRepository;

/**
 * Parity unit test for {@link PaymentService} &mdash; the pure-Java rendering of
 * the COBOL program {@code DBCRFUN} (the debit/credit "make payment" function,
 * feature <strong>F-015</strong>) that backs the frozen z/OS&nbsp;Connect
 * endpoint {@code PUT /makepayment/dbcr}. The COBOL is the authoritative
 * behavioural specification, so this suite pins behavioural <em>parity</em>
 * &mdash; the exact single-character fail codes, the already-signed amount
 * convention, the facility-type-496 channel rules, the four PROCTRAN type codes,
 * and the movement of <em>both</em> (independent) balances &mdash; never
 * "improved" behaviour.
 *
 * <h2>What the COBOL specifies (the migration contract)</h2>
 * <ul>
 *   <li><strong>Sign convention.</strong> {@code IF COMM-AMT &lt; 0}
 *       ({@code DBCRFUN.cbl} L307) makes a negative amount a DEBIT and a
 *       non-negative amount a CREDIT. The amount arrives <em>already signed</em>
 *       and is never re-negated.</li>
 *   <li><strong>Dual-balance movement.</strong> On success both balances move by
 *       the signed amount &mdash; {@code AVAIL-BAL + COMM-AMT} and
 *       {@code ACTUAL-BAL + COMM-AMT} ({@code DBCRFUN.cbl} L384-387). The two
 *       columns are independent (cleared vs. pending) and are never collapsed.</li>
 *   <li><strong>Facility-type-496 channel rules.</strong> On the PAYMENT channel
 *       ({@code COMM-FACILTYPE = 496}) a debit or credit against a
 *       {@code MORTGAGE}/{@code LOAN} account fails with {@code '4'}
 *       ({@code DBCRFUN.cbl} L330-335 debit, L368-373 credit) and an
 *       insufficient-funds debit fails with {@code '3'} ({@code DBCRFUN.cbl}
 *       L342-347). The teller/branch channel (any other facility type) bypasses
 *       both guards.</li>
 *   <li><strong>PROCTRAN type codes.</strong> A successful payment appends one
 *       audit row: teller debit {@link TransactionType#DEB}, teller credit
 *       {@link TransactionType#CRE}, payment debit {@link TransactionType#PDR},
 *       payment credit {@link TransactionType#PCR} ({@code DBCRFUN.cbl}
 *       L491-517).</li>
 *   <li><strong>Other fail codes.</strong> account not found {@code '1'}
 *       ({@code SQLCODE +100}, L284); a persistence/SQL error on the update
 *       {@code '2'} (L427).</li>
 * </ul>
 *
 * <h2>Aligned with the finalized production code (Style&nbsp;B, exception)</h2>
 * <p>These assertions were reconciled against the finalized
 * {@link PaymentService}. Two alignment facts shape the suite:</p>
 * <ol>
 *   <li><strong>Fail codes are surfaced by throwing.</strong>
 *       {@code processPayment(...)} does not return a populated envelope on a
 *       failure &mdash; it throws an unchecked {@link BusinessRuleException}
 *       carrying the COBOL fail code (read via {@code getFailCode()}), which also
 *       rolls back the surrounding {@code @Transactional} unit of work. The
 *       failing-path tests therefore use
 *       {@code assertThatThrownBy(...).isInstanceOf(BusinessRuleException.class)}
 *       and assert the exact {@code failCode}. The success envelope (returned
 *       wrapper) carries {@code CommSuccess == "Y"} and {@code CommFailCode == " "}.</li>
 *   <li><strong>The account is read under a write lock.</strong> The service
 *       reads through {@link AccountRepository#findByIdForUpdate(AccountId)} (the
 *       {@code @Lock(PESSIMISTIC_WRITE)} finder), <em>not</em> the plain
 *       {@code findById}, so every fixture is stubbed on
 *       {@code findByIdForUpdate(...)}.</li>
 * </ol>
 *
 * <h2>Why this is a pure Mockito unit test (no Spring, no DB)</h2>
 * <p>No {@code @SpringBootTest}, {@code @DataJpaTest}, {@code MockMvc} or
 * {@code ApplicationContext} is bootstrapped; the two repository collaborators
 * are {@code @Mock} doubles wired into the service by {@code @InjectMocks}. All
 * money is asserted with {@link BigDecimal} {@code compareTo} semantics (never
 * {@code equals}, which is scale-sensitive) at scale&nbsp;2; no
 * {@code double}/{@code float} appears anywhere.</p>
 */
@ExtendWith(MockitoExtension.class)
class PaymentServiceTest
{

	/**
	 * The single institution sort code that keys every account. Sourced from the
	 * production {@link BankConstants#SORT_CODE} constant (rather than a
	 * re-hard-coded literal) so the parity test drifts with production if the
	 * sort code ever changes.
	 */
	private static final String SORT_CODE = BankConstants.SORT_CODE;

	/**
	 * The PAYMENT-channel facility type ({@code 496}). On this channel the
	 * MORTGAGE/LOAN ({@code '4'}) and insufficient-funds ({@code '3'}) guards
	 * apply. Sourced from {@link BankConstants#PAYMENT_FACILITY_TYPE}.
	 */
	private static final Integer PAYMENT_FACILITY = BankConstants.PAYMENT_FACILITY_TYPE;

	/**
	 * An arbitrary non-496 facility type modelling the teller/branch channel,
	 * which deliberately bypasses both facility-496 guards. Any value other than
	 * {@link #PAYMENT_FACILITY} exercises the teller path; {@code 1234} is used
	 * for readability.
	 */
	private static final Integer TELLER_FACILITY = 1234;

	/** The eight-digit, zero-padded account number used by every fixture. */
	private static final String ACCOUNT_NUMBER = "00000123";

	/** COBOL fail code: the account does not exist ({@code DBCRFUN} L284). */
	private static final String FAIL_ACCOUNT_NOT_FOUND = "1";

	/** COBOL fail code: a persistence/SQL error on the update ({@code DBCRFUN} L427). */
	private static final String FAIL_SQL_ERROR = "2";

	/** COBOL fail code: insufficient funds on a payment-channel debit ({@code DBCRFUN} L347). */
	private static final String FAIL_INSUFFICIENT_FUNDS = "3";

	/** COBOL fail code: payment on a MORTGAGE/LOAN account ({@code DBCRFUN} L335/L373). */
	private static final String FAIL_RESTRICTED_ACCOUNT = "4";

	/** Success flag written to the response commarea on a successful payment. */
	private static final String SUCCESS_FLAG = "Y";

	/** Blank fail code written to the response commarea on success. */
	private static final String BLANK_FAIL_CODE = " ";

	/** Mocked account repository (locked read + balance persistence). */
	@Mock
	private AccountRepository accountRepository;

	/**
	 * Mocked account-control repository. Used only as the
	 * {@code PESSIMISTIC_WRITE} semaphore that serialises PROCTRAN-reference
	 * allocation; stubbed leniently in {@link #lockControlRow()} because only
	 * the audit-appending (success) paths reach it.
	 */
	@Mock
	private AccountControlRepository accountControlRepository;

	/** Mocked append-only PROCTRAN audit-log repository. */
	@Mock
	private ProcessedTransactionRepository processedTransactionRepository;

	/**
	 * System under test &mdash; constructed by Mockito via constructor injection
	 * with the three mocked repositories ({@code new PaymentService(
	 * accountRepository, accountControlRepository,
	 * processedTransactionRepository)}).
	 */
	@InjectMocks
	private PaymentService paymentService;

	/**
	 * Makes the {@code account_control} semaphore read return a row for every
	 * test. {@code PaymentService} acquires this {@code PESSIMISTIC_WRITE} lock
	 * before allocating the next PROCTRAN reference; only the audit-appending
	 * success paths reach it, so the stub is {@code lenient()} to coexist with
	 * the failure-path tests (which roll back before any append) under the
	 * strict {@link MockitoExtension}.
	 */
	@BeforeEach
	void lockControlRow()
	{
		AccountControl control = new AccountControl();
		control.setSortCode(SORT_CODE);
		// Seed the PROCTRAN-reference counter at zero so the audit-append path can
		// allocate the next reference as last_transaction_reference + 1.
		control.setLastTransactionReference(0L);
		lenient().when(accountControlRepository
				.findBySortCodeForUpdate(SORT_CODE))
				.thenReturn(Optional.of(control));
	}

	/**
	 * Builds an {@link Account} fixture keyed under {@link #SORT_CODE} with the
	 * two independent balances and a whole-pounds overdraft limit.
	 *
	 * @param accountNumber    the eight-digit, zero-padded account number
	 * @param accountType      the plain account-type string (for example
	 *                         {@code "CURRENT"}, {@code "MORTGAGE"}, {@code "LOAN"})
	 * @param availableBalance the available (cleared) balance as a scale-2 decimal
	 *                         string
	 * @param actualBalance    the actual balance as a scale-2 decimal string
	 * @param overdraftLimit   the overdraft limit in whole pounds
	 * @return the populated account fixture
	 */
	private static Account account(String accountNumber, String accountType,
			String availableBalance, String actualBalance, int overdraftLimit)
	{
		Account account = new Account();
		account.setId(new AccountId(SORT_CODE, accountNumber));
		account.setCustomerNumber("0000000001");
		account.setAccountType(accountType);
		account.setAvailableBalance(new BigDecimal(availableBalance));
		account.setActualBalance(new BigDecimal(actualBalance));
		account.setOverdraftLimit(overdraftLimit);
		return account;
	}

	/**
	 * Builds a {@code PAYDBCR} request envelope for the given account, signed
	 * amount and facility type. The amount is supplied <em>already signed</em>
	 * (negative for a debit, non-negative for a credit), matching the inbound
	 * contract &mdash; the service never re-negates it.
	 *
	 * @param accountNumber the eight-digit, zero-padded account number
	 * @param signedAmount  the already-signed amount as a scale-2 decimal string
	 * @param facilityType  the calling-channel facility type ({@link #PAYMENT_FACILITY}
	 *                      for the payment channel, any other value for teller)
	 * @return the populated {@link PaymentJson} request envelope
	 */
	private static PaymentJson request(String accountNumber, String signedAmount,
			Integer facilityType)
	{
		OriginJson origin = new OriginJson();
		origin.setCommFaciltype(facilityType);

		DbcrJson dbcr = new DbcrJson();
		dbcr.setCommAccno(accountNumber);
		dbcr.setCommAmt(new BigDecimal(signedAmount));
		dbcr.setCommOrigin(origin);

		PaymentJson request = new PaymentJson();
		request.setPAYDBCR(dbcr);
		return request;
	}

	/**
	 * Stubs the locked account read to return the supplied fixture, reproducing a
	 * found {@code ACCOUNT} row. Argument matching uses {@code any(AccountId.class)}
	 * so the test is not coupled to the service's internal key padding.
	 *
	 * @param account the fixture account the locked read should return
	 */
	private void givenAccountExists(Account account)
	{
		when(accountRepository.findByIdForUpdate(any(AccountId.class)))
				.thenReturn(Optional.of(account));
	}

	/**
	 * Stubs the locked account read to return empty, reproducing the COBOL
	 * {@code SQLCODE +100} (account not found) that drives fail code {@code '1'}.
	 */
	private void givenAccountMissing()
	{
		when(accountRepository.findByIdForUpdate(any(AccountId.class)))
				.thenReturn(Optional.empty());
	}

	/**
	 * Captures the single {@link Account} persisted by the service and returns it
	 * for balance assertions.
	 *
	 * @return the captured saved account
	 */
	private Account captureSavedAccount()
	{
		ArgumentCaptor<Account> captor = ArgumentCaptor.forClass(Account.class);
		verify(accountRepository).save(captor.capture());
		return captor.getValue();
	}

	/**
	 * Captures the single {@link ProcessedTransaction} appended by the service
	 * and returns it for type-code/amount assertions.
	 *
	 * @return the captured appended PROCTRAN row
	 */
	private ProcessedTransaction captureSavedTransaction()
	{
		ArgumentCaptor<ProcessedTransaction> captor = ArgumentCaptor
				.forClass(ProcessedTransaction.class);
		verify(processedTransactionRepository).save(captor.capture());
		return captor.getValue();
	}

	/**
	 * Asserts that neither repository mutated state &mdash; no account balance was
	 * saved and no PROCTRAN audit row was appended. Used on every failing path to
	 * prove the guard fired before any write leaked through.
	 */
	private void assertNoPersistence()
	{
		verify(accountRepository, never()).save(any());
		verify(processedTransactionRepository, never()).save(any());
	}

	// ------------------------------------------------------------------
	// Success paths (cases 1-4): both balances move by the signed amount
	// and the correct PROCTRAN type code is appended.
	// ------------------------------------------------------------------

	/**
	 * Case 1 &mdash; a {@code +100.00} credit on the PAYMENT channel against a
	 * {@code CURRENT} account moves both balances from {@code 200.00} to
	 * {@code 300.00} and appends a {@link TransactionType#PCR} (payment credit)
	 * audit row carrying the signed amount.
	 */
	@Test
	@DisplayName("Credit on facility 496 updates both balances and appends a PCR row")
	void creditCurrentAccount_facility496_updatesBothBalances_andAppendsPcr()
	{
		Account account = account(ACCOUNT_NUMBER, "CURRENT", "200.00", "200.00", 0);
		givenAccountExists(account);

		PaymentJson result = paymentService
				.processPayment(request(ACCOUNT_NUMBER, "100.00", PAYMENT_FACILITY));

		// Success envelope: "Y" / blank fail code, both new balances reflected.
		DbcrJson out = result.getPAYDBCR();
		assertThat(out.getCommSuccess()).isEqualTo(SUCCESS_FLAG);
		assertThat(out.getCommFailCode()).isEqualTo(BLANK_FAIL_CODE);
		assertThat(out.getCommAvBal().compareTo(new BigDecimal("300.00"))).isZero();
		assertThat(out.getCommActBal().compareTo(new BigDecimal("300.00"))).isZero();

		// Both independent balances moved together by +100.00.
		Account saved = captureSavedAccount();
		assertThat(saved.getAvailableBalance().compareTo(new BigDecimal("300.00")))
				.isZero();
		assertThat(saved.getActualBalance().compareTo(new BigDecimal("300.00")))
				.isZero();

		// Payment-channel credit -> PCR, carrying the signed amount.
		ProcessedTransaction proc = captureSavedTransaction();
		assertThat(proc.getTypeCode()).isEqualTo(TransactionType.PCR);
		assertThat(proc.getAmount().compareTo(new BigDecimal("100.00"))).isZero();
	}

	/**
	 * Case 2 &mdash; a {@code -50.00} debit on the PAYMENT channel against a
	 * {@code CURRENT} account with sufficient funds moves both balances from
	 * {@code 200.00} to {@code 150.00} and appends a {@link TransactionType#PDR}
	 * (payment debit) audit row.
	 */
	@Test
	@DisplayName("Debit on facility 496 with sufficient funds updates both balances and appends a PDR row")
	void debitCurrentAccount_facility496_sufficientFunds_updatesBothBalances_andAppendsPdr()
	{
		Account account = account(ACCOUNT_NUMBER, "CURRENT", "200.00", "200.00", 0);
		givenAccountExists(account);

		PaymentJson result = paymentService
				.processPayment(request(ACCOUNT_NUMBER, "-50.00", PAYMENT_FACILITY));

		DbcrJson out = result.getPAYDBCR();
		assertThat(out.getCommSuccess()).isEqualTo(SUCCESS_FLAG);
		assertThat(out.getCommFailCode()).isEqualTo(BLANK_FAIL_CODE);
		assertThat(out.getCommAvBal().compareTo(new BigDecimal("150.00"))).isZero();
		assertThat(out.getCommActBal().compareTo(new BigDecimal("150.00"))).isZero();

		Account saved = captureSavedAccount();
		assertThat(saved.getAvailableBalance().compareTo(new BigDecimal("150.00")))
				.isZero();
		assertThat(saved.getActualBalance().compareTo(new BigDecimal("150.00")))
				.isZero();

		// Payment-channel debit -> PDR, carrying the (negative) signed amount.
		ProcessedTransaction proc = captureSavedTransaction();
		assertThat(proc.getTypeCode()).isEqualTo(TransactionType.PDR);
		assertThat(proc.getAmount().compareTo(new BigDecimal("-50.00"))).isZero();
	}

	/**
	 * Case 3 &mdash; a debit on the teller/branch channel (facility type other
	 * than {@code 496}) appends a {@link TransactionType#DEB} (counter
	 * withdrawal) audit row rather than {@code PDR}, and still moves both
	 * balances.
	 */
	@Test
	@DisplayName("Debit on the teller channel appends a DEB row")
	void debitCurrentAccount_defaultChannel_appendsDeb()
	{
		Account account = account(ACCOUNT_NUMBER, "CURRENT", "200.00", "200.00", 0);
		givenAccountExists(account);

		PaymentJson result = paymentService
				.processPayment(request(ACCOUNT_NUMBER, "-50.00", TELLER_FACILITY));

		assertThat(result.getPAYDBCR().getCommSuccess()).isEqualTo(SUCCESS_FLAG);

		Account saved = captureSavedAccount();
		assertThat(saved.getAvailableBalance().compareTo(new BigDecimal("150.00")))
				.isZero();
		assertThat(saved.getActualBalance().compareTo(new BigDecimal("150.00")))
				.isZero();

		// Teller (default) channel debit -> DEB, not PDR.
		ProcessedTransaction proc = captureSavedTransaction();
		assertThat(proc.getTypeCode()).isEqualTo(TransactionType.DEB);
	}

	/**
	 * Case 4 &mdash; a credit on the teller/branch channel appends a
	 * {@link TransactionType#CRE} (counter received) audit row rather than
	 * {@code PCR}, and still moves both balances.
	 */
	@Test
	@DisplayName("Credit on the teller channel appends a CRE row")
	void creditCurrentAccount_defaultChannel_appendsCre()
	{
		Account account = account(ACCOUNT_NUMBER, "CURRENT", "200.00", "200.00", 0);
		givenAccountExists(account);

		PaymentJson result = paymentService
				.processPayment(request(ACCOUNT_NUMBER, "50.00", TELLER_FACILITY));

		assertThat(result.getPAYDBCR().getCommSuccess()).isEqualTo(SUCCESS_FLAG);

		Account saved = captureSavedAccount();
		assertThat(saved.getAvailableBalance().compareTo(new BigDecimal("250.00")))
				.isZero();
		assertThat(saved.getActualBalance().compareTo(new BigDecimal("250.00")))
				.isZero();

		// Teller (default) channel credit -> CRE, not PCR.
		ProcessedTransaction proc = captureSavedTransaction();
		assertThat(proc.getTypeCode()).isEqualTo(TransactionType.CRE);
	}

	// ------------------------------------------------------------------
	// Failing paths (cases 5-9): the guard throws BusinessRuleException
	// carrying the exact COBOL fail code and NOTHING is persisted.
	// ------------------------------------------------------------------

	/**
	 * Case 5 &mdash; when the locked account read returns empty the service
	 * fails with code {@code '1'} (COBOL {@code SQLCODE +100}) and writes
	 * neither the account nor a PROCTRAN row.
	 */
	@Test
	@DisplayName("Account not found fails '1' and writes nothing")
	void accountNotFound_failCode1()
	{
		givenAccountMissing();

		assertThatThrownBy(() -> paymentService
				.processPayment(request(ACCOUNT_NUMBER, "100.00", PAYMENT_FACILITY)))
				.isInstanceOf(BusinessRuleException.class)
				.hasFieldOrPropertyWithValue("failCode", FAIL_ACCOUNT_NOT_FOUND);

		assertNoPersistence();
	}

	/**
	 * Case 6 &mdash; a debit against a {@code MORTGAGE} account on the PAYMENT
	 * channel fails with code {@code '4'} (the MORTGAGE/LOAN channel
	 * restriction) and writes nothing.
	 */
	@Test
	@DisplayName("Debit a MORTGAGE on facility 496 fails '4' and writes nothing")
	void debitMortgage_facility496_failCode4()
	{
		givenAccountExists(
				account(ACCOUNT_NUMBER, "MORTGAGE", "200.00", "200.00", 0));

		assertThatThrownBy(() -> paymentService
				.processPayment(request(ACCOUNT_NUMBER, "-50.00", PAYMENT_FACILITY)))
				.isInstanceOf(BusinessRuleException.class)
				.hasFieldOrPropertyWithValue("failCode", FAIL_RESTRICTED_ACCOUNT);

		assertNoPersistence();
	}

	/**
	 * Case 7 &mdash; a debit against a {@code LOAN} account on the PAYMENT
	 * channel also fails with code {@code '4'}.
	 */
	@Test
	@DisplayName("Debit a LOAN on facility 496 fails '4'")
	void debitLoan_facility496_failCode4()
	{
		givenAccountExists(account(ACCOUNT_NUMBER, "LOAN", "200.00", "200.00", 0));

		assertThatThrownBy(() -> paymentService
				.processPayment(request(ACCOUNT_NUMBER, "-50.00", PAYMENT_FACILITY)))
				.isInstanceOf(BusinessRuleException.class)
				.hasFieldOrPropertyWithValue("failCode", FAIL_RESTRICTED_ACCOUNT);

		assertNoPersistence();
	}

	/**
	 * Case 8 &mdash; a <em>credit</em> against a {@code MORTGAGE} account on the
	 * PAYMENT channel is blocked too: the COBOL applies the MORTGAGE/LOAN guard
	 * to both directions, so a credit also fails with code {@code '4'}.
	 */
	@Test
	@DisplayName("Credit a MORTGAGE on facility 496 also fails '4'")
	void creditMortgage_facility496_failCode4()
	{
		givenAccountExists(
				account(ACCOUNT_NUMBER, "MORTGAGE", "200.00", "200.00", 0));

		assertThatThrownBy(() -> paymentService
				.processPayment(request(ACCOUNT_NUMBER, "50.00", PAYMENT_FACILITY)))
				.isInstanceOf(BusinessRuleException.class)
				.hasFieldOrPropertyWithValue("failCode", FAIL_RESTRICTED_ACCOUNT);

		assertNoPersistence();
	}

	/**
	 * Case 9 &mdash; a debit that would breach the overdraft floor on the
	 * PAYMENT channel fails with code {@code '3'}: with {@code avail = 10.00} and
	 * {@code overdraft = 0} the floor is {@code 0.00}, and
	 * {@code (10.00 + (-100.00)) = -90.00 < 0.00}. Nothing is persisted.
	 */
	@Test
	@DisplayName("Debit beyond the overdraft floor on facility 496 fails '3'")
	void debitInsufficientFunds_facility496_failCode3()
	{
		givenAccountExists(account(ACCOUNT_NUMBER, "CURRENT", "10.00", "10.00", 0));

		assertThatThrownBy(() -> paymentService
				.processPayment(request(ACCOUNT_NUMBER, "-100.00", PAYMENT_FACILITY)))
				.isInstanceOf(BusinessRuleException.class)
				.hasFieldOrPropertyWithValue("failCode", FAIL_INSUFFICIENT_FUNDS);

		assertNoPersistence();
	}

	/**
	 * Case 9 (boundary) &mdash; a debit to <em>exactly</em> the overdraft floor
	 * is PERMITTED, pinning the {@code <} (strictly-less-than) semantics of the
	 * insufficient-funds guard ({@code DBCRFUN.cbl} L344 {@code IF WS-DIFFERENCE
	 * < 0}). With {@code avail = 0.00} and {@code overdraft = 100} the floor is
	 * {@code -100.00}, and {@code (0.00 + (-100.00)) = -100.00}, which equals the
	 * floor and is therefore allowed (not a fail). Both balances are driven to
	 * {@code -100.00} and a {@link TransactionType#PDR} row is appended.
	 */
	@Test
	@DisplayName("Debit to exactly the overdraft floor on facility 496 is permitted (boundary)")
	void debitToExactOverdraftFloor_facility496_isPermitted()
	{
		givenAccountExists(account(ACCOUNT_NUMBER, "CURRENT", "0.00", "0.00", 100));

		PaymentJson result = paymentService
				.processPayment(request(ACCOUNT_NUMBER, "-100.00", PAYMENT_FACILITY));

		assertThat(result.getPAYDBCR().getCommSuccess()).isEqualTo(SUCCESS_FLAG);

		Account saved = captureSavedAccount();
		assertThat(saved.getAvailableBalance().compareTo(new BigDecimal("-100.00")))
				.isZero();
		assertThat(saved.getActualBalance().compareTo(new BigDecimal("-100.00")))
				.isZero();

		ProcessedTransaction proc = captureSavedTransaction();
		assertThat(proc.getTypeCode()).isEqualTo(TransactionType.PDR);
	}

	// ------------------------------------------------------------------
	// Channel bypass, persistence error, and balance independence
	// (cases 10-12).
	// ------------------------------------------------------------------

	/**
	 * Case 10 &mdash; the teller/branch channel deliberately bypasses BOTH
	 * facility-496 guards. A debit against a {@code MORTGAGE} account with
	 * would-be-insufficient funds nonetheless SUCCEEDS off the payment channel:
	 * both balances move to {@code -90.00} and a default-channel
	 * {@link TransactionType#DEB} row is appended.
	 */
	@Test
	@DisplayName("Teller channel bypasses the MORTGAGE and insufficient-funds checks and succeeds (DEB)")
	void tellerChannel_bypassesMortgageAndInsufficientChecks()
	{
		givenAccountExists(account(ACCOUNT_NUMBER, "MORTGAGE", "10.00", "10.00", 0));

		PaymentJson result = paymentService
				.processPayment(request(ACCOUNT_NUMBER, "-100.00", TELLER_FACILITY));

		assertThat(result.getPAYDBCR().getCommSuccess()).isEqualTo(SUCCESS_FLAG);

		// Both balances move to -90.00 despite MORTGAGE + would-be overdraft.
		Account saved = captureSavedAccount();
		assertThat(saved.getAvailableBalance().compareTo(new BigDecimal("-90.00")))
				.isZero();
		assertThat(saved.getActualBalance().compareTo(new BigDecimal("-90.00")))
				.isZero();

		// Default (teller) channel debit -> DEB, not PDR.
		ProcessedTransaction proc = captureSavedTransaction();
		assertThat(proc.getTypeCode()).isEqualTo(TransactionType.DEB);
	}

	/**
	 * Case 11 &mdash; when the account update raises a Spring
	 * {@link DataIntegrityViolationException} (a {@code DataAccessException}
	 * subclass), the service translates it to the COBOL SQL-error fail code
	 * {@code '2'}. Because the PROCTRAN append is downstream of the failed save,
	 * no audit row leaks through.
	 */
	@Test
	@DisplayName("A persistence error on the account update fails '2'")
	void saveError_failCode2()
	{
		givenAccountExists(account(ACCOUNT_NUMBER, "CURRENT", "200.00", "200.00", 0));
		when(accountRepository.save(any(Account.class)))
				.thenThrow(new DataIntegrityViolationException("simulated SQL error"));

		assertThatThrownBy(() -> paymentService
				.processPayment(request(ACCOUNT_NUMBER, "100.00", PAYMENT_FACILITY)))
				.isInstanceOf(BusinessRuleException.class)
				.hasFieldOrPropertyWithValue("failCode", FAIL_SQL_ERROR);

		verify(processedTransactionRepository, never()).save(any());
	}

	/**
	 * Case 12 &mdash; the available and actual balances are independent columns
	 * and are never collapsed. Starting from deliberately different balances
	 * ({@code avail = 100.00}, {@code actual = 80.00}), a {@code +20.00} credit
	 * yields {@code 120.00} and {@code 100.00} respectively, preserving the
	 * {@code 20.00} delta between them.
	 */
	@Test
	@DisplayName("The two balances are updated independently and never collapsed")
	void bothBalancesIndependentlyUpdated()
	{
		givenAccountExists(account(ACCOUNT_NUMBER, "CURRENT", "100.00", "80.00", 0));

		PaymentJson result = paymentService
				.processPayment(request(ACCOUNT_NUMBER, "20.00", PAYMENT_FACILITY));

		DbcrJson out = result.getPAYDBCR();
		assertThat(out.getCommSuccess()).isEqualTo(SUCCESS_FLAG);
		// The 20.00 delta is preserved between the two columns: 120.00 vs 100.00.
		assertThat(out.getCommAvBal().compareTo(new BigDecimal("120.00"))).isZero();
		assertThat(out.getCommActBal().compareTo(new BigDecimal("100.00"))).isZero();

		Account saved = captureSavedAccount();
		assertThat(saved.getAvailableBalance().compareTo(new BigDecimal("120.00")))
				.isZero();
		assertThat(saved.getActualBalance().compareTo(new BigDecimal("100.00")))
				.isZero();

		ProcessedTransaction proc = captureSavedTransaction();
		assertThat(proc.getTypeCode()).isEqualTo(TransactionType.PCR);
	}

	// ------------------------------------------------------------------
	// Input-contract guard (case 13): an over-width external account number is
	// REJECTED (never truncated), closing the wrong-account aliasing
	// vulnerability at the service boundary (defence-in-depth behind the
	// controller's @Valid @Size(max=8) cascade).
	// ------------------------------------------------------------------

	/**
	 * Case 13 &mdash; an over-width external account number is REJECTED, not
	 * truncated. The frozen {@code makepayment} contract fixes {@code CommAccno}
	 * at {@code maxLength 8} ({@code COMM-ACCNO PIC X(8)}). A sixteen-character
	 * value &mdash; whose rightmost eight characters are deliberately
	 * {@code "00000123"}, the very account every other fixture uses &mdash; would,
	 * under a naive trailing-character pad, have been silently reduced to that
	 * suffix and moved money on a <em>different</em>, real account. The service
	 * must instead fail with the safe account-not-found code {@code '1'} and
	 * persist nothing. The locked account read is never even attempted for an
	 * over-width key, so no aliasing can occur.
	 */
	@Test
	@DisplayName("Over-width account number is rejected '1' and never aliased/persisted")
	void overWidthAccountNumber_rejected_failCode1_noAliasing()
	{
		// 16 chars; trailing-8 would be "00000123" -> proves a truncating pad
		// would have aliased the real account used by every other fixture.
		assertThatThrownBy(() -> paymentService.processPayment(
				request("9999999900000123", "100.00", PAYMENT_FACILITY)))
				.isInstanceOf(BusinessRuleException.class)
				.hasFieldOrPropertyWithValue("failCode", FAIL_ACCOUNT_NOT_FOUND);

		// The over-width key is rejected BEFORE the locked read, so no account is
		// ever looked up (no aliasing) and nothing is persisted.
		verify(accountRepository, never()).findByIdForUpdate(any(AccountId.class));
		assertNoPersistence();
	}

	// ------------------------------------------------------------------
	// Concurrency regression (CWE-362): every PROCTRAN-reference allocation
	// must be serialised by the account_control PESSIMISTIC_WRITE semaphore so
	// that concurrent payments cannot compute the same (sort_code, reference)
	// and collide on the append-only PROCTRAN primary key.
	// ------------------------------------------------------------------

	/**
	 * Concurrency-safety parity: the payment audit append MUST acquire the
	 * {@code account_control} row under {@code PESSIMISTIC_WRITE}
	 * ({@link AccountControlRepository#findBySortCodeForUpdate(String)}) BEFORE
	 * it allocates the {@code PROCTRAN} reference by incrementing that row's
	 * {@code last_transaction_reference} counter and saving it. That
	 * lock is the single per-sort-code semaphore shared by every audit-append
	 * path, so holding it before the allocation is precisely what stops two
	 * parallel payments from minting the same reference and colliding on the
	 * {@code (sort_code, reference)} primary key. The ordering is pinned with a
	 * Mockito {@link InOrder} verification, so a regression that drops or
	 * re-orders the lock (re-introducing the race) fails the build.
	 */
	@Test
	@DisplayName("Payment append locks account_control BEFORE allocating the PROCTRAN reference")
	void paymentAppend_acquiresControlLockBeforeReferenceAllocation()
	{
		Account account = account(ACCOUNT_NUMBER, "CURRENT", "200.00", "200.00", 0);
		givenAccountExists(account);

		paymentService.processPayment(
				request(ACCOUNT_NUMBER, "100.00", PAYMENT_FACILITY));

		// The PESSIMISTIC_WRITE lock on account_control must be taken strictly
		// before the reference is allocated from that row's
		// last_transaction_reference counter (the control-row save), and the
		// PROCTRAN append save follows both.
		InOrder inOrder = inOrder(accountControlRepository,
				processedTransactionRepository);
		inOrder.verify(accountControlRepository)
				.findBySortCodeForUpdate(SORT_CODE);
		inOrder.verify(accountControlRepository)
				.save(any(AccountControl.class));
		inOrder.verify(processedTransactionRepository).save(any());
	}

}
