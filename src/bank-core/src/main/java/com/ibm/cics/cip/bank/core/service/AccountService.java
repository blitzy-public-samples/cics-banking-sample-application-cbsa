/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.core.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ibm.cics.cip.bank.core.constants.BankConstants;
import com.ibm.cics.cip.bank.core.domain.AccountType;
import com.ibm.cics.cip.bank.core.entity.Account;
import com.ibm.cics.cip.bank.core.entity.AccountId;
import com.ibm.cics.cip.bank.core.entity.Customer;
import com.ibm.cics.cip.bank.core.entity.CustomerId;
import com.ibm.cics.cip.bank.core.exception.BusinessRuleException;
import com.ibm.cics.cip.bank.core.repository.AccountRepository;
import com.ibm.cics.cip.bank.core.repository.CustomerRepository;
import com.ibm.cics.cip.bank.core.util.BankFormat;

/**
 * Account business service, reproducing the COBOL account programs
 * {@code CREACC} (create), {@code UPDACC} (update) and {@code DELACC} (delete)
 * exactly &mdash; their validation order, single-character fail codes and
 * PROCTRAN audit semantics.
 *
 * <p>Each public method is {@link Transactional @Transactional}, so the account
 * mutation and any PROCTRAN audit append it triggers commit or roll back as a
 * single unit of work (review finding F-TXN-1). Validation failures raise an
 * unchecked {@link BusinessRuleException} carrying the COBOL fail code.</p>
 *
 * <p><strong>Create ({@code CREACC}).</strong> Validates that the owning
 * customer exists (fail {@code 1}), that the customer holds fewer than the
 * {@link BankConstants#MAX_ACCOUNTS_PER_CUSTOMER ten-account} maximum (fail
 * {@code 8}) and that the account type is valid (fail {@code A}); then allocates
 * the next account number from the control row, inserts the account with zero
 * balances, today's opened/last-statement date and a next-statement date one
 * month out, and appends an {@code OCA} audit row &mdash; in that order, so a
 * failed insert rolls back the number allocation.</p>
 *
 * <p><strong>Update ({@code UPDACC}).</strong> Changes only the account type,
 * interest rate and overdraft limit; balances are never touched and no PROCTRAN
 * row is written.</p>
 *
 * <p><strong>Delete ({@code DELACC}).</strong> Captures the account's terminal
 * actual balance, removes the account row, appends an {@code ODA} audit row
 * carrying that closing balance and decrements the account counter.</p>
 */
@Service
public class AccountService
{

	/** Fail code: owning customer (create) or account (update/delete) not found. */
	private static final String FAIL_NOT_FOUND = "1";

	/** Fail code: customer already holds the maximum number of accounts. */
	private static final String FAIL_TOO_MANY_ACCOUNTS = "8";

	/** Fail code: invalid account type. */
	private static final String FAIL_INVALID_TYPE = "A";

	/** Monetary scale for balances and interest rate. */
	private static final int MONEY_SCALE = 2;

	private final AccountRepository accountRepository;

	private final CustomerRepository customerRepository;

	private final IdentityService identityService;

	private final ProcessedTransactionAppender proctranAppender;

	/**
	 * Constructs the account service with its collaborators.
	 *
	 * @param accountRepository  repository for {@link Account} persistence
	 * @param customerRepository repository used to confirm the owning customer
	 *                           exists on create
	 * @param identityService    allocator/releaser of account numbers
	 * @param proctranAppender   atomic PROCTRAN audit-row appender
	 */
	public AccountService(AccountRepository accountRepository,
			CustomerRepository customerRepository,
			IdentityService identityService,
			ProcessedTransactionAppender proctranAppender)
	{
		this.accountRepository = accountRepository;
		this.customerRepository = customerRepository;
		this.identityService = identityService;
		this.proctranAppender = proctranAppender;
	}

