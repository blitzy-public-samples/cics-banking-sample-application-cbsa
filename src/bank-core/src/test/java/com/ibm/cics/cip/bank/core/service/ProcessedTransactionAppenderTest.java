/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.core.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.only;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ibm.cics.cip.bank.core.constants.BankConstants;
import com.ibm.cics.cip.bank.core.domain.TransactionType;
import com.ibm.cics.cip.bank.core.entity.AccountControl;
import com.ibm.cics.cip.bank.core.entity.ProcessedTransaction;
import com.ibm.cics.cip.bank.core.repository.AccountControlRepository;
import com.ibm.cics.cip.bank.core.repository.ProcessedTransactionRepository;

/**
 * Parity unit test for {@link ProcessedTransactionAppender} &mdash; the sole
 * writer of the customer-level {@code OCC}/{@code ODC} and the transfer
 * {@code TFR} append-only PROCTRAN audit rows, and the gap-free PROCTRAN
 * reference allocator (the {@code WRITE-PROCTRAN-DB2} sections of {@code CRECUST},
 * {@code DELCUS} and {@code XFRFUN}).
 *
 * <h2>Why this test exists</h2>
 * <p>Every production call site mocks this appender (it is injected into
 * {@code CustomerService} and {@code TransferService}), so before this suite its
 * real code &mdash; the reference increment, the row build, scale-2 rounding, the
 * {@code deleted = false} default and the fixed-width description layouts &mdash;
 * was never executed by any test. This class exercises the <em>real</em> appender
 * (mocking only its two repositories) so a regression in any of those AAP-critical
 * concerns is caught: a wrong type code, a wrong PROCTRAN date format, an
 * off-by-one reference, {@code deleted = true}, or a collapsed/double money scale
 * can no longer ship undetected on the customer-create/delete and transfer audit
 * paths.</p>
 *
 * <h2>Test strategy &mdash; pure Mockito, DB-free</h2>
 * <p>{@link MockitoExtension} drives a plain unit test: the
 * {@link ProcessedTransactionRepository} and {@link AccountControlRepository}
 * collaborators are {@link Mock @Mock}s and the appender is the
 * {@link InjectMocks @InjectMocks} subject. No Spring context, datasource, JPA or
 * Flyway is started; the {@code @Transactional(MANDATORY)} annotations are inert
 * without a proxy, so each method runs directly and its observable effects are
 * asserted. The class never references {@code com.ibm.cics.server} (JCICS),
 * {@code com.ibm.jzos} or {@code com.ibm.websphere}.</p>
 *
 * @see ProcessedTransactionAppender
 * @see AccountControlRepository#findBySortCodeForUpdate(String)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ProcessedTransactionAppender — append-only PROCTRAN audit rows + gap-free reference allocation under PESSIMISTIC_WRITE")
class ProcessedTransactionAppenderTest
{

	/** Branch sort code (the value the production services pass). */
	private static final String SORT = BankConstants.SORT_CODE;

	/** Mocked PROCTRAN repository (the audit row sink). */
	@Mock
	private ProcessedTransactionRepository proctranRepository;

	/**
	 * Mocked account-control repository whose locked row carries the
	 * {@code last_transaction_reference} counter the appender increments.
	 */
	@Mock
	private AccountControlRepository accountControlRepository;

	/** The appender under test, with the two mocks injected by Mockito. */
	@InjectMocks
	private ProcessedTransactionAppender appender;

	// ------------------------------------------------------------------ //
	// Fixtures and helpers                                                //
	// ------------------------------------------------------------------ //

	/**
	 * Builds an {@link AccountControl} row for {@link #SORT} carrying the given
	 * last-allocated transaction reference.
	 *
	 * @param lastReference the current {@code last_transaction_reference}
	 * @return the control-row fixture
	 */
	private static AccountControl control(long lastReference)
	{
		AccountControl control = new AccountControl();
		control.setSortCode(SORT);
		control.setLastTransactionReference(lastReference);
		return control;
	}

	/**
	 * Stubs {@code proctranRepository.save(...)} to echo its argument, so the
	 * row the appender persists is returned to the caller for assertion.
	 */
	private void stubSaveEcho()
	{
		when(proctranRepository.save(any(ProcessedTransaction.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));
	}

	// ------------------------------------------------------------------ //
	// Customer create (OCC) / delete (ODC)                                //
	// ------------------------------------------------------------------ //

	/**
	 * {@code appendCustomerCreate} builds an {@code OCC} row keyed on the
	 * customer-level sentinel account {@code "00000000"}, allocates the next
	 * reference ({@code lastReference + 1}) from the locked control row (read
	 * <em>before</em> the row is persisted), defaults {@code deleted = false},
	 * carries a scale-2 zero amount, and lays the description out as
	 * {@code sortCode(6) + customerNumber(10) + name(14) + DD/MM/YYYY(10)}.
	 */
	@Test
	@DisplayName("appendCustomerCreate — OCC row, txn '00000000', deleted=false, ref=last+1 under lock-before-save, DD/MM/YYYY description")
	void appendCustomerCreate_buildsOccRow_allocatesReferenceUnderLock()
	{
		AccountControl ctrl = control(41L);
		when(accountControlRepository.findBySortCodeForUpdate(SORT))
				.thenReturn(Optional.of(ctrl));
		stubSaveEcho();

		LocalDate dateOfBirth = LocalDate.of(1990, 3, 15);
		ProcessedTransaction row = appender.appendCustomerCreate(SORT, 42L,
				"JOHN SMITH", dateOfBirth);

		// Type code + key fields.
		assertThat(row.getTypeCode()).isEqualTo(TransactionType.OCC);
		assertThat(row.getTransactionNumber()).isEqualTo("00000000");
		assertThat(row.isDeleted()).isFalse();
		assertThat(row.getAmount()).isEqualByComparingTo("0.00");
		assertThat(row.getAmount().scale()).isEqualTo(2);
		assertThat(row.getDate()).isNotNull();
		assertThat(row.getTime()).isNotNull();

		// Gap-free reference = lastReference + 1; the control row is incremented
		// and persisted under the same transaction.
		assertThat(row.getId().getSortCode()).isEqualTo(SORT);
		assertThat(row.getId().getRef()).isEqualTo("000000000042");
		assertThat(ctrl.getLastTransactionReference()).isEqualTo(42L);
		verify(accountControlRepository).save(ctrl);

		// DD/MM/YYYY customer-description layout, fixed 40-char width.
		String description = row.getDescription();
		assertThat(description).hasSize(40);
		assertThat(description).startsWith("9876540000000042JOHN SMITH");
		assertThat(description).endsWith("15/03/1990");

		// The reference is read under the PESSIMISTIC_WRITE control-row lock
		// BEFORE the audit row is persisted (one global lock order; no MAX scan).
		InOrder order = inOrder(accountControlRepository, proctranRepository);
		order.verify(accountControlRepository).findBySortCodeForUpdate(SORT);
		order.verify(proctranRepository).save(any(ProcessedTransaction.class));
	}

	/**
	 * {@code appendCustomerDelete} builds an {@code ODC} row with the same
	 * customer sentinel/zeroed account number and the same DD/MM/YYYY
	 * description layout, allocating the next reference from the control row.
	 */
	@Test
	@DisplayName("appendCustomerDelete — ODC row, txn '00000000', deleted=false, ref=last+1, DD/MM/YYYY description")
	void appendCustomerDelete_buildsOdcRow()
	{
		AccountControl ctrl = control(7L);
		when(accountControlRepository.findBySortCodeForUpdate(SORT))
				.thenReturn(Optional.of(ctrl));
		stubSaveEcho();

		ProcessedTransaction row = appender.appendCustomerDelete(SORT, 9L,
				"JANE DOE", LocalDate.of(1985, 12, 1));

		assertThat(row.getTypeCode()).isEqualTo(TransactionType.ODC);
		assertThat(row.getTransactionNumber()).isEqualTo("00000000");
		assertThat(row.isDeleted()).isFalse();
		assertThat(row.getId().getRef()).isEqualTo("000000000008");
		assertThat(row.getDescription()).hasSize(40).endsWith("01/12/1985");
	}

	// ------------------------------------------------------------------ //
	// Transfer (TFR)                                                      //
	// ------------------------------------------------------------------ //

	/**
	 * {@code appendTransfer} builds a {@code TFR} row keyed on the source
	 * (debited) account, encodes the target sort code and account in the
	 * description ({@code "TRANSFER" left-justified in 26 + targetSortCode(6) +
	 * targetAccount(8)}), and records the scale-2 transfer amount.
	 */
	@Test
	@DisplayName("appendTransfer — TFR row keyed on source account, target sort/account encoded in the description, scale-2 amount")
	void appendTransfer_buildsTfrRow_keyedOnSource_targetInDescription()
	{
		AccountControl ctrl = control(100L);
		when(accountControlRepository.findBySortCodeForUpdate(SORT))
				.thenReturn(Optional.of(ctrl));
		stubSaveEcho();

		ProcessedTransaction row = appender.appendTransfer(SORT, 1L, 987654L, 2L,
				new BigDecimal("200.00"));

		assertThat(row.getTypeCode()).isEqualTo(TransactionType.TFR);
		// The row is keyed on the source account number (eight digits).
		assertThat(row.getTransactionNumber()).isEqualTo("00000001");
		assertThat(row.isDeleted()).isFalse();
		assertThat(row.getAmount()).isEqualByComparingTo("200.00");
		assertThat(row.getAmount().scale()).isEqualTo(2);
		assertThat(row.getId().getRef()).isEqualTo("000000000101");

		String description = row.getDescription();
		assertThat(description).hasSize(40);
		assertThat(description).startsWith("TRANSFER");
		// Target sort code (6) + target account (8) end the description.
		assertThat(description).endsWith("98765400000002");
	}

	// ------------------------------------------------------------------ //
	// Core append: scale-2 HALF_UP, gap-free sequence, lock, error path   //
	// ------------------------------------------------------------------ //

	/**
	 * The core {@code append} scales the amount to two decimal places using
	 * {@link java.math.RoundingMode#HALF_UP}: {@code 1.005 -> 1.01} (rounds the
	 * tie away from zero, distinguishing HALF_UP from HALF_EVEN/DOWN) and
	 * {@code 2.344 -> 2.34} (rounds the sub-five fraction down). The result is
	 * always at scale 2 &mdash; never a collapsed or {@code double}-derived value.
	 */
	@Test
	@DisplayName("append — amount scaled to 2 places HALF_UP (1.005→1.01, 2.344→2.34), never double")
	void append_scalesAmountHalfUpToTwoPlaces()
	{
		AccountControl ctrl = control(0L);
		when(accountControlRepository.findBySortCodeForUpdate(SORT))
				.thenReturn(Optional.of(ctrl));
		stubSaveEcho();

		ProcessedTransaction roundedUp = appender.append(SORT, "00000001", "TFR",
				"tie-rounds-up", new BigDecimal("1.005"));
		assertThat(roundedUp.getAmount()).isEqualByComparingTo("1.01");
		assertThat(roundedUp.getAmount().scale()).isEqualTo(2);

		ProcessedTransaction roundedDown = appender.append(SORT, "00000001",
				"DEB", "sub-five-rounds-down", new BigDecimal("2.344"));
		assertThat(roundedDown.getAmount()).isEqualByComparingTo("2.34");
		assertThat(roundedDown.getAmount().scale()).isEqualTo(2);
	}

	/**
	 * The core {@code append} allocates sequential, gap-free references straight
	 * from the control-row counter ({@code 41 -> 42 -> 43}), reading the
	 * already-incremented counter on the locked row rather than performing a
	 * {@code MAX(ref)} scan, and persists one audit row per call.
	 */
	@Test
	@DisplayName("append — sequential gap-free references from the control counter (41→42→43)")
	void append_allocatesSequentialGapFreeReferences()
	{
		AccountControl ctrl = control(41L);
		when(accountControlRepository.findBySortCodeForUpdate(SORT))
				.thenReturn(Optional.of(ctrl));
		stubSaveEcho();

		ProcessedTransaction first = appender.append(SORT, "00000001", "TFR",
				"a", new BigDecimal("1.00"));
		ProcessedTransaction second = appender.append(SORT, "00000001", "TFR",
				"b", new BigDecimal("2.00"));

		assertThat(first.getId().getRef()).isEqualTo("000000000042");
		assertThat(second.getId().getRef()).isEqualTo("000000000043");
		assertThat(ctrl.getLastTransactionReference()).isEqualTo(43L);
		verify(accountControlRepository, times(2)).save(ctrl);
		verify(proctranRepository, times(2)).save(any(ProcessedTransaction.class));
	}

	/**
	 * The reference is derived from the {@code PESSIMISTIC_WRITE}-locked control
	 * row, not from a {@code MAX(ref)} scan of the append-only table: the
	 * PROCTRAN repository is consulted <em>only</em> to persist the row (ADR-003 /
	 * review finding F2-02 &mdash; the O(1) read+increment that replaced the O(N)
	 * sequential scan).
	 */
	@Test
	@DisplayName("append — reference from the locked control row; PROCTRAN touched only by save (no MAX scan)")
	void append_referenceFromLockedControlRow_noMaxScan()
	{
		AccountControl ctrl = control(10L);
		when(accountControlRepository.findBySortCodeForUpdate(SORT))
				.thenReturn(Optional.of(ctrl));
		stubSaveEcho();

		ProcessedTransaction row = appender.append(SORT, "00000001", "CRE",
				"x", new BigDecimal("5.00"));

		assertThat(row.getId().getRef()).isEqualTo("000000000011");
		verify(accountControlRepository).findBySortCodeForUpdate(SORT);
		verify(proctranRepository, only()).save(any(ProcessedTransaction.class));
	}

	/**
	 * When no account-control row exists for the sort code the appender cannot
	 * allocate a reference: it raises {@link IllegalStateException} and persists
	 * nothing (the enclosing transaction will roll back).
	 */
	@Test
	@DisplayName("append — missing account-control row raises IllegalStateException and persists nothing")
	void append_missingControlRow_throwsIllegalStateAndPersistsNothing()
	{
		when(accountControlRepository.findBySortCodeForUpdate(SORT))
				.thenReturn(Optional.empty());

		assertThatThrownBy(() -> appender.append(SORT, "00000001", "TFR", "x",
				new BigDecimal("1.00")))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("Missing account-control row");

		verify(proctranRepository, never()).save(any());
	}

}
