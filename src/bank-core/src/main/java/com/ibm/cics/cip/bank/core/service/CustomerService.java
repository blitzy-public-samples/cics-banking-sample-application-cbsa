/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.core.service;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.ibm.cics.cip.bank.core.constants.BankConstants;
import com.ibm.cics.cip.bank.core.domain.Title;
import com.ibm.cics.cip.bank.core.dto.common.CommKey;
import com.ibm.cics.cip.bank.core.dto.createcustomer.CreateCustomerForm;
import com.ibm.cics.cip.bank.core.dto.createcustomer.CreateCustomerJson;
import com.ibm.cics.cip.bank.core.dto.createcustomer.CrecustJson;
import com.ibm.cics.cip.bank.core.dto.customerenquiry.CustomerEnquiryJson;
import com.ibm.cics.cip.bank.core.dto.customerenquiry.InqCustDob;
import com.ibm.cics.cip.bank.core.dto.customerenquiry.InqCustReviewDate;
import com.ibm.cics.cip.bank.core.dto.customerenquiry.InqCustZJson;
import com.ibm.cics.cip.bank.core.dto.deletecustomer.DelcusJson;
import com.ibm.cics.cip.bank.core.dto.deletecustomer.DeleteCustomerJson;
import com.ibm.cics.cip.bank.core.dto.updatecustomer.UpdateCustomerForm;
import com.ibm.cics.cip.bank.core.dto.updatecustomer.UpdateCustomerJson;
import com.ibm.cics.cip.bank.core.dto.updatecustomer.UpdcustJson;
import com.ibm.cics.cip.bank.core.entity.Account;
import com.ibm.cics.cip.bank.core.entity.Customer;
import com.ibm.cics.cip.bank.core.entity.CustomerId;
import com.ibm.cics.cip.bank.core.exception.BusinessRuleException;
import com.ibm.cics.cip.bank.core.repository.AccountRepository;
import com.ibm.cics.cip.bank.core.repository.CustomerControlRepository;
import com.ibm.cics.cip.bank.core.repository.CustomerRepository;

/**
 * Authoritative customer business service, reproducing the behaviour of four
 * COBOL customer programs exactly &mdash; {@code CRECUST} (create, F-006),
 * {@code INQCUST} (inquire, F-008), {@code UPDCUST} (update, F-011) and
 * {@code DELCUS} (delete, F-014). The COBOL is the specification of record:
 * every single-character fail code, every validation step, and every ordering
 * rule is preserved verbatim (behavioural parity, not enhancement &mdash; AAP
 * &sect;0.7).
 *
 * <h2>Transaction semantics (CICS SYNCPOINT/ROLLBACK)</h2>
 * <p>The three mutating operations are
 * {@link Transactional @Transactional}{@code (propagation = REQUIRED,
 * isolation = READ_COMMITTED)} so the customer mutation and the PROCTRAN audit
 * append it triggers form one atomic unit of work; a thrown
 * {@link BusinessRuleException} (unchecked) rolls the whole transaction back,
 * which is what makes identity allocation gap-free (a consumed counter is
 * restored on rollback &mdash; ADR-003). The read-only {@link #inquireCustomer}
 * is {@code @Transactional(readOnly = true)}.</p>
 *
 * <h2>Operation summary</h2>
 * <ul>
 *   <li><strong>Create ({@code CRECUST}).</strong> Validate the title token
 *       (fail {@code 'T'}), run the asynchronous five-agency credit check (fail
 *       {@code 'C'} if none reply within three seconds), validate the date of
 *       birth (fail {@code 'O'}/{@code 'Z'}/{@code 'Y'}), allocate the next
 *       customer number from the control row (fail {@code '3'}), insert the
 *       customer, then append an {@code OCC} PROCTRAN row &mdash; in that order,
 *       so any failure rolls back the number allocation. The credit-score
 *       review date is today plus a random 1&ndash;21 days.</li>
 *   <li><strong>Inquire ({@code INQCUST}).</strong> The sentinels
 *       {@code 0000000000} (random customer) and {@code 9999999999} (highest
 *       customer) are resolved from the {@code CUSTCTRL} control row, never with
 *       a {@code MAX()} table scan (F-008). A miss sets the response success
 *       flag to {@code 'N'} with fail code {@code '1'} (INQCUST does not
 *       abend).</li>
 *   <li><strong>Update ({@code UPDCUST}).</strong> Validate the title first
 *       (fail {@code 'T'}), read the customer (fail {@code '1'} not found,
 *       {@code '2'} read error), then change <em>only</em> the name and/or
 *       address subject to the blank rules (fail {@code '4'} when both are
 *       blank), and save (fail {@code '3'}). The date of birth, credit score
 *       and review date are never touched and <em>no</em> PROCTRAN row is
 *       written.</li>
 *   <li><strong>Delete ({@code DELCUS}).</strong> Read the customer (fail
 *       {@code '1'} not found), cascade-delete each owned account via
 *       {@link AccountService} (which captures the terminal balance and appends
 *       its own {@code ODA} row), remove the customer row, then append an
 *       {@code ODC} PROCTRAN row &mdash; all in one transaction. The customer
 *       counter is <em>not</em> decremented, exactly as {@code DELCUS.cbl}
 *       leaves {@code NUMBER-OF-CUSTOMERS} untouched.</li>
 * </ul>
 *
 * <h2>PROCTRAN append mechanism</h2>
 * <p>Audit rows are appended through the shared
 * {@link ProcessedTransactionAppender} sibling service rather than by building
 * {@code ProcessedTransaction} rows here directly. The appender allocates the
 * unique twelve-digit reference from the {@code last_transaction_reference}
 * counter on the {@code account_control} row, incremented under
 * a pessimistic lock, and formats the COBOL-faithful 40-byte
 * description and the {@code OCC}/{@code ODC} type codes; it is
 * {@code @Transactional(MANDATORY)} so it joins this service's transaction.
 * This is the mechanism every sibling service uses and reproduces the COBOL
 * {@code WRITE-PROCTRAN} sections faithfully (the create path uses {@code OCC}
 * per {@code CRECUST.cbl} line 1223, the delete path {@code ODC} per
 * {@code DELCUS.cbl} line 632).</p>
 *
 * <h2>Frozen wire contract (F-019)</h2>
 * <p>Each operation returns the fully populated success envelope DTO; failures
 * are signalled by throwing {@link BusinessRuleException}, which the
 * controllers translate into their endpoint-specific failure envelopes so the
 * JSON contract is preserved byte-for-byte.</p>
 */
