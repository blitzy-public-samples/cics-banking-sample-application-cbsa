/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.core.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
import com.ibm.cics.cip.bank.core.domain.AccountType;
import com.ibm.cics.cip.bank.core.domain.TransactionType;
import com.ibm.cics.cip.bank.core.dto.accountenquiry.AccountEnquiryJson;
import com.ibm.cics.cip.bank.core.dto.createaccount.CreateAccountForm;
import com.ibm.cics.cip.bank.core.dto.createaccount.CreateAccountJson;
import com.ibm.cics.cip.bank.core.dto.deleteaccount.DeleteAccountJson;
import com.ibm.cics.cip.bank.core.dto.listaccounts.ListAccountsJson;
import com.ibm.cics.cip.bank.core.dto.updateaccount.UpdateAccountForm;
import com.ibm.cics.cip.bank.core.dto.updateaccount.UpdateAccountJson;
import com.ibm.cics.cip.bank.core.entity.Account;
import com.ibm.cics.cip.bank.core.entity.AccountControl;
import com.ibm.cics.cip.bank.core.entity.AccountId;
import com.ibm.cics.cip.bank.core.entity.Customer;
import com.ibm.cics.cip.bank.core.entity.CustomerId;
import com.ibm.cics.cip.bank.core.entity.ProcessedTransaction;
import com.ibm.cics.cip.bank.core.exception.BusinessRuleException;
import com.ibm.cics.cip.bank.core.repository.AccountControlRepository;
import com.ibm.cics.cip.bank.core.repository.AccountRepository;
import com.ibm.cics.cip.bank.core.repository.CustomerRepository;
import com.ibm.cics.cip.bank.core.repository.ProcessedTransactionRepository;

/**
 * Parity unit test for {@link AccountService} &mdash; the pure-Java rendering of
 * five COBOL programs that together own every account-level operation:
 * {@code CREACC} (create, feature <strong>F-007</strong>), {@code INQACC}
 * (inquire one, <strong>F-009</strong>), {@code INQACCCU} (list by customer,
 * <strong>F-010</strong>), {@code UPDACC} (restricted update,
 * <strong>F-012</strong>), and {@code DELACC} (delete, <strong>F-013</strong>).
 * The COBOL is the authoritative behavioural specification, so this suite pins
 * behavioural <em>parity</em> &mdash; never "improved" behaviour.
 *
 * <h2>The migration contract these tests pin</h2>
 * <ul>
 *   <li><strong>CREACC strict five-step ordering.</strong> The owning-customer
 *       existence check (fail {@code '1'}), the account count (a persistence
 *       error fails {@code '9'}), the ten-account ceiling (fail {@code '8'}),
 *       and the account-type validation (fail {@code 'A'}) all run
 *       <em>before</em> the account number is allocated. Allocation is the
 *       LAST step, so a rejected create never consumes a number (gap-free
 *       identity, ADR-003). Every early-failure test therefore asserts
 *       {@code identityService.allocateAccountNumber()} was <strong>never</strong>
 *       called.</li>
 *   <li><strong>The ten-account ceiling is {@code >=}.</strong> A customer that
 *       already holds nine accounts may create the tenth; a customer holding ten
 *       is rejected with {@code '8'}.</li>
 *   <li><strong>INQACC highest-account sentinel.</strong> The number
 *       {@code 99999999} is resolved by reading {@code lastAccountNumber} from
 *       the {@code account_control} row (a primary-key {@code findById}), never a
 *       {@code MAX()}/{@code ORDER BY ... DESC} table scan.</li>
 *   <li><strong>INQACCCU caps the list at twenty.</strong> A customer with more
 *       than twenty accounts yields exactly twenty rendered entries.</li>
 *   <li><strong>UPDACC is deliberately restricted.</strong> It changes only the
 *       account type, interest rate and overdraft limit &mdash; never the two
 *       balances &mdash; and writes <strong>no</strong> {@code PROCTRAN} record.</li>
 *   <li><strong>DELACC captures the terminal actual balance BEFORE deletion</strong>
 *       and carries it as the amount of the account-close {@code PROCTRAN}
 *       record, then physically removes the account row.</li>
 * </ul>
 *
 * <h2>Aligned with the finalized production code (exception style)</h2>
 * <p>These assertions were reconciled against the finalized
 * {@link AccountService} (the mandatory ALIGN-WITH-PRODUCTION step), which
 * differs from the suggested contract in several behaviourally significant ways
 * &mdash; production is authoritative:</p>
 * <ol>
 *   <li><strong>Fail codes are surfaced by throwing.</strong> {@code createAccount},
 *       {@code listAccountsByCustomer}, {@code updateAccount} and
 *       {@code deleteAccount} do not return a populated envelope on a failure
 *       &mdash; they throw an unchecked {@link BusinessRuleException} carrying the
 *       COBOL fail code (read via {@code getFailCode()}), which also rolls back
 *       the surrounding {@code @Transactional} unit of work. The failing-path
 *       tests therefore use {@code assertThatThrownBy(...)} and assert the exact
 *       {@code failCode}. The single soft path is {@link AccountService#inquireAccount(long)},
 *       whose not-found outcome is a populated envelope with
 *       {@code InqaccSuccess == "N"} and no hard fail code.</li>
 *   <li><strong>PROCTRAN audit type codes are {@code OCA}/{@code ODA}.</strong>
 *       Account create appends a {@link TransactionType#OCA} row (amount
 *       {@code 0.00}); account delete appends a {@link TransactionType#ODA} row
 *       carrying the captured terminal actual balance.</li>
 *   <li><strong>The insert/update persistence-error fail code is {@code '7'}.</strong></li>
 * </ol>
 *
 * <h2>Why this is a pure Mockito unit test (no Spring, no DB)</h2>
 * <p>No {@code @SpringBootTest}, {@code @DataJpaTest}, {@code MockMvc} or
 * {@code ApplicationContext} is bootstrapped; the five collaborators are
 * {@code @Mock} doubles wired into the service by {@code @InjectMocks}. All money
 * is asserted with {@link BigDecimal} {@code compareTo} semantics (never
 * {@code equals}, which is scale-sensitive) at scale&nbsp;2; no {@code double}/
 * {@code float} appears anywhere. {@link MockitoExtension} runs in its default
 * strict-stubs mode, so each test stubs only the collaborators its code path
 * actually reaches.</p>
 */
