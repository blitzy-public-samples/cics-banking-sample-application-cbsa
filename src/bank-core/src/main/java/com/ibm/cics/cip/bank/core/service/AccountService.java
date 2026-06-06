/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.core.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.ibm.cics.cip.bank.core.constants.BankConstants;
import com.ibm.cics.cip.bank.core.domain.AccountType;
import com.ibm.cics.cip.bank.core.domain.TransactionType;
import com.ibm.cics.cip.bank.core.dto.DtoFormat;
import com.ibm.cics.cip.bank.core.dto.accountenquiry.AccountEnquiryJson;
import com.ibm.cics.cip.bank.core.dto.accountenquiry.InqaccJson;
import com.ibm.cics.cip.bank.core.dto.common.CommKey;
import com.ibm.cics.cip.bank.core.dto.createaccount.CreaccJson;
import com.ibm.cics.cip.bank.core.dto.createaccount.CreateAccountForm;
import com.ibm.cics.cip.bank.core.dto.createaccount.CreateAccountJson;
import com.ibm.cics.cip.bank.core.dto.deleteaccount.DelaccJson;
import com.ibm.cics.cip.bank.core.dto.deleteaccount.DeleteAccountJson;
import com.ibm.cics.cip.bank.core.dto.listaccounts.AccountDetails;
import com.ibm.cics.cip.bank.core.dto.listaccounts.InqAccczJson;
import com.ibm.cics.cip.bank.core.dto.listaccounts.ListAccountsJson;
import com.ibm.cics.cip.bank.core.dto.updateaccount.UpdaccJson;
import com.ibm.cics.cip.bank.core.dto.updateaccount.UpdateAccountForm;
import com.ibm.cics.cip.bank.core.dto.updateaccount.UpdateAccountJson;
import com.ibm.cics.cip.bank.core.entity.Account;
import com.ibm.cics.cip.bank.core.entity.AccountControl;
import com.ibm.cics.cip.bank.core.entity.AccountId;
import com.ibm.cics.cip.bank.core.entity.Customer;
import com.ibm.cics.cip.bank.core.entity.CustomerId;
import com.ibm.cics.cip.bank.core.entity.ProcessedTransaction;
import com.ibm.cics.cip.bank.core.entity.ProcessedTransactionId;
import com.ibm.cics.cip.bank.core.exception.BusinessRuleException;
import com.ibm.cics.cip.bank.core.repository.AccountControlRepository;
import com.ibm.cics.cip.bank.core.repository.AccountRepository;
import com.ibm.cics.cip.bank.core.repository.CustomerRepository;
import com.ibm.cics.cip.bank.core.repository.ProcessedTransactionRepository;
import com.ibm.cics.cip.bank.core.util.BankFormat;

/**
 * Account business service &mdash; the authoritative pure-Java port of five
 * COBOL programs that together own every account-level operation: {@code CREACC}
 * (create, feature&nbsp;F-007), {@code INQACC} (inquire, F-009),
 * {@code INQACCCU} (list by customer, F-010), {@code UPDACC} (restricted update,
 * F-012), and {@code DELACC} (delete, F-013). The COBOL is the behavioural
 * specification of record: its single-character fail codes, its strict
 * validation ordering, its dual-balance semantics, and its append-only audit
 * rules are reproduced exactly (AAP&nbsp;&sect;0.6, &sect;0.7).
 *
 * <h2>Transaction boundary</h2>
 * <p>Each mutating operation ({@link #createAccount(CreateAccountForm)},
 * {@link #updateAccount(UpdateAccountForm)}, {@link #deleteAccount(long)}) runs
 * inside a single {@link Transactional @Transactional} boundary
 * ({@link Propagation#REQUIRED}, {@link Isolation#READ_COMMITTED}) &mdash; the
 * Spring rendering of the COBOL {@code EXEC CICS SYNCPOINT}/{@code ROLLBACK}
 * pair. The read paths ({@link #inquireAccount(long)},
 * {@link #listAccountsByCustomer(long)}) are {@code @Transactional(readOnly = true)}.
 * An unchecked {@link BusinessRuleException} unwinds the enclosing transaction
 * on any fail code.</p>
 *
 * <h2>Gap-free identity (BIND)</h2>
 * <p>{@code CREACC} allocates the new account number <em>after</em> every
 * validation has passed, so a rejected create rolls back nothing it consumed.
 * This service preserves that ordering precisely: it delegates allocation to
 * {@link IdentityService#allocateAccountNumber()} (which increments the
 * {@code account_control} counter row under a {@code PESSIMISTIC_WRITE} lock
 * inside this same transaction) as the <strong>last</strong> step before the
 * insert. A subsequent failure therefore rolls back the consumed counter
 * automatically &mdash; no database {@code IDENTITY}/{@code SEQUENCE} is used,
 * because neither can undo a consumed value (ADR-003).</p>
 *
 * <h2>Dual balances (BIND)</h2>
 * <p>{@code availableBalance} (cleared funds) and {@code actualBalance} (pending
 * funds) are independent values and are <strong>never collapsed</strong>. On
 * create both are initialised to {@code 0.00}; on delete the terminal
 * {@code actualBalance} is captured <em>before</em> the row is removed and
 * carried as the account-close {@code PROCTRAN} amount.</p>
 *
 * <h2>Money</h2>
 * <p>The interest rate and both balances are {@link BigDecimal} normalised to
 * scale&nbsp;2 with {@link RoundingMode#HALF_UP}; no binary floating-point type
 * appears anywhere in this service.</p>
 *
 * <h2>Audit (PROCTRAN) type codes</h2>
 * <p>Account create appends a {@link TransactionType#OCA} row with amount
 * {@code 0.00} ({@code CREACC} L966-967); account delete appends a
 * {@link TransactionType#ODA} row whose amount is the captured terminal actual
 * balance ({@code DELACC} L513/L519). Update and inquire/list write
 * <strong>no</strong> {@code PROCTRAN} record.</p>
 *
 * <h2>Result / failure convention</h2>
 * <p>Following the sibling {@code PaymentService} pattern, each method builds and
 * returns the fully populated <em>success</em> response envelope and throws a
 * {@link BusinessRuleException} carrying the verbatim COBOL fail code on a
 * business rejection; the owning controller catches it and shapes the
 * endpoint-specific failure envelope. The single exception is
 * {@link #inquireAccount(long)}, whose plain &quot;not-found&quot; outcome is a
 * soft response with {@code InqaccSuccess = "N"} (and no hard fail code), exactly
 * as {@code INQACC} sets {@code INQACC-SUCCESS = 'N'}.</p>
 *
 * <h2>Fail codes</h2>
 * <ul>
 *   <li><strong>create</strong> &mdash; {@code 1} owning customer absent,
 *       {@code 9} account-count step failed, {@code 8} customer already holds the
 *       maximum of ten accounts, {@code A} invalid account type, {@code 7}
 *       persistence failure on insert;</li>
 *   <li><strong>list</strong> &mdash; {@code 1} customer not found;</li>
 *   <li><strong>delete</strong> &mdash; {@code 1} account not found, {@code 3}
 *       delete failed, {@code HRAC} (abend marker) on a read SQL error.</li>
 * </ul>
 */
