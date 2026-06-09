/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.core.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.ibm.cics.cip.bank.core.domain.TransactionType;
import com.ibm.cics.cip.bank.core.entity.AccountControl;
import com.ibm.cics.cip.bank.core.entity.ProcessedTransaction;
import com.ibm.cics.cip.bank.core.entity.ProcessedTransactionId;
import com.ibm.cics.cip.bank.core.repository.AccountControlRepository;
import com.ibm.cics.cip.bank.core.repository.ProcessedTransactionRepository;
import com.ibm.cics.cip.bank.core.util.BankFormat;

/**
 * Appends rows to the append-only {@code processed_transaction} (PROCTRAN)
 * audit log, reproducing the {@code WRITE-PROCTRAN-DB2} sections that the COBOL
 * business programs ({@code CRECUST}, {@code DELCUS}, {@code XFRFUN}) execute as
 * part of a customer create/delete or a funds transfer. The account
 * create/delete ({@code CREACC}/{@code DELACC}) and the debit/credit
 * ({@code DBCRFUN}) movements append their own PROCTRAN rows inline through
 * {@code AccountService} and {@code PaymentService} respectively; this appender
 * is therefore the sole writer of the customer-level {@code OCC}/{@code ODC}
 * audit rows and the transfer {@code TFR} audit row.
 *
 * <p><strong>Atomicity (review finding F-TXN-1).</strong> Every public method
 * here is annotated {@link Transactional @Transactional} with
 * {@link Propagation#MANDATORY MANDATORY}, so an audit append can only run
 * <em>inside</em> a transaction the calling service already started. The
 * mutation (account/customer change) and its PROCTRAN append therefore commit
 * or roll back together as a single unit of work &mdash; exactly the CICS
 * {@code SYNCPOINT}/{@code ROLLBACK} guarantee the legacy programs relied on,
 * and the property that the previous webui implementation violated by opening a
 * second JDBC connection for the audit write.</p>
 *
 * <p><strong>Field semantics (review finding F-PT-1).</strong> The
 * {@code transaction_number} column carries the eight-digit <em>account
 * number</em> the movement applies to (or {@code "00000000"} for the
 * customer-level create/delete events, matching {@code CRECUST}'s
 * {@code MOVE ZEROS TO HV-PROCTRAN-ACC-NUMBER}); it is <em>not</em> a generated
 * audit sequence. The {@code ref} column carries the unique twelve-digit audit
 * reference and is the table's primary-key discriminator. This restores the
 * original meaning of the webui {@code accountNumber} response field, which is
 * populated from {@code transaction_number}.</p>
 *
 * <p><strong>Reference allocation.</strong> Because the table has no database
 * identity/sequence (ADR-003) the next {@code ref} is derived as
 * {@code MAX(ref) + 1} for the sort code. To make that allocation safe under
 * concurrency without a sequence, the appender first acquires the
 * {@code PESSIMISTIC_WRITE} lock on the {@link AccountControl} row for the sort
 * code; since every PROCTRAN write for a sort code passes through this same
 * lock, two concurrent appends cannot compute the same reference. The lock is
 * always taken <em>after</em> any account-row locks a caller already holds
 * (payments/transfers lock account rows first), giving a single global lock
 * order &mdash; account rows ascending, then the control row &mdash; that is
 * free of deadlock.</p>
 *
 * <p><strong>Description layout.</strong> The forty-character description area
 * is built to the exact fixed-width layout the frozen webui {@code GET}
 * processed-transactions endpoint parses (it remains a read-only JDBC adapter),
 * so the migrated writer and the preserved reader stay byte-compatible.</p>
 */
@Service
public class ProcessedTransactionAppender
{

	/** PROCTRAN type code for a branch-channel create-customer event. */
	public static final String TYPE_CREATE_CUSTOMER = "OCC";

	/** PROCTRAN type code for a branch-channel delete-customer event. */
	public static final String TYPE_DELETE_CUSTOMER = "ODC";

	/** PROCTRAN type code for a bank-to-bank transfer event. */
	public static final String TYPE_TRANSFER = "TFR";