@ExtendWith(MockitoExtension.class)
class AccountServiceTest
{

	/**
	 * The single institution sort code that keys every account and customer.
	 * Sourced from the production {@link BankConstants#SORT_CODE} constant
	 * (rather than a re-hard-coded literal) so the parity test drifts with
	 * production if the sort code ever changes.
	 */
	private static final String SORT_CODE = BankConstants.SORT_CODE;

	/** The ten-digit, zero-padded owning customer number used by every fixture. */
	private static final String CUSTOMER_NUMBER = "0000000123";

	/** An eight-digit, zero-padded account number used by the single-account fixtures. */
	private static final String ACCOUNT_NUMBER = "00000001";

	/**
	 * The "highest account" sentinel ({@code INQACC} L218): a request for this
	 * account number is resolved from the control row's {@code lastAccountNumber}.
	 */
	private static final long HIGHEST_ACCOUNT_SENTINEL = 99999999L;

	/** COBOL fail code: the owning customer does not exist ({@code CREACC}/{@code INQACCCU}). */
	private static final String FAIL_CUSTOMER_NOT_FOUND = "1";

	/** COBOL fail code: the account-count step failed ({@code CREACC} L340). */
	private static final String FAIL_COUNT_ERROR = "9";

	/** COBOL fail code: the customer already holds the maximum of ten accounts ({@code CREACC} L347). */
	private static final String FAIL_TOO_MANY_ACCOUNTS = "8";

	/** COBOL fail code: the account type is not one of the five valid values ({@code CREACC} L1224). */
	private static final String FAIL_INVALID_TYPE = "A";

	/** COBOL fail code: a persistence failure inserting/updating the account row ({@code CREACC} L862). */
	private static final String FAIL_INSERT_ERROR = "7";

	/** COBOL fail code: the account was not found on delete ({@code DELACC} L350). */
	private static final String FAIL_ACCOUNT_NOT_FOUND = "1";

	/** COBOL fail code: the physical delete failed ({@code DELACC} L447). */
	private static final String FAIL_DELETE_ERROR = "3";

	/** Success flag value (COBOL {@code COMM-SUCCESS = 'Y'}). */
	private static final String FLAG_SUCCESS = "Y";

	/** Failure flag value (COBOL {@code COMM-SUCCESS = 'N'}). */
	private static final String FLAG_FAILURE = "N";

	/** Mocked account repository (create, read, list, count-by-customer, delete). */
	@Mock
	private AccountRepository accountRepository;

	/** Mocked account-control repository (resolves the highest-account sentinel). */
	@Mock
	private AccountControlRepository accountControlRepository;

	/** Mocked customer repository (owning-customer existence check only). */
	@Mock
	private CustomerRepository customerRepository;

	/** Mocked append-only PROCTRAN audit-log repository. */
	@Mock
	private ProcessedTransactionRepository processedTransactionRepository;