@Service
public class AccountService
{

	/** Fail code &mdash; the owning customer does not exist ({@code CREACC} L317). */
	private static final String FAIL_CUSTOMER_NOT_FOUND = "1";

	/** Fail code &mdash; the account-count step failed ({@code CREACC} L340). */
	private static final String FAIL_COUNT_ERROR = "9";

	/** Fail code &mdash; the customer already holds the maximum number of accounts ({@code CREACC} L347). */
	private static final String FAIL_TOO_MANY_ACCOUNTS = "8";

	/** Fail code &mdash; the account type is not one of the five valid values ({@code CREACC} L1224). */
	private static final String FAIL_INVALID_TYPE = "A";

	/** Fail code &mdash; persistence failure when inserting the account row ({@code CREACC} L862). */
	private static final String FAIL_INSERT_ERROR = "7";

	/** Fail code &mdash; the account was not found on delete ({@code DELACC} L350, {@code SQLCODE +100}). */
	private static final String FAIL_ACCOUNT_NOT_FOUND = "1";

	/** Fail code &mdash; the physical delete failed ({@code DELACC} L447). */
	private static final String FAIL_DELETE_ERROR = "3";

	/** Abend marker &mdash; a read SQL error on delete ({@code DELACC} L282/L315/L339). */
	private static final String ABEND_READ_ERROR = "HRAC";

	/** Success flag value (COBOL {@code COMM-SUCCESS = 'Y'}). */
	private static final String FLAG_SUCCESS = "Y";

	/** Failure flag value (COBOL {@code COMM-SUCCESS = 'N'}). */
	private static final String FLAG_FAILURE = "N";

	/** Fail-code value written to the create envelope on success ({@code CreateAccountController} parity). */
	private static final String SUCCESS_FAIL_CODE = "";

	/** Fail-code value denoting &quot;no failure&quot; ({@code "0"}); the {@code INQACCCU} initial value (L197) and the {@code DelAccFailCd} success value. */
	private static final String FAIL_NONE = "0";

	/** Scale (number of decimal places) for every monetary and rate value. */
	private static final int MONEY_SCALE = 2;

	/** Maximum number of accounts returned by {@code INQACCCU} ({@code OCCURS 1 TO 20}). */
	private static final int MAX_LISTED_ACCOUNTS = 20;

	/** Number of days added to the opening date to derive the next statement date ({@code CREACC} L807-813). */
	private static final int STATEMENT_CYCLE_DAYS = 30;

	/** Fixed width (characters) of the {@code PROCTRAN} description field. */
	private static final int DESCRIPTION_WIDTH = 40;

	/** Sentinel account number requesting the highest existing account ({@code INQACC} L218). */
	private static final long HIGHEST_ACCOUNT_SENTINEL = 99999999L;

	/** Account persistence (create, read, list, delete, count-by-customer). */
	private final AccountRepository accountRepository;

	/** Account-control row access used to resolve the highest-account sentinel. */
	private final AccountControlRepository accountControlRepository;

	/** Customer persistence used DIRECTLY for the owning-customer existence check (avoids a customer&harr;account service cycle). */
	private final CustomerRepository customerRepository;

	/** Append-only {@code PROCTRAN} audit persistence. */
	private final ProcessedTransactionRepository processedTransactionRepository;

	/** Gap-free account-number allocator (counter-row increment under {@code PESSIMISTIC_WRITE}). */
	private final IdentityService identityService;

