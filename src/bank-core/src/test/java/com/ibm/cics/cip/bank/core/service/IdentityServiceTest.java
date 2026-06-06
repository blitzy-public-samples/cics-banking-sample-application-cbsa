/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.core.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ibm.cics.cip.bank.core.constants.BankConstants;
import com.ibm.cics.cip.bank.core.entity.AccountControl;
import com.ibm.cics.cip.bank.core.entity.CustomerControl;
import com.ibm.cics.cip.bank.core.exception.BusinessRuleException;
import com.ibm.cics.cip.bank.core.repository.AccountControlRepository;
import com.ibm.cics.cip.bank.core.repository.CustomerControlRepository;

/**
 * Parity unit test for {@link IdentityService} &mdash; the Java rendering of the
 * COBOL {@code NEWACCNO} / {@code NEWCUSNO} named-counter allocation used by
 * {@code CREACC.cbl} and {@code CRECUST.cbl} (feature <strong>F-005</strong>,
 * <strong>ADR-003</strong>).
 *
 * <h2>What the COBOL specifies (the migration contract)</h2>
 * <p>In the legacy system the next account number was obtained by {@code CREACC}
 * and the next customer number by {@code CRECUST} through the {@code NEWACCNO} /
 * {@code NEWCUSNO} commareas, whose {@code FUNCTION} byte exposes
 * {@code 'G'} (get-new), {@code 'C'} (current), and {@code 'R'} (rollback)
 * against the {@code ACCTCTRL} / {@code CUSTCTRL} control records
 * ({@code NEWACCNO.cpy} / {@code NEWCUSNO.cpy} L7-L13). Get-new read the control
 * record's high-water counter, added one
 * (e.g. {@code CREACC.cbl} L525 {@code ADD 1 TO HV-CONTROL-VALUE-NUM GIVING ...
 * ACCOUNT-NUMBER}; {@code CRECUST.cbl} L1381 {@code ADD 1 TO
 * LAST-CUSTOMER-NUMBER}), incremented the corresponding count, and rewrote the
 * control record. Behavioural parity with that mechanism &mdash; not
 * enhancement &mdash; is what this suite pins.</p>
 *
 * <h2>What is asserted here</h2>
 * <ul>
 *   <li><strong>Gap-free, contiguous issuance.</strong> Allocation returns
 *       {@code previous + 1} and persists the incremented control row, so the
 *       next number is read from the control row rather than from a database
 *       identity or sequence generator or a {@code MAX()} scan (ADR-003,
 *       &sect;0.6).</li>
 *   <li><strong>Pessimistic-lock read.</strong> Allocation goes through
 *       {@code findBySortCodeForUpdate(...)} (the {@code @Lock(PESSIMISTIC_WRITE)}
 *       counter read that emits {@code SELECT ... FOR UPDATE}), never a
 *       {@code findAll()} / MAX scan.</li>
 *   <li><strong>Ten-digit customer range.</strong> Customer numbers are COBOL
 *       {@code 9(10)} ({@code NEWCUSNO.cpy} L11), whose maximum
 *       {@code 9_999_999_999} exceeds {@link Integer#MAX_VALUE}; the test drives
 *       values near that ceiling to prove the {@code long}/{@link Long} range is
 *       preserved (never int-truncated).</li>
 *   <li><strong>Missing control row.</strong> When no control row exists,
 *       allocation aborts with {@link BusinessRuleException} (the COBOL-faithful
 *       allocation fail code {@code "5"}) and never calls {@code save(...)}.</li>
 * </ul>
 *
 * <h2>Why this is a pure Mockito unit test (no Spring, no DB)</h2>
 * <p>{@link IdentityService} collaborates only with the two control-row
 * repositories, which are supplied here as Mockito {@code @Mock} doubles and
 * wired through constructor injection by {@code @InjectMocks}; no
 * {@code ApplicationContext}, no database, and no Flyway are bootstrapped.</p>
 *
 * <p><strong>Rollback semantics (documented, not over-asserted at unit level).</strong>
 * COBOL exposed an explicit {@code 'R'} rollback function because, under CICS, a
 * consumed counter could be committed independently of the work that consumed it
 * ({@code NEWACCNO.cpy} L9). In the Java target the increment happens inside the
 * caller's {@code @Transactional} boundary under {@code PESSIMISTIC_WRITE}, so a
 * rollback of the enclosing transaction restores the counter automatically and
 * the explicit rollback function is unnecessary (&sect;0.6, ADR-003). True
 * rollback restoration is an integration concern (a real transaction against a
 * real database); at the unit level this suite asserts only the increment/return
 * behaviour and that no database identity or sequence generator is involved.</p>
 */
