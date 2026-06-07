/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.core.bootstrap;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.ibm.cics.cip.bank.core.constants.BankConstants;
import com.ibm.cics.cip.bank.core.domain.TransactionType;
import com.ibm.cics.cip.bank.core.entity.Account;
import com.ibm.cics.cip.bank.core.entity.AccountControl;
import com.ibm.cics.cip.bank.core.entity.AccountId;
import com.ibm.cics.cip.bank.core.entity.Customer;
import com.ibm.cics.cip.bank.core.entity.CustomerId;
import com.ibm.cics.cip.bank.core.entity.ProcessedTransaction;
import com.ibm.cics.cip.bank.core.entity.ProcessedTransactionId;
import com.ibm.cics.cip.bank.core.repository.AccountControlRepository;
import com.ibm.cics.cip.bank.core.repository.AccountRepository;
import com.ibm.cics.cip.bank.core.repository.CustomerRepository;
import com.ibm.cics.cip.bank.core.repository.ProcessedTransactionRepository;
import com.ibm.cics.cip.bank.core.service.IdentityService;

/**
 * Startup sample-data generator &mdash; the Java port of the legacy COBOL
 * {@code BANKDATA} batch program ({@code src/base/cobol_src/BANKDATA.cbl}).
 *
 * <p>{@code BANKDATA} populated the VSAM/Db2 store with a configurable number of
 * randomly generated customers, each owning between one and five accounts, so
 * that a freshly installed bank had realistic data to exercise. This bean
 * reproduces that behaviour for the relational ({@code PostgreSQL}) runtime as a
 * Spring Boot {@link CommandLineRunner}: when enabled it seeds the database with
 * {@code bank.seed.customers} customers (default {@code 1000}), allocating every
 * identity through {@link IdentityService} (gap-free counter rows) and appending
 * a {@code PROCTRAN} audit row for each create exactly as the live Java create
 * flows do.</p>
 *
 * <h2>Behavioural parity with {@code BANKDATA} (preserved verbatim)</h2>
 * <ul>
 *   <li>One to five accounts per customer, assigned <strong>by ordinal</strong>
 *       from the fixed account-type / interest-rate / overdraft table
 *       ({@code DEFINE-ACC} / {@code POPULATE-ACC}, {@code INITIALISE-ARRAYS}).</li>
 *   <li>Name = {@code title forename initial surname}; address =
 *       {@code houseNo streetTree streetRoad, town} &mdash; all picked at random
 *       from the working-storage sample arrays.</li>
 *   <li>Date of birth: day {@code 1-28}, month {@code 1-12}, year
 *       {@code 1900-2000} (day capped at 28 so every month is valid, exactly as
 *       the COBOL does).</li>
 *   <li>Credit score {@code 1-999}; credit-score review date = today plus a
 *       random {@code 1-21} days.</li>
 *   <li>Account opened date ({@code GENERATE-OPENED-DATE}): day {@code 1-28},
 *       month {@code 1-12}, year in {@code [birthYear, 2014]} that must be
 *       strictly greater than the birth year &mdash; retried up to 100 times,
 *       then falling back to the date of birth.</li>
 *   <li>Statement dates hard-coded to {@code 2021-07-01} (last) and
 *       {@code 2021-08-01} (next).</li>
 *   <li>Balances: a random {@code 1-999999} value applied to <em>both</em> the
 *       available and the actual balance; <strong>negated</strong> for
 *       {@code LOAN} and {@code MORTGAGE} accounts.</li>
 * </ul>
 *
 * <h2>Deliberate divergences from {@code BANKDATA}</h2>
 * <ol>
 *   <li><strong>Non-destructive idempotency instead of {@code DELETE}.</strong>
 *       {@code BANKDATA} began by deleting every existing ACCOUNT/CUSTOMER row
 *       ({@code DELETE-DB2-ROWS}). The relational store is owned by Flyway and
 *       JPA, so this seeder must never destroy data: if any customer already
 *       exists it logs and returns, preventing duplicate seeding and identity
 *       counter drift on restart.</li>
 *   <li><strong>{@code PROCTRAN} create rows are appended.</strong> The legacy
 *       {@code BANKDATA} writes <em>no</em> {@code PROCTRAN} records (it writes
 *       only CUSTOMER, ACCOUNT and CONTROL rows). The Agent Action Plan folder
 *       specification has primary authority and mandates that the seeder
 *       "append PROCTRAN create records exactly as the create flows do," so that
 *       seeded data is indistinguishable from data created at runtime. This bean
 *       therefore appends one create-customer row (type {@code ICC}) per
 *       customer and one create-account row (type {@code ICA}) per account. The
 *       live branch flows use the branch-channel variants {@code OCC}/{@code OCA}
 *       via {@code ProcessedTransactionAppender}; this seeder uses the
 *       <em>web</em>-channel variants {@code ICC}/{@code ICA} as the prompt
 *       explicitly directs, while reproducing every other field (reference
 *       allocation, fixed-width description layout, amount, date/time, deleted
 *       flag) identically to the appender.</li>
 * </ol>
 *
 * <h2>Cross-cutting rules honoured by this class</h2>
 * <ul>
 *   <li><strong>Identity only via {@link IdentityService}.</strong> Every
 *       account and customer number is allocated by incrementing a control row
 *       under a pessimistic lock; no database {@code IDENTITY}/{@code SEQUENCE},
 *       {@code @GeneratedValue} or {@code MAX()+1} scheme is used (ADR-003).</li>
 *   <li><strong>Money only via {@link BigDecimal}.</strong> All monetary values
 *       are {@code BigDecimal} at scale 2 produced with
 *       {@link RoundingMode#HALF_UP}; {@code double}/{@code float} are never
 *       used anywhere in this file.</li>
 *   <li><strong>Dates only via {@code java.time}.</strong></li>
 *   <li><strong>Programmatic transactions.</strong> Each customer (with its
 *       accounts and {@code PROCTRAN} rows) is created inside its own
 *       {@link TransactionTemplate} callback. This is required because
 *       {@link IdentityService}'s allocation methods are
 *       {@code @Transactional(MANDATORY)} and must run inside an active
 *       transaction; a self-invoked {@code @Transactional} helper on this same
 *       bean would be bypassed by the Spring proxy, so a programmatic boundary
 *       is used instead.</li>
 *   <li><strong>Gated OFF by default.</strong> The bean is annotated
 *       {@link Profile @Profile("seed")} so it only runs when the {@code seed}
 *       profile is active &mdash; it is inert (and therefore safe) in
 *       production unless explicitly enabled.</li>
 * </ul>
 */