	/** Mocked gap-free account-number allocator (asserted never-called on early create failures). */
	@Mock
	private IdentityService identityService;

	/**
	 * System under test &mdash; constructed by Mockito via constructor injection
	 * with the five mocked collaborators ({@code new AccountService(
	 * accountRepository, accountControlRepository, customerRepository,
	 * processedTransactionRepository, identityService)}).
	 */
	@InjectMocks
	private AccountService accountService;

	// ------------------------------------------------------------------
	// Fixture builders
	// ------------------------------------------------------------------

	/**
	 * Builds a fully populated {@link Account} fixture keyed under
	 * {@link #SORT_CODE}. Every field the service reads when projecting an account
	 * onto a response envelope (both balances, the interest rate, the opening and
	 * statement dates, the overdraft limit, the type and the owning customer
	 * number) is populated so that the enquiry, list and delete response builders
	 * never dereference a {@code null}.
	 *
	 * @param accountNumber    the eight-digit, zero-padded account number
	 * @param accountType      the plain account-type string (for example
	 *                         {@code "CURRENT"} or {@code "SAVING"})
	 * @param availableBalance the available (cleared) balance as a scale-2 string
	 * @param actualBalance    the actual (pending) balance as a scale-2 string
	 * @param overdraftLimit   the overdraft limit in whole pounds
	 * @return the populated account fixture
	 */
	private static Account account(String accountNumber, String accountType,
			String availableBalance, String actualBalance, int overdraftLimit)
	{
		Account account = new Account();
		account.setId(new AccountId(SORT_CODE, accountNumber));
		account.setCustomerNumber(CUSTOMER_NUMBER);
		account.setAccountType(accountType);
		account.setInterestRate(new BigDecimal("1.00"));
		account.setOpened(LocalDate.of(2023, 1, 1));
		account.setOverdraftLimit(overdraftLimit);
		account.setLastStatementDate(LocalDate.of(2023, 1, 1));
		account.setNextStatementDate(LocalDate.of(2023, 1, 31));
		account.setAvailableBalance(new BigDecimal(availableBalance));
		account.setActualBalance(new BigDecimal(actualBalance));
		return account;
	}

	/**
	 * Builds an owning-customer fixture. Only its presence (or absence) matters to
	 * the service &mdash; {@code createAccount} and {@code listAccountsByCustomer}
	 * test the {@link Optional} for emptiness only &mdash; so a default instance
	 * suffices.
	 *
	 * @return a customer fixture
	 */
	private static Customer customer()
	{
		return new Customer();
	}

	/**
	 * Builds an {@link AccountControl} counter-row fixture carrying the given
	 * {@code lastAccountNumber}, used to resolve the {@code INQACC}
	 * highest-account ({@value #HIGHEST_ACCOUNT_SENTINEL}) sentinel.
	 *
	 * @param lastAccountNumber the highest allocated account number
	 * @return the populated control-row fixture
	 */
	private static AccountControl accountControl(long lastAccountNumber)
	{
		AccountControl control = new AccountControl();
		control.setSortCode(SORT_CODE);
		control.setLastAccountNumber(lastAccountNumber);
		return control;
	}

	/**
	 * Builds a {@code creacc} request form. The customer number is supplied as a
	 * display-numeric string (the service parses and re-pads it); the account
	 * type is the parsed {@link AccountType} enum (a {@code null} value models an
	 * invalid/blank inbound type).
	 *
	 * @param custNumber     the display-numeric customer number
	 * @param accountType    the parsed account type ({@code null} &rarr; invalid)
	 * @param overdraftLimit the requested overdraft limit
	 * @param interestRate   the requested interest rate
	 * @return the populated {@link CreateAccountForm}
	 */
	private static CreateAccountForm createForm(String custNumber,
			AccountType accountType, Integer overdraftLimit,
			BigDecimal interestRate)
	{
		CreateAccountForm form = new CreateAccountForm();
		form.setCustNumber(custNumber);
		form.setAccountType(accountType);
		form.setOverdraftLimit(overdraftLimit);
		form.setInterestRate(interestRate);
		return form;
	}

	/**
	 * Builds an {@code updacc} request form. The account type is the parsed
	 * {@link AccountType} enum (a {@code null} value models an invalid/blank
	 * inbound type).
	 *
	 * @param acctNumber   the account number to update
	 * @param acctType     the new account type ({@code null} &rarr; invalid)
	 * @param interestRate the new interest rate
	 * @param overdraft    the new overdraft limit
	 * @return the populated {@link UpdateAccountForm}
	 */
	private static UpdateAccountForm updateForm(int acctNumber,
			AccountType acctType, BigDecimal interestRate, Integer overdraft)
	{
		UpdateAccountForm form = new UpdateAccountForm();
		form.setCustNumber(CUSTOMER_NUMBER);
		form.setAcctNumber(acctNumber);
		form.setAcctType(acctType);
		form.setAcctInterestRate(interestRate);
		form.setAcctOverdraft(overdraft);
		return form;
	}