	/**
	 * Constructs the service with its collaborating repositories and the
	 * identity allocator. All collaborators are injected by constructor for
	 * testability and immutability; the customer service bean is deliberately
	 * <strong>not</strong> injected (the owning-customer check goes straight to
	 * {@link CustomerRepository}) so the customer&harr;account dependency edge
	 * stays one-directional.
	 *
	 * @param accountRepository              the account repository
	 * @param accountControlRepository       the account-control repository
	 * @param customerRepository             the customer repository (existence check only)
	 * @param processedTransactionRepository the {@code PROCTRAN} repository
	 * @param identityService                the account-number allocator
	 */
	public AccountService(AccountRepository accountRepository,
			AccountControlRepository accountControlRepository,
			CustomerRepository customerRepository,
			ProcessedTransactionRepository processedTransactionRepository,
			IdentityService identityService)
	{
		this.accountRepository = accountRepository;
		this.accountControlRepository = accountControlRepository;
		this.customerRepository = customerRepository;
		this.processedTransactionRepository = processedTransactionRepository;
		this.identityService = identityService;
	}

	// ------------------------------------------------------------------
	// CREACC (F-007) — create account
	// ------------------------------------------------------------------

	/**
	 * Creates a new account for an existing customer, reproducing
	 * {@code CREACC}'s strict five-step ordering so that any validation failure
	 * rolls back before the account number is consumed (gap-free identity,
	 * ADR-003):
	 *
	 * <ol>
	 *   <li>the owning customer must exist &mdash; else fail {@code 1}
	 *       ({@code CREACC} L317);</li>
	 *   <li>count the customer's existing accounts &mdash; a persistence error
	 *       fails {@code 9} ({@code CREACC} L340);</li>
	 *   <li>reject when that count is already at the maximum of ten &mdash; fail
	 *       {@code 8} ({@code CREACC} L347);</li>
	 *   <li>the account type must be valid &mdash; else fail {@code A}
	 *       ({@code CREACC} L1224);</li>
	 *   <li>allocate the next account number (LAST, so a rollback restores the
	 *       counter), persist the account &mdash; a persistence error fails
	 *       {@code 7} ({@code CREACC} L862) &mdash; then append the
	 *       {@link TransactionType#OCA} {@code PROCTRAN} row (amount
	 *       {@code 0.00}).</li>
	 * </ol>
	 *
	 * <p>The new account opens today with both balances at {@code 0.00}, its
	 * last statement dated today and its next statement {@value #STATEMENT_CYCLE_DAYS}
	 * days later ({@code CREACC} L807-813).</p>
	 *
	 * @param form the create-account request input (customer number, account
	 *             type, overdraft limit, interest rate)
	 * @return the populated success envelope ({@code CommSuccess = "Y"})
	 * @throws BusinessRuleException with fail code {@code 1}, {@code 9},
	 *                               {@code 8}, {@code A}, or {@code 7}
	 */
	@Transactional(propagation = Propagation.REQUIRED,
			isolation = Isolation.READ_COMMITTED)
	public CreateAccountJson createAccount(CreateAccountForm form)
	{
		// STEP 1 — the owning customer must exist (CREACC L317, fail '1').
		long customerNumber = parseIdentifier(form.getCustNumber());
		String customerKey = BankFormat.customerNumber(customerNumber);
		Optional<Customer> owningCustomer = customerRepository
				.findById(new CustomerId(BankConstants.SORT_CODE, customerKey));
		if (owningCustomer.isEmpty())
		{
			throw new BusinessRuleException(FAIL_CUSTOMER_NOT_FOUND,
					"Customer " + customerKey + " does not exist");
		}

		// STEP 2 — count the customer's accounts (CREACC L340, fail '9' on error).
		long existingAccounts;
		try
		{
			existingAccounts = accountRepository
					.countByIdSortCodeAndCustomerNumber(BankConstants.SORT_CODE,
							customerKey);
		}
		catch (DataAccessException ex)
		{
			throw new BusinessRuleException(FAIL_COUNT_ERROR,
					"Failed to count accounts for customer " + customerKey, ex);
		}

		// STEP 3 — reject at the ten-account ceiling (CREACC L347, fail '8').
		if (existingAccounts >= BankConstants.MAX_ACCOUNTS_PER_CUSTOMER)
		{
			throw new BusinessRuleException(FAIL_TOO_MANY_ACCOUNTS,
					"Customer " + customerKey + " already holds the maximum of "
							+ BankConstants.MAX_ACCOUNTS_PER_CUSTOMER
							+ " accounts");
		}

		// STEP 4 — validate the account type (CREACC L1224, fail 'A'). The form
		// carries the parsed AccountType enum; a null value means the inbound
		// string was not one of the five valid literals.
		AccountType accountType = form.getAccountType();
		if (accountType == null)
		{
			throw new BusinessRuleException(FAIL_INVALID_TYPE,
					"Account type is not one of ISA, MORTGAGE, SAVING, CURRENT, "
							+ "LOAN");
		}

		// STEP 5 — allocate the number LAST, persist, then append PROCTRAN.
		long accountNumber = identityService.allocateAccountNumber();
		Account account = buildNewAccount(accountNumber, customerKey,
				accountType, form.getInterestRate(), form.getOverdraftLimit());
		Account saved;
		try
		{
			saved = accountRepository.save(account);
		}
		catch (DataAccessException ex)
		{
			throw new BusinessRuleException(FAIL_INSERT_ERROR,
					"Failed to insert account "
							+ account.getId().getAccountNumber(),
					ex);
		}
		appendCreateProcessedTransaction(saved);
		return buildCreateResponse(saved);
	}