@Component
@Profile("seed")
public class BankDataSeeder implements CommandLineRunner
{

	/** SLF4J logger (the Spring Boot standard logging facade). */
	private static final Logger LOGGER = LoggerFactory
			.getLogger(BankDataSeeder.class);

	// -----------------------------------------------------------------------
	// Fixed-width field widths (mirrors the COBOL display-numeric layouts and
	// the create-flow conventions in ProcessedTransactionAppender / BankFormat).
	// -----------------------------------------------------------------------

	/** Customer number width &mdash; COBOL {@code 9(10)}. */
	private static final int CUSTOMER_NUMBER_WIDTH = 10;

	/** Account number width &mdash; COBOL {@code 9(8)}. */
	private static final int ACCOUNT_NUMBER_WIDTH = 8;

	/** {@code PROCTRAN} reference width &mdash; COBOL {@code 9(12)}. */
	private static final int REFERENCE_WIDTH = 12;

	/** {@code PROCTRAN} description width &mdash; COBOL {@code X(40)}. */
	private static final int DESCRIPTION_WIDTH = 40;

	/** Width of the customer-name slot inside the create-customer description. */
	private static final int NAME_DESC_WIDTH = 14;

	/** Width of the account-type slot inside the create-account description. */
	private static final int ACCOUNT_TYPE_DESC_WIDTH = 8;

	/** Maximum stored customer name length &mdash; COBOL {@code X(60)}. */
	private static final int CUSTOMER_NAME_MAX = 60;

	/** Maximum stored customer address length &mdash; COBOL {@code X(160)}. */
	private static final int CUSTOMER_ADDRESS_MAX = 160;

	/** Money scale used for every {@link BigDecimal} monetary value. */
	private static final int MONEY_SCALE = 2;

	/**
	 * Transaction-number value used for customer-level {@code PROCTRAN} rows that
	 * do not pertain to a specific account (mirrors the appender's
	 * {@code "00000000"} sentinel).
	 */
	private static final String NO_ACCOUNT = "00000000";

	/** Six-character footer flag for create-account descriptions. */
	private static final String FOOTER_CREATE = "CREATE";

	/** Number of customers between informational progress log lines. */
	private static final int PROGRESS_INTERVAL = 1000;

