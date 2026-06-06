/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.core.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.ibm.cics.cip.bank.core.constants.BankConstants;
import com.ibm.cics.cip.bank.core.domain.Title;
import com.ibm.cics.cip.bank.core.entity.Account;
import com.ibm.cics.cip.bank.core.entity.Customer;
import com.ibm.cics.cip.bank.core.entity.CustomerId;
import com.ibm.cics.cip.bank.core.exception.BusinessRuleException;
import com.ibm.cics.cip.bank.core.repository.AccountRepository;
import com.ibm.cics.cip.bank.core.repository.CustomerRepository;
import com.ibm.cics.cip.bank.core.util.BankFormat;

/**
 * Customer business service, reproducing the behaviour of the COBOL customer
 * programs {@code CRECUST} (create), {@code UPDCUST} (update) and
 * {@code DELCUS} (delete) exactly &mdash; including their validation order,
 * single-character fail codes and PROCTRAN audit semantics.
 *
 * <p>Every public method is {@link Transactional @Transactional} with the
 * default {@link Propagation#REQUIRED REQUIRED} propagation, so the customer
 * mutation and the PROCTRAN audit append it triggers form one atomic unit of
 * work (review finding F-TXN-1). Validation failures are signalled with an
 * unchecked {@link BusinessRuleException} carrying the COBOL fail code, which
 * both rolls the transaction back and is translated onto the response envelope
 * by the controller advice.</p>
 *
 * <p><strong>Create ({@code CRECUST}).</strong> The flow validates the title
 * token, performs the asynchronous five-agency credit check, validates the date
 * of birth, allocates the next customer number from the control row and inserts
 * the customer, then appends an {@code OCC} PROCTRAN row &mdash; in that order,
 * so any failure rolls back the number allocation. The credit score is the
 * average returned by {@link CreditAgencyService} (never a hard-coded zero) and
 * the credit-score review date is today plus a random 1&ndash;21 days.</p>
 *
 * <p><strong>Update ({@code UPDCUST}).</strong> Only the name and address are
 * changed; balances and other fields are untouched and <em>no</em> PROCTRAN row
 * is written.</p>
 *
 * <p><strong>Delete ({@code DELCUS}).</strong> The customer's accounts are
 * deleted first (each via {@link AccountService#deleteAccount(long)}, which
 * appends its own {@code ODA} audit row and decrements the account counter),
 * then the customer row is removed, an {@code ODC} PROCTRAN row is appended and
 * the customer counter is decremented &mdash; all inside the one transaction.</p>
 */
@Service
public class CustomerService
{

	/** Lowest acceptable year of birth, matching {@code CRECUST} ({@code < 1601}). */
	private static final int MIN_BIRTH_YEAR = 1601;

	/** Maximum acceptable age in years, matching {@code CRECUST} ({@code > 150}). */
	private static final int MAX_AGE_YEARS = 150;

	/** Inclusive lower bound of the random credit-score review window (days). */
	private static final int REVIEW_DAYS_MIN = 1;

	/** Inclusive upper bound of the random credit-score review window (days). */
	private static final int REVIEW_DAYS_MAX = 21;

	/** Fail code: invalid customer title. */
	private static final String FAIL_INVALID_TITLE = "T";

	/** Fail code: date of birth out of range (year too early or age over 150). */
	private static final String FAIL_DOB_RANGE = "O";

	/** Fail code: date of birth in the future. */
	private static final String FAIL_DOB_FUTURE = "Y";

	/** Fail code: customer not found. */
	private static final String FAIL_NOT_FOUND = "1";

	/** Fail code: nothing to update (both name and address blank). */
	private static final String FAIL_NOTHING_TO_UPDATE = "4";

	private final CustomerRepository customerRepository;

	private final AccountRepository accountRepository;

	private final AccountService accountService;

	private final IdentityService identityService;

	private final CreditAgencyService creditAgencyService;

	private final ProcessedTransactionAppender proctranAppender;

	/**
	 * Constructs the customer service with its collaborators.
	 *
	 * @param customerRepository  repository for {@link Customer} persistence
	 * @param accountRepository   repository used to find a customer's accounts
	 *                            for the delete cascade
	 * @param accountService      account service used to delete each owned
	 *                            account (with its own audit row) in the cascade
	 * @param identityService     allocator/releaser of customer numbers
	 * @param creditAgencyService asynchronous credit-score provider
	 * @param proctranAppender    atomic PROCTRAN audit-row appender
	 */
	public CustomerService(CustomerRepository customerRepository,
			AccountRepository accountRepository, AccountService accountService,
			IdentityService identityService,
			CreditAgencyService creditAgencyService,
			ProcessedTransactionAppender proctranAppender)
	{
		this.customerRepository = customerRepository;
		this.accountRepository = accountRepository;
		this.accountService = accountService;
		this.identityService = identityService;
		this.creditAgencyService = creditAgencyService;
		this.proctranAppender = proctranAppender;
	}