	/**
	 * Builds the new {@link Account} entity from the validated inputs, applying
	 * the {@code CREACC} field defaults: opened today, last statement today,
	 * next statement {@value #STATEMENT_CYCLE_DAYS} days later, and both balances
	 * initialised to {@code 0.00}. The account type is stored as its upper-case
	 * COBOL literal (the enum constant name).
	 *
	 * @param accountNumber  the freshly allocated account number
	 * @param customerKey    the zero-padded ten-digit owning customer number
	 * @param accountType    the validated account type
	 * @param interestRate   the requested interest rate (normalised to scale 2)
	 * @param overdraftLimit the requested overdraft limit ({@code null} &rarr; 0)
	 * @return the populated, not-yet-persisted account entity
	 */
	private Account buildNewAccount(long accountNumber, String customerKey,
			AccountType accountType, BigDecimal interestRate,
			Integer overdraftLimit)
	{
		LocalDate opened = LocalDate.now();
		Account account = new Account();
		account.setId(new AccountId(BankConstants.SORT_CODE,
				BankFormat.accountNumber(accountNumber)));
		account.setCustomerNumber(customerKey);
		account.setAccountType(accountType.name());
		account.setInterestRate(scale2(interestRate));
		account.setOpened(opened);
		account.setOverdraftLimit(
				overdraftLimit == null ? Integer.valueOf(0) : overdraftLimit);
		account.setLastStatementDate(opened);
		account.setNextStatementDate(opened.plusDays(STATEMENT_CYCLE_DAYS));
		account.setAvailableBalance(scale2(BigDecimal.ZERO));
		account.setActualBalance(scale2(BigDecimal.ZERO));
		return account;
	}

	/**
	 * Builds the create-account success envelope from the persisted account,
	 * reproducing the frozen contract verbatim: the composite key as a
	 * {@link CommKey}, both zero balances, the opening and statement dates as
	 * eight-digit {@code DDMMYYYY} integers, {@code CommSuccess = "Y"}, and an
	 * empty fail code.
	 *
	 * @param account the persisted account
	 * @return the populated {@link CreateAccountJson} envelope
	 */
	private CreateAccountJson buildCreateResponse(Account account)
	{
		CreaccJson out = new CreaccJson();
		out.setCommAccType(account.getAccountType());
		out.setCommCustno(account.getCustomerNumber());
		out.setCommKey(new CommKey(
				Integer.parseInt(account.getId().getSortCode()),
				Long.parseLong(account.getId().getAccountNumber())));
		out.setCommInterestRate(account.getInterestRate());
		out.setCommOpened(DtoFormat.dateToInt(account.getOpened()));
		out.setCommOverdraftLimit(account.getOverdraftLimit() == null
				? Integer.valueOf(0)
				: account.getOverdraftLimit());
		out.setCommLastStatementDate(
				DtoFormat.dateToInt(account.getLastStatementDate()));
		out.setCommNextStatementDate(
				DtoFormat.dateToInt(account.getNextStatementDate()));
		out.setCommAvailableBalance(account.getAvailableBalance());
		out.setCommActualBalance(account.getActualBalance());
		out.setCommSuccess(FLAG_SUCCESS);
		out.setCommFailCode(SUCCESS_FAIL_CODE);
		return new CreateAccountJson(out);
	}

	// ------------------------------------------------------------------
	// INQACC (F-009) — inquire single account
	// ------------------------------------------------------------------

	/**
	 * Inquires on a single account, reproducing {@code INQACC}. The sentinel
	 * value {@value #HIGHEST_ACCOUNT_SENTINEL} requests the highest existing
	 * account: it is resolved from the control row's {@code LAST-ACCOUNT-NUMBER}
	 * ({@code INQACC} L218/L830) rather than a {@code MAX()} scan. A plain
	 * &quot;not found&quot; is not a hard failure &mdash; it returns a soft
	 * envelope with {@code InqaccSuccess = "N"}, exactly as {@code INQACC} sets
	 * {@code INQACC-SUCCESS = 'N'} (L756).
	 *
	 * @param accountNumber the account number to inquire on, or
	 *                      {@value #HIGHEST_ACCOUNT_SENTINEL} for the highest
	 *                      account
	 * @return the account-enquiry envelope; {@code InqaccSuccess = "Y"} when
	 *         found, {@code "N"} when not found
	 */
	@Transactional(readOnly = true)
	public AccountEnquiryJson inquireAccount(long accountNumber)
	{
		long effectiveNumber = accountNumber;
		if (accountNumber == HIGHEST_ACCOUNT_SENTINEL)
		{
			Optional<AccountControl> control = accountControlRepository
					.findBySortCodeForUpdate(BankConstants.SORT_CODE);
			if (control.isEmpty()
					|| control.get().getLastAccountNumber() == null)
			{
				return notFoundEnquiry(accountNumber);
			}
			effectiveNumber = control.get().getLastAccountNumber();
		}

		Optional<Account> account = accountRepository
				.findById(new AccountId(BankConstants.SORT_CODE,
						BankFormat.accountNumber(effectiveNumber)));
		if (account.isEmpty())
		{
			return notFoundEnquiry(effectiveNumber);
		}
		return buildEnquiryResponse(account.get());
	}