	// -----------------------------------------------------------------------
	// Hard-coded statement dates (BANKDATA POPULATE-ACC).
	// -----------------------------------------------------------------------

	/** Hard-coded last-statement date from {@code BANKDATA} ({@code 2021-07-01}). */
	private static final LocalDate LAST_STATEMENT_DATE = LocalDate.of(2021, 7, 1);

	/** Hard-coded next-statement date from {@code BANKDATA} ({@code 2021-08-01}). */
	private static final LocalDate NEXT_STATEMENT_DATE = LocalDate.of(2021, 8, 1);

	// -----------------------------------------------------------------------
	// Sample-data arrays copied from BANKDATA INITIALISE-ARRAYS. These literal
	// values are non-behavioral sample data; their presence (not their exact
	// content) is what matters for parity.
	// -----------------------------------------------------------------------

	/** The 36 customer titles ({@code TITLE-ALPHABET}). */
	private static final List<String> TITLES = List.of(
			"Mr", "Mrs", "Miss", "Ms", "Mr", "Mrs", "Miss", "Ms", "Mr", "Mrs",
			"Miss", "Ms", "Mr", "Mrs", "Miss", "Ms", "Mr", "Mrs", "Miss", "Ms",
			"Dr", "Drs", "Dr", "Ms", "Dr", "Ms", "Dr", "Ms", "Professor",
			"Professor", "Professor", "Lord", "Sir", "Sir", "Lady", "Lady");

	/** The 50 forenames ({@code FORENAME}). */
	private static final List<String> FORENAMES = List.of(
			"Michael", "Will", "Geoff", "Chris", "Dave", "Luke", "Adam",
			"Giuseppe", "James", "Jon", "Andy", "Lou", "Robert", "Sam",
			"Frederick", "Buford", "William", "Howard", "Anthony", "Bruce",
			"Peter", "Stephen", "Donald", "Dennis", "Harold", "Amy", "Belinda",
			"Charlotte", "Donna", "Felicia", "Gretchen", "Henrietta", "Imogen",
			"Josephine", "Kimberley", "Lucy", "Monica", "Natalie", "Ophelia",
			"Patricia", "Querida", "Rachel", "Samantha", "Tanya", "Ulrika",
			"Virginia", "Wendy", "Xaviera", "Yvonne", "Zsa Zsa");

	/**
	 * The single-letter middle initials ({@code INITIALS}). The COBOL string is
	 * {@code 'ABCDEFGHIJLKMNOPQRSTUVWXYZ'} &mdash; note that {@code L} precedes
	 * {@code K}; that ordering is preserved verbatim.
	 */
	private static final List<String> INITIALS = List.of(
			"A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "L", "K", "M",
			"N", "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z");

	/** The surnames ({@code SURNAME}). */
	private static final List<String> SURNAMES = List.of(
			"Jones", "Davidson", "Baker", "Smith", "Taylor", "Evans", "Roberts",
			"Wright", "Walker", "Green", "Price", "Downton", "Gatting",
			"Robinson", "Justice", "Tell", "Stark", "Strange", "Parker",
			"Blake", "Jackson", "Groves", "Palmer", "Ramsbottom", "Lloyd",
			"Hughes", "Briggs", "Higins", "Goodwin", "Valmont", "Brown",
			"Hopkins", "Bonney", "Jenkins", "Wilmore", "Franklin", "Renton",
			"Seward", "Morris", "Johnson", "Brennan", "Thomson", "Barker",
			"Corbett", "Weber", "Leigh", "Croft", "Walken", "Dubois",
			"Stephens");

	/** The 26 street "tree" name parts ({@code STREET-NAME-TREE}). */
	private static final List<String> STREET_TREES = List.of(
			"Acacia", "Birch", "Cypress", "Douglas", "Elm", "Fir", "Gorse",
			"Holly", "Ironwood", "Joshua", "Kapok", "Laburnam", "Maple",
			"Nutmeg", "Oak", "Pine", "Quercine", "Rowan", "Sycamore", "Thorn",
			"Ulmus", "Viburnum", "Willow", "Xylophone", "Yew", "Zebratree");

	/** The 19 street "road" name parts ({@code STREET-NAME-ROAD}). */
	private static final List<String> STREET_ROADS = List.of(
			"Avenue", "Boulevard", "Close", "Crescent", "Drive", "Escalade",
			"Frontage", "Lane", "Mews", "Rise", "Court", "Opening", "Loke",
			"Square", "Houses", "Gate", "Street", "Grove", "March");