	/**
	 * Captures the single {@link Account} persisted by the service and returns it
	 * for field assertions.
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
	 * Captures the single {@link ProcessedTransaction} appended by the service and
	 * returns it for type-code/amount assertions.
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

	// ==================================================================
	// CREACC (F-007) — create account: strict five-step ordering, exact
	// fail codes, and the allocation-is-last invariant.
	// ==================================================================

	/**
	 * Step 1 &mdash; the owning customer must exist. When the customer lookup is
	 * empty the create fails with code {@code '1'} and, crucially, the account
	 * number is <strong>never</strong> allocated and nothing is persisted
	 * (allocation is the last step).
	 */
	@Test
	@DisplayName("create: owning customer absent fails '1' and never allocates a number")
	void create_customerNotFound_failCode1_andNeverAllocates()
	{
		when(customerRepository.findById(any(CustomerId.class)))
				.thenReturn(Optional.empty());

		assertThatThrownBy(() -> accountService.createAccount(
				createForm(CUSTOMER_NUMBER, AccountType.SAVING, 0,
						new BigDecimal("1.50"))))
				.isInstanceOf(BusinessRuleException.class)
				.hasFieldOrPropertyWithValue("failCode", FAIL_CUSTOMER_NOT_FOUND);

		verify(identityService, never()).allocateAccountNumber();
		verify(accountRepository, never()).save(any());
		verify(processedTransactionRepository, never()).save(any());
	}

	/**
	 * Step 2 &mdash; a persistence error while counting the customer's existing
	 * accounts fails with code {@code '9'}. The number is never allocated and
	 * nothing is persisted.
	 */
	@Test
	@DisplayName("create: account-count persistence error fails '9' and never allocates")
	void create_countError_failCode9()
	{
		when(customerRepository.findById(any(CustomerId.class)))
				.thenReturn(Optional.of(customer()));
		when(accountRepository.countByIdSortCodeAndCustomerNumber(anyString(),
				anyString()))
				.thenThrow(new DataIntegrityViolationException("simulated count error"));

		assertThatThrownBy(() -> accountService.createAccount(
				createForm(CUSTOMER_NUMBER, AccountType.SAVING, 0,
						new BigDecimal("1.50"))))
				.isInstanceOf(BusinessRuleException.class)
				.hasFieldOrPropertyWithValue("failCode", FAIL_COUNT_ERROR);

		verify(identityService, never()).allocateAccountNumber();
		verify(accountRepository, never()).save(any());
		verify(processedTransactionRepository, never()).save(any());
	}

	/**
	 * Step 3 &mdash; a customer that already holds the maximum of ten accounts
	 * (count {@code == MAX}) is rejected with code {@code '8'}; the number is
	 * never allocated. Pins the {@code >=} direction of the ceiling together with
	 * {@link #create_maxAccountsBoundary_ninthAllowed()}.
	 */
	@Test
	@DisplayName("create: ten accounts already held fails '8' (== MAX) and never allocates")
	void create_maxAccountsReached_failCode8()
	{
		when(customerRepository.findById(any(CustomerId.class)))
				.thenReturn(Optional.of(customer()));
		when(accountRepository.countByIdSortCodeAndCustomerNumber(anyString(),
				anyString()))
				.thenReturn((long) BankConstants.MAX_ACCOUNTS_PER_CUSTOMER);

		assertThatThrownBy(() -> accountService.createAccount(
				createForm(CUSTOMER_NUMBER, AccountType.SAVING, 0,
						new BigDecimal("1.50"))))
				.isInstanceOf(BusinessRuleException.class)
				.hasFieldOrPropertyWithValue("failCode", FAIL_TOO_MANY_ACCOUNTS);

		verify(identityService, never()).allocateAccountNumber();
		verify(accountRepository, never()).save(any());
		verify(processedTransactionRepository, never()).save(any());
	}