	/**
	 * Builds the &quot;not found&quot; enquiry envelope, echoing the requested
	 * account number and flagging {@code InqaccSuccess = "N"} (no hard fail
	 * code), per {@code INQACC}.
	 *
	 * @param accountNumber the account number that was not found
	 * @return the soft not-found enquiry envelope
	 */
	private AccountEnquiryJson notFoundEnquiry(long accountNumber)
	{
		AccountEnquiryJson response = new AccountEnquiryJson();
		InqaccJson commarea = response.getInqaccCommarea();
		commarea.setInqaccSortcode(Integer.parseInt(BankConstants.SORT_CODE));
		commarea.setInqaccAccno((int) accountNumber);
		commarea.setInqaccSuccess(FLAG_FAILURE);
		return response;
	}

	/**
	 * Maps a found {@link Account} onto the account-enquiry envelope: both
	 * independent balances, the dates as eight-digit {@code DDMMYYYY} integers,
	 * the interest rate, the type, and {@code InqaccSuccess = "Y"}.
	 *
	 * @param account the account to map
	 * @return the populated account-enquiry envelope
	 */
	private AccountEnquiryJson buildEnquiryResponse(Account account)
	{
		AccountEnquiryJson response = new AccountEnquiryJson();
		InqaccJson commarea = response.getInqaccCommarea();
		commarea.setInqaccCustno(
				Long.parseLong(account.getCustomerNumber().trim()));
		commarea.setInqaccSortcode(
				Integer.parseInt(account.getId().getSortCode()));
		commarea.setInqaccAccno(
				Integer.parseInt(account.getId().getAccountNumber()));
		commarea.setInqaccAccType(account.getAccountType());
		commarea.setInqaccInterestRate(account.getInterestRate());
		commarea.setInqaccOpened(DtoFormat.dateToInt(account.getOpened()));
		commarea.setInqaccOverdraft(account.getOverdraftLimit() == null
				? Integer.valueOf(0)
				: account.getOverdraftLimit());
		commarea.setInqaccLastStatementDate(
				DtoFormat.dateToInt(account.getLastStatementDate()));
		commarea.setInqaccNextStatementDate(
				DtoFormat.dateToInt(account.getNextStatementDate()));
		commarea.setInqaccAvailableBalance(account.getAvailableBalance());
		commarea.setInqaccActualBalance(account.getActualBalance());
		commarea.setInqaccSuccess(FLAG_SUCCESS);
		return response;
	}

	// ------------------------------------------------------------------
	// INQACCCU (F-010) — list accounts by customer
	// ------------------------------------------------------------------

	/**
	 * Lists every account owned by a customer, reproducing {@code INQACCCU}. The
	 * customer must exist &mdash; absence fails {@code 1} ({@code INQACCCU}
	 * L215-217). The result is ordered by account number and capped at
	 * {@value #MAX_LISTED_ACCOUNTS} entries ({@code OCCURS 1 TO 20 DEPENDING ON};
	 * {@code INQACCCU} L464). The number-of-accounts count is conveyed by the
	 * returned list size.
	 *
	 * @param customerNumber the owning customer number
	 * @return the populated list envelope ({@code CommSuccess = "Y"},
	 *         {@code CustomerFound = "Y"}) with up to twenty account details
	 * @throws BusinessRuleException with fail code {@code 1} when the customer
	 *                               does not exist
	 */
	@Transactional(readOnly = true)
	public ListAccountsJson listAccountsByCustomer(long customerNumber)
	{
		String customerKey = BankFormat.customerNumber(customerNumber);
		Optional<Customer> owningCustomer = customerRepository
				.findById(new CustomerId(BankConstants.SORT_CODE, customerKey));
		if (owningCustomer.isEmpty())
		{
			throw new BusinessRuleException(FAIL_CUSTOMER_NOT_FOUND,
					"Customer " + customerKey + " not found");
		}

		List<Account> accounts = accountRepository
				.findByIdSortCodeAndCustomerNumberOrderByIdAccountNumberAsc(
						BankConstants.SORT_CODE, customerKey);

		List<AccountDetails> details = new ArrayList<>();
		for (Account account : accounts)
		{
			if (details.size() >= MAX_LISTED_ACCOUNTS)
			{
				break;
			}
			details.add(toAccountDetails(account));
		}

		InqAccczJson commarea = new InqAccczJson();
		commarea.setCustomerNumber(customerKey);
		commarea.setAccountDetails(details);
		commarea.setCustomerFound(FLAG_SUCCESS);
		commarea.setCommSuccess(FLAG_SUCCESS);
		commarea.setCommFailCode(FAIL_NONE);
		return new ListAccountsJson(commarea);
	}