@Service
public class CustomerService
{

	/** Logger for credit-agency deadline diagnostics during customer create. */
	private static final Logger LOG = LoggerFactory
			.getLogger(CustomerService.class);

	/** Lowest acceptable year of birth, matching {@code CRECUST} ({@code < 1601}). */
	private static final int MIN_BIRTH_YEAR = 1601;

	/** Maximum acceptable age in years, matching {@code CRECUST} ({@code > 150}). */
	private static final int MAX_AGE_YEARS = 150;

	/** Inclusive upper bound of the random credit-score review window (days). */
	private static final int REVIEW_DAYS_MAX = 21;

	/** Width of the compact {@code DDMMYYYY} date-of-birth string. */
	private static final int DOB_STRING_WIDTH = 8;

	/**
	 * Fixed storage width of the customer name, matching COBOL
	 * {@code CUSTOMER-NAME PIC X(60)} / {@code COMM-NAME PIC X(60)} and the
	 * {@code customer.name VARCHAR(60)} column. An over-length value is truncated
	 * to this width before persist, reproducing the COBOL fixed-width
	 * {@code MOVE} (which silently truncates into a {@code PIC X(60)} field)
	 * rather than letting the database reject it &mdash; behavioural parity, not
	 * enhancement (AAP &sect;0.6/&sect;0.7).
	 */
	private static final int NAME_MAX_WIDTH = 60;

	/**
	 * Fixed storage width of the customer address, matching COBOL
	 * {@code CUSTOMER-ADDRESS PIC X(160)} / {@code COMM-ADDRESS PIC X(160)} and
	 * the {@code customer.address VARCHAR(160)} column. Truncated to this width
	 * before persist for the same COBOL fixed-width {@code MOVE} parity reason as
	 * {@link #NAME_MAX_WIDTH}.
	 */
	private static final int ADDRESS_MAX_WIDTH = 160;

	/**
	 * Number of credit agencies queried in parallel during create, reproducing
	 * the five identical COBOL programs {@code CRDTAGY1}&ndash;{@code CRDTAGY5}.
	 */
	private static final int NUMBER_OF_AGENCIES = 5;

	/**
	 * Fixed deadline, in seconds, to wait for agency replies before averaging
	 * whatever has arrived &mdash; the {@code EXEC CICS DELAY FOR SECONDS(3)}
	 * that the COBOL {@code CRECUST} issues after fanning the agencies out.
	 */
	private static final int CREDIT_CHECK_DEADLINE_SECONDS = 3;

	/** INQCUST sentinel: pick a random existing customer (COBOL {@code 0000000000}). */
	private static final long SENTINEL_RANDOM = 0L;

	/** INQCUST sentinel: return the highest customer (COBOL {@code 9999999999}). */
	private static final long SENTINEL_HIGHEST = 9_999_999_999L;

	/** Bounded number of random picks attempted for the {@code 0000000000} sentinel. */
	private static final int RANDOM_PICK_MAX_RETRIES = 10;

	/** Fail code: invalid customer title (CRECUST / UPDCUST). */
	private static final String FAIL_INVALID_TITLE = "T";

	/** Fail code: no credit agency replied within the deadline (CRECUST). */
	private static final String FAIL_NO_AGENCY = "C";

	/** Fail code: date of birth out of range (year too early or age over 150). */
	private static final String FAIL_DOB_RANGE = "O";

	/** Fail code: date of birth is not a valid calendar date (CRECUST). */
	private static final String FAIL_DOB_INVALID = "Z";

	/** Fail code: date of birth is in the future (CRECUST). */
	private static final String FAIL_DOB_FUTURE = "Y";

	/** Fail code: customer number could not be allocated/persisted (CRECUST/UPDCUST). */
	private static final String FAIL_PERSIST = "3";

	/** Fail code: customer not found (INQCUST / UPDCUST / DELCUS). */
	private static final String FAIL_NOT_FOUND = "1";

	/** Fail code: error reading the customer record (UPDCUST). */
	private static final String FAIL_READ_ERROR = "2";

	/** Fail code: nothing to update (both name and address blank, UPDCUST). */
	private static final String FAIL_NOTHING_TO_UPDATE = "4";

	/** Eye-catcher echoed in the create-customer response envelope. */
	private static final String CUSTOMER_EYECATCHER = "CUST";

	/** Response success flag value (COBOL {@code 'Y'}). */
	private static final String FLAG_SUCCESS = "Y";

	/** Response failure flag value (COBOL {@code 'N'}). */
	private static final String FLAG_FAILURE = "N";

	/** Empty fail code surfaced on a successful create (consumer treats non-empty as failure). */
	private static final String SUCCESS_FAIL_CODE = "";

	/** Inquiry fail code surfaced on success (COBOL leaves the fail code blank). */
	private static final String INQUIRY_SUCCESS_FAIL_CODE = " ";

	private final CustomerRepository customerRepository;

	private final CustomerControlRepository customerControlRepository;

	private final AccountRepository accountRepository;

	private final AccountService accountService;

	private final IdentityService identityService;

	private final CreditAgencyService creditAgencyService;

	private final ProcessedTransactionAppender proctranAppender;

	/**
	 * Source of randomness for the credit-score review-date offset (today +
	 * 1&ndash;21 days) and for the {@code INQCUST} {@code 0000000000}
	 * random-customer sentinel pick.
	 */
	private final Random random = new Random();