	/** The 50 towns ({@code TOWN}). */
	private static final List<String> TOWNS = List.of(
			"Norwich", "Acle", "Aylsham", "Wymondham", "Attleborough", "Cromer",
			"Cambridge", "Peterborough", "Weobley", "Wembley", "Hereford",
			"Ross-on-Wye", "Hay-on-Wye", "Nottingham", "Northampton",
			"Nuneaton", "Oxford", "Oswestry", "Ormskirk", "Royston", "Chilcomb",
			"Winchester", "Wrexham", "Crewe", "Plymouth", "Portsmouth", "Forfar",
			"Fife", "Aberdeen", "Glasgow", "Birmingham", "Bolton", "Whitby",
			"Manchester", "Chester", "Leicester", "Lowestoft", "Ipswich",
			"Colchester", "Dover", "Brighton", "Salisbury", "Bristol", "Bath",
			"Gloucester", "Cheltenham", "Durham", "Carlisle", "York", "Exeter");

	/**
	 * The ordinal account-attribute table from {@code BANKDATA}
	 * ({@code INITIALISE-ARRAYS}). A customer allocated {@code N} accounts
	 * receives the first {@code N} rows of this table, in this exact order. The
	 * interest rate is held as a {@code String} so it can be converted with the
	 * {@link BigDecimal} string constructor (never the {@code double}
	 * constructor).
	 *
	 * @param type          the account type (a plain {@code String}, matching the
	 *                      {@link Account#setAccountType(String)} field)
	 * @param interestRate  the interest rate as a decimal string (scale 2)
	 * @param overdraftLimit the overdraft limit in whole units
	 */
	private record AccountTemplate(String type, String interestRate,
			int overdraftLimit)
	{
	}

	/** The fixed five-row ordinal account table (ISA, SAVING, CURRENT, LOAN, MORTGAGE). */
	private static final List<AccountTemplate> ACCOUNT_TEMPLATES = List.of(
			new AccountTemplate("ISA", "2.10", 0),
			new AccountTemplate("SAVING", "1.75", 0),
			new AccountTemplate("CURRENT", "0.00", 100),
			new AccountTemplate("LOAN", "17.90", 0),
			new AccountTemplate("MORTGAGE", "5.25", 0));

	// -----------------------------------------------------------------------
	// Injected collaborators (constructor injection only; all final).
	// -----------------------------------------------------------------------

	private final IdentityService identityService;

	private final CustomerRepository customerRepository;

	private final AccountRepository accountRepository;

	private final ProcessedTransactionRepository processedTransactionRepository;

	/**
	 * Repository for the {@code account_control} row, used to allocate
	 * {@code PROCTRAN} references from its {@code last_transaction_reference}
	 * counter under a {@code PESSIMISTIC_WRITE} lock (the same gap-free,
	 * O(1) counter the live append paths use; F2-02 / ADR-003).
	 */
	private final AccountControlRepository accountControlRepository;

	/**
	 * Programmatic transaction boundary used to wrap each per-customer seed in
	 * its own transaction so the {@code MANDATORY}-propagation
	 * {@link IdentityService} allocations run inside an active transaction.
	 */
	private final TransactionTemplate transactionTemplate;

	/** Number of customers to generate (configurable; default 1000). */
	private final int customerCount;

	/**
	 * Optional fixed RNG seed for reproducible runs (mirrors {@code BANKDATA}'s
	 * {@code RANDOM-SEED} parm). {@code null} means use a non-deterministic seed.
	 */
	private final Long randomSeed;

	/**
	 * Creates the seeder with all collaborators and configuration injected via
	 * the constructor.
	 *
	 * @param identityService                gap-free identity allocator
	 * @param customerRepository             customer persistence + idempotency
	 *                                       guard
	 * @param accountRepository              account persistence
	 * @param processedTransactionRepository {@code PROCTRAN} append repository
	 * @param accountControlRepository       {@code account_control} repository
	 *                                       supplying the gap-free
	 *                                       {@code last_transaction_reference}
	 *                                       counter under a write lock
	 * @param transactionManager             the platform transaction manager used
	 *                                       to build the {@link TransactionTemplate}
	 * @param customerCount                  number of customers to generate
	 *                                       ({@code bank.seed.customers}, default
	 *                                       1000)
	 * @param randomSeed                     optional fixed RNG seed
	 *                                       ({@code bank.seed.random-seed}); may be
	 *                                       {@code null}
	 */
	public BankDataSeeder(IdentityService identityService,
			CustomerRepository customerRepository,
			AccountRepository accountRepository,
			ProcessedTransactionRepository processedTransactionRepository,
			AccountControlRepository accountControlRepository,
			PlatformTransactionManager transactionManager,
			@Value("${bank.seed.customers:1000}") int customerCount,
			@Value("${bank.seed.random-seed:#{null}}") Long randomSeed)
	{
		this.identityService = identityService;
		this.customerRepository = customerRepository;
		this.accountRepository = accountRepository;
		this.processedTransactionRepository = processedTransactionRepository;
		this.accountControlRepository = accountControlRepository;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
		this.customerCount = customerCount;
		this.randomSeed = randomSeed;
	}

