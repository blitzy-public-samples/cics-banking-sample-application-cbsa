/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.core.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;

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
import com.ibm.cics.cip.bank.core.dto.createcustomer.CreateCustomerForm;
import com.ibm.cics.cip.bank.core.dto.createcustomer.CreateCustomerJson;
import com.ibm.cics.cip.bank.core.dto.customerenquiry.CustomerEnquiryJson;
import com.ibm.cics.cip.bank.core.dto.deletecustomer.DeleteCustomerJson;
import com.ibm.cics.cip.bank.core.dto.updatecustomer.UpdateCustomerForm;
import com.ibm.cics.cip.bank.core.dto.updatecustomer.UpdateCustomerJson;
import com.ibm.cics.cip.bank.core.entity.Account;
import com.ibm.cics.cip.bank.core.entity.AccountId;
import com.ibm.cics.cip.bank.core.entity.Customer;
import com.ibm.cics.cip.bank.core.entity.CustomerControl;
import com.ibm.cics.cip.bank.core.entity.CustomerId;
import com.ibm.cics.cip.bank.core.exception.BusinessRuleException;
import com.ibm.cics.cip.bank.core.repository.AccountRepository;
import com.ibm.cics.cip.bank.core.repository.CustomerControlRepository;
import com.ibm.cics.cip.bank.core.repository.CustomerRepository;