	/**
	 * Constructs the customer service with its collaborators (constructor
	 * injection only &mdash; no field injection).
	 *
	 * <p>The {@link AccountService} edge is the single permitted service-to-service
	 * dependency (used by the {@code DELCUS} cascade). {@code AccountService} does
	 * not inject {@code CustomerService} in return (it performs its own customer
	 * existence check through {@link CustomerRepository}), so no Spring
	 * construction cycle exists and no {@code @Lazy} indirection is required.</p>
	 *
	 * @param customerRepository        repository for {@link Customer} persistence
	 * @param customerControlRepository repository used to read the
	 *                                  {@code CUSTCTRL} control row for INQCUST
	 *                                  sentinel resolution
	 * @param accountRepository         repository used to list a customer's
	 *                                  accounts for the delete cascade
	 * @param accountService            account service used to delete each owned
	 *                                  account (with its own audit row) in the
	 *                                  cascade
	 * @param identityService           allocator of customer numbers (control-row
	 *                                  counter, gap-free)
	 * @param creditAgencyService       asynchronous credit-score provider
	 * @param proctranAppender          atomic PROCTRAN audit-row appender
	 */
	public CustomerService(CustomerRepository customerRepository,
			CustomerControlRepository customerControlRepository,
			AccountRepository accountRepository, AccountService accountService,
			IdentityService identityService,
			CreditAgencyService creditAgencyService,
			ProcessedTransactionAppender proctranAppender)
	{
		this.customerRepository = customerRepository;
		this.customerControlRepository = customerControlRepository;
		this.accountRepository = accountRepository;
		this.accountService = accountService;
		this.identityService = identityService;
		this.creditAgencyService = creditAgencyService;
		this.proctranAppender = proctranAppender;
	}

	/**
	 * Creates a customer, reproducing {@code CRECUST} (F-006).
	 *
	 * <p>The validation steps run <strong>before</strong> the customer number is
	 * allocated, so a thrown {@link BusinessRuleException} rolls back the
	 * transaction and restores the control-row counter (gap-free identity,
	 * ADR-003). The order is: title &rarr; credit check &rarr; date-of-birth
	 * &rarr; allocate &rarr; persist customer &rarr; append {@code OCC} PROCTRAN
	 * row &rarr; populate response.</p>
	 *
	 * @param form the create-customer request form supplying the customer name
	 *             (its first token must be a valid {@link Title} or blank), the
	 *             address, and the date of birth as a compact {@code DDMMYYYY}
	 *             string
	 * @return the fully populated {@link CreateCustomerJson} success envelope
	 * @throws BusinessRuleException {@code 'T'} invalid title, {@code 'C'} no
	 *                               credit-agency reply, {@code 'O'} birth year
	 *                               before {@value #MIN_BIRTH_YEAR} or age over
	 *                               {@value #MAX_AGE_YEARS}, {@code 'Z'} invalid
	 *                               calendar date, {@code 'Y'} date of birth in
	 *                               the future, or {@code '3'} if the number
	 *                               could not be allocated
	 */
	@Transactional(propagation = Propagation.REQUIRED,
			isolation = Isolation.READ_COMMITTED)
	public CreateCustomerJson createCustomer(CreateCustomerForm form)
	{
		// Truncate to the COBOL fixed-field widths (COMM-NAME PIC X(60),
		// COMM-ADDRESS PIC X(160)) up front, so the truncated value flows
		// identically into title validation, the entity, the PROCTRAN audit row,
		// and the response envelope -- exactly as the single fixed-width COBOL
		// commarea field behaves. This reproduces the COBOL MOVE's silent
		// truncation (behavioural parity) and prevents an over-length input from
		// reaching the VARCHAR column and being rejected by the database.
		String name = truncate(form.getCustName(), NAME_MAX_WIDTH);
		String address = truncate(form.getCustAddress(), ADDRESS_MAX_WIDTH);

		// 1. Title validation (CRECUST: fail 'T'). A blank title is valid.
		if (!Title.isValidTitle(firstToken(name)))
		{
			throw new BusinessRuleException(FAIL_INVALID_TITLE,
					"Invalid customer title: " + firstToken(name));
		}

		// 2. Asynchronous five-agency credit check (CRECUST: fail 'C' if none
		//    reply within the deadline) and the random credit-score review date.
		int creditScore = performCreditCheck();
		LocalDate reviewDate = LocalDate.now()
				.plusDays(1L + random.nextInt(REVIEW_DAYS_MAX));

		// 3. Date-of-birth validation (CRECUST DATE-OF-BIRTH-CHECK:
		//    'O' year < 1601, 'Z' invalid calendar date, 'O' age > 150,
		//    'Y' future), evaluated in the exact COBOL order.
		LocalDate dateOfBirth = validateAndParseDateOfBirth(form.getCustDob());

		// 4. Allocate the next customer number under the control-row lock
		//    (CRECUST: counter failure collapses to fail '3').
		long customerNumber;
		try
		{
			customerNumber = identityService.allocateCustomerNumber();
		}
		catch (DataAccessException ex)
		{
			throw new BusinessRuleException(FAIL_PERSIST,
					"Unable to allocate a customer number", ex);
		}

		// 5. Insert the customer, then append the create-customer audit row
		//    (OCC) atomically through the shared appender.
		Customer customer = new Customer();
		customer.setId(new CustomerId(BankConstants.SORT_CODE,
				pad10(customerNumber)));
		customer.setName(name);
		customer.setAddress(address);
		customer.setDateOfBirth(dateOfBirth);
		customer.setCreditScore((short) creditScore);
		customer.setCsReviewDate(reviewDate);
		try
		{
			customerRepository.save(customer);
		}
		catch (DataAccessException ex)
		{
			throw new BusinessRuleException(FAIL_PERSIST,
					"Unable to persist the new customer", ex);
		}
		proctranAppender.appendCustomerCreate(BankConstants.SORT_CODE,
				customerNumber, name, dateOfBirth);

		LOG.info("Customer created: {}", pad10(customerNumber));

		// 6. Populate the create-customer success envelope.
		return buildCreateResponse(customerNumber, name, address, dateOfBirth,
				creditScore, reviewDate);
	}