	/**
	 * Seeds the database when the {@code seed} profile is active.
	 *
	 * <p>Performs the non-destructive idempotency guard first: if any customer
	 * already exists, logs and returns without seeding. Otherwise generates
	 * {@link #customerCount} customers, each in its own transaction, reusing a
	 * single {@link Random} instance so that a fixed {@link #randomSeed} yields
	 * fully reproducible data.</p>
	 *
	 * @param args the command-line arguments (unused)
	 */
	@Override
	public void run(String... args)
	{
		// --- Idempotency guard (non-destructive; see class-level divergence #1).
		long existing = customerRepository.count();
		if (existing > 0)
		{
			LOGGER.info("Bank data already present ({} customers); skipping seed.",
					existing);
			return;
		}

		if (customerCount <= 0)
		{
			LOGGER.info("bank.seed.customers={}; nothing to seed.", customerCount);
			return;
		}

		// --- Single RNG for the whole run (optionally seeded for reproducibility).
		final Random rng = (randomSeed != null) ? new Random(randomSeed)
				: new Random();

		LOGGER.info("Seeding {} customers (1-5 accounts each) under sort code {}...",
				customerCount, BankConstants.SORT_CODE);

		for (int i = 0; i < customerCount; i++)
		{
			// Each customer + its accounts + PROCTRAN rows run in their OWN
			// transaction. The injected TransactionTemplate opens the boundary so
			// IdentityService's MANDATORY allocations execute inside an active
			// transaction (a self-invoked @Transactional helper on this bean would
			// be bypassed by the Spring proxy).
			transactionTemplate.executeWithoutResult(status -> seedOneCustomer(rng));

			if ((i + 1) % PROGRESS_INTERVAL == 0)
			{
				LOGGER.info("Seeded {} of {} customers.", i + 1, customerCount);
			}
		}

		LOGGER.info("Bank data seeding complete: {} customers created.",
				customerCount);
	}