	/**
	 * Sentinel {@code transaction_number} for customer-level events, matching
	 * the COBOL {@code MOVE ZEROS TO HV-PROCTRAN-ACC-NUMBER}.
	 */
	private static final String NO_ACCOUNT = "00000000";

	/** Fixed header of a transfer description ({@code PROC-TRAN-DESC-XFR-FLAG}). */
	private static final String TRANSFER_HEADER = "TRANSFER";

	/** Width of the transfer-description header field. */
	private static final int TRANSFER_HEADER_WIDTH = 26;

	/** Width of the customer-name field inside a customer description. */
	private static final int NAME_WIDTH = 14;

	/** Total fixed width of the PROCTRAN description area. */
	private static final int DESCRIPTION_WIDTH = 40;

	/** Monetary scale used for all PROCTRAN amounts. */
	private static final int MONEY_SCALE = 2;

	private final ProcessedTransactionRepository proctranRepository;

	private final AccountControlRepository accountControlRepository;

	/**
	 * Constructs the appender with the repositories it needs to allocate a
	 * unique reference and persist the audit row.
	 *
	 * @param proctranRepository        repository for the PROCTRAN table
	 * @param accountControlRepository  repository whose control row is locked to
	 *                                  serialise reference allocation per sort
	 *                                  code
	 */
	public ProcessedTransactionAppender(
			ProcessedTransactionRepository proctranRepository,
			AccountControlRepository accountControlRepository)
	{
		this.proctranRepository = proctranRepository;
		this.accountControlRepository = accountControlRepository;
	}