	/**
	 * Inquires on a customer, reproducing {@code INQCUST} (F-008).
	 *
	 * <p>The two sentinels are resolved from the {@code CUSTCTRL} control row,
	 * never with a {@code MAX()} table scan:</p>
	 * <ul>
	 *   <li>{@code 0000000000} &rarr; a random existing customer strictly within
	 *       the populated range (bounded retries, then a guaranteed fall back to
	 *       the highest customer);</li>
	 *   <li>{@code 9999999999} &rarr; the highest customer
	 *       ({@code LAST-CUSTOMER-NUMBER});</li>
	 *   <li>any other value &rarr; a direct key lookup.</li>
	 * </ul>
	 *
	 * <p>A miss is not an abend: the response carries success flag {@code 'N'}
	 * and fail code {@code '1'}, matching the COBOL
	 * {@code INQCUST-INQ-SUCCESS = 'N'} convention.</p>
	 *
	 * @param customerNumber the customer number, or a sentinel
	 * @return the populated {@link CustomerEnquiryJson} envelope (success or
	 *         not-found)
	 */
	@Transactional(readOnly = true)
	public CustomerEnquiryJson inquireCustomer(long customerNumber)
	{
		Customer customer;
		if (customerNumber == SENTINEL_RANDOM)
		{
			customer = pickRandomCustomer();
		}
		else if (customerNumber == SENTINEL_HIGHEST)
		{
			long highest = highestCustomerNumber();
			customer = (highest <= 0L) ? null
					: customerRepository
							.findById(new CustomerId(BankConstants.SORT_CODE,
									pad10(highest)))
							.orElse(null);
		}
		else
		{
			customer = customerRepository
					.findById(new CustomerId(BankConstants.SORT_CODE,
							pad10(customerNumber)))
					.orElse(null);
		}
		return buildEnquiryResponse(customerNumber, customer);
	}

	/**
	 * Updates a customer's name and/or address, reproducing {@code UPDCUST}
	 * (F-011). The operation is deliberately restricted: it changes only the
	 * name and address, never the date of birth, credit score or review date,
	 * and it writes <em>no</em> PROCTRAN row (an update is not a financial
	 * movement).
	 *
	 * <p>The steps follow the exact {@code UPDCUST.cbl} order: validate the title
	 * first (fail {@code 'T'}), read the customer (fail {@code '1'} not found,
	 * {@code '2'} read error), apply the blank rules (fail {@code '4'} when both
	 * name and address are blank), then rewrite (fail {@code '3'}).</p>
	 *
	 * <p>Blank rules, matching {@code UPDCUST} exactly &mdash; a field is
	 * &quot;provided&quot; when it is non-empty and does not start with a
	 * space:</p>
	 * <ul>
	 *   <li>both name and address blank &rarr; fail {@code '4'};</li>
	 *   <li>name blank, address provided &rarr; update the address only;</li>
	 *   <li>address blank, name provided &rarr; update the name only;</li>
	 *   <li>both provided &rarr; update both.</li>
	 * </ul>
	 *
	 * @param form the update-customer request form supplying the customer number
	 *             and the new name and/or address
	 * @return the populated {@link UpdateCustomerJson} success envelope
	 * @throws BusinessRuleException {@code 'T'} invalid title, {@code '1'}
	 *                               customer not found, {@code '2'} read error,
	 *                               {@code '4'} neither name nor address
	 *                               supplied, or {@code '3'} on a persistence
	 *                               failure
	 */
	@Transactional(propagation = Propagation.REQUIRED,
			isolation = Isolation.READ_COMMITTED)
	public UpdateCustomerJson updateCustomer(UpdateCustomerForm form)
	{
		long customerNumber = parseCustomerNumber(form.getCustNumber());
		// Truncate to the COBOL fixed-field widths (COMM-NAME PIC X(60),
		// COMM-ADDRESS PIC X(160)) before the blank rules and the rewrite, so an
		// over-length name or address is silently truncated (COBOL MOVE parity)
		// instead of reaching the VARCHAR column and being rejected by the
		// database. truncate(...) is null-safe, so a null field is preserved for
		// the blank-rule and title checks below.
		String newName = truncate(form.getCustName(), NAME_MAX_WIDTH);
		String newAddress = truncate(form.getCustAddress(), ADDRESS_MAX_WIDTH);

		// 1. Title validation first (UPDCUST order). A blank name yields a blank
		//    title token, which is valid.
		if (!Title.isValidTitle(firstToken(newName)))
		{
			throw new BusinessRuleException(FAIL_INVALID_TITLE,
					"Invalid customer title: " + firstToken(newName));
		}

		// 2. Read the customer: not found -> '1', read error -> '2'.
		Customer customer;
		try
		{
			customer = customerRepository
					.findById(new CustomerId(BankConstants.SORT_CODE,
							pad10(customerNumber)))
					.orElse(null);
		}
		catch (DataAccessException ex)
		{
			throw new BusinessRuleException(FAIL_READ_ERROR,
					"Error reading customer " + customerNumber, ex);
		}
		if (customer == null)
		{
			throw new BusinessRuleException(FAIL_NOT_FOUND,
					"Customer not found: " + customerNumber);
		}

		// 3. Blank rules (UPDCUST): reject when both are blank, otherwise update
		//    whichever field(s) were supplied.
		boolean nameProvided = isProvided(newName);
		boolean addressProvided = isProvided(newAddress);
		if (!nameProvided && !addressProvided)
		{
			throw new BusinessRuleException(FAIL_NOTHING_TO_UPDATE,
					"Neither name nor address supplied for update");
		}
		if (nameProvided)
		{
			customer.setName(newName);
		}
		if (addressProvided)
		{
			customer.setAddress(newAddress);
		}

		// 4. Rewrite the customer (UPDCUST: fail '3' on a REWRITE error).
		//    No PROCTRAN row is written; DOB / credit score / review date are
		//    never touched.
		Customer saved;
		try
		{
			saved = customerRepository.save(customer);
		}
		catch (DataAccessException ex)
		{
			throw new BusinessRuleException(FAIL_PERSIST,
					"Error updating customer " + customerNumber, ex);
		}

		LOG.info("Customer updated: {}", saved.getId().getCustomerNumber());

		// 5. Populate the update-customer success envelope.
		return buildUpdateResponse(saved);
	}