	/**
	 * Generates and persists a single customer together with its one-to-five
	 * accounts and the matching {@code PROCTRAN} create rows. Invoked inside a
	 * {@link TransactionTemplate} callback so all identity allocation and
	 * persistence share one transaction.
	 *
	 * @param rng the shared random-number generator
	 */
	private void seedOneCustomer(Random rng)
	{
		// Allocate PROCTRAN references from the last_transaction_reference counter
		// on the account_control row -- the same gap-free, O(1) counter the live
		// append paths use (ADR-003 / F2-02). Read the control row ONCE under a
		// PESSIMISTIC_WRITE lock at the start of this customer's transaction,
		// increment the counter locally per appended row, then write the final
		// value back before the transaction commits (see end of method). The
		// seeder is single-threaded on an empty database (per the idempotency
		// guard), but locking keeps it consistent with the live append paths and
		// re-entrant with IdentityService's own account_control acquisition within
		// this same transaction.
		AccountControl accountControl = accountControlRepository
				.findBySortCodeForUpdate(BankConstants.SORT_CODE)
				.orElseThrow(() -> new IllegalStateException(
						"account_control row missing for sort code "
								+ BankConstants.SORT_CODE));
		long referenceCounter = accountControl.getLastTransactionReference();

		// --- 3a. Allocate and build the Customer. -------------------------------
		long custNo = identityService.allocateCustomerNumber();
		String customerNumber = pad(custNo, CUSTOMER_NUMBER_WIDTH);

		String name = buildName(rng);
		String address = buildAddress(rng);
		LocalDate dateOfBirth = buildDateOfBirth(rng);
		short creditScore = (short) randomInclusive(rng, 1, 999);
		LocalDate reviewDate = LocalDate.now().plusDays(randomInclusive(rng, 1, 21));

		Customer customer = new Customer();
		customer.setId(new CustomerId(BankConstants.SORT_CODE, customerNumber));
		customer.setName(name);
		customer.setAddress(address);
		customer.setDateOfBirth(dateOfBirth);
		customer.setCreditScore(creditScore);
		customer.setCsReviewDate(reviewDate);
		customerRepository.save(customer);

		// --- 3b. Append the create-customer PROCTRAN row (web variant ICC). -----
		referenceCounter++;
		ProcessedTransaction customerTxn = new ProcessedTransaction();
		customerTxn.setId(new ProcessedTransactionId(BankConstants.SORT_CODE,
				pad(referenceCounter, REFERENCE_WIDTH)));
		customerTxn.setTransactionNumber(NO_ACCOUNT);
		customerTxn.setDate(LocalDate.now());
		customerTxn.setTime(LocalTime.now());
		customerTxn.setTypeCode(TransactionType.ICC);
		customerTxn.setDescription(
				customerDescription(customerNumber, name, dateOfBirth));
		customerTxn.setAmount(zeroMoney());
		customerTxn.setDeleted(false);
		processedTransactionRepository.save(customerTxn);

		// --- 3c. Build the 1-5 accounts (by ordinal). ---------------------------
		int noOfAccounts = randomInclusive(rng, 1, ACCOUNT_TEMPLATES.size());
		for (int ordinal = 0; ordinal < noOfAccounts; ordinal++)
		{
			AccountTemplate template = ACCOUNT_TEMPLATES.get(ordinal);

			long acctNo = identityService.allocateAccountNumber();
			String accountNumber = pad(acctNo, ACCOUNT_NUMBER_WIDTH);

			LocalDate opened = generateOpenedDate(rng, dateOfBirth);

			BigDecimal balance = money(randomInclusive(rng, 1, 999999));
			if (isOverdraftBearingDebt(template.type()))
			{
				// LOAN and MORTGAGE balances are seeded negative (money owed).
				balance = balance.negate();
			}

			Account account = new Account();
			account.setId(new AccountId(BankConstants.SORT_CODE, accountNumber));
			account.setCustomerNumber(customerNumber);
			account.setAccountType(template.type());
			account.setInterestRate(money(template.interestRate()));
			account.setOpened(opened);
			account.setOverdraftLimit(Integer.valueOf(template.overdraftLimit()));
			account.setLastStatementDate(LAST_STATEMENT_DATE);
			account.setNextStatementDate(NEXT_STATEMENT_DATE);
			account.setAvailableBalance(balance);
			account.setActualBalance(balance);
			accountRepository.save(account);

			// Append the create-account PROCTRAN row (web variant ICA).
			referenceCounter++;
			ProcessedTransaction accountTxn = new ProcessedTransaction();
			accountTxn.setId(new ProcessedTransactionId(BankConstants.SORT_CODE,
					pad(referenceCounter, REFERENCE_WIDTH)));
			accountTxn.setTransactionNumber(accountNumber);
			accountTxn.setDate(LocalDate.now());
			accountTxn.setTime(LocalTime.now());
			accountTxn.setTypeCode(TransactionType.ICA);
			accountTxn.setDescription(accountDescription(customerNumber,
					template.type(), LAST_STATEMENT_DATE, NEXT_STATEMENT_DATE));
			accountTxn.setAmount(zeroMoney());
			accountTxn.setDeleted(false);
			processedTransactionRepository.save(accountTxn);
		}

		// Write the advanced reference counter back to the locked account_control
		// row so the next customer's transaction (and every live append path
		// post-seed) continues allocating references gap-free from where this
		// customer finished.
		accountControl.setLastTransactionReference(referenceCounter);
		accountControlRepository.save(accountControl);
	}

	// =======================================================================
	// Field-generation helpers (BANKDATA DEFINE-CUSTOMER / DEFINE-ACC).
	// =======================================================================

	/**
	 * Builds a customer name of the form {@code title forename initial surname},
	 * each part picked at random, truncated to the stored maximum width.
	 *
	 * @param rng the shared random-number generator
	 * @return the generated name (at most {@value #CUSTOMER_NAME_MAX} characters)
	 */
	private String buildName(Random rng)
	{
		String name = pick(TITLES, rng) + " " + pick(FORENAMES, rng) + " "
				+ pick(INITIALS, rng) + " " + pick(SURNAMES, rng);
		return truncate(name, CUSTOMER_NAME_MAX);
	}