	/**
	 * Appends a create-customer ({@code OCC}) audit row.
	 *
	 * @param sortCode       the six-digit sort code
	 * @param customerNumber the new customer's number
	 * @param name           the customer name
	 * @param dateOfBirth    the customer date of birth
	 * @return the persisted audit row
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public ProcessedTransaction appendCustomerCreate(String sortCode,
			long customerNumber, String name, LocalDate dateOfBirth)
	{
		return append(sortCode, NO_ACCOUNT, TYPE_CREATE_CUSTOMER,
				customerDescription(sortCode, customerNumber, name,
						dateOfBirth),
				BigDecimal.ZERO);
	}

	/**
	 * Appends a delete-customer ({@code ODC}) audit row.
	 *
	 * @param sortCode       the six-digit sort code
	 * @param customerNumber the removed customer's number
	 * @param name           the customer name
	 * @param dateOfBirth    the customer date of birth
	 * @return the persisted audit row
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public ProcessedTransaction appendCustomerDelete(String sortCode,
			long customerNumber, String name, LocalDate dateOfBirth)
	{
		return append(sortCode, NO_ACCOUNT, TYPE_DELETE_CUSTOMER,
				customerDescription(sortCode, customerNumber, name,
						dateOfBirth),
				BigDecimal.ZERO);
	}

	/**
	 * Appends a transfer ({@code TFR}) audit row keyed on the source account,
	 * with the target sort code and account encoded in the description.
	 *
	 * @param sortCode       the source account's sort code (the row's sort code)
	 * @param sourceAccount  the source (debited) account number
	 * @param targetSortCode the target account's sort code
	 * @param targetAccount  the target (credited) account number
	 * @param amount         the transfer amount
	 * @return the persisted audit row
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public ProcessedTransaction appendTransfer(String sortCode,
			long sourceAccount, long targetSortCode, long targetAccount,
			BigDecimal amount)
	{
		return append(sortCode, BankFormat.accountNumber(sourceAccount),
				TYPE_TRANSFER,
				transferDescription(targetSortCode, targetAccount), amount);
	}

	/**
	 * Core append routine: allocates a unique reference under the control-row
	 * lock and persists a single audit row with {@code deleted = false}.
	 *
	 * @param sortCode          the six-digit sort code
	 * @param transactionNumber the eight-digit account number (or
	 *                          {@value #NO_ACCOUNT})
	 * @param typeCode          the three-character PROCTRAN type code
	 * @param description       the description area (padded/truncated to 40)
	 * @param amount            the amount (scaled to 2 decimal places)
	 * @return the persisted audit row
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public ProcessedTransaction append(String sortCode,
			String transactionNumber, String typeCode, String description,
			BigDecimal amount)
	{
		String paddedSortCode = BankFormat.pad(sortCode,
				BankFormat.SORT_CODE_LENGTH);

		// Serialise reference allocation for this sort code by locking the
		// control row, then allocate the next reference from the
		// last_transaction_reference counter on that same locked row. This is an
		// O(1) read+increment that mirrors the gap-free account/customer-number
		// counter pattern (ADR-003); it replaces the former O(N)
		// MAX(CAST(TRIM(ref) AS BIGINT)) sequential scan over the append-only
		// PROCTRAN table, which degraded every audit write linearly as the log
		// grew (F2-02). The increment is committed on the locked row via save(),
		// so a rollback of the enclosing transaction restores the counter.
		AccountControl control = accountControlRepository
				.findBySortCodeForUpdate(paddedSortCode)
				.orElseThrow(() -> new IllegalStateException(
						"Missing account-control row for sort code "
								+ paddedSortCode));

		long nextReference = control.getLastTransactionReference() + 1L;
		control.setLastTransactionReference(nextReference);
		accountControlRepository.save(control);

		ProcessedTransaction row = new ProcessedTransaction();
		row.setId(new ProcessedTransactionId(paddedSortCode,
				BankFormat.reference(nextReference)));
		row.setTransactionNumber(transactionNumber);
		row.setDate(LocalDate.now());
		row.setTime(LocalTime.now());
		row.setTypeCode(TransactionType.fromCode(typeCode));
		row.setDescription(fixedWidth(description, DESCRIPTION_WIDTH));
		row.setAmount(amount.setScale(MONEY_SCALE, RoundingMode.HALF_UP));
		row.setDeleted(false);
		return proctranRepository.save(row);
	}

	/**
	 * Builds the forty-character create/delete-customer description:
	 * {@code sortCode(6) + customerNumber(10) + name(14) + DD/MM/YYYY(10)}.
	 *
	 * @param sortCode       the sort code
	 * @param customerNumber the customer number
	 * @param name           the customer name
	 * @param dateOfBirth    the date of birth
	 * @return the fixed-width description
	 */
	static String customerDescription(String sortCode, long customerNumber,
			String name, LocalDate dateOfBirth)
	{
		StringBuilder description = new StringBuilder();
		description.append(BankFormat.pad(sortCode, BankFormat.SORT_CODE_LENGTH));
		description.append(BankFormat.customerNumber(customerNumber));
		description.append(rightPad(name, NAME_WIDTH));
		description.append(dateSlashes(dateOfBirth));
		return description.toString();
	}

	/**
	 * Builds the forty-character transfer description:
	 * {@code "TRANSFER" left-justified in 26 + targetSortCode(6) +
	 * targetAccount(8)}.
	 *
	 * @param targetSortCode the target sort code
	 * @param targetAccount  the target account number
	 * @return the fixed-width description
	 */
	static String transferDescription(long targetSortCode, long targetAccount)
	{
		StringBuilder description = new StringBuilder();
		description.append(rightPad(TRANSFER_HEADER, TRANSFER_HEADER_WIDTH));
		description.append(BankFormat.sortCode(targetSortCode));
		description.append(BankFormat.accountNumber(targetAccount));
		return description.toString();
	}

	/**
	 * Formats a date as the {@code DD/MM/YYYY} field used inside the customer
	 * description area (the separators occupy the positions the frozen reader
	 * skips).
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
	 * Right-pads a value with spaces (and truncates) to a fixed width, matching
	 * the COBOL {@code PIC X(n)} {@code MOVE} semantics.
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
	 * Right-pads/truncates the assembled description to the fixed PROCTRAN
	 * description width.
	 *
	 * @param value the assembled description
	 * @param width the target width (40)
	 * @return the forty-character description
	 */
	private static String fixedWidth(String value, int width)
	{
		return rightPad(value, width);
	}

}