	/**
	 * Deletes a customer and all of its accounts, reproducing {@code DELCUS}
	 * (F-014).
	 *
	 * <p>The steps follow the exact {@code DELCUS.cbl} order: read the customer
	 * (fail {@code '1'} if missing), capture its details for the audit row and
	 * the response <em>before</em> deletion, cascade-delete each owned account
	 * via {@link AccountService#deleteAccount(com.ibm.cics.cip.bank.core.entity.AccountId)}
	 * (which captures the terminal balance and appends its own {@code ODA}
	 * record), remove the customer row, then append an {@code ODC} PROCTRAN row.
	 * The customer counter is deliberately <em>not</em> decremented &mdash;
	 * {@code DELCUS.cbl} leaves {@code NUMBER-OF-CUSTOMERS} unchanged, preserving
	 * the high-water mark for gap-free allocation.</p>
	 *
	 * <p>Every step runs in this one transaction, so a failure after deletions
	 * have begun rolls all of them back together (the COBOL abended for the same
	 * reason), keeping the customer, accounts and PROCTRAN audit in step.</p>
	 *
	 * @param customerNumber the customer number to delete
	 * @return the populated {@link DeleteCustomerJson} success envelope capturing
	 *         the deleted customer's details
	 * @throws BusinessRuleException {@code '1'} if the customer does not exist
	 */
	@Transactional(propagation = Propagation.REQUIRED,
			isolation = Isolation.READ_COMMITTED)
	public DeleteCustomerJson deleteCustomer(long customerNumber)
	{
		String paddedCustomerNumber = pad10(customerNumber);
		// Read the customer under a PESSIMISTIC_WRITE lock (DELCUS holds the
		// record from read through delete). This serialises two concurrent
		// DELCUS requests for the same customer: the first commits the whole
		// cascade, and the second re-reads after the winner commits, finds the
		// customer already gone, and falls through to the fail '1' below at
		// HTTP 200 — instead of both reading via a non-locking findById and the
		// loser colliding at flush with a StaleObjectStateException surfaced as
		// HTTP 500 (frozen "always HTTP 200 business envelope" contract; the
		// cascade's per-account deletes already lock each account row via
		// AccountService.deleteAccount -> AccountRepository.findByIdForUpdate).
		Customer customer = customerRepository
				.findByIdForUpdate(new CustomerId(BankConstants.SORT_CODE,
						paddedCustomerNumber))
				.orElseThrow(() -> new BusinessRuleException(FAIL_NOT_FOUND,
						"Customer not found: " + customerNumber));

		// Snapshot the customer details for the audit row and the response
		// before the row is removed.
		String sortCode = customer.getId().getSortCode();
		String custno = customer.getId().getCustomerNumber();
		String name = customer.getName();
		String address = customer.getAddress();
		LocalDate dateOfBirth = customer.getDateOfBirth();
		LocalDate reviewDate = customer.getCsReviewDate();
		Short creditScore = customer.getCreditScore();

		// Cascade-delete the customer's accounts (DELCUS DELETE-ACCOUNTS); each
		// AccountService.deleteAccount call runs in this same transaction and
		// appends its own ODA audit row. Exceptions propagate so the whole unit
		// of work rolls back together.
		List<Account> accounts = accountRepository
				.findByIdSortCodeAndCustomerNumberOrderByIdAccountNumberAsc(
						BankConstants.SORT_CODE, paddedCustomerNumber);
		for (Account account : accounts)
		{
			accountService.deleteAccount(account.getId());
		}

		// Remove the customer row, then append the delete-customer audit row
		// (ODC) atomically. The customer counter is NOT decremented.
		customerRepository.delete(customer);
		proctranAppender.appendCustomerDelete(BankConstants.SORT_CODE,
				customerNumber, name, dateOfBirth);

		LOG.info("Customer deleted: {}", custno);

		// Populate the delete-customer success envelope from the snapshot.
		return buildDeleteResponse(sortCode, custno, name, address, dateOfBirth,
				reviewDate, creditScore);
	}

	// ------------------------------------------------------------------ //
	// Credit check (CRECUST credit-agency fan-out, F-017)                //
	// ------------------------------------------------------------------ //

	/**
	 * Runs the asynchronous five-agency credit check and returns the averaged
	 * score, reproducing the {@code CRECUST} credit-check section (F-017).
	 *
	 * <p>{@value #NUMBER_OF_AGENCIES} concurrent
	 * {@link CreditAgencyService#requestCreditScore()} tasks are fanned out onto
	 * the dedicated credit-agency executor; the method then waits up to
	 * {@value #CREDIT_CHECK_DEADLINE_SECONDS} seconds for them to complete (the
	 * COBOL {@code EXEC CICS DELAY FOR SECONDS(3)}). The scores of the agencies
	 * that finished within the deadline are averaged with truncating integer
	 * division, exactly matching the COBOL
	 * {@code COMPUTE WS-ACTUAL-CS-SCR = WS-TOTAL-CS-SCR / WS-RETRIEVED-CNT}. A
	 * deadline timeout is expected and benign &mdash; whatever replied is
	 * averaged. An agency that completed exceptionally is not counted. If
	 * <em>no</em> agency replied in time the create fails with code
	 * {@value #FAIL_NO_AGENCY} (the canonical no-credit-data outcome onto which
	 * the COBOL CICS-infrastructure fail codes collapse).</p>
	 *
	 * @return the averaged credit score (1&ndash;999) of the agencies that
	 *         replied within the deadline
	 * @throws BusinessRuleException {@value #FAIL_NO_AGENCY} if no agency replied
	 *                               within the deadline, or if the wait was
	 *                               interrupted
	 */
	private int performCreditCheck()
	{
		List<CompletableFuture<Integer>> futures = new ArrayList<>(
				NUMBER_OF_AGENCIES);
		for (int agency = 0; agency < NUMBER_OF_AGENCIES; agency++)
		{
			try
			{
				futures.add(creditAgencyService.requestCreditScore());
			}
			catch (RejectedExecutionException rejected)
			{
				// The dedicated credit-agency executor is saturated (all threads
				// busy and the bounded queue full), so this agency task could not
				// even be started. Treat it exactly like an agency that did not
				// reply: do not add a future for it. If every agency is rejected
				// the futures list is empty, the deadline wait below returns
				// immediately, retrieved stays zero, and the create degrades to
				// fail code 'C' -- the intended "no agency replied" outcome
				// (F-006/F-017) -- instead of surfacing a raw
				// RejectedExecutionException as an HTTP 500. Spring's
				// TaskRejectedException is a RejectedExecutionException, so this
				// catch covers it too.
				LOG.debug(
						"Credit-agency executor saturated; agency request "
								+ "rejected and treated as no reply");
			}
		}

		// Wait for the fixed deadline, mirroring EXEC CICS DELAY FOR SECONDS(3).
		CompletableFuture<Void> all = CompletableFuture
				.allOf(futures.toArray(new CompletableFuture[0]));
		try
		{
			all.get(CREDIT_CHECK_DEADLINE_SECONDS, TimeUnit.SECONDS);
		}
		catch (TimeoutException timeout)
		{
			// Expected and benign: one or more agencies did not reply in time,
			// so we fall through and average whatever did arrive.
			LOG.debug(
					"Credit-agency deadline of {}s reached before all replied; "
							+ "averaging those that did",
					CREDIT_CHECK_DEADLINE_SECONDS);
		}
		catch (InterruptedException interrupted)
		{
			Thread.currentThread().interrupt();
			throw new BusinessRuleException(FAIL_NO_AGENCY,
					"Interrupted while awaiting credit-agency replies");
		}
		catch (ExecutionException execution)
		{
			// An individual agency failing is tolerated; the per-future
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
			throw new BusinessRuleException(FAIL_NO_AGENCY,
					"No credit agency responded within the deadline");
		}

		// Truncating integer division, exactly as the COBOL COMPUTE does.
		return (int) (total / retrieved);
	}