	/**
	 * Maps one {@link Account} onto a list element. The list envelope encodes the
	 * account dates as {@code String} values (via
	 * {@link DtoFormat#dateToString(LocalDate)}) and the account number as its
	 * zero-padded eight-character form.
	 *
	 * @param account the account to map
	 * @return the populated per-account detail element
	 */
	private AccountDetails toAccountDetails(Account account)
	{
		AccountDetails details = new AccountDetails();
		details.setCommCustno(account.getCustomerNumber());
		details.setCommAccno(account.getId().getAccountNumber());
		details.setCommAccType(account.getAccountType());
		details.setCommInterestRate(account.getInterestRate());
		details.setCommOpened(DtoFormat.dateToString(account.getOpened()));
		details.setCommOverdraft(account.getOverdraftLimit() == null
				? Integer.valueOf(0)
				: account.getOverdraftLimit());
		details.setCommLastStatementDate(
				DtoFormat.dateToString(account.getLastStatementDate()));
		details.setCommNextStatementDate(
				DtoFormat.dateToString(account.getNextStatementDate()));
		details.setCommAvailableBalance(account.getAvailableBalance());
		details.setCommActualBalance(account.getActualBalance());
		return details;
	}

	// ------------------------------------------------------------------
	// UPDACC (F-012) — restricted update (no PROCTRAN, never balances)
	// ------------------------------------------------------------------

	/**
	 * Updates an account's mutable attributes, reproducing {@code UPDACC}'s
	 * deliberately <strong>restricted</strong> behaviour: it changes only the
	 * account type, interest rate, and overdraft limit and writes
	 * <strong>no</strong> {@code PROCTRAN} record. The balances, opening date,
	 * owning customer number, and statement dates are never touched.
	 *
	 * <p>A missing account, a blank/invalid account type, or a persistence
	 * failure is a soft failure: the method throws a {@link BusinessRuleException}
	 * which the controller renders as {@code CommSuccess = "N"} (this envelope
	 * carries no fail-code field, so the code is informational only), mirroring
	 * {@code UPDACC}'s {@code MOVE 'N' TO COMM-SUCCESS}.</p>
	 *
	 * @param form the update-account request input (account number, type,
	 *             interest rate, overdraft limit)
	 * @return the populated success envelope ({@code CommSuccess = "Y"})
	 * @throws BusinessRuleException when the account is absent, the type is
	 *                               blank/invalid, or the update fails to persist
	 */
	@Transactional(propagation = Propagation.REQUIRED,
			isolation = Isolation.READ_COMMITTED)
	public UpdateAccountJson updateAccount(UpdateAccountForm form)
	{
		long accountNumber = form.getAcctNumber();
		String accountKey = BankFormat.accountNumber(accountNumber);
		Optional<Account> existing = accountRepository.findById(
				new AccountId(BankConstants.SORT_CODE, accountKey));
		if (existing.isEmpty())
		{
			throw new BusinessRuleException(FAIL_ACCOUNT_NOT_FOUND,
					"Account " + accountKey + " not found");
		}

		// Reject a blank/invalid account type (UPDACC 'spaces' guard). The form
		// carries the parsed enum; a null value means the inbound type was blank
		// or not one of the five valid literals.
		AccountType accountType = form.getAcctType();
		if (accountType == null)
		{
			throw new BusinessRuleException(FAIL_INVALID_TYPE,
					"Account type must not be blank");
		}

		// Update ONLY the three permitted fields — never balances, opened date,
		// customer number, or statement dates.
		Account account = existing.get();
		account.setAccountType(accountType.name());
		account.setInterestRate(scale2(form.getAcctInterestRate()));
		account.setOverdraftLimit(form.getAcctOverdraft());

		Account saved;
		try
		{
			saved = accountRepository.save(account);
		}
		catch (DataAccessException ex)
		{
			throw new BusinessRuleException(FAIL_INSERT_ERROR,
					"Failed to update account " + accountKey, ex);
		}
		return buildUpdateResponse(saved);
	}

	/**
	 * Builds the update-account success envelope from the persisted account,
	 * reproducing the frozen contract verbatim (dates as {@code String} values,
	 * {@code CommSuccess = "Y"}). The envelope reflects the updated type,
	 * interest rate, and overdraft limit; the balances it echoes are the
	 * account's unchanged values.
	 *
	 * @param account the persisted account
	 * @return the populated {@link UpdateAccountJson} envelope
	 */
	private UpdateAccountJson buildUpdateResponse(Account account)
	{
		UpdaccJson out = new UpdaccJson();
		out.setCommCustno(account.getCustomerNumber());
		out.setCommSortcode(account.getId().getSortCode());
		out.setCommAccno(account.getId().getAccountNumber());
		out.setCommInterestRate(account.getInterestRate());
		out.setCommOpened(DtoFormat.dateToString(account.getOpened()));
		out.setCommOverdraft(account.getOverdraftLimit() == null
				? Integer.valueOf(0)
				: account.getOverdraftLimit());
		out.setCommLastStatementDate(
				DtoFormat.dateToString(account.getLastStatementDate()));
		out.setCommNextStatementDate(
				DtoFormat.dateToString(account.getNextStatementDate()));
		out.setCommAvailableBalance(account.getAvailableBalance());
		out.setCommActualBalance(account.getActualBalance());
		out.setCommAccountType(account.getAccountType());
		out.setCommSuccess(FLAG_SUCCESS);
		return new UpdateAccountJson(out);
	}

	// ------------------------------------------------------------------
	// DELACC (F-013) — delete account (capture terminal balance, append PROCTRAN)
	// ------------------------------------------------------------------