	/**
	 * Builds a customer address of the form
	 * {@code houseNo streetTree streetRoad, town}, truncated to the stored
	 * maximum width.
	 *
	 * @param rng the shared random-number generator
	 * @return the generated address (at most {@value #CUSTOMER_ADDRESS_MAX}
	 *         characters)
	 */
	private String buildAddress(Random rng)
	{
		int houseNumber = randomInclusive(rng, 1, 99);
		String address = houseNumber + " " + pick(STREET_TREES, rng) + " "
				+ pick(STREET_ROADS, rng) + ", " + pick(TOWNS, rng);
		return truncate(address, CUSTOMER_ADDRESS_MAX);
	}

	/**
	 * Builds a date of birth with day {@code 1-28}, month {@code 1-12} and year
	 * {@code 1900-2000}. The day is capped at 28 (as in the COBOL) so the date is
	 * valid for every month.
	 *
	 * @param rng the shared random-number generator
	 * @return the generated date of birth
	 */
	private LocalDate buildDateOfBirth(Random rng)
	{
		int day = randomInclusive(rng, 1, 28);
		int month = randomInclusive(rng, 1, 12);
		int year = randomInclusive(rng, 1900, 2000);
		return LocalDate.of(year, month, day);
	}

	/**
	 * Generates an account opened date ({@code GENERATE-OPENED-DATE}): day
	 * {@code 1-28}, month {@code 1-12}, and a year in {@code [birthYear, 2014]}
	 * that must be strictly greater than the birth year. The selection is retried
	 * up to 100 times; if no qualifying year is found the date of birth itself is
	 * used as the fallback (faithfully reproducing the COBOL behaviour).
	 *
	 * @param rng         the shared random-number generator
	 * @param dateOfBirth the customer's date of birth
	 * @return the generated opened date
	 */
	private LocalDate generateOpenedDate(Random rng, LocalDate dateOfBirth)
	{
		int birthYear = dateOfBirth.getYear();
		for (int attempt = 0; attempt < 100; attempt++)
		{
			int day = randomInclusive(rng, 1, 28);
			int month = randomInclusive(rng, 1, 12);
			int year = randomInclusive(rng, birthYear, 2014);
			if (year > birthYear)
			{
				return LocalDate.of(year, month, day);
			}
		}
		// Fallback after 100 unsuccessful attempts: use the date of birth.
		return dateOfBirth;
	}

	/**
	 * Indicates whether the given account type carries a debt balance that
	 * {@code BANKDATA} seeds negative (i.e. {@code LOAN} or {@code MORTGAGE}).
	 *
	 * @param accountType the account type
	 * @return {@code true} for {@code LOAN} or {@code MORTGAGE}
	 */
	private static boolean isOverdraftBearingDebt(String accountType)
	{
		return "LOAN".equals(accountType) || "MORTGAGE".equals(accountType);
	}

	// =======================================================================
	// PROCTRAN description builders (mirror ProcessedTransactionAppender so the
	// seeded rows are byte-for-byte identical to runtime create rows).
	// =======================================================================

	/**
	 * Builds the forty-character create-customer description:
	 * {@code sortCode(6) + customerNumber(10) + name(14) + DD/MM/YYYY(10)}.
	 *
	 * @param customerNumber the zero-padded customer number (width 10)
	 * @param name           the customer name
	 * @param dateOfBirth    the date of birth
	 * @return the fixed-width (40) description
	 */
	private static String customerDescription(String customerNumber, String name,
			LocalDate dateOfBirth)
	{
		String description = BankConstants.SORT_CODE + customerNumber
				+ rightPad(name, NAME_DESC_WIDTH) + dateSlashes(dateOfBirth);
		return fixedWidth(description, DESCRIPTION_WIDTH);
	}

	/**
	 * Builds the forty-character create-account description:
	 * {@code customerNumber(10) + accountType(8) + lastStmt DDMMYYYY(8) +
	 * nextStmt DDMMYYYY(8) + "CREATE"(6)}.
	 *
	 * @param customerNumber the zero-padded owning customer number (width 10)
	 * @param accountType    the account type
	 * @param lastStatement  the last-statement date
	 * @param nextStatement  the next-statement date
	 * @return the fixed-width (40) description
	 */
	private static String accountDescription(String customerNumber,
			String accountType, LocalDate lastStatement, LocalDate nextStatement)
	{
		String description = customerNumber
				+ rightPad(accountType, ACCOUNT_TYPE_DESC_WIDTH)
				+ dateCompact(lastStatement) + dateCompact(nextStatement)
				+ FOOTER_CREATE;
		return fixedWidth(description, DESCRIPTION_WIDTH);
	}