	// ------------------------------------------------------------------ //
	// Date-of-birth validation (CRECUST DATE-OF-BIRTH-CHECK)             //
	// ------------------------------------------------------------------ //

	/**
	 * Validates and parses the compact {@code DDMMYYYY} date-of-birth string
	 * exactly as {@code CRECUST} does, in the COBOL evaluation order: year before
	 * {@value #MIN_BIRTH_YEAR} &rarr; {@code 'O'}; not a valid calendar date
	 * &rarr; {@code 'Z'}; age over {@value #MAX_AGE_YEARS} &rarr; {@code 'O'};
	 * date in the future &rarr; {@code 'Y'}. A null, wrong-length or non-numeric
	 * value is treated as an invalid date ({@code 'Z'}).
	 *
	 * @param dobString the date of birth as a {@code DDMMYYYY} string
	 * @return the parsed, validated {@link LocalDate}
	 * @throws BusinessRuleException {@code 'O'}, {@code 'Z'} or {@code 'Y'} per
	 *                               the rules above
	 */
	private LocalDate validateAndParseDateOfBirth(String dobString)
	{
		if (dobString == null)
		{
			throw new BusinessRuleException(FAIL_DOB_INVALID,
					"Date of birth is not a valid date");
		}
		String trimmed = dobString.trim();
		if (trimmed.length() != DOB_STRING_WIDTH
				|| !trimmed.chars().allMatch(Character::isDigit))
		{
			throw new BusinessRuleException(FAIL_DOB_INVALID,
					"Date of birth is not a valid eight-digit DDMMYYYY date");
		}

		int day = Integer.parseInt(trimmed.substring(0, 2));
		int month = Integer.parseInt(trimmed.substring(2, 4));
		int year = Integer.parseInt(trimmed.substring(4, 8));

		// COBOL checks the year bound before validating the calendar date.
		if (year < MIN_BIRTH_YEAR)
		{
			throw new BusinessRuleException(FAIL_DOB_RANGE,
					"Date of birth year is before " + MIN_BIRTH_YEAR);
		}

		LocalDate dateOfBirth;
		try
		{
			dateOfBirth = LocalDate.of(year, month, day);
		}
		catch (DateTimeException ex)
		{
			throw new BusinessRuleException(FAIL_DOB_INVALID,
					"Date of birth is not a valid calendar date", ex);
		}

		LocalDate today = LocalDate.now();
		if (today.getYear() - year > MAX_AGE_YEARS)
		{
			throw new BusinessRuleException(FAIL_DOB_RANGE,
					"Customer age exceeds " + MAX_AGE_YEARS + " years");
		}
		if (dateOfBirth.isAfter(today))
		{
			throw new BusinessRuleException(FAIL_DOB_FUTURE,
					"Date of birth is in the future");
		}
		return dateOfBirth;
	}

	// ------------------------------------------------------------------ //
	// INQCUST sentinel resolution (control row, never MAX() scan)        //
	// ------------------------------------------------------------------ //

	/**
	 * Resolves the {@code 0000000000} sentinel by picking a random existing
	 * customer, reproducing the {@code INQCUST GENERATE-RANDOM-CUSTOMER} logic.
	 *
	 * <p>The upper bound is read from the {@code CUSTCTRL} control row
	 * ({@code LAST-CUSTOMER-NUMBER}), never with a {@code MAX()} scan. Up to
	 * {@value #RANDOM_PICK_MAX_RETRIES} random candidates in the populated range
	 * are tried; if every candidate misses (the range can be sparse after
	 * deletes) the highest customer &mdash; which is guaranteed to exist whenever
	 * the bound is positive &mdash; is returned as a deterministic fall back.</p>
	 *
	 * @return a random existing {@link Customer}, or {@code null} if no customers
	 *         exist
	 */
	private Customer pickRandomCustomer()
	{
		long highest = highestCustomerNumber();
		if (highest <= 0L)
		{
			return null;
		}
		for (int attempt = 0; attempt < RANDOM_PICK_MAX_RETRIES; attempt++)
		{
			// Integer-only random selection in the inclusive range [1, highest].
			// Rule U1 forbids double/float ANYWHERE in bank-core, so the prior
			// 1L + (long)(random.nextDouble() * highest) form is replaced with
			// the bounded RandomGenerator#nextLong(origin, bound) - the bound is
			// exclusive, hence highest + 1L gives the inclusive upper bound.
			// The highest > 0 guard above ensures origin < bound, and 'highest'
			// is a CUSTCTRL customer number (<= 10 digits) so highest + 1L can
			// never overflow a long. nextLong already returns <= highest, so the
			// previous explicit clamp is no longer needed.
			long candidate = random.nextLong(1L, highest + 1L);
			Optional<Customer> found = customerRepository.findById(
					new CustomerId(BankConstants.SORT_CODE, pad10(candidate)));
			if (found.isPresent())
			{
				return found.get();
			}
		}
		// Deterministic fall back: the highest customer always exists when the
		// control-row bound is positive.
		return customerRepository
				.findById(new CustomerId(BankConstants.SORT_CODE,
						pad10(highest)))
				.orElse(null);
	}