	/**
	 * Deletes the account with the given number, reproducing {@code DELACC}. The
	 * account's terminal <em>actual</em> balance is captured <strong>before</strong>
	 * the row is removed and carried as the amount of the account-close
	 * {@link TransactionType#ODA} {@code PROCTRAN} record ({@code DELACC}
	 * L411/L519); the account row is then physically removed while the audit
	 * history is preserved (PROCTRAN is append-only).
	 *
	 * @param accountNumber the account number to delete
	 * @return the populated success envelope echoing the deleted account's
	 *         terminal available and actual balances
	 * @throws BusinessRuleException with fail code {@code 1} (not found),
	 *                               {@code 3} (delete failed), or {@code HRAC}
	 *                               (read SQL error)
	 */
	@Transactional(propagation = Propagation.REQUIRED,
			isolation = Isolation.READ_COMMITTED)
	public DeleteAccountJson deleteAccount(long accountNumber)
	{
		return deleteAccount(new AccountId(BankConstants.SORT_CODE,
				BankFormat.accountNumber(accountNumber)));
	}

	/**
	 * Deletes the account identified by its composite key. This overload is the
	 * cascade-delete entry point invoked when a customer is closed (the customer
	 * service removes each of the customer's accounts in turn); the
	 * {@link #deleteAccount(long)} endpoint method delegates here so both paths
	 * share one implementation and one audit rule.
	 *
	 * @param accountId the composite key (sort code + account number) of the
	 *                  account to delete
	 * @return the populated success envelope echoing the deleted account's
	 *         terminal available and actual balances
	 * @throws BusinessRuleException with fail code {@code 1} (not found),
	 *                               {@code 3} (delete failed), or {@code HRAC}
	 *                               (read SQL error)
	 */
	@Transactional(propagation = Propagation.REQUIRED,
			isolation = Isolation.READ_COMMITTED)
	public DeleteAccountJson deleteAccount(AccountId accountId)
	{
		Optional<Account> account;
		try
		{
			account = accountRepository.findById(accountId);
		}
		catch (DataAccessException ex)
		{
			// A read SQL error is the DELACC abend path (L282/L315/L339).
			throw new BusinessRuleException(ABEND_READ_ERROR,
					"SQL error reading account " + accountId.getAccountNumber(),
					ex);
		}
		if (account.isEmpty())
		{
			throw new BusinessRuleException(FAIL_ACCOUNT_NOT_FOUND,
					"Account " + accountId.getAccountNumber() + " not found");
		}
		return doDelete(account.get());
	}

	/**
	 * Shared delete logic: snapshot the terminal actual balance, physically
	 * remove the account row, append the {@link TransactionType#ODA} close record
	 * carrying that balance, and build the response. All steps run inside the
	 * caller's transaction so they commit or roll back together.
	 *
	 * @param account the account to delete (its in-memory field values remain
	 *                readable after the row is removed)
	 * @return the populated delete-account success envelope
	 * @throws BusinessRuleException with fail code {@code 3} when the physical
	 *                               delete fails
	 */
	private DeleteAccountJson doDelete(Account account)
	{
		// Capture the terminal ACTUAL balance BEFORE removal — it is the amount
		// the account-close PROCTRAN record carries (DELACC L411/L519). The
		// available balance is preserved independently for the response.
		BigDecimal terminalActualBalance = scale2(account.getActualBalance());

		try
		{
			accountRepository.delete(account);
		}
		catch (DataAccessException ex)
		{
			throw new BusinessRuleException(FAIL_DELETE_ERROR,
					"Failed to delete account "
							+ account.getId().getAccountNumber(),
					ex);
		}

		appendDeleteProcessedTransaction(account, terminalActualBalance);
		return buildDeleteResponse(account, terminalActualBalance);
	}

	/**
	 * Builds the delete-account success envelope from the deleted account's
	 * detached snapshot, echoing both independent balances (the available
	 * balance unchanged, the actual balance the captured terminal value), the
	 * dates as {@code String} values, the success flags, and the primary fail
	 * code {@code "0"}.
	 *
	 * @param account                the deleted account snapshot
	 * @param terminalActualBalance  the actual balance captured before deletion
	 * @return the populated {@link DeleteAccountJson} envelope
	 */
	private DeleteAccountJson buildDeleteResponse(Account account,
			BigDecimal terminalActualBalance)
	{
		DelaccJson out = new DelaccJson();
		out.setDelaccAccno(account.getId().getAccountNumber());
		out.setDelaccSortcode(account.getId().getSortCode());
		out.setDelaccCustno(account.getCustomerNumber());
		out.setDelaccAccType(account.getAccountType());
		out.setDelaccInterestRate(account.getInterestRate());
		out.setDelaccOverdraft(account.getOverdraftLimit() == null
				? Integer.valueOf(0)
				: account.getOverdraftLimit());
		out.setDelaccAvailableBalance(scale2(account.getAvailableBalance()));
		out.setDelaccActualBalance(terminalActualBalance);
		out.setDelaccOpened(DtoFormat.dateToString(account.getOpened()));
		out.setDelaccLastStatementDate(
				DtoFormat.dateToString(account.getLastStatementDate()));
		out.setDelaccNextStatementDate(
				DtoFormat.dateToString(account.getNextStatementDate()));
		out.setDelaccFailCode(FAIL_NONE);
		out.setDelaccSuccess(FLAG_SUCCESS);
		out.setDelaccDelSuccess(FLAG_SUCCESS);
		return new DeleteAccountJson(out);
	}