	/**
	 * Boundary &mdash; a customer holding exactly nine accounts (one below the
	 * ceiling) may create the tenth: the create PROCEEDS, the number is allocated,
	 * and the account is saved. Confirms the ceiling is {@code >= 10}, not
	 * {@code > 10}.
	 */
	@Test
	@DisplayName("create: ninth-to-tenth account is permitted (count 9, boundary)")
	void create_maxAccountsBoundary_ninthAllowed()
	{
		when(customerRepository.findById(any(CustomerId.class)))
				.thenReturn(Optional.of(customer()));
		when(accountRepository.countByIdSortCodeAndCustomerNumber(anyString(),
				anyString()))
				.thenReturn((long) (BankConstants.MAX_ACCOUNTS_PER_CUSTOMER - 1));
		when(identityService.allocateAccountNumber()).thenReturn(12345678L);
		when(accountRepository.save(any(Account.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));

		CreateAccountJson result = accountService.createAccount(
				createForm(CUSTOMER_NUMBER, AccountType.SAVING, 250,
						new BigDecimal("1.50")));

		assertThat(result.getCreAcc().getCommSuccess()).isEqualTo(FLAG_SUCCESS);
		verify(identityService).allocateAccountNumber();
		verify(accountRepository).save(any(Account.class));
	}

	/**
	 * Step 4 &mdash; an invalid account type (modelled by a {@code null} parsed
	 * {@link AccountType}, the value the production form carries when the inbound
	 * literal is not one of the five valid types) fails with code {@code 'A'};
	 * the number is never allocated and nothing is persisted.
	 */
	@Test
	@DisplayName("create: invalid account type fails 'A' and never allocates")
	void create_invalidAccountType_failCodeA_andNeverAllocates()
	{
		when(customerRepository.findById(any(CustomerId.class)))
				.thenReturn(Optional.of(customer()));
		when(accountRepository.countByIdSortCodeAndCustomerNumber(anyString(),
				anyString()))
				.thenReturn(0L);

		assertThatThrownBy(() -> accountService.createAccount(
				createForm(CUSTOMER_NUMBER, null, 0, new BigDecimal("1.50"))))
				.isInstanceOf(BusinessRuleException.class)
				.hasFieldOrPropertyWithValue("failCode", FAIL_INVALID_TYPE);

		verify(identityService, never()).allocateAccountNumber();
		verify(accountRepository, never()).save(any());
		verify(processedTransactionRepository, never()).save(any());
	}

	/**
	 * Step 5 (happy path) &mdash; every validation passes, so the service
	 * allocates the number LAST, saves the account with the allocated number and
	 * the form's type/rate/overdraft, and appends a {@link TransactionType#OCA}
	 * account-create {@code PROCTRAN} row of amount {@code 0.00}. An
	 * {@link InOrder} assertion proves allocation happens AFTER the count check
	 * (the allocation-is-last invariant, F-007).
	 */
	@Test
	@DisplayName("create: happy path allocates last, saves the account, and appends an OCA row")
	void create_happyPath_allocatesLast_savesAccount_appendsOca()
	{
		when(customerRepository.findById(any(CustomerId.class)))
				.thenReturn(Optional.of(customer()));
		when(accountRepository.countByIdSortCodeAndCustomerNumber(anyString(),
				anyString()))
				.thenReturn(0L);
		when(identityService.allocateAccountNumber()).thenReturn(12345678L);
		when(accountRepository.save(any(Account.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));

		CreateAccountJson result = accountService.createAccount(
				createForm(CUSTOMER_NUMBER, AccountType.SAVING, 500,
						new BigDecimal("1.50")));

		// Success envelope.
		assertThat(result.getCreAcc().getCommSuccess()).isEqualTo(FLAG_SUCCESS);

		// The saved account carries the allocated number and the form's inputs.
		Account saved = captureSavedAccount();
		assertThat(saved.getId().getAccountNumber()).isEqualTo("12345678");
		assertThat(saved.getAccountType()).isEqualTo(AccountType.SAVING.name());
		assertThat(saved.getInterestRate().compareTo(new BigDecimal("1.50")))
				.isZero();
		assertThat(saved.getOverdraftLimit()).isEqualTo(500);

		// The audit row is an OCA (branch/web create) of amount 0.00.
		ProcessedTransaction proc = captureSavedTransaction();
		assertThat(proc.getTypeCode()).isEqualTo(TransactionType.OCA);
		assertThat(proc.getAmount().compareTo(new BigDecimal("0.00"))).isZero();

		// Allocation-is-last: the number is allocated AFTER the account count.
		InOrder inOrder = inOrder(accountRepository, identityService);
		inOrder.verify(accountRepository)
				.countByIdSortCodeAndCustomerNumber(anyString(), anyString());
		inOrder.verify(identityService).allocateAccountNumber();
		inOrder.verify(accountRepository).save(any(Account.class));
	}

	/**
	 * Step 5 (insert failure) &mdash; every validation passes and a number is
	 * allocated, but the account insert raises a Spring
	 * {@link DataIntegrityViolationException} (a {@code DataAccessException}
	 * subclass). The service translates it to fail code {@code '7'}, and because
	 * the PROCTRAN append is downstream of the failed save, no audit row leaks
	 * through (the surrounding transaction &mdash; including the consumed counter
	 * &mdash; rolls back).
	 */
	@Test
	@DisplayName("create: a persistence error on the account insert fails '7'")
	void create_insertError_failCode7()
	{
		when(customerRepository.findById(any(CustomerId.class)))
				.thenReturn(Optional.of(customer()));
		when(accountRepository.countByIdSortCodeAndCustomerNumber(anyString(),
				anyString()))
				.thenReturn(0L);
		when(identityService.allocateAccountNumber()).thenReturn(12345678L);
		when(accountRepository.save(any(Account.class)))
				.thenThrow(new DataIntegrityViolationException("simulated insert error"));

		assertThatThrownBy(() -> accountService.createAccount(
				createForm(CUSTOMER_NUMBER, AccountType.SAVING, 0,
						new BigDecimal("1.50"))))
				.isInstanceOf(BusinessRuleException.class)
				.hasFieldOrPropertyWithValue("failCode", FAIL_INSERT_ERROR);

		verify(processedTransactionRepository, never()).save(any());
	}

	// ==================================================================
	// INQACC (F-009) — inquire a single account (read-only). The
	// highest-account sentinel reads the control row, never a MAX scan.
	// ==================================================================

	/**
	 * A normal account number returns the account's details with success flag
	 * {@code 'Y'}, including both independent balances asserted via
	 * {@code compareTo}. The control row is never consulted for a non-sentinel
	 * number.
	 */
	@Test
	@DisplayName("inquire: an existing account returns its details with success 'Y'")
	void inquire_normalAccount_returnsDetails()
	{
		when(accountRepository.findById(any(AccountId.class)))
				.thenReturn(Optional.of(
						account(ACCOUNT_NUMBER, "CURRENT", "250.00", "200.00", 100)));

		AccountEnquiryJson result = accountService.inquireAccount(1L);

		assertThat(result.getInqaccCommarea().getInqaccSuccess())
				.isEqualTo(FLAG_SUCCESS);
		assertThat(result.getInqaccCommarea().getInqaccAvailableBalance()
				.compareTo(new BigDecimal("250.00"))).isZero();
		assertThat(result.getInqaccCommarea().getInqaccActualBalance()
				.compareTo(new BigDecimal("200.00"))).isZero();
	}

	/**
	 * The {@value #HIGHEST_ACCOUNT_SENTINEL} sentinel resolves the highest account
	 * by reading {@code lastAccountNumber} from the {@code account_control} row
	 * (a primary-key {@code findById}) and then fetching that one account by key.
	 * It must NOT perform a {@code MAX()}/{@code ORDER BY ... DESC} table scan
	 * &mdash; asserted by verifying the branch-wide scan finder is never called.
	 */
	@Test
	@DisplayName("inquire: sentinel 99999999 reads the control row's highest account (no MAX scan)")
	void inquire_highestSentinel99999999_readsControlRowNotMaxScan()
	{
		when(accountControlRepository.findById(SORT_CODE))
				.thenReturn(Optional.of(accountControl(1L)));
		when(accountRepository.findById(any(AccountId.class)))
				.thenReturn(Optional.of(
						account(ACCOUNT_NUMBER, "CURRENT", "250.00", "200.00", 100)));

		AccountEnquiryJson result = accountService
				.inquireAccount(HIGHEST_ACCOUNT_SENTINEL);

		assertThat(result.getInqaccCommarea().getInqaccSuccess())
				.isEqualTo(FLAG_SUCCESS);

		// The highest account is read from the control row, then fetched by key.
		verify(accountControlRepository).findById(SORT_CODE);
		verify(accountRepository).findById(any(AccountId.class));
		// No MAX()/ORDER BY ... DESC branch-wide scan is ever used.
		verify(accountRepository, never())
				.findByIdSortCodeOrderByIdAccountNumberAsc(anyString());
	}

	/**
	 * A missing account is a soft outcome: the enquiry returns a populated
	 * envelope flagged {@code InqaccSuccess = "N"} with no hard fail code (no
	 * {@link BusinessRuleException} is thrown), reproducing {@code INQACC}'s
	 * {@code MOVE 'N' TO INQACC-SUCCESS}.
	 */
	@Test
	@DisplayName("inquire: a missing account yields success flag 'N' (soft, no throw)")
	void inquire_notFound_successFlagN()
	{
		when(accountRepository.findById(any(AccountId.class)))
				.thenReturn(Optional.empty());

		AccountEnquiryJson result = accountService.inquireAccount(2L);

		assertThat(result.getInqaccCommarea().getInqaccSuccess())
				.isEqualTo(FLAG_FAILURE);
	}

	// ==================================================================
	// INQACCCU (F-010) — list a customer's accounts (read-only),
	// capped at twenty entries.
	// ==================================================================

	/**
	 * An absent owning customer fails the list with code {@code '1'}; the
	 * customer-account finder is never reached.
	 */
	@Test
	@DisplayName("list: owning customer absent fails '1'")
	void list_customerNotFound_failCode1()
	{
		when(customerRepository.findById(any(CustomerId.class)))
				.thenReturn(Optional.empty());

		assertThatThrownBy(() -> accountService.listAccountsByCustomer(123L))
				.isInstanceOf(BusinessRuleException.class)
				.hasFieldOrPropertyWithValue("failCode", FAIL_CUSTOMER_NOT_FOUND);

		verify(accountRepository, never())
				.findByIdSortCodeAndCustomerNumberOrderByIdAccountNumberAsc(
						anyString(), anyString());
	}

	/**
	 * A customer that owns more than twenty accounts has the rendered list
	 * truncated to exactly twenty entries ({@code INQACCCU} {@code OCCURS 1 TO 20};
	 * the COBOL caps the table at twenty). Twenty-five accounts are supplied; the
	 * result holds exactly twenty.
	 */
	@Test
	@DisplayName("list: more than twenty accounts are truncated to exactly twenty")
	void list_capsAt20()
	{
		when(customerRepository.findById(any(CustomerId.class)))
				.thenReturn(Optional.of(customer()));
		List<Account> twentyFive = new ArrayList<>();
		for (int i = 1; i <= 25; i++)
		{
			twentyFive.add(account(String.format("%08d", i), "CURRENT",
					"100.00", "100.00", 0));
		}
		when(accountRepository
				.findByIdSortCodeAndCustomerNumberOrderByIdAccountNumberAsc(
						anyString(), anyString()))
				.thenReturn(twentyFive);

		ListAccountsJson result = accountService.listAccountsByCustomer(123L);

		assertThat(result.getInqAcccz().getAccountDetails()).hasSize(20);
		assertThat(result.getInqAcccz().getCustomerFound()).isEqualTo(FLAG_SUCCESS);
	}

	// ==================================================================
	// UPDACC (F-012) — restricted update: only type/rate/overdraft, never
	// the balances, and NEVER a PROCTRAN record.
	// ==================================================================

	/**
	 * A successful update changes only the account type, interest rate and
	 * overdraft limit; both independent balances are left exactly as they were,
	 * and NO {@code PROCTRAN} audit row is written. The captured saved account
	 * proves both halves of the rule.
	 */
	@Test
	@DisplayName("update: changes only type/rate/overdraft, leaves both balances untouched, writes no PROCTRAN")
	void update_changesTypeRateOverdraftOnly_balancesUntouched_noProctran()
	{
		when(accountRepository.findById(any(AccountId.class)))
				.thenReturn(Optional.of(
						account(ACCOUNT_NUMBER, "CURRENT", "200.00", "180.00", 100)));
		when(accountRepository.save(any(Account.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));

		UpdateAccountJson result = accountService.updateAccount(
				updateForm(1, AccountType.SAVING, new BigDecimal("2.50"), 500));

		assertThat(result.getUpdAcc().getCommSuccess()).isEqualTo(FLAG_SUCCESS);

		Account saved = captureSavedAccount();
		// The three permitted fields are updated...
		assertThat(saved.getAccountType()).isEqualTo(AccountType.SAVING.name());
		assertThat(saved.getInterestRate().compareTo(new BigDecimal("2.50")))
				.isZero();
		assertThat(saved.getOverdraftLimit()).isEqualTo(500);
		// ...but BOTH balances are left exactly as they were (never touched).
		assertThat(saved.getAvailableBalance().compareTo(new BigDecimal("200.00")))
				.isZero();
		assertThat(saved.getActualBalance().compareTo(new BigDecimal("180.00")))
				.isZero();

		// UPDACC writes no PROCTRAN audit row.
		verify(processedTransactionRepository, never()).save(any());
	}

	/**
	 * A missing account is rejected and nothing is persisted.
	 *
	 * <p><strong>ALIGN:</strong> the finalized {@code UPDACC} service THROWS
	 * {@link BusinessRuleException} (fail code {@code '1'}) for a missing account;
	 * the controller renders that as {@code CommSuccess = "N"} (the
	 * {@code UpdaccJson} envelope carries no fail-code field). At the service
	 * level &mdash; the unit under test here &mdash; we therefore assert the throw,
	 * not an envelope flag.</p>
	 */
	@Test
	@DisplayName("update: a missing account is rejected and never saved")
	void update_accountNotFound_isRejected_andNeverSaves()
	{
		when(accountRepository.findById(any(AccountId.class)))
				.thenReturn(Optional.empty());

		assertThatThrownBy(() -> accountService.updateAccount(
				updateForm(2, AccountType.SAVING, new BigDecimal("2.50"), 500)))
				.isInstanceOf(BusinessRuleException.class)
				.hasFieldOrPropertyWithValue("failCode", FAIL_ACCOUNT_NOT_FOUND);

		verify(accountRepository, never()).save(any());
		verify(processedTransactionRepository, never()).save(any());
	}

	// ==================================================================
	// DELACC (F-013) — delete: capture the terminal actual balance BEFORE
	// removal, append an ODA close record, then physically remove the row.
	// ==================================================================

	/**
	 * A successful delete captures the terminal <em>actual</em> balance BEFORE the
	 * account row is removed and carries it as the amount of the account-close
	 * {@link TransactionType#ODA} {@code PROCTRAN} record. An {@link InOrder}
	 * assertion proves the ordering: the account is read, then physically
	 * deleted, and only then is the audit row appended.
	 */
	@Test
	@DisplayName("delete: captures the terminal actual balance before deleting and appends an ODA row")
	void delete_capturesTerminalActualBalanceBeforeDelete_appendsOda()
	{
		Account existing = account(ACCOUNT_NUMBER, "CURRENT", "400.00", "321.45",
				100);
		when(accountRepository.findById(any(AccountId.class)))
				.thenReturn(Optional.of(existing));

		DeleteAccountJson result = accountService.deleteAccount(1L);

		// The account-close audit row is an ODA carrying the terminal actual balance.
		ProcessedTransaction proc = captureSavedTransaction();
		assertThat(proc.getTypeCode()).isEqualTo(TransactionType.ODA);
		assertThat(proc.getAmount().compareTo(new BigDecimal("321.45"))).isZero();

		// The response echoes the terminal actual balance and flags delete success.
		assertThat(result.getDelAcc().getDelaccDelSuccess()).isEqualTo(FLAG_SUCCESS);
		assertThat(result.getDelAcc().getDelaccActualBalance()
				.compareTo(new BigDecimal("321.45"))).isZero();

		// Read BEFORE delete BEFORE audit: the balance is captured from the row
		// read, the row is then physically removed, and only then is the audit
		// appended (PROCTRAN is append-only).
		InOrder inOrder = inOrder(accountRepository, processedTransactionRepository);
		inOrder.verify(accountRepository).findById(any(AccountId.class));
		inOrder.verify(accountRepository).delete(any(Account.class));
		inOrder.verify(processedTransactionRepository)
				.save(any(ProcessedTransaction.class));
	}

	/**
	 * A missing account fails the delete with code {@code '1'}; the row is never
	 * deleted and no audit row is appended.
	 */
	@Test
	@DisplayName("delete: a missing account fails '1' and never deletes or audits")
	void delete_notFound_failCode1()
	{
		when(accountRepository.findById(any(AccountId.class)))
				.thenReturn(Optional.empty());

		assertThatThrownBy(() -> accountService.deleteAccount(2L))
				.isInstanceOf(BusinessRuleException.class)
				.hasFieldOrPropertyWithValue("failCode", FAIL_ACCOUNT_NOT_FOUND);

		verify(accountRepository, never()).delete(any());
		verify(processedTransactionRepository, never()).save(any());
	}

	/**
	 * A persistence error on the physical delete fails with code {@code '3'};
	 * because the audit append is downstream of the failed delete, no PROCTRAN
	 * row leaks through.
	 */
	@Test
	@DisplayName("delete: a persistence error on the physical delete fails '3'")
	void delete_deleteError_failCode3()
	{
		when(accountRepository.findById(any(AccountId.class)))
				.thenReturn(Optional.of(
						account(ACCOUNT_NUMBER, "CURRENT", "400.00", "321.45", 100)));
		doThrow(new DataIntegrityViolationException("simulated delete error"))
				.when(accountRepository).delete(any(Account.class));

		assertThatThrownBy(() -> accountService.deleteAccount(1L))
				.isInstanceOf(BusinessRuleException.class)
				.hasFieldOrPropertyWithValue("failCode", FAIL_DELETE_ERROR);

		verify(processedTransactionRepository, never()).save(any());
	}
}