	/**
	 * Reads the highest allocated customer number from the {@code CUSTCTRL}
	 * control row ({@code LAST-CUSTOMER-NUMBER}) without a {@code MAX()} scan.
	 *
	 * @return the highest customer number, or {@code 0} if the control row is
	 *         absent or carries no value
	 */
	private long highestCustomerNumber()
	{
		return customerControlRepository.findById(BankConstants.SORT_CODE)
				.map(control -> control.getLastCustomerNumber() == null ? 0L
						: control.getLastCustomerNumber())
				.orElse(0L);
	}

	// ------------------------------------------------------------------ //
	// Response-envelope builders (frozen wire contract, F-019)           //
	// ------------------------------------------------------------------ //

	/**
	 * Builds the create-customer success envelope, populating the inner
	 * {@link CrecustJson} commarea exactly as the frozen contract requires.
	 *
	 * @param customerNumber the allocated customer number
	 * @param name           the customer name
	 * @param address        the customer address
	 * @param dateOfBirth    the validated date of birth
	 * @param creditScore    the averaged credit score
	 * @param reviewDate     the credit-score review date
	 * @return the populated {@link CreateCustomerJson} envelope
	 */
	private CreateCustomerJson buildCreateResponse(long customerNumber,
			String name, String address, LocalDate dateOfBirth, int creditScore,
			LocalDate reviewDate)
	{
		CrecustJson out = new CrecustJson();
		out.setCommEyecatcher(CUSTOMER_EYECATCHER);
		out.setCommKey(new CommKey(Integer.parseInt(BankConstants.SORT_CODE),
				customerNumber));
		out.setCommName(name);
		out.setCommAddress(address);
		out.setCommDateOfBirth(dateToString(dateOfBirth));
		out.setCommCreditScore(creditScore);
		out.setCommCsReviewDate(dateToString(reviewDate));
		out.setCommSuccess(FLAG_SUCCESS);
		out.setCommFailCode(SUCCESS_FAIL_CODE);
		return new CreateCustomerJson(out);
	}

	/**
	 * Builds the customer-enquiry envelope from a resolved customer, or a
	 * not-found envelope ({@code 'N'} / {@code '1'}) when the lookup missed.
	 *
	 * @param requestedNumber the customer number that was requested (echoed on a
	 *                        miss)
	 * @param customer        the resolved customer, or {@code null}
	 * @return the populated {@link CustomerEnquiryJson} envelope
	 */
	private CustomerEnquiryJson buildEnquiryResponse(long requestedNumber,
			Customer customer)
	{
		InqCustZJson inq = new InqCustZJson();
		if (customer == null)
		{
			inq.setInqCustScode(BankConstants.SORT_CODE);
			inq.setInqCustCustno(pad10(requestedNumber));
			inq.setInqCustInqSuccess(FLAG_FAILURE);
			inq.setInqCustInqFailCd(FAIL_NOT_FOUND);
		}
		else
		{
			inq.setInqCustEye(CUSTOMER_EYECATCHER);
			inq.setInqCustScode(customer.getId().getSortCode());
			inq.setInqCustCustno(customer.getId().getCustomerNumber());
			inq.setInqCustName(customer.getName());
			inq.setInqCustAddress(customer.getAddress());
			inq.setInqCustDob(toDobComponent(customer.getDateOfBirth()));
			inq.setInqCustCreditScore(customer.getCreditScore() == null ? 0
					: customer.getCreditScore().intValue());
			inq.setInqCustCsReviewDate(
					toReviewDateComponent(customer.getCsReviewDate()));
			inq.setInqCustInqSuccess(FLAG_SUCCESS);
			inq.setInqCustInqFailCd(INQUIRY_SUCCESS_FAIL_CODE);
		}
		CustomerEnquiryJson envelope = new CustomerEnquiryJson();
		envelope.setInqCustZ(inq);
		return envelope;
	}

	/**
	 * Builds the update-customer success envelope echoing the persisted
	 * customer.
	 *
	 * @param customer the persisted customer
	 * @return the populated {@link UpdateCustomerJson} envelope
	 */
	private UpdateCustomerJson buildUpdateResponse(Customer customer)
	{
		UpdcustJson out = new UpdcustJson();
		out.setCommSortcode(customer.getId().getSortCode());
		out.setCommCustno(customer.getId().getCustomerNumber());
		out.setCommName(customer.getName());
		out.setCommAddress(customer.getAddress());
		out.setCommDateOfBirth(dateToInt(customer.getDateOfBirth()));
		out.setCommCreditScore(customer.getCreditScore() == null ? 0
				: customer.getCreditScore().intValue());
		out.setCommCreditScoreReviewDate(dateToInt(customer.getCsReviewDate()));
		out.setCommUpdateSuccess(FLAG_SUCCESS);
		out.setCommUpdateFailCode(SUCCESS_FAIL_CODE);
		UpdateCustomerJson wrapper = new UpdateCustomerJson();
		wrapper.setUpdcust(out);
		return wrapper;
	}

	/**
	 * Builds the delete-customer success envelope from the snapshot captured
	 * before deletion.
	 *
	 * @param sortCode    the sort code
	 * @param custno      the (zero-padded) customer number
	 * @param name        the customer name
	 * @param address     the customer address
	 * @param dateOfBirth the date of birth
	 * @param reviewDate  the credit-score review date
	 * @param creditScore the credit score (may be {@code null})
	 * @return the populated {@link DeleteCustomerJson} envelope
	 */
	private DeleteCustomerJson buildDeleteResponse(String sortCode,
			String custno, String name, String address, LocalDate dateOfBirth,
			LocalDate reviewDate, Short creditScore)
	{
		DelcusJson out = new DelcusJson();
		out.setCommSortcode(sortCode);
		out.setCommCustno(custno);
		out.setCommName(name);
		out.setCommAddress(address);
		out.setCommDateOfBirth(dateToString(dateOfBirth));
		out.setCommCsReviewDate(dateToString(reviewDate));
		out.setCommCreditScore(
				creditScore == null ? 0 : creditScore.intValue());
		out.setCommDelFailCode("0");
		out.setCommDelSuccess(FLAG_SUCCESS);
		return new DeleteCustomerJson(out);
	}