	/**
	 * Creates an account, reproducing {@code CREACC}.
	 *
	 * @param customerNumber the owning customer number
	 * @param accountType    the account type (must be a valid
	 *                       {@link AccountType})
	 * @param interestRate   the interest rate
	 * @param overdraftLimit the overdraft limit
	 * @return the persisted {@link Account}, including its allocated number,
	 *         opened/statement dates and zero balances
	 * @throws BusinessRuleException {@code 1} if the customer does not exist,
	 *                               {@code 8} if the customer already holds the
	 *                               maximum number of accounts, or {@code A} if
	 *                               the account type is invalid
	 */
	@Transactional
	public Account createAccount(long customerNumber, String accountType,
			BigDecimal interestRate, Integer overdraftLimit)
	{
		String paddedCustomerNumber = BankFormat.customerNumber(customerNumber);

		// 1. Customer must exist (CREACC fail '1').
		customerRepository
				.findById(new CustomerId(BankConstants.SORT_CODE,
						paddedCustomerNumber))
				.orElseThrow(() -> new BusinessRuleException(FAIL_NOT_FOUND,
						"Customer not found: " + customerNumber));

		// 2. Enforce the per-customer account maximum (CREACC fail '8').
		long existingAccounts = accountRepository
				.countByIdSortCodeAndCustomerNumber(BankConstants.SORT_CODE,
						paddedCustomerNumber);
		if (existingAccounts >= BankConstants.MAX_ACCOUNTS_PER_CUSTOMER)
		{
			throw new BusinessRuleException(FAIL_TOO_MANY_ACCOUNTS,
					"Customer " + customerNumber + " already holds the maximum "
							+ BankConstants.MAX_ACCOUNTS_PER_CUSTOMER
							+ " accounts");
		}

		// 3. Validate the account type (CREACC fail 'A').
		if (!AccountType.isValid(accountType))
		{
			throw new BusinessRuleException(FAIL_INVALID_TYPE,
					"Invalid account type: " + accountType);
		}

		// 4. Allocate the next account number under the control-row lock.
		long accountNumber = identityService
				.allocateAccountNumber(BankConstants.SORT_CODE);

		// 5. Insert the account: zero balances, opened today, last statement
		//    today, next statement one month out (CREACC CALCULATE-DATES).
		LocalDate today = LocalDate.now();
		LocalDate nextStatement = today.plusDays(today.lengthOfMonth());
		Account account = new Account();
		account.setId(new AccountId(BankConstants.SORT_CODE,
				BankFormat.accountNumber(accountNumber)));
		account.setCustomerNumber(paddedCustomerNumber);
		account.setAccountType(accountType);
		account.setInterestRate(scale(interestRate));
		account.setOpened(today);
		account.setOverdraftLimit(overdraftLimit);
		account.setLastStatementDate(today);
		account.setNextStatementDate(nextStatement);
		account.setAvailableBalance(BigDecimal.ZERO.setScale(MONEY_SCALE));
		account.setActualBalance(BigDecimal.ZERO.setScale(MONEY_SCALE));
		Account saved = accountRepository.save(account);

		// 6. Append the create-account audit row (OCA) atomically.
		proctranAppender.appendAccountCreate(BankConstants.SORT_CODE,
				accountNumber, customerNumber, accountType, today,
				nextStatement);

		return saved;
	}

	/**
	 * Updates an account's type, interest rate and overdraft limit, reproducing
	 * {@code UPDACC}. Balances are never changed and no PROCTRAN row is written.
	 *
	 * @param accountNumber  the account number to update
	 * @param accountType    the new account type
	 * @param interestRate   the new interest rate
	 * @param overdraftLimit the new overdraft limit
	 * @return the updated {@link Account}
	 * @throws BusinessRuleException {@code 1} if the account does not exist
	 */
	@Transactional
	public Account updateAccount(long accountNumber, String accountType,
			BigDecimal interestRate, Integer overdraftLimit)
	{
		Account account = accountRepository
				.findById(new AccountId(BankConstants.SORT_CODE,
						BankFormat.accountNumber(accountNumber)))
				.orElseThrow(() -> new BusinessRuleException(FAIL_NOT_FOUND,
						"Account not found: " + accountNumber));

		account.setAccountType(accountType);
		account.setInterestRate(scale(interestRate));
		account.setOverdraftLimit(overdraftLimit);
		// UPDACC writes no PROCTRAN record and never touches the balances.
		return accountRepository.save(account);
	}

	/**
	 * Deletes an account, reproducing {@code DELACC}.
	 *
	 * @param accountNumber the account number to delete
	 * @return a detached snapshot of the deleted {@link Account}, including its
	 *         terminal balances
	 * @throws BusinessRuleException {@code 1} if the account does not exist
	 */
	@Transactional
	public Account deleteAccount(long accountNumber)
	{
		Account account = accountRepository
				.findById(new AccountId(BankConstants.SORT_CODE,
						BankFormat.accountNumber(accountNumber)))
				.orElseThrow(() -> new BusinessRuleException(FAIL_NOT_FOUND,
						"Account not found: " + accountNumber));

		// Capture the terminal actual balance for the audit row (DELACC
		// MOVE ACCOUNT-ACT-BAL-STORE TO HV-PROCTRAN-AMOUNT).
		BigDecimal terminalBalance = account.getActualBalance();
		long customerNumber = Long
				.parseLong(account.getCustomerNumber().trim());
		String accountType = account.getAccountType();
		LocalDate lastStatement = account.getLastStatementDate();
		LocalDate nextStatement = account.getNextStatementDate();

		// Remove the account row.
		accountRepository.delete(account);

		// Append the delete-account audit row (ODA) carrying the closing
		// balance, atomically.
		proctranAppender.appendAccountDelete(BankConstants.SORT_CODE,
				accountNumber, customerNumber, accountType, lastStatement,
				nextStatement, terminalBalance);

		// Decrement the account counter (high-water mark is preserved).
		identityService.releaseAccount(BankConstants.SORT_CODE);

		return account;
	}

	/**
	 * Looks up a customer's owning {@link Customer} record is not required here;
	 * this read-only helper returns an account by number for callers that need
	 * to confirm existence without locking.
	 *
	 * @param accountNumber the account number
	 * @return the {@link Account}, if present
	 */
	@Transactional(readOnly = true)
	public Optional<Account> findAccount(long accountNumber)
	{
		return accountRepository.findById(new AccountId(BankConstants.SORT_CODE,
				BankFormat.accountNumber(accountNumber)));
	}

	/**
	 * Scales a monetary/rate value to two decimal places using
	 * {@link RoundingMode#HALF_UP}, treating {@code null} as zero.
	 *
	 * @param value the value to scale
	 * @return the scaled value
	 */
	private BigDecimal scale(BigDecimal value)
	{
		BigDecimal safe = (value == null) ? BigDecimal.ZERO : value;
		return safe.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
	}

}