	/**
	 * Creates a customer, reproducing {@code CRECUST}.
	 *
	 * @param name        the customer name (its first token must be a valid
	 *                    {@link Title}, or blank)
	 * @param address     the customer address
	 * @param dateOfBirth the customer date of birth
	 * @return the persisted {@link Customer}, including the allocated number,
	 *         averaged credit score and review date
	 * @throws BusinessRuleException with the relevant fail code on a validation
	 *                               failure ({@code T}, {@code C}, {@code O} or
	 *                               {@code Y})
	 */
	@Transactional
	public Customer createCustomer(String name, String address,
			LocalDate dateOfBirth)
	{
		// 1. Title validation (CRECUST: fail 'T').
		String title = firstToken(name);
		if (!Title.isValidTitle(title))
		{
			throw new BusinessRuleException(FAIL_INVALID_TITLE,
					"Invalid customer title: " + title);
		}

		// 2. Asynchronous credit check (CRECUST CREDIT-CHECK; throws 'C' if no
		//    agency replied within the deadline).
		int creditScore = creditAgencyService.requestCreditScore();
		LocalDate reviewDate = LocalDate.now()
				.plusDays(ThreadLocalRandom.current()
						.nextInt(REVIEW_DAYS_MIN, REVIEW_DAYS_MAX + 1));

		// 3. Date-of-birth validation (CRECUST DATE-OF-BIRTH-CHECK).
		validateDateOfBirth(dateOfBirth);

		// 4. Allocate the next customer number under the control-row lock.
		long customerNumber = identityService
				.allocateCustomerNumber(BankConstants.SORT_CODE);

		// 5. Insert the customer.
		Customer customer = new Customer();
		customer.setId(new CustomerId(BankConstants.SORT_CODE,
				BankFormat.customerNumber(customerNumber)));
		customer.setName(name);
		customer.setAddress(address);
		customer.setDateOfBirth(dateOfBirth);
		customer.setCreditScore((short) creditScore);
		customer.setCsReviewDate(reviewDate);
		Customer saved = customerRepository.save(customer);

		// 6. Append the create-customer audit row (OCC) atomically.
		proctranAppender.appendCustomerCreate(BankConstants.SORT_CODE,
				customerNumber, name, dateOfBirth);

		return saved;
	}

	/**
	 * Updates a customer's name and/or address, reproducing {@code UPDCUST}. No
	 * PROCTRAN row is written and no other field is changed.
	 *
	 * @param customerNumber the customer number to update
	 * @param name           the new name, or blank to leave unchanged
	 * @param address        the new address, or blank to leave unchanged
	 * @return the updated {@link Customer}
	 * @throws BusinessRuleException {@code 1} if the customer does not exist,
	 *                               {@code T} if a supplied name has an invalid
	 *                               title, or {@code 4} if both name and address
	 *                               are blank
	 */
	@Transactional
	public Customer updateCustomer(long customerNumber, String name,
			String address)
	{
		Customer customer = customerRepository
				.findById(new CustomerId(BankConstants.SORT_CODE,
						BankFormat.customerNumber(customerNumber)))
				.orElseThrow(() -> new BusinessRuleException(FAIL_NOT_FOUND,
						"Customer not found: " + customerNumber));

		boolean nameProvided = isProvided(name);
		boolean addressProvided = isProvided(address);
		if (!nameProvided && !addressProvided)
		{
			throw new BusinessRuleException(FAIL_NOTHING_TO_UPDATE,
					"Neither name nor address supplied for update");
		}

		if (nameProvided)
		{
			if (!Title.isValidTitle(firstToken(name)))
			{
				throw new BusinessRuleException(FAIL_INVALID_TITLE,
						"Invalid customer title: " + firstToken(name));
			}
			customer.setName(name);
		}
		if (addressProvided)
		{
			customer.setAddress(address);
		}
		// UPDCUST writes no PROCTRAN record for an update.
		return customerRepository.save(customer);
	}