	// =======================================================================
	// Low-level formatting / money / random helpers.
	// =======================================================================

	/**
	 * Left-zero-pads a numeric value to a fixed width, preserving COBOL
	 * display-numeric leading zeros.
	 *
	 * @param value the value to pad
	 * @param width the target width
	 * @return the zero-padded string
	 */
	private static String pad(long value, int width)
	{
		return String.format("%0" + width + "d", value);
	}

	/**
	 * Right-pads a value with spaces (and truncates) to a fixed width, matching
	 * COBOL {@code PIC X(n)} {@code MOVE} semantics.
	 *
	 * @param value the value (may be {@code null}, treated as empty)
	 * @param width the target width
	 * @return the fixed-width string
	 */
	private static String rightPad(String value, int width)
	{
		String safe = (value == null) ? "" : value;
		if (safe.length() >= width)
		{
			return safe.substring(0, width);
		}
		StringBuilder builder = new StringBuilder(safe);
		while (builder.length() < width)
		{
			builder.append(' ');
		}
		return builder.toString();
	}

	/**
	 * Right-pads/truncates an assembled description to the fixed PROCTRAN
	 * description width.
	 *
	 * @param value the assembled description
	 * @param width the target width (40)
	 * @return the fixed-width description
	 */
	private static String fixedWidth(String value, int width)
	{
		return rightPad(value, width);
	}

	/**
	 * Truncates a value to a maximum length without padding.
	 *
	 * @param value the value (may be {@code null}, treated as empty)
	 * @param max   the maximum length
	 * @return the (possibly truncated) value
	 */
	private static String truncate(String value, int max)
	{
		String safe = (value == null) ? "" : value;
		return (safe.length() > max) ? safe.substring(0, max) : safe;
	}

	/**
	 * Formats a date as the eight-digit {@code DDMMYYYY} field used inside the
	 * account description area.
	 *
	 * @param date the date to format
	 * @return the {@code DDMMYYYY} string
	 */
	private static String dateCompact(LocalDate date)
	{
		return String.format("%02d%02d%04d", date.getDayOfMonth(),
				date.getMonthValue(), date.getYear());
	}

	/**
	 * Formats a date as the {@code DD/MM/YYYY} field used inside the customer
	 * description area.
	 *
	 * @param date the date to format
	 * @return the {@code DD/MM/YYYY} string
	 */
	private static String dateSlashes(LocalDate date)
	{
		return String.format("%02d/%02d/%04d", date.getDayOfMonth(),
				date.getMonthValue(), date.getYear());
	}

	/**
	 * Returns a uniformly distributed random integer in the inclusive range
	 * {@code [lowInclusive, highInclusive]}.
	 *
	 * @param rng           the shared random-number generator
	 * @param lowInclusive  the inclusive lower bound
	 * @param highInclusive the inclusive upper bound
	 * @return a random integer within the range
	 */
	private static int randomInclusive(Random rng, int lowInclusive,
			int highInclusive)
	{
		return lowInclusive + rng.nextInt(highInclusive - lowInclusive + 1);
	}

	/**
	 * Picks a random element from a list.
	 *
	 * @param list the list to pick from (non-empty)
	 * @param rng  the shared random-number generator
	 * @param <T>  the element type
	 * @return a randomly selected element
	 */
	private static <T> T pick(List<T> list, Random rng)
	{
		return list.get(rng.nextInt(list.size()));
	}

	/**
	 * Converts a whole-number value into a scale-2 {@link BigDecimal} using
	 * {@link RoundingMode#HALF_UP}.
	 *
	 * @param value the whole-number value
	 * @return the money value at scale 2
	 */
	private static BigDecimal money(long value)
	{
		return BigDecimal.valueOf(value).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
	}

	/**
	 * Converts a decimal string into a scale-2 {@link BigDecimal} using
	 * {@link RoundingMode#HALF_UP}. The string constructor is used deliberately;
	 * the {@code double} constructor is never used.
	 *
	 * @param value the decimal string (e.g. {@code "2.10"})
	 * @return the money value at scale 2
	 */
	private static BigDecimal money(String value)
	{
		return new BigDecimal(value).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
	}

	/**
	 * Returns {@code 0.00} as a scale-2 {@link BigDecimal} for zero-amount
	 * create PROCTRAN rows.
	 *
	 * @return {@code BigDecimal} zero at scale 2
	 */
	private static BigDecimal zeroMoney()
	{
		return BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
	}

}