	// ------------------------------------------------------------------
	// PROCTRAN audit append + shared private helpers
	// ------------------------------------------------------------------

	/**
	 * Appends the account-create {@code PROCTRAN} record: a
	 * {@link TransactionType#OCA} row with amount {@code 0.00} ({@code CREACC}
	 * L966-967).
	 *
	 * @param account the freshly persisted account
	 */
	private void appendCreateProcessedTransaction(Account account)
	{
		appendProcessedTransaction(account.getId().getAccountNumber(),
				TransactionType.OCA, scale2(BigDecimal.ZERO),
				buildProctranDescription(account));
	}

	/**
	 * Appends the account-close {@code PROCTRAN} record: a
	 * {@link TransactionType#ODA} row whose amount is the captured terminal
	 * actual balance ({@code DELACC} L513/L519).
	 *
	 * @param account               the account being deleted
	 * @param terminalActualBalance the actual balance captured before deletion
	 */
	private void appendDeleteProcessedTransaction(Account account,
			BigDecimal terminalActualBalance)
	{
		appendProcessedTransaction(account.getId().getAccountNumber(),
				TransactionType.ODA, terminalActualBalance,
				buildProctranDescription(account));
	}

	/**
	 * Builds and persists one append-only {@code PROCTRAN} audit row. The unique
	 * transaction reference replaces the COBOL CICS task number, which has no
	 * mainframe-free equivalent: it is allocated as
	 * {@link ProcessedTransactionRepository#findMaxReference(String)
	 * findMaxReference(sortCode) + 1} inside the caller's transaction, so a
	 * rolled-back operation also rolls back the consumed reference.
	 *
	 * @param accountNumber the zero-padded eight-digit account number
	 * @param type          the PROCTRAN type code ({@code OCA}/{@code ODA})
	 * @param amount        the transaction amount (scale 2)
	 * @param description   the fixed-width (40) description text
	 */
	private void appendProcessedTransaction(String accountNumber,
			TransactionType type, BigDecimal amount, String description)
	{
		long nextReference = processedTransactionRepository
				.findMaxReference(BankConstants.SORT_CODE) + 1L;
		ProcessedTransaction row = new ProcessedTransaction();
		row.setId(new ProcessedTransactionId(BankConstants.SORT_CODE,
				BankFormat.reference(nextReference)));
		row.setTransactionNumber(accountNumber);
		row.setDate(LocalDate.now());
		row.setTime(LocalTime.now());
		row.setTypeCode(type);
		row.setDescription(description);
		row.setAmount(scale2(amount));
		row.setDeleted(false);
		processedTransactionRepository.save(row);
	}

	/**
	 * Builds the forty-character {@code PROCTRAN} description for an
	 * account-create or account-close record, reproducing the COBOL field
	 * layout: customer number (10) + account type (8) + last statement date
	 * {@code DDMMYYYY} (8) + next statement date {@code DDMMYYYY} (8) +
	 * six trailing spaces ({@code CREACC} L961-965).
	 *
	 * @param account the account whose attributes populate the description
	 * @return the fixed-width (40) description text
	 */
	private String buildProctranDescription(Account account)
	{
		StringBuilder description = new StringBuilder();
		description.append(rightPadSpace(account.getCustomerNumber(), 10));
		description.append(rightPadSpace(account.getAccountType(), 8));
		description.append(rightPadSpace(
				DtoFormat.dateToString(account.getLastStatementDate()), 8));
		description.append(rightPadSpace(
				DtoFormat.dateToString(account.getNextStatementDate()), 8));
		return rightPadSpace(description.toString(), DESCRIPTION_WIDTH);
	}

	/**
	 * Parses a display-numeric identifier (the inbound customer number) to a
	 * {@code long}, trimming any surrounding whitespace. The inbound value is
	 * pattern-validated as one-to-ten digits by the request DTO.
	 *
	 * @param value the display-numeric identifier string
	 * @return the parsed numeric value
	 */
	private static long parseIdentifier(String value)
	{
		return Long.parseLong(value.trim());
	}

	/**
	 * Normalises a monetary or rate value to scale 2 with
	 * {@link RoundingMode#HALF_UP}, treating {@code null} as zero, so no binary
	 * floating-point arithmetic enters the pipeline.
	 *
	 * @param value the value to normalise (may be {@code null})
	 * @return the value at scale 2, or {@code 0.00} when {@code null}
	 */
	private static BigDecimal scale2(BigDecimal value)
	{
		BigDecimal safe = (value == null) ? BigDecimal.ZERO : value;
		return safe.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
	}

	/**
	 * Right-pads (or truncates) a value with spaces to a fixed width, preserving
	 * the COBOL fixed-format field convention. A {@code null} value is treated as
	 * empty.
	 *
	 * @param value the value to pad (may be {@code null})
	 * @param width the target width
	 * @return the value padded or truncated to exactly {@code width} characters
	 */
	private static String rightPadSpace(String value, int width)
	{
		String safe = (value == null) ? "" : value;
		if (safe.length() >= width)
		{
			return safe.substring(0, width);
		}
		StringBuilder padded = new StringBuilder(safe);
		while (padded.length() < width)
		{
			padded.append(' ');
		}
		return padded.toString();
	}

}