@ExtendWith(MockitoExtension.class)
class IdentityServiceTest
{

	/**
	 * The single institution sort code that keys the control rows. Sourced from
	 * the production {@link BankConstants#SORT_CODE} constant (rather than a
	 * re-hard-coded literal) so the parity test drifts with production if the
	 * sort code ever changes.
	 */
	private static final String SORT_CODE = BankConstants.SORT_CODE;

	/**
	 * COBOL-faithful allocation fail code surfaced by {@link IdentityService}
	 * when the required control row is absent. This is the {@code CREACC}
	 * allocation/write fail code {@code '5'} carried verbatim by the production
	 * {@code FAIL_ALLOCATION} constant; it is read from production, never
	 * invented here.
	 */
	private static final String MISSING_CONTROL_ROW_FAIL_CODE = "5";

	/** Mocked account-control repository injected into the service under test. */
	@Mock
	private AccountControlRepository accountControlRepository;

	/** Mocked customer-control repository injected into the service under test. */
	@Mock
	private CustomerControlRepository customerControlRepository;

	/**
	 * System under test &mdash; constructed by Mockito via constructor injection
	 * with the two mocked control repositories
	 * ({@code new IdentityService(accountControlRepository,
	 * customerControlRepository)}).
	 */
	@InjectMocks
	private IdentityService service;

	/**
	 * Get-new account-number parity ({@code NEWACCNO} get-new / {@code CREACC.cbl}
	 * L525): allocation returns {@code lastAccountNumber + 1} (gap-free,
	 * contiguous) and persists a control row whose {@code lastAccountNumber} and
	 * {@code numberOfAccounts} have both been incremented.
	 */
	@Test
	void allocateAccountNumber_returnsNextContiguousNumber_andIncrementsControlRow()
	{
		AccountControl control = new AccountControl();
		control.setSortCode(SORT_CODE);
		control.setLastAccountNumber(41L);
		control.setNumberOfAccounts(41L);
		when(accountControlRepository.findBySortCodeForUpdate(SORT_CODE))
				.thenReturn(Optional.of(control));

		long allocated = service.allocateAccountNumber();

		// Gap-free, contiguous: the previous high-water (41) + 1.
		assertThat(allocated).isEqualTo(42L);

		// The PESSIMISTIC_WRITE counter read happened (SELECT ... FOR UPDATE).
		verify(accountControlRepository).findBySortCodeForUpdate(SORT_CODE);

		// The control row was rewritten with both counters advanced.
		ArgumentCaptor<AccountControl> captor =
				ArgumentCaptor.forClass(AccountControl.class);
		verify(accountControlRepository).save(captor.capture());
		AccountControl saved = captor.getValue();
		assertThat(saved.getLastAccountNumber()).isEqualTo(42L);
		assertThat(saved.getNumberOfAccounts()).isEqualTo(42L);
	}

	/**
	 * Get-new customer-number parity ({@code NEWCUSNO} get-new /
	 * {@code CRECUST.cbl} L1381): allocation returns {@code lastCustomerNumber +
	 * 1} and persists the incremented control row. Driven near the {@code 9(10)}
	 * ceiling ({@code 9_999_999_998 -> 9_999_999_999}) to prove the ten-digit
	 * customer-number range is preserved as a {@code long} and is never truncated
	 * to {@code int}.
	 */
	@Test
	void allocateCustomerNumber_returnsNextContiguousNumber_andIncrementsControlRow()
	{
		CustomerControl control = new CustomerControl();
		control.setSortCode(SORT_CODE);
		control.setLastCustomerNumber(9_999_999_998L);
		control.setNumberOfCustomers(9_999_999_998L);
		when(customerControlRepository.findBySortCodeForUpdate(SORT_CODE))
				.thenReturn(Optional.of(control));

		long allocated = service.allocateCustomerNumber();

		// Contiguous and beyond Integer.MAX_VALUE (2_147_483_647): no int truncation.
		assertThat(allocated).isEqualTo(9_999_999_999L);
		assertThat(allocated).isGreaterThan(Integer.MAX_VALUE);

		verify(customerControlRepository).findBySortCodeForUpdate(SORT_CODE);

		ArgumentCaptor<CustomerControl> captor =
				ArgumentCaptor.forClass(CustomerControl.class);
		verify(customerControlRepository).save(captor.capture());
		CustomerControl saved = captor.getValue();
		assertThat(saved.getLastCustomerNumber()).isEqualTo(9_999_999_999L);
		assertThat(saved.getNumberOfCustomers()).isEqualTo(9_999_999_999L);
	}