	/**
	 * Builds the nested date-of-birth component (day/month/year) for the enquiry
	 * envelope; a {@code null} date yields a zero-valued component.
	 *
	 * @param date the date of birth, or {@code null}
	 * @return the populated {@link InqCustDob}
	 */
	private InqCustDob toDobComponent(LocalDate date)
	{
		InqCustDob dob = new InqCustDob();
		if (date != null)
		{
			dob.setInqCustDobDd(date.getDayOfMonth());
			dob.setInqCustDobMm(date.getMonthValue());
			dob.setInqCustDobYyyy(date.getYear());
		}
		return dob;
	}

	/**
	 * Builds the nested credit-score review-date component (day/month/year) for
	 * the enquiry envelope; a {@code null} date yields a zero-valued component.
	 *
	 * @param date the review date, or {@code null}
	 * @return the populated {@link InqCustReviewDate}
	 */
	private InqCustReviewDate toReviewDateComponent(LocalDate date)
	{
		InqCustReviewDate review = new InqCustReviewDate();
		if (date != null)
		{
			review.setInqCustCsReviewDd(date.getDayOfMonth());
			review.setInqCustCsReviewMm(date.getMonthValue());
			review.setInqCustCsReviewYyyy(date.getYear());
		}
		return review;
	}

	// ------------------------------------------------------------------ //
	// Formatting / parsing helpers (inlined; no util import needed)      //
	// ------------------------------------------------------------------ //

	/**
	 * Formats a customer number as a fixed-width, ten-character, left-zero-padded
	 * string for the {@code CHAR(10)} entity key (matches
	 * {@code BankFormat.customerNumber}).
	 *
	 * @param value the customer number
	 * @return the ten-character, zero-padded string
	 */
	private String pad10(long value)
	{
		return String.format("%010d", value);
	}

	/**
	 * Encodes a date as the eight-character {@code DDMMYYYY} string used by the
	 * String-typed envelope date fields (matches {@code DtoFormat.dateToString};
	 * a {@code null} date encodes as the absent sentinel {@code "0"}).
	 *
	 * @param date the date, or {@code null}
	 * @return the {@code DDMMYYYY} string, or {@code "0"}
	 */
	private String dateToString(LocalDate date)
	{
		if (date == null)
		{
			return "0";
		}
		return String.format("%02d%02d%04d", date.getDayOfMonth(),
				date.getMonthValue(), date.getYear());
	}

	/**
	 * Encodes a date as the integer {@code DDMMYYYY} value used by the
	 * {@code int}-typed update envelope date fields (matches
	 * {@code DtoFormat.dateToInt}; a leading zero on the day is naturally
	 * dropped, and a {@code null} date encodes as {@code 0}).
	 *
	 * @param date the date, or {@code null}
	 * @return the {@code DDMMYYYY} integer, or {@code 0}
	 */
	private int dateToInt(LocalDate date)
	{
		if (date == null)
		{
			return 0;
		}
		return Integer.parseInt(dateToString(date));
	}

	/**
	 * Parses a customer number supplied as a string (trimming surrounding
	 * whitespace), reproducing the controller's {@code Long.parseLong} of the
	 * commarea customer-number field.
	 *
	 * @param value the customer number string
	 * @return the parsed customer number
	 * @throws BusinessRuleException {@code '1'} if the value is null or not a
	 *                               valid number (treated as not found)
	 */
	private long parseCustomerNumber(String value)
	{
		if (value == null)
		{
			throw new BusinessRuleException(FAIL_NOT_FOUND,
					"Customer number not supplied");
		}
		try
		{
			return Long.parseLong(value.trim());
		}
		catch (NumberFormatException ex)
		{
			throw new BusinessRuleException(FAIL_NOT_FOUND,
					"Invalid customer number: " + value, ex);
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
	 * Reports whether a field value is &quot;provided&quot; for the
	 * {@code UPDCUST} blank rules: non-null, non-empty, and not starting with a
	 * space (the COBOL {@code field = SPACES OR field(1:1) = ' '} test, negated).
	 *
	 * @param value the value to test
	 * @return {@code true} if the value is supplied
	 */
	private boolean isProvided(String value)
	{
		return value != null && !value.isEmpty() && value.charAt(0) != ' ';
	}

	/**
	 * Truncates a value to a fixed maximum width, reproducing the COBOL
	 * fixed-width {@code MOVE} into a {@code PIC X(n)} field &mdash; which keeps
	 * the leftmost {@code n} characters and silently discards the rest rather
	 * than raising an error.
	 *
	 * <p>This is behavioural parity, not enhancement (AAP &sect;0.6/&sect;0.7):
	 * the legacy commarea fields {@code COMM-NAME PIC X(60)} and
	 * {@code COMM-ADDRESS PIC X(160)} are fixed width, so an over-length value
	 * could never overflow them. Applying the same ceiling here keeps an
	 * over-length name or address from reaching the {@code VARCHAR(60)} /
	 * {@code VARCHAR(160)} column and being rejected by the database. The method
	 * is {@code null}-safe (a {@code null} or already-short value is returned
	 * unchanged) so the caller's blank-rule and title checks see the value
	 * exactly as supplied.</p>
	 *
	 * @param value    the value to truncate, or {@code null}
	 * @param maxWidth the fixed field width (the COBOL {@code PIC X(maxWidth)})
	 * @return the value truncated to at most {@code maxWidth} characters, or the
	 *         original value when it is {@code null} or already within the width
	 */
	private String truncate(String value, int maxWidth)
	{
		if (value == null || value.length() <= maxWidth)
		{
			return value;
		}
		return value.substring(0, maxWidth);
	}

}