/**
 * Behavioural-parity unit test for {@link CustomerService}, the pure-Java
 * rendering of four COBOL customer programs &mdash; {@code CRECUST} (create,
 * feature <strong>F-006</strong>, including the asynchronous five-agency credit
 * fan-out of <strong>F-017</strong>), {@code INQCUST} (inquire, including the
 * control-row sentinels, <strong>F-008</strong>), {@code UPDCUST} (restricted
 * name/address update, <strong>F-011</strong>) and {@code DELCUS} (delete with
 * account cascade, <strong>F-014</strong>). The COBOL is the authoritative
 * specification of record, so this suite pins behavioural <em>parity</em> &mdash;
 * never &quot;improved&quot; behaviour (AAP &sect;0.7).
 *
 * <h2>Parity contracts pinned here</h2>
 * <ul>
 *   <li><strong>Create ordering &amp; fail codes.</strong> Title token
 *       (fail {@code 'T'}) &rarr; asynchronous credit check (fail {@code 'C'}
 *       when no agency replies inside the three-second deadline; the averaged
 *       score is a truncating integer mean of the replies that arrived) &rarr;
 *       date-of-birth matrix ({@code 'O'} year before 1601, {@code 'Z'} invalid
 *       calendar date, {@code 'O'} age over 150, {@code 'Y'} future) &rarr;
 *       number allocation (fail {@code '3'}) &rarr; persist &rarr; append the
 *       create audit row. Validation precedes allocation so a failure rolls the
 *       consumed counter back (gap-free identity, ADR-003).</li>
 *   <li><strong>Inquire sentinels.</strong> {@code 9999999999} (highest) and
 *       {@code 0000000000} (random) are resolved from the {@code CUSTCTRL}
 *       control row, never with a {@code MAX()} scan; a miss surfaces success
 *       flag {@code 'N'} / fail code {@code '1'} in the envelope (INQCUST does
 *       not abend).</li>
 *   <li><strong>Restricted update.</strong> Only name and address change; the
 *       date of birth, credit score and review date are never touched and
 *       <em>no</em> audit row is written (F-011). Both blank &rarr; {@code '4'}.</li>
 *   <li><strong>Delete cascade.</strong> Each owned account is deleted
 *       <em>before</em> the customer row, then the delete audit row is appended
 *       (F-014).</li>
 * </ul>
 *
 * <h2>How the COBOL fail-code surface maps to the Java rendering (parity rationale)</h2>
 * <ul>
 *   <li>Fail codes are surfaced by <em>throwing</em> {@link BusinessRuleException}
 *       (its {@link BusinessRuleException#getFailCode() getFailCode()} returns a
 *       single-character {@code String}); the negative paths therefore assert
 *       the thrown exception, not an envelope flag. Inquire is the one exception
 *       &mdash; a miss is a populated {@code 'N'}/{@code '1'} envelope, not a
 *       throw.</li>
 *   <li>The customer service appends PROCTRAN rows through the shared
 *       {@link ProcessedTransactionAppender} collaborator (not the raw
 *       {@code ProcessedTransactionRepository}); the create/delete audit
 *       assertions therefore verify {@code appendCustomerCreate(...)} /
 *       {@code appendCustomerDelete(...)} on that mock, which reproduces the
 *       {@code OCC}/{@code ODC} PROCTRAN write (the web-origin {@code ICC}/{@code
 *       IDC} intent of the source COBOL) atomically.</li>
 *   <li>The &quot;highest&quot; sentinel reads the control row via
 *       {@code customerControlRepository.findById(SORT_CODE)}; there is no
 *       {@code MAX()} finder to call.</li>
 *   <li><strong>The COBOL named-counter codes {@code '3'} (acquire) and
 *       {@code '5'} (release) are SUBSUMED by the transactional model, never
 *       silently dropped.</strong> {@code CRECUST.cbl} brackets the
 *       customer-number allocation with {@code ENQ}/{@code DEQ NAMED COUNTER},
 *       failing {@code '3'} when the acquire fails and {@code '5'} when the
 *       release ({@code DEQ}) fails (around L538), decrementing the counter and
 *       dequeuing on the failure path so no number leaks. Per AAP &sect;0.6 and
 *       ADR-003 that lock lifecycle is replaced by one {@code @Transactional}
 *       boundary holding a {@code PESSIMISTIC_WRITE} lock on the customer
 *       control row: the lock releases implicitly at the boundary (so the
 *       explicit {@code DEQ}-release step &mdash; hence a <em>distinct</em> Java
 *       fail {@code '5'} &mdash; &quot;is no longer needed&quot;, AAP &sect;0.6),
 *       and a rollback restores the consumed counter automatically. An acquire
 *       {@code DataAccessException} therefore maps to {@code '3'}
 *       ({@code create_allocationError_failCode3()}); the fail-{@code '5'}
 *       GUARANTEE (a create that fails AFTER the number was consumed leaks
 *       neither the number nor a stray {@code PROCTRAN}) is reproduced by
 *       transactional rollback and pinned by
 *       {@code create_postAllocationFailure_rollsBackConsumedCounter_appendsNoProcessedTransaction()}.</li>
 * </ul>
 *
 * <p>All collaborators are Mockito mocks, so the suite is deterministic and
 * fast; the only deliberately slow tests are the two that exercise the genuine
 * three-second credit-check deadline (no-reply {@code 'C'} and partial
 * completion). The credit futures are otherwise pre-completed.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CustomerService — COBOL parity (CRECUST / INQCUST / UPDCUST / DELCUS)")
class CustomerServiceTest
{

	/** Bank sort code used for every composite key and control-row lookup. */
	private static final String SORT_CODE = BankConstants.SORT_CODE;

	/** A name whose first token ("Mr") is a recognised, valid honorific title. */
	private static final String VALID_NAME = "Mr John Smith";

	/** A representative within-width customer address. */
	private static final String VALID_ADDRESS = "1 High Street, Anytown";

	/** A valid compact {@code DDMMYYYY} date of birth (01/01/1990). */
	private static final String VALID_DOB = "01011990";

	/** COBOL {@code CUSTOMER-NAME} / {@code COMM-NAME} fixed width. */
	private static final int NAME_WIDTH = 60;

	/** COBOL {@code CUSTOMER-ADDRESS} / {@code COMM-ADDRESS} fixed width. */
	private static final int ADDRESS_WIDTH = 160;

	@Mock
	private CustomerRepository customerRepository;

	@Mock
	private CustomerControlRepository customerControlRepository;

	@Mock
	private AccountRepository accountRepository;

	@Mock
	private AccountService accountService;

	@Mock
	private IdentityService identityService;

	@Mock
	private CreditAgencyService creditAgencyService;

	// COBOL parity: CustomerService appends PROCTRAN rows through this sibling
	// appender (reproducing the OCC/ODC write), not ProcessedTransactionRepository.
	@Mock
	private ProcessedTransactionAppender proctranAppender;

	@InjectMocks
	private CustomerService customerService;

	// ------------------------------------------------------------------ //
	// Fixtures and helpers                                                //
	// ------------------------------------------------------------------ //

	/** A completed credit-agency future carrying the supplied score. */
	private static CompletableFuture<Integer> completed(int score)
	{
		return CompletableFuture.completedFuture(score);
	}

	/** A credit-agency future that never completes (drives the 3s deadline). */
	private static CompletableFuture<Integer> pending()
	{
		return new CompletableFuture<>();
	}

	/**
	 * Stubs every one of the five fan-out calls to reply instantly with the same
	 * score, so the credit check completes well inside the deadline.
	 */
	private void stubAllAgenciesComplete(int score)
	{
		when(creditAgencyService.requestCreditScore())
				.thenAnswer(invocation -> completed(score));
	}

	/** Builds a valid create-customer form (valid title, address and DOB). */
	private CreateCustomerForm validCreateForm()
	{
		return new CreateCustomerForm(VALID_NAME, VALID_ADDRESS, VALID_DOB);
	}

	/** Builds a create-customer form with a specific (DDMMYYYY) date of birth. */
	private CreateCustomerForm createFormWithDob(String dob)
	{
		return new CreateCustomerForm(VALID_NAME, VALID_ADDRESS, dob);
	}

	/** Builds a persisted-customer fixture keyed on the supplied number. */
	private Customer customerFixture(String customerNumber)
	{
		Customer customer = new Customer();
		customer.setId(new CustomerId(SORT_CODE, customerNumber));
		customer.setName("Mr Existing Customer");
		customer.setAddress("1 Existing Road, Anytown");
		customer.setDateOfBirth(LocalDate.of(1980, 1, 1));
		customer.setCreditScore((short) 555);
		customer.setCsReviewDate(LocalDate.of(2024, 6, 1));
		return customer;
	}

	/** Builds an account fixture owned by the supplied customer number. */
	private Account accountFixture(String accountNumber, String customerNumber)
	{
		Account account = new Account();
		account.setId(new AccountId(SORT_CODE, accountNumber));
		account.setCustomerNumber(customerNumber);
		return account;
	}

	/** Builds an update-customer form. */
	private UpdateCustomerForm updateForm(String number, String name,
			String address)
	{
		UpdateCustomerForm form = new UpdateCustomerForm();
		form.setCustNumber(number);
		form.setCustName(name);
		form.setCustAddress(address);
		return form;
	}

	// ================================================================== //
	// createCustomer (CRECUST, F-006 / credit fan-out F-017)             //
	// ================================================================== //

	@Test
	@DisplayName("create: invalid honorific title fails 'T' before any credit check, allocation, persist or audit")
	void create_invalidTitle_failCodeT()
	{
		// "Xyz" is not a recognised honorific, so the title check (the first
		// step of CRECUST) rejects it with fail code 'T'.
		CreateCustomerForm form = new CreateCustomerForm("Xyz John Smith",
				VALID_ADDRESS, VALID_DOB);

		assertThatThrownBy(() -> customerService.createCustomer(form))
				.isInstanceOf(BusinessRuleException.class)
				.extracting(ex -> ((BusinessRuleException) ex).getFailCode())
				.isEqualTo("T");

		// Title is validated up front, so nothing downstream runs: no credit
		// check, no number allocated, no customer saved, no audit row.
		verifyNoInteractions(creditAgencyService, identityService,
				customerRepository, customerControlRepository, proctranAppender);
	}

	@Test
	@DisplayName("create: credit score is the truncating integer average of the five completed agency replies (100..500 -> 300), audit row appended")
	void create_creditScoreAveragedFromCompletedFutures()
	{
		// Five distinct, already-completed replies: average = 1500 / 5 = 300.
		// Consecutive single-value thenReturn calls (rather than the varargs
		// thenReturn(T, T...) overload) avoid an unchecked generic-array
		// creation warning for CompletableFuture<Integer>, while returning the
		// same sequence on the service's five requestCreditScore() calls.
		when(creditAgencyService.requestCreditScore())
				.thenReturn(completed(100))
				.thenReturn(completed(200))
				.thenReturn(completed(300))
				.thenReturn(completed(400))
				.thenReturn(completed(500));
		when(identityService.allocateCustomerNumber()).thenReturn(1L);

		CreateCustomerJson result = customerService
				.createCustomer(validCreateForm());

		ArgumentCaptor<Customer> saved = ArgumentCaptor.forClass(Customer.class);
		verify(customerRepository).save(saved.capture());
		// Credit score is an integer in 0..999 (NOT money): assert as an int.
		assertThat(saved.getValue().getCreditScore().intValue()).isEqualTo(300);

		// The create audit row (OCC / web-origin ICC intent) is appended through
		// the shared appender for the allocated number.
		verify(proctranAppender).appendCustomerCreate(eq(SORT_CODE), eq(1L),
				anyString(), any(LocalDate.class));

		assertThat(result.getCreCust().getCommSuccess()).isEqualTo("Y");
		assertThat(result.getCreCust().getCommCreditScore()).isEqualTo(300);
	}

	@Test
	@DisplayName("create: no agency replies within the 3s deadline -> fail 'C', and nothing is allocated or persisted")
	void create_noCreditAgencyCompletesInTime_failCodeC()
	{
		// Every fan-out future never completes, so the fixed three-second
		// deadline elapses with zero replies -> canonical 'C'. This is the
		// single deliberately ~3s-bounded test for the no-reply path.
		when(creditAgencyService.requestCreditScore())
				.thenAnswer(invocation -> pending());

		assertThatThrownBy(
				() -> customerService.createCustomer(validCreateForm()))
				.isInstanceOf(BusinessRuleException.class)
				.extracting(ex -> ((BusinessRuleException) ex).getFailCode())
				.isEqualTo("C");

		// A failed credit check precedes allocation, so the identity counter is
		// never consumed and no customer is persisted (gap-free identity).
		verify(identityService, never()).allocateCustomerNumber();
		verify(customerRepository, never()).save(any());
		verifyNoInteractions(proctranAppender);
	}

	@Test
	@DisplayName("create: partial completion averages only the replies that arrived (200,400 + 3 silent -> 300)")
	void create_partialCreditCompletion_averagesOnlyCompleted()
	{
		// Two agencies reply (200, 400); three never do. After the deadline the
		// score is the mean of the completed replies only: (200 + 400) / 2 = 300.
		// Tolerates ~3s while proving the average ignores the silent agencies.
		// Consecutive single-value thenReturn calls avoid an unchecked
		// generic-array creation warning for CompletableFuture<Integer> while
		// reproducing the same per-call reply sequence (two complete, three
		// never do).
		when(creditAgencyService.requestCreditScore())
				.thenReturn(completed(200))
				.thenReturn(completed(400))
				.thenReturn(pending())
				.thenReturn(pending())
				.thenReturn(pending());
		when(identityService.allocateCustomerNumber()).thenReturn(1L);

		CreateCustomerJson result = customerService
				.createCustomer(validCreateForm());

		ArgumentCaptor<Customer> saved = ArgumentCaptor.forClass(Customer.class);
		verify(customerRepository).save(saved.capture());
		assertThat(saved.getValue().getCreditScore().intValue()).isEqualTo(300);
		assertThat(result.getCreCust().getCommCreditScore()).isEqualTo(300);
	}

	@Test
	@DisplayName("create: date-of-birth year before 1601 fails 'O'")
	void create_dobYearBefore1601_failCodeO()
	{
		// Credit check precedes the DOB check, so the agencies must reply first.
		stubAllAgenciesComplete(500);

		// 01/01/1500 -> year 1500 < 1601.
		assertThatThrownBy(() -> customerService
				.createCustomer(createFormWithDob("01011500")))
				.isInstanceOf(BusinessRuleException.class)
				.extracting(ex -> ((BusinessRuleException) ex).getFailCode())
				.isEqualTo("O");

		verify(identityService, never()).allocateCustomerNumber();
		verify(customerRepository, never()).save(any());
		verifyNoInteractions(proctranAppender);
	}

	@Test
	@DisplayName("create: an impossible calendar date (31/02/2000) fails 'Z'")
	void create_dobInvalidCalendarDate_failCodeZ()
	{
		stubAllAgenciesComplete(500);

		// 31 February 2000 is not a real date.
		assertThatThrownBy(() -> customerService
				.createCustomer(createFormWithDob("31022000")))
				.isInstanceOf(BusinessRuleException.class)
				.extracting(ex -> ((BusinessRuleException) ex).getFailCode())
				.isEqualTo("Z");

		verify(identityService, never()).allocateCustomerNumber();
		verify(customerRepository, never()).save(any());
		verifyNoInteractions(proctranAppender);
	}

	@Test
	@DisplayName("create: age over 150 years fails 'O'")
	void create_dobAgeOver150_failCodeO()
	{
		stubAllAgenciesComplete(500);

		// Compute a DOB that makes the customer 151 years old relative to the
		// current year, so (currentYear - birthYear) > 150 regardless of run date
		// while keeping the year >= 1601 and the date a valid, past calendar day.
		int birthYear = LocalDate.now().getYear() - 151;
		String dob = "0101" + birthYear;

		assertThatThrownBy(
				() -> customerService.createCustomer(createFormWithDob(dob)))
				.isInstanceOf(BusinessRuleException.class)
				.extracting(ex -> ((BusinessRuleException) ex).getFailCode())
				.isEqualTo("O");

		verify(identityService, never()).allocateCustomerNumber();
		verify(customerRepository, never()).save(any());
		verifyNoInteractions(proctranAppender);
	}

	@Test
	@DisplayName("create: a date of birth in the future fails 'Y'")
	void create_dobInFuture_failCodeY()
	{
		stubAllAgenciesComplete(500);

		// 01 January of next year is always after today.
		int futureYear = LocalDate.now().getYear() + 1;
		String dob = "0101" + futureYear;

		assertThatThrownBy(
				() -> customerService.createCustomer(createFormWithDob(dob)))
				.isInstanceOf(BusinessRuleException.class)
				.extracting(ex -> ((BusinessRuleException) ex).getFailCode())
				.isEqualTo("Y");

		verify(identityService, never()).allocateCustomerNumber();
		verify(customerRepository, never()).save(any());
		verifyNoInteractions(proctranAppender);
	}

	@Test
	@DisplayName("create happy path: allocates the number, saves the customer, appends the create audit row, and sets the review date within today+1..+21")
	void create_happyPath_savesCustomer_allocatesNumber_appendsIcc_setsReviewDate()
	{
		stubAllAgenciesComplete(700);
		when(identityService.allocateCustomerNumber()).thenReturn(42L);

		LocalDate today = LocalDate.now();

		CreateCustomerJson result = customerService
				.createCustomer(validCreateForm());

		// The number is allocated from the control-row counter (gap-free).
		verify(identityService).allocateCustomerNumber();

		ArgumentCaptor<Customer> saved = ArgumentCaptor.forClass(Customer.class);
		verify(customerRepository).save(saved.capture());
		Customer persisted = saved.getValue();
		// The allocated number is zero-padded to the ten-character key width.
		assertThat(persisted.getId().getCustomerNumber()).isEqualTo("0000000042");
		assertThat(persisted.getId().getSortCode()).isEqualTo(SORT_CODE);
		assertThat(persisted.getCreditScore().intValue()).isEqualTo(700);
		// CRECUST sets the credit-score review date to today + random(1..21) days.
		assertThat(persisted.getCsReviewDate())
				.isAfterOrEqualTo(today.plusDays(1))
				.isBeforeOrEqualTo(today.plusDays(21));

		// The create audit row (web create-customer; OCC / ICC intent) is appended.
		verify(proctranAppender).appendCustomerCreate(eq(SORT_CODE), eq(42L),
				anyString(), any(LocalDate.class));

		assertThat(result.getCreCust().getCommSuccess()).isEqualTo("Y");
	}

	/**
	 * CRECUST counter-ACQUIRE failure &mdash; fail code {@code '3'} parity.
	 *
	 * <p>{@code CRECUST.cbl} fails {@code '3'} when the {@code ENQ}/acquire of the
	 * named counter fails. The Java allocator surfaces an acquire
	 * {@code DataAccessException} as the same {@code '3'} before any row is
	 * written, so nothing is saved or audited. (The COBOL release-failure
	 * {@code '5'} is the separate post-acquire path whose GUARANTEE is pinned by
	 * {@code create_postAllocationFailure_rollsBackConsumedCounter_appendsNoProcessedTransaction()}.)</p>
	 */
	@Test
	@DisplayName("create: a counter-acquire failure fails '3' (CRECUST acquire path); nothing is saved or audited")
	void create_allocationError_failCode3()
	{
		stubAllAgenciesComplete(500);
		// CRECUST fails '3' on a named-counter ACQUIRE failure; the Java
		// allocator maps an acquire DataAccessException to the same '3'.
		when(identityService.allocateCustomerNumber())
				.thenThrow(new DataIntegrityViolationException("counter clash"));

		assertThatThrownBy(
				() -> customerService.createCustomer(validCreateForm()))
				.isInstanceOf(BusinessRuleException.class)
				.extracting(ex -> ((BusinessRuleException) ex).getFailCode())
				.isEqualTo("3");

		verify(customerRepository, never()).save(any());
		verifyNoInteractions(proctranAppender);
	}

	/**
	 * CRECUST fail-code {@code '5'} parity &mdash; the named-counter
	 * RELEASE-failure GUARANTEE, reproduced by transactional rollback.
	 *
	 * <p><strong>COBOL behaviour.</strong> {@code CRECUST.cbl} brackets the
	 * customer-number allocation with {@code ENQ}/{@code DEQ NAMED COUNTER}: it
	 * fails {@code '3'} if the counter <em>acquire</em> fails and {@code '5'} if
	 * the counter <em>release</em> ({@code DEQ}) fails (around L538). On any
	 * post-acquire failure it decrements the counter and dequeues so that
	 * <em>no customer number is ever leaked</em> &mdash; that gap-free guarantee
	 * is the whole point of the fail-{@code '5'} path.</p>
	 *
	 * <p><strong>Java rendering (AAP &sect;0.6, ADR-003).</strong> The
	 * {@code ENQ}/{@code DEQ} lock lifecycle is replaced by a single
	 * {@code @Transactional} boundary that holds a {@code PESSIMISTIC_WRITE} row
	 * lock on the {@code customer_control} counter row. The lock releases
	 * implicitly at the transaction boundary, so the explicit {@code DEQ}-release
	 * step &mdash; and therefore a <em>distinct</em> Java fail {@code '5'}
	 * &mdash; &quot;is no longer needed&quot;; the finalized service maps both
	 * the counter and the persistence {@code DataAccessException} to {@code '3'}.
	 * What matters for parity is not the code letter but the GUARANTEE: a
	 * rollback of the enclosing transaction restores the consumed counter
	 * automatically (database {@code IDENTITY}/{@code SEQUENCE} generation is
	 * forbidden precisely because it could not undo a consumed value).</p>
	 *
	 * <p><strong>What this test pins.</strong> When every validation has passed
	 * and the number has already been consumed, a subsequent persistence failure
	 * must (a) surface a fail code by throwing &mdash; which rolls the
	 * {@code @Transactional} unit back and so restores the counter &mdash; and
	 * (b) leak neither the consumed number nor a stray {@code PROCTRAN} row. We
	 * assert the counter is consumed exactly once and that the service performs
	 * <em>no</em> explicit compensating release or decrement (it relies wholly on
	 * the transaction boundary, the {@code DEQ} subsumption), and that no audit
	 * row is written. This is the faithful Java equivalent of the COBOL
	 * counter-release/rollback path, not a fabricated synthetic {@code '5'} code
	 * (which AAP &sect;0.6 forbids).</p>
	 */
	@Test
	@DisplayName("create: COBOL fail-'5' guarantee — a post-allocation persistence failure throws to roll back the consumed counter and leaks no PROCTRAN")
	void create_postAllocationFailure_rollsBackConsumedCounter_appendsNoProcessedTransaction()
	{
		stubAllAgenciesComplete(500);
		// The counter is acquired successfully (consumed), then the customer
		// insert fails with a DataAccessException — the Java analogue of the COBOL
		// post-acquire failure that fail '5' (DEQ release, CRECUST L538) guarded.
		when(identityService.allocateCustomerNumber()).thenReturn(42L);
		when(customerRepository.save(any(Customer.class)))
				.thenThrow(new DataIntegrityViolationException(
						"simulated post-allocation persistence failure"));

		// The failure is surfaced by throwing (rolling back the @Transactional
		// unit and so restoring the consumed counter, AAP §0.6 + ADR-003). Both
		// the counter and persistence DataAccessExceptions map to '3' because the
		// COBOL DEQ-release step (fail '5') is subsumed by the transaction
		// boundary per AAP §0.6 — so this test pins the GUARANTEE that COBOL
		// fail '5' protected (no leaked number, no stray audit), not a code letter.
		assertThatThrownBy(
				() -> customerService.createCustomer(validCreateForm()))
				.isInstanceOf(BusinessRuleException.class)
				.extracting(ex -> ((BusinessRuleException) ex).getFailCode())
				.isEqualTo("3");

		// The counter WAS consumed (exactly once) ...
		verify(identityService).allocateCustomerNumber();
		// ... and the service performs NO explicit compensating release or
		// decrement: the COBOL DEQ-release step (whose failure was fail '5') is
		// subsumed by the transaction boundary, so the SOLE identity interaction
		// is the single allocation — the counter rollback is the transaction's
		// responsibility, not an out-of-band release call.
		verifyNoMoreInteractions(identityService);
		// No audit row leaks: the PROCTRAN append is downstream of the failed
		// insert and the rollback discards the consumed counter with it.
		verifyNoInteractions(proctranAppender);
	}

	// --- Retained CP3 robustness parity (fixed-width truncation, saturation) ---

	@Test
	@DisplayName("create: an over-length name and address are truncated to the COBOL fixed-field widths (60/160) before persist")
	void create_truncatesOverLengthNameAndAddress()
	{
		stubAllAgenciesComplete(500);
		when(identityService.allocateCustomerNumber()).thenReturn(1L);

		// Name = "Mr " + 58 chars = 61 (one over 60); address = 161 (one over 160).
		String overLongName = "Mr " + "A".repeat(58);
		String overLongAddress = "B".repeat(ADDRESS_WIDTH + 1);
		assertThat(overLongName).hasSize(NAME_WIDTH + 1);
		assertThat(overLongAddress).hasSize(ADDRESS_WIDTH + 1);

		customerService.createCustomer(new CreateCustomerForm(overLongName,
				overLongAddress, VALID_DOB));

		ArgumentCaptor<Customer> saved = ArgumentCaptor.forClass(Customer.class);
		verify(customerRepository).save(saved.capture());
		// A COBOL MOVE into PIC X(60) / PIC X(160) keeps the leftmost characters.
		assertThat(saved.getValue().getName()).hasSize(NAME_WIDTH)
				.isEqualTo(overLongName.substring(0, NAME_WIDTH));
		assertThat(saved.getValue().getAddress()).hasSize(ADDRESS_WIDTH)
				.isEqualTo(overLongAddress.substring(0, ADDRESS_WIDTH));
	}

	@Test
	@DisplayName("create: a within-width name and address are persisted unchanged")
	void create_leavesWithinWidthValuesUnchanged()
	{
		stubAllAgenciesComplete(500);
		when(identityService.allocateCustomerNumber()).thenReturn(1L);

		customerService.createCustomer(validCreateForm());

		ArgumentCaptor<Customer> saved = ArgumentCaptor.forClass(Customer.class);
		verify(customerRepository).save(saved.capture());
		assertThat(saved.getValue().getName()).isEqualTo(VALID_NAME);
		assertThat(saved.getValue().getAddress()).isEqualTo(VALID_ADDRESS);
	}

	@Test
	@DisplayName("create: a saturated credit-agency executor (every submission rejected) degrades to fail 'C', not an HTTP 500")
	void create_executorSaturated_failCodeC()
	{
		// A saturated executor rejects every submission synchronously with a
		// RejectedExecutionException (Spring's TaskRejectedException is one). A
		// rejected agency is treated exactly like an agency that did not reply,
		// so with all five rejected the create degrades to the canonical 'C'.
		when(creditAgencyService.requestCreditScore())
				.thenThrow(new RejectedExecutionException("executor saturated"));

		assertThatThrownBy(
				() -> customerService.createCustomer(validCreateForm()))
				.isInstanceOf(BusinessRuleException.class)
				.extracting(ex -> ((BusinessRuleException) ex).getFailCode())
				.isEqualTo("C");

		verify(identityService, never()).allocateCustomerNumber();
		verify(customerRepository, never()).save(any());
		verifyNoInteractions(proctranAppender);
	}


	// ================================================================== //
	// inquireCustomer (INQCUST, F-008; read-only, miss does not abend)   //
	// ================================================================== //

	@Test
	@DisplayName("inquire: a normal customer number returns the populated details with success 'Y'")
	void inquire_normalCustomer_returnsDetails()
	{
		Customer customer = customerFixture("0000000123");
		when(customerRepository.findById(any(CustomerId.class)))
				.thenReturn(Optional.of(customer));

		CustomerEnquiryJson result = customerService.inquireCustomer(123L);

		assertThat(result.getInqCustZ().getInqCustInqSuccess()).isEqualTo("Y");
		assertThat(result.getInqCustZ().getInqCustName())
				.isEqualTo(customer.getName());
		// The normal path never consults the control row (no sentinel).
		verifyNoInteractions(customerControlRepository);
	}

	@Test
	@DisplayName("inquire: the 9999999999 'highest' sentinel reads the control row (LAST-CUSTOMER-NUMBER), never a MAX() scan")
	void inquire_highestSentinel9999999999_readsControlRowNotMaxScan()
	{
		// The control row supplies the highest allocated number (123).
		CustomerControl control = new CustomerControl();
		control.setSortCode(SORT_CODE);
		control.setLastCustomerNumber(123L);
		when(customerControlRepository.findById(SORT_CODE))
				.thenReturn(Optional.of(control));

		Customer customer = customerFixture("0000000123");
		when(customerRepository.findById(any(CustomerId.class)))
				.thenReturn(Optional.of(customer));

		CustomerEnquiryJson result = customerService
				.inquireCustomer(9999999999L);

		assertThat(result.getInqCustZ().getInqCustInqSuccess()).isEqualTo("Y");
		assertThat(result.getInqCustZ().getInqCustCustno())
				.isEqualTo("0000000123");
		// The highest number comes from the control row, not a MAX() scan;
		// COBOL parity: the 'highest' sentinel reads the control row, so there
		// is no MAX() finder on the repository.
		verify(customerControlRepository).findById(SORT_CODE);
	}

	@Test
	@DisplayName("inquire: the 0000000000 'random' sentinel uses the control row for its upper bound (no MAX() scan)")
	void inquire_randomSentinel0000000000_usesControlRowUpperBound()
	{
		// The random pick's upper bound is the control row's last number.
		CustomerControl control = new CustomerControl();
		control.setSortCode(SORT_CODE);
		control.setLastCustomerNumber(123L);
		when(customerControlRepository.findById(SORT_CODE))
				.thenReturn(Optional.of(control));

		// The first random candidate resolves to an existing customer.
		Customer customer = customerFixture("0000000077");
		when(customerRepository.findById(any(CustomerId.class)))
				.thenReturn(Optional.of(customer));

		CustomerEnquiryJson result = customerService.inquireCustomer(0L);

		// A customer is returned (kept light: the specific random pick is not
		// asserted) and the upper bound came from the control row.
		assertThat(result.getInqCustZ().getInqCustInqSuccess()).isEqualTo("Y");
		verify(customerControlRepository).findById(SORT_CODE);
	}

	@Test
	@DisplayName("inquire: a missing customer surfaces success 'N' / fail '1' in the envelope (INQCUST does not throw)")
	void inquire_notFound_failCode1()
	{
		when(customerRepository.findById(any(CustomerId.class)))
				.thenReturn(Optional.empty());

		CustomerEnquiryJson result = customerService.inquireCustomer(555L);

		// COBOL parity: INQCUST signals a miss with INQCUST-INQ-SUCCESS = 'N'
		// and fail code '1' on the envelope rather than throwing.
		assertThat(result.getInqCustZ().getInqCustInqSuccess()).isEqualTo("N");
		assertThat(result.getInqCustZ().getInqCustInqFailCd()).isEqualTo("1");
	}


	// ================================================================== //
	// updateCustomer (UPDCUST, F-011; name/address only, no PROCTRAN)    //
	// ================================================================== //

	@Test
	@DisplayName("update: changes name and address only, never DOB/credit-score/review-date, and writes NO PROCTRAN")
	void update_changesNameAndAddressOnly_neverDobCreditReview_noProctran()
	{
		LocalDate dob = LocalDate.of(1985, 6, 15);
		LocalDate review = LocalDate.of(2024, 1, 1);
		Customer existing = new Customer();
		existing.setId(new CustomerId(SORT_CODE, "0000000123"));
		existing.setName("Mr Old Name");
		existing.setAddress("Old Address");
		existing.setDateOfBirth(dob);
		existing.setCreditScore((short) 500);
		existing.setCsReviewDate(review);
		when(customerRepository.findById(any(CustomerId.class)))
				.thenReturn(Optional.of(existing));
		when(customerRepository.save(any(Customer.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));

		UpdateCustomerJson result = customerService
				.updateCustomer(updateForm("123", "Mr Jane Doe", "99 New Road"));

		ArgumentCaptor<Customer> saved = ArgumentCaptor.forClass(Customer.class);
		verify(customerRepository).save(saved.capture());
		Customer persisted = saved.getValue();
		// Name and address are the only fields that change.
		assertThat(persisted.getName()).isEqualTo("Mr Jane Doe");
		assertThat(persisted.getAddress()).isEqualTo("99 New Road");
		// DOB, credit score and review date are untouched (the F-011 invariant).
		assertThat(persisted.getDateOfBirth()).isEqualTo(dob);
		assertThat(persisted.getCreditScore().intValue()).isEqualTo(500);
		assertThat(persisted.getCsReviewDate()).isEqualTo(review);

		assertThat(result.getUpdcust().getCommUpdateSuccess()).isEqualTo("Y");
		// UPDCUST is not a financial movement: no audit row is ever written.
		verifyNoInteractions(proctranAppender);
	}

	@Test
	@DisplayName("update: both name and address blank fails '4'; nothing is saved and no PROCTRAN is written")
	void update_bothNameAndAddressBlank_failCode4()
	{
		Customer existing = customerFixture("0000000123");
		when(customerRepository.findById(any(CustomerId.class)))
				.thenReturn(Optional.of(existing));

		assertThatThrownBy(() -> customerService
				.updateCustomer(updateForm("123", "", "")))
				.isInstanceOf(BusinessRuleException.class)
				.extracting(ex -> ((BusinessRuleException) ex).getFailCode())
				.isEqualTo("4");

		verify(customerRepository, never()).save(any());
		verifyNoInteractions(proctranAppender);
	}

	@Test
	@DisplayName("update: a missing customer fails '1'; nothing is saved and no PROCTRAN is written")
	void update_customerNotFound_failCode1()
	{
		when(customerRepository.findById(any(CustomerId.class)))
				.thenReturn(Optional.empty());

		assertThatThrownBy(() -> customerService
				.updateCustomer(updateForm("123", "Mr Jane Doe", "99 New Road")))
				.isInstanceOf(BusinessRuleException.class)
				.extracting(ex -> ((BusinessRuleException) ex).getFailCode())
				.isEqualTo("1");

		verify(customerRepository, never()).save(any());
		verifyNoInteractions(proctranAppender);
	}

	@Test
	@DisplayName("update: an invalid honorific title fails 'T' before the customer is even read (UPDCUST validates the title first)")
	void update_invalidTitle_failCodeT()
	{
		assertThatThrownBy(() -> customerService.updateCustomer(
				updateForm("123", "Xyz Jane Doe", "99 New Road")))
				.isInstanceOf(BusinessRuleException.class)
				.extracting(ex -> ((BusinessRuleException) ex).getFailCode())
				.isEqualTo("T");

		// Title is validated before the read, so no repository call is made.
		verify(customerRepository, never()).findById(any());
		verify(customerRepository, never()).save(any());
		verifyNoInteractions(proctranAppender);
	}

	@Test
	@DisplayName("update: a read error fails '2'; nothing is saved and no PROCTRAN is written")
	void update_readError_failCode2()
	{
		// A DataAccessException raised while reading the customer is translated
		// to fail code '2' (UPDCUST read error).
		when(customerRepository.findById(any(CustomerId.class)))
				.thenThrow(new DataIntegrityViolationException("read failure"));

		assertThatThrownBy(() -> customerService
				.updateCustomer(updateForm("123", "Mr Jane Doe", "99 New Road")))
				.isInstanceOf(BusinessRuleException.class)
				.extracting(ex -> ((BusinessRuleException) ex).getFailCode())
				.isEqualTo("2");

		verify(customerRepository, never()).save(any());
		verifyNoInteractions(proctranAppender);
	}

	@Test
	@DisplayName("update: an over-length name and address are truncated to the COBOL fixed-field widths (60/160) before rewrite")
	void update_truncatesOverLengthNameAndAddress()
	{
		Customer existing = new Customer();
		existing.setId(new CustomerId(SORT_CODE, "0000000123"));
		when(customerRepository.findById(any(CustomerId.class)))
				.thenReturn(Optional.of(existing));
		when(customerRepository.save(any(Customer.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));

		String overLongName = "Mr " + "A".repeat(58);
		String overLongAddress = "B".repeat(ADDRESS_WIDTH + 1);

		customerService.updateCustomer(
				updateForm("123", overLongName, overLongAddress));

		ArgumentCaptor<Customer> saved = ArgumentCaptor.forClass(Customer.class);
		verify(customerRepository).save(saved.capture());
		assertThat(saved.getValue().getName()).hasSize(NAME_WIDTH)
				.isEqualTo(overLongName.substring(0, NAME_WIDTH));
		assertThat(saved.getValue().getAddress()).hasSize(ADDRESS_WIDTH)
				.isEqualTo(overLongAddress.substring(0, ADDRESS_WIDTH));
		verifyNoInteractions(proctranAppender);
	}

	// ================================================================== //
	// deleteCustomer (DELCUS, F-014; cascade then delete then audit)    //
	// ================================================================== //

	@Test
	@DisplayName("delete: cascades each owned account BEFORE deleting the customer, then appends the delete audit row")
	void delete_cascadeDeletesAccounts_thenCustomer_appendsIdc()
	{
		Customer existing = customerFixture("0000000123");
		when(customerRepository.findByIdForUpdate(any(CustomerId.class)))
				.thenReturn(Optional.of(existing));

		Account account1 = accountFixture("00000001", "0000000123");
		Account account2 = accountFixture("00000002", "0000000123");
		when(accountRepository
				.findByIdSortCodeAndCustomerNumberOrderByIdAccountNumberAsc(
						SORT_CODE, "0000000123"))
				.thenReturn(List.of(account1, account2));

		DeleteCustomerJson result = customerService.deleteCustomer(123L);

		// Each account is removed (with its own ODA audit) BEFORE the customer
		// row, exactly as DELCUS orders its cascade.
		InOrder inOrder = inOrder(accountService, customerRepository);
		inOrder.verify(accountService).deleteAccount(account1.getId());
		inOrder.verify(accountService).deleteAccount(account2.getId());
		inOrder.verify(customerRepository).delete(existing);

		// The delete audit row (web delete-customer; ODC / IDC intent) is appended.
		verify(proctranAppender).appendCustomerDelete(eq(SORT_CODE), eq(123L),
				anyString(), any(LocalDate.class));

		assertThat(result.getDelCus().getCommDelSuccess()).isEqualTo("Y");
	}

	@Test
	@DisplayName("delete: a missing customer fails '1'; no cascade, no customer delete, no PROCTRAN")
	void delete_customerNotFound_failCode1()
	{
		when(customerRepository.findByIdForUpdate(any(CustomerId.class)))
				.thenReturn(Optional.empty());

		assertThatThrownBy(() -> customerService.deleteCustomer(999L))
				.isInstanceOf(BusinessRuleException.class)
				.extracting(ex -> ((BusinessRuleException) ex).getFailCode())
				.isEqualTo("1");

		verify(accountRepository, never())
				.findByIdSortCodeAndCustomerNumberOrderByIdAccountNumberAsc(
						anyString(), anyString());
		verifyNoInteractions(accountService);
		verify(customerRepository, never()).delete(any());
		verifyNoInteractions(proctranAppender);
	}
}