	/**
	 * Deletes a customer and all of its accounts, reproducing {@code DELCUS}.
	 *
	 * <p>Each owned account is deleted first via
	 * {@link AccountService#deleteAccount(long)} (appending an {@code ODA} row
	 * and decrementing the account counter), then the customer row is removed,
	 * an {@code ODC} audit row is appended and the customer counter is
	 * decremented &mdash; all atomically.</p>
	 *
	 * @param customerNumber the customer number to delete
	 * @return a detached snapshot of the deleted {@link Customer}
	 * @throws BusinessRuleException {@code 1} if the customer does not exist
	 */
	@Transactional
	public Customer deleteCustomer(long customerNumber)
	{
		String paddedCustomerNumber = BankFormat.customerNumber(customerNumber);
		Customer customer = customerRepository
				.findById(new CustomerId(BankConstants.SORT_CODE,
						paddedCustomerNumber))
				.orElseThrow(() -> new BusinessRuleException(FAIL_NOT_FOUND,
						"Customer not found: " + customerNumber));

		// Snapshot the customer details for the audit row and the response
		// before the row is removed.
		String name = customer.getName();
		LocalDate dateOfBirth = customer.getDateOfBirth();

		// Cascade-delete the customer's accounts (DELCUS DELETE-ACCOUNTS); each
		// AccountService.deleteAccount call runs in this same transaction.
		List<Account> accounts = accountRepository
				.findByCustomerNumberOrderByIdAccountNumberAsc(
						paddedCustomerNumber);
		for (Account account : accounts)
		{
			accountService.deleteAccount(
					Long.parseLong(account.getId().getAccountNumber().trim()));
		}

		// Remove the customer row.
		customerRepository.delete(customer);

		// Append the delete-customer audit row (ODC) atomically.
		proctranAppender.appendCustomerDelete(BankConstants.SORT_CODE,
				customerNumber, name, dateOfBirth);

		// Decrement the customer counter (high-water mark is preserved).
		identityService.releaseCustomer(BankConstants.SORT_CODE);

		return customer;
	}

	/**
	 * Looks up a customer by number without modifying it. Provided for callers
	 * (such as {@link AccountService}) that must confirm a customer exists.
	 *
	 * @param customerNumber the customer number
	 * @return the {@link Customer}, if present
	 */
	@Transactional(readOnly = true)
	public Optional<Customer> findCustomer(long customerNumber)
	{
		return customerRepository.findById(new CustomerId(
				BankConstants.SORT_CODE,
				BankFormat.customerNumber(customerNumber)));
	}

	/**
	 * Validates a date of birth exactly as {@code CRECUST} does.
	 *
	 * @param dateOfBirth the date of birth to validate
	 * @throws BusinessRuleException {@code O} if the year is before
	 *                               {@value #MIN_BIRTH_YEAR} or the age exceeds
	 *                               {@value #MAX_AGE_YEARS}, or {@code Y} if the
	 *                               date is in the future
	 */
	private void validateDateOfBirth(LocalDate dateOfBirth)
	{
		if (dateOfBirth == null || dateOfBirth.getYear() < MIN_BIRTH_YEAR)
		{
			throw new BusinessRuleException(FAIL_DOB_RANGE,
					"Date of birth year is before " + MIN_BIRTH_YEAR);
		}
		LocalDate today = LocalDate.now();
		if (today.getYear() - dateOfBirth.getYear() > MAX_AGE_YEARS)
		{
			throw new BusinessRuleException(FAIL_DOB_RANGE,
					"Customer age exceeds " + MAX_AGE_YEARS + " years");
		}
		if (dateOfBirth.isAfter(today))
		{
			throw new BusinessRuleException(FAIL_DOB_FUTURE,
					"Date of birth is in the future");
		}
	}

	/**
	 * Returns the first whitespace-delimited token of a name (the title), or the
	 * empty string when the name is null or blank &mdash; matching the COBOL
	 * {@code UNSTRING COMM-NAME DELIMITED BY SPACE}.
	 *
	 * @param name the name
	 * @return the first token, or {@code ""}
	 */
	private String firstToken(String name)
	{
		if (name == null)
		{
			return "";
		}
		String trimmed = name.trim();
		if (trimmed.isEmpty())
		{
			return "";
		}
		int space = trimmed.indexOf(' ');
		return (space < 0) ? trimmed : trimmed.substring(0, space);
	}

	/**
	 * Reports whether a value is supplied (non-null and not blank).
	 *
	 * @param value the value to test
	 * @return {@code true} if the value is non-null and contains a non-space
	 *         character
	 */
	private boolean isProvided(String value)
	{
		return value != null && !value.trim().isEmpty();
	}

}