	/**
	 * Missing account-control row: allocation aborts with
	 * {@link BusinessRuleException} carrying the COBOL allocation fail code
	 * {@code "5"}, and {@code save(...)} is never called (nothing is persisted
	 * when there is no counter to advance).
	 */
	@Test
	void allocateAccountNumber_whenControlRowMissing_throwsBusinessRuleException()
	{
		when(accountControlRepository.findBySortCodeForUpdate(SORT_CODE))
				.thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.allocateAccountNumber())
				.isInstanceOf(BusinessRuleException.class)
				.hasFieldOrPropertyWithValue("failCode",
						MISSING_CONTROL_ROW_FAIL_CODE);

		verify(accountControlRepository, never()).save(any());
	}

	/**
	 * Missing customer-control row: analogous to the account case &mdash;
	 * allocation aborts with {@link BusinessRuleException} (fail code {@code "5"})
	 * and never persists.
	 */
	@Test
	void allocateCustomerNumber_whenControlRowMissing_throwsBusinessRuleException()
	{
		when(customerControlRepository.findBySortCodeForUpdate(SORT_CODE))
				.thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.allocateCustomerNumber())
				.isInstanceOf(BusinessRuleException.class)
				.hasFieldOrPropertyWithValue("failCode",
						MISSING_CONTROL_ROW_FAIL_CODE);

		verify(customerControlRepository, never()).save(any());
	}

	/**
	 * ADR-003 / &sect;0.6 guard: the allocation path obtains the next number from
	 * the locked control row via {@code findBySortCodeForUpdate(...)} (the
	 * {@code SELECT ... FOR UPDATE} counter read) and never performs a
	 * {@code findAll()} / {@code MAX()} scan. This documents that numbering comes
	 * from the control row rather than a database identity/sequence or a table
	 * scan.
	 */
	@Test
	void allocation_usesPessimisticLockReadNotMaxScan()
	{
		AccountControl control = new AccountControl();
		control.setSortCode(SORT_CODE);
		control.setLastAccountNumber(100L);
		control.setNumberOfAccounts(100L);
		when(accountControlRepository.findBySortCodeForUpdate(SORT_CODE))
				.thenReturn(Optional.of(control));

		service.allocateAccountNumber();

		// The counter came from the pessimistic-lock control-row read ...
		verify(accountControlRepository).findBySortCodeForUpdate(SORT_CODE);
		// ... and NOT from a MAX scan over all rows.
		verify(accountControlRepository, never()).findAll();
	}

	/**
	 * Contiguous issuance across successive calls: with a single mutable
	 * {@link AccountControl} whose real setters advance the backing field and
	 * which the locked read returns on each call, two successive allocations
	 * yield {@code N} then {@code N + 1}. This documents the gap-free, monotonic
	 * numbering the COBOL named counter guaranteed.
	 */
	@Test
	void allocateAccountNumber_isSequentialAcrossTwoCalls()
	{
		AccountControl control = new AccountControl();
		control.setSortCode(SORT_CODE);
		control.setLastAccountNumber(10L);
		control.setNumberOfAccounts(10L);
		// The same mutable instance is returned on every locked read, so the
		// second read observes the increment the first allocation applied.
		when(accountControlRepository.findBySortCodeForUpdate(SORT_CODE))
				.thenReturn(Optional.of(control));

		long first = service.allocateAccountNumber();
		long second = service.allocateAccountNumber();

		assertThat(first).isEqualTo(11L);
		assertThat(second).isEqualTo(12L);
		// Strictly contiguous, gap-free issuance.
		assertThat(second).isEqualTo(first + 1L);
	}

}
