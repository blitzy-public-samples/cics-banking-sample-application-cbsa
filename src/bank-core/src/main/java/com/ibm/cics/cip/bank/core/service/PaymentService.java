/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.core.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.ibm.cics.cip.bank.core.constants.BankConstants;
import com.ibm.cics.cip.bank.core.domain.AccountType;
import com.ibm.cics.cip.bank.core.domain.TransactionType;
import com.ibm.cics.cip.bank.core.dto.payment.DbcrJson;
import com.ibm.cics.cip.bank.core.dto.payment.OriginJson;
import com.ibm.cics.cip.bank.core.dto.payment.PaymentJson;
import com.ibm.cics.cip.bank.core.entity.Account;
import com.ibm.cics.cip.bank.core.entity.AccountId;
import com.ibm.cics.cip.bank.core.entity.ProcessedTransaction;
import com.ibm.cics.cip.bank.core.entity.ProcessedTransactionId;
import com.ibm.cics.cip.bank.core.exception.BusinessRuleException;
import com.ibm.cics.cip.bank.core.repository.AccountRepository;
import com.ibm.cics.cip.bank.core.repository.ProcessedTransactionRepository;

/**
 * Debit/credit (payment) business service &mdash; the authoritative pure-Java
 * port of the COBOL program {@code DBCRFUN} (feature&nbsp;F-015). It backs the
 * frozen z/OS&nbsp;Connect <em>make-payment</em> endpoint
 * ({@code PUT /makepayment/dbcr}) and reproduces the legacy behaviour exactly:
 * its single-character fail codes, its signed-amount convention, its
 * facility-type channel rules, and its <em>dual-balance</em> update semantics
 * (AAP&nbsp;&sect;0.6, &sect;0.7).
 *
 * <h2>Transaction boundary</h2>
 * <p>The whole operation runs inside a single
 * {@link Transactional @Transactional} boundary
 * ({@link Propagation#REQUIRED}, {@link Isolation#READ_COMMITTED}) so that the
 * account balance update and the {@code PROCTRAN} audit append commit or roll
 * back together &mdash; the Spring rendering of the COBOL
 * {@code EXEC CICS SYNCPOINT}/{@code ROLLBACK} pair. The account row is read
 * under a {@code PESSIMISTIC_WRITE} lock
 * ({@link AccountRepository#findByIdForUpdate(AccountId)}), reproducing the CICS
 * record lock {@code DBCRFUN} holds between its {@code SELECT} and
 * {@code UPDATE}; an unchecked {@link BusinessRuleException} unwinds the
 * transaction on any fail code.</p>
 *
 * <h2>Sign convention (BIND)</h2>
 * <p>A negative {@code CommAmt} is a DEBIT (money out); a non-negative amount
 * (including zero) is a CREDIT (money in). This mirrors {@code IF COMM-AMT &lt; 0}
 * in {@code DBCRFUN} (L307). The sign is <strong>already applied</strong> by the
 * inbound {@code DbcrJson} (its {@code DbcrJson(TransferForm)} constructor
 * negates a debit), so this service <strong>never re-negates</strong> the
 * amount.</p>
 *
 * <h2>Facility-type channel rules</h2>
 * <p>The facility type {@code CommFaciltype} discriminates the calling channel.
 * Facility type {@value com.ibm.cics.cip.bank.core.constants.BankConstants#PAYMENT_FACILITY_TYPE}
 * is the PAYMENT channel; any other value is the teller/branch channel. On the
 * PAYMENT channel:</p>
 * <ul>
 *   <li>a debit or credit against a {@code MORTGAGE} or {@code LOAN} account
 *       fails with code {@code 4} ({@code DBCRFUN} L330-338 debit, L368-376
 *       credit);</li>
 *   <li>a debit that would breach the overdraft floor fails with code {@code 3}
 *       (insufficient funds; {@code DBCRFUN} L340-347, generalised by
 *       AAP&nbsp;&sect;0.6 &mdash; see {@link #processPayment(PaymentJson)}).</li>
 * </ul>
 * The teller/branch channel (any facility type other than {@code 496})
 * deliberately <strong>bypasses</strong> both the account-type guard and the
 * insufficient-funds guard.
 *
 * <h2>Balances (BIND)</h2>
 * <p>On success both the available and the actual balance move together by the
 * signed amount ({@code DBCRFUN} L384-387). They are independent values
 * (cleared vs. pending funds) and are <strong>never collapsed</strong> into a
 * single figure.</p>
 *
 * <h2>Audit (PROCTRAN) type codes</h2>
 * <p>Exactly four type codes are produced on this path ({@code DBCRFUN}
 * L491-517): teller debit {@link TransactionType#DEB} (description
 * {@code "COUNTER WTHDRW"}) and teller credit {@link TransactionType#CRE}
 * (description {@code "COUNTER RECVED"}); payment debit
 * {@link TransactionType#PDR} and payment credit {@link TransactionType#PCR},
 * each described by the first fourteen characters of the origin
 * ({@code COMM-ORIGIN(1:14)}).</p>
 *
 * <h2>Fail codes</h2>
 * <ul>
 *   <li>{@code 1} &mdash; account not found ({@code DBCRFUN} L283-284,
 *       {@code SQLCODE +100});</li>
 *   <li>{@code 2} &mdash; persistence/SQL error on the update ({@code DBCRFUN}
 *       L425-427);</li>
 *   <li>{@code 3} &mdash; insufficient funds on a payment debit ({@code DBCRFUN}
 *       L344-347);</li>
 *   <li>{@code 4} &mdash; payment against a {@code MORTGAGE}/{@code LOAN}
 *       account ({@code DBCRFUN} L330-335, L368-373).</li>
 * </ul>
 */
@Service
public class PaymentService
{

	/** Fail code: the account does not exist ({@code DBCRFUN} {@code SQLCODE +100}). */
	private static final String FAIL_ACCOUNT_NOT_FOUND = "1";

	/** Fail code: a persistence/SQL error occurred applying the update. */
	private static final String FAIL_SQL_ERROR = "2";

	/** Fail code: insufficient funds on a payment-channel debit. */
	private static final String FAIL_INSUFFICIENT_FUNDS = "3";

	/** Fail code: payment-channel movement on a {@code MORTGAGE}/{@code LOAN} account. */
	private static final String FAIL_RESTRICTED_ACCOUNT = "4";

	/** Monetary scale (two decimal places) for every computed money value. */
	private static final int MONEY_SCALE = 2;

	/** Fixed COBOL width of the account number ({@code COMM-ACCNO PIC X(8)}). */
	private static final int ACCOUNT_NUMBER_WIDTH = 8;

	/** Fixed width of the PROCTRAN reference ({@code PROC-TRAN-REF PIC 9(12)}). */
	private static final int REFERENCE_WIDTH = 12;

	/** Fixed width of the PROCTRAN description ({@code PROC-TRAN-DESC PIC X(40)}). */
	private static final int DESCRIPTION_WIDTH = 40;

	/** Width of the origin slice carried into a payment description ({@code COMM-ORIGIN(1:14)}). */
	private static final int ORIGIN_DESC_WIDTH = 14;

	/** Teller-debit description ({@code DBCRFUN} L493). */
	private static final String DESC_COUNTER_WITHDRAW = "COUNTER WTHDRW";

	/** Teller-credit description ({@code DBCRFUN} L506). */
	private static final String DESC_COUNTER_RECEIVED = "COUNTER RECVED";

	/** Success flag written to the response commarea ({@code COMM-SUCCESS = 'Y'}). */
	private static final String SUCCESS_FLAG = "Y";

	/** Blank fail code written to the response commarea on success ({@code COMM-FAIL-CODE}). */
	private static final String BLANK_FAIL_CODE = " ";

	/** Repository for reading (under a write lock) and saving the {@link Account}. */
	private final AccountRepository accountRepository;

	/** Append-only repository for the {@code PROCTRAN} audit log. */
	private final ProcessedTransactionRepository processedTransactionRepository;

	/**
	 * Constructs the payment service with its collaborators (constructor
	 * injection only).
	 *
	 * @param accountRepository              repository for {@link Account}
	 *                                       read/lock/update
	 * @param processedTransactionRepository repository for appending the
	 *                                       {@code PROCTRAN} audit row
	 */
	public PaymentService(AccountRepository accountRepository,
			ProcessedTransactionRepository processedTransactionRepository)
	{
		this.accountRepository = accountRepository;
		this.processedTransactionRepository = processedTransactionRepository;
	}

	/**
	 * Applies a debit or credit to a single account, reproducing
	 * {@code DBCRFUN} end to end. Works against the inner {@link DbcrJson}
	 * commarea carried by the {@code PAYDBCR} envelope.
	 *
	 * <p><strong>Overdraft rule.</strong> Per AAP&nbsp;&sect;0.6 the binding
	 * insufficient-funds rule is that a debit is permitted only when
	 * {@code (availableBalance + commAmt) >= -overdraftLimit}. Because
	 * {@code commAmt} is already negative for a debit, this is equivalent to
	 * {@code availableBalance + commAmt >= -overdraftLimit}. The overdraft limit
	 * is an {@code Integer} while balances are scale&nbsp;2, so the limit is
	 * converted to a scale-2, negated {@link BigDecimal} before the
	 * {@link BigDecimal#compareTo(BigDecimal) compareTo}. This generalises the
	 * literal {@code DBCRFUN} L341-344 check (which compares the new balance with
	 * an effective floor of zero) so that an account's configured overdraft is
	 * honoured; the guard still fires only on the PAYMENT channel
	 * ({@code COMM-FACILTYPE = 496}).</p>
	 *
	 * @param request the {@code PAYDBCR} request envelope carrying the inner
	 *                debit/credit commarea
	 * @return the same envelope with its commarea populated with the two new
	 *         balances and the success flags
	 * @throws BusinessRuleException {@code 1} account not found, {@code 2}
	 *                               persistence error, {@code 3} insufficient
	 *                               funds (payment debit), {@code 4} payment on a
	 *                               {@code MORTGAGE}/{@code LOAN} account &mdash;
	 *                               each rolls the transaction back
	 */
	@Transactional(propagation = Propagation.REQUIRED,
			isolation = Isolation.READ_COMMITTED)
	public PaymentJson processPayment(PaymentJson request)
	{
		DbcrJson commarea = (request == null) ? null : request.getPAYDBCR();
		if (commarea == null)
		{
			// A request without a PAYDBCR payload cannot identify an account;
			// treat it as account-not-found, matching the legacy '1' outcome.
			throw new BusinessRuleException(FAIL_ACCOUNT_NOT_FOUND,
					"Missing PAYDBCR payload");
		}

		// COMM-ACCNO PIC X(8): normalise to the fixed, zero-padded width used by
		// the account key. An over-width value is REJECTED (never truncated) so a
		// contract-invalid account number can never be aliased onto another
		// account's key (financial-integrity guard; see normaliseAccountNumber).
		String accountNumber = normaliseAccountNumber(commarea.getCommAccno());

		// COMM-AMT is ALREADY signed (negative = debit, non-negative = credit);
		// never re-negate it. Normalise to scale 2 for the money pipeline.
		BigDecimal signedAmount = scale2(commarea.getCommAmt());
		boolean isDebit = signedAmount.signum() < 0;

		// COMM-FACILTYPE: 496 is the PAYMENT channel; anything else is the
		// teller/branch channel, which bypasses the '4' and '3' guards.
		boolean isPaymentChannel = resolveFacilityType(
				commarea.getCommOrigin()) == BankConstants.PAYMENT_FACILITY_TYPE;

		// Read the ACCOUNT row under a write lock (DBCRFUN holds the record lock
		// from SELECT through UPDATE). Absent -> fail '1'.
		Optional<Account> located = accountRepository.findByIdForUpdate(
				new AccountId(BankConstants.SORT_CODE, accountNumber));
		Account account = located
				.orElseThrow(() -> new BusinessRuleException(
						FAIL_ACCOUNT_NOT_FOUND,
						"Account not found: " + accountNumber));

		// MORTGAGE/LOAN guard on the PAYMENT channel only (DBCRFUN L330-338 for a
		// debit, L368-376 for a credit) -> fail '4'. A single check covers both
		// directions because the COBOL condition is identical in each branch.
		AccountType accountType = AccountType.fromValue(account.getAccountType());
		boolean isRestrictedType = accountType == AccountType.MORTGAGE
				|| accountType == AccountType.LOAN;
		if (isRestrictedType && isPaymentChannel)
		{
			throw new BusinessRuleException(FAIL_RESTRICTED_ACCOUNT,
					"Payment not permitted on " + account.getAccountType()
							+ " account " + accountNumber);
		}

		BigDecimal currentAvailable = scale2(account.getAvailableBalance());
		BigDecimal currentActual = scale2(account.getActualBalance());

		// Insufficient-funds guard, payment-channel debit only.
		// DBCRFUN L341: WS-DIFFERENCE = HV-ACCOUNT-AVAIL-BAL + COMM-AMT, then
		// L344 IF WS-DIFFERENCE < 0 AND COMM-FACILTYPE = 496 -> '3'.
		// AAP §0.6 generalises the zero floor to the account's overdraft limit:
		// permit the debit only when (available + commAmt) >= -overdraftLimit.
		if (isDebit && isPaymentChannel)
		{
			BigDecimal projectedAvailable = currentAvailable.add(signedAmount)
					.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
			BigDecimal overdraftFloor = overdraftFloor(
					account.getOverdraftLimit());
			if (projectedAvailable.compareTo(overdraftFloor) < 0)
			{
				throw new BusinessRuleException(FAIL_INSUFFICIENT_FUNDS,
						"Insufficient funds on account " + accountNumber);
			}
		}

		// Apply BOTH balances by the signed amount (DBCRFUN L384-387). The two
		// balances are independent and are never collapsed.
		BigDecimal newAvailable = currentAvailable.add(signedAmount)
				.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
		BigDecimal newActual = currentActual.add(signedAmount)
				.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
		account.setAvailableBalance(newAvailable);
		account.setActualBalance(newActual);

		// Persist the updated account (DBCRFUN UPDATE ACCOUNT). A persistence
		// failure maps to the legacy SQL-error fail code '2' (DBCRFUN L425-427).
		try
		{
			accountRepository.save(account);
		}
		catch (DataAccessException ex)
		{
			throw new BusinessRuleException(FAIL_SQL_ERROR,
					"Failed to update account " + accountNumber, ex);
		}

		// Append the PROCTRAN audit row (DBCRFUN WRITE-TO-PROCTRAN, L491-517).
		appendProcessedTransaction(accountNumber, signedAmount, isDebit,
				isPaymentChannel, commarea.getCommOrigin());

		// Populate the response commarea (DBCRFUN L416-419 + success flags) and
		// return the wrapping envelope.
		commarea.setCommAvBal(newAvailable);
		commarea.setCommActBal(newActual);
		commarea.setCommSuccess(SUCCESS_FLAG);
		commarea.setCommFailCode(BLANK_FAIL_CODE);
		return request;
	}

	/**
	 * Builds and persists the append-only {@code PROCTRAN} audit row for this
	 * movement, reproducing {@code DBCRFUN}'s {@code WRITE-TO-PROCTRAN-DB2}
	 * section.
	 *
	 * <p>The unique transaction reference replaces the COBOL CICS task number
	 * ({@code EIBTASKN}), which has no equivalent in a mainframe-free runtime: it
	 * is allocated as {@link ProcessedTransactionRepository#findMaxReference(String)
	 * findMaxReference(sortCode) + 1}. The allocation and the insert run inside
	 * the caller's {@code @Transactional} boundary, which serialises them for
	 * behavioural parity; a rolled-back payment also rolls back the consumed
	 * reference.</p>
	 *
	 * @param accountNumber the zero-padded eight-digit account number
	 * @param signedAmount  the signed movement amount (scale 2)
	 * @param isDebit       {@code true} for a debit, {@code false} for a credit
	 * @param isPaymentChannel {@code true} when on the PAYMENT channel (496)
	 * @param origin        the calling-channel origin (may be {@code null})
	 */
	private void appendProcessedTransaction(String accountNumber,
			BigDecimal signedAmount, boolean isDebit, boolean isPaymentChannel,
			OriginJson origin)
	{
		long nextReference = processedTransactionRepository
				.findMaxReference(BankConstants.SORT_CODE) + 1L;

		ProcessedTransaction row = new ProcessedTransaction();
		row.setId(new ProcessedTransactionId(BankConstants.SORT_CODE,
				padLeftZero(Long.toString(nextReference), REFERENCE_WIDTH)));
		row.setTransactionNumber(accountNumber);
		row.setDate(LocalDate.now());
		row.setTime(LocalTime.now());
		row.setTypeCode(resolveTransactionType(isDebit, isPaymentChannel));
		row.setDescription(
				resolveDescription(isDebit, isPaymentChannel, origin));
		row.setAmount(scale2(signedAmount));
		row.setDeleted(false);
		processedTransactionRepository.save(row);
	}

	/**
	 * Resolves the four-way PROCTRAN type code from the movement direction and
	 * channel ({@code DBCRFUN} L492/L499/L505/L512).
	 *
	 * @param isDebit          {@code true} for a debit, {@code false} for a credit
	 * @param isPaymentChannel {@code true} on the PAYMENT channel (496)
	 * @return {@link TransactionType#DEB}/{@link TransactionType#CRE} for the
	 *         teller channel, {@link TransactionType#PDR}/{@link TransactionType#PCR}
	 *         for the PAYMENT channel
	 */
	private TransactionType resolveTransactionType(boolean isDebit,
			boolean isPaymentChannel)
	{
		if (isDebit)
		{
			return isPaymentChannel ? TransactionType.PDR : TransactionType.DEB;
		}
		return isPaymentChannel ? TransactionType.PCR : TransactionType.CRE;
	}

	/**
	 * Resolves the forty-character PROCTRAN description ({@code DBCRFUN}
	 * L493/L500-501/L506/L513-514). The teller channel uses the fixed counter
	 * strings; the PAYMENT channel uses the first fourteen characters of the
	 * origin ({@code COMM-ORIGIN(1:14)}). The result is right-padded/truncated to
	 * the fixed COBOL field width.
	 *
	 * @param isDebit          {@code true} for a debit, {@code false} for a credit
	 * @param isPaymentChannel {@code true} on the PAYMENT channel (496)
	 * @param origin           the calling-channel origin (may be {@code null})
	 * @return the fixed-width (40) description text
	 */
	private String resolveDescription(boolean isDebit, boolean isPaymentChannel,
			OriginJson origin)
	{
		String description;
		if (isPaymentChannel)
		{
			String originText = originText(origin);
			description = originText.length() > ORIGIN_DESC_WIDTH
					? originText.substring(0, ORIGIN_DESC_WIDTH)
					: originText;
		}
		else
		{
			description = isDebit ? DESC_COUNTER_WITHDRAW
					: DESC_COUNTER_RECEIVED;
		}
		return rightPadSpace(description, DESCRIPTION_WIDTH);
	}

	/**
	 * Reconstructs the origin string ({@code COMM-ORIGIN}) as the application id
	 * concatenated with the user id, the basis for the {@code COMM-ORIGIN(1:14)}
	 * slice used as the payment-channel description.
	 *
	 * @param origin the calling-channel origin (may be {@code null})
	 * @return the concatenated origin string (never {@code null})
	 */
	private static String originText(OriginJson origin)
	{
		if (origin == null)
		{
			return "";
		}
		String applid = (origin.getCommApplid() == null) ? ""
				: origin.getCommApplid();
		String userid = (origin.getCommUserid() == null) ? ""
				: origin.getCommUserid();
		return applid + userid;
	}

	/**
	 * Resolves the facility type from the origin's {@code CommFaciltype},
	 * defaulting to the PAYMENT facility type
	 * ({@value com.ibm.cics.cip.bank.core.constants.BankConstants#PAYMENT_FACILITY_TYPE})
	 * when the origin or its facility type is absent &mdash; the payment-channel
	 * caller is the default for this endpoint.
	 *
	 * @param origin the calling-channel origin (may be {@code null})
	 * @return the resolved facility type
	 */
	private static int resolveFacilityType(OriginJson origin)
	{
		if (origin == null || origin.getCommFaciltype() == null)
		{
			return BankConstants.PAYMENT_FACILITY_TYPE;
		}
		return origin.getCommFaciltype();
	}

	/**
	 * Computes the overdraft floor as a scale-2, negated {@link BigDecimal}
	 * (aligning the {@code Integer} overdraft limit with the scale-2 balances
	 * before any comparison, per AAP&nbsp;&sect;0.6). A {@code null} limit is
	 * treated as zero.
	 *
	 * @param overdraftLimit the account's overdraft limit (may be {@code null})
	 * @return the negated, scale-2 overdraft floor (for example a limit of
	 *         {@code 500} yields {@code -500.00})
	 */
	private static BigDecimal overdraftFloor(Integer overdraftLimit)
	{
		int limit = (overdraftLimit == null) ? 0 : overdraftLimit;
		return new BigDecimal(limit).setScale(MONEY_SCALE, RoundingMode.HALF_UP)
				.negate();
	}

	/**
	 * Normalises a monetary value to scale 2 with {@link RoundingMode#HALF_UP},
	 * treating {@code null} as zero. Used for every computed money result so no
	 * binary floating-point arithmetic enters the pipeline.
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
	 * Normalises the external {@code COMM-ACCNO} to the fixed COBOL account-key
	 * width ({@code PIC X(8)}), preserving the leading-zero convention by
	 * left-padding shorter values, but <strong>rejecting</strong> &mdash; never
	 * truncating &mdash; an over-width value.
	 *
	 * <p>Unlike {@link #padLeftZero(String, int)} (used only for the internally
	 * generated PROCTRAN reference), this method must never reduce an
	 * <em>external</em> identifier to its trailing
	 * {@value #ACCOUNT_NUMBER_WIDTH} characters: doing so could alias a
	 * contract-invalid request onto a <em>different</em> account's key and move
	 * money on the wrong record (a financial-integrity defect). The frozen
	 * {@code makepayment} contract fixes {@code CommAccno} at {@code maxLength 8}
	 * ({@code PIC X(8)}), so a trimmed value longer than
	 * {@value #ACCOUNT_NUMBER_WIDTH} is contract-invalid and is failed with the
	 * legacy account-not-found code ({@code '1'}) &mdash; the same safe
	 * {@code PAYDBCR} failure envelope a non-existent account already produces.
	 * The exception message is deliberately generic and never echoes the
	 * offending value (CWE-209 safe).</p>
	 *
	 * <p>This is defence-in-depth: the controller's {@code @Valid} cascade
	 * ({@code @Size(max = 8)} on {@code DbcrJson.CommAccno}) already rejects an
	 * over-width account number at HTTP&nbsp;400 before the service is reached;
	 * this guard additionally protects any direct (non-HTTP) caller of
	 * {@link #processPayment(PaymentJson)}.</p>
	 *
	 * @param rawAccountNumber the inbound {@code COMM-ACCNO} (may be {@code null}
	 *                         or blank; surrounding whitespace is trimmed)
	 * @return the left-zero-padded, eight-character account number
	 * @throws BusinessRuleException fail code {@code '1'} when the trimmed value
	 *                               exceeds the fixed contract width of
	 *                               {@value #ACCOUNT_NUMBER_WIDTH} characters
	 */
	private static String normaliseAccountNumber(String rawAccountNumber)
	{
		String trimmed = (rawAccountNumber == null) ? "" : rawAccountNumber.trim();
		if (trimmed.length() > ACCOUNT_NUMBER_WIDTH)
		{
			// Over-width: reject rather than truncate. Truncating to the rightmost
			// 8 characters would alias a different account and move money on the
			// wrong record. Map to the legacy '1' (account-not-found) outcome,
			// which the controller renders as a well-formed PAYDBCR failure
			// envelope. Do NOT include the raw value in the message (CWE-209).
			throw new BusinessRuleException(FAIL_ACCOUNT_NOT_FOUND,
					"Account number exceeds the contract width of "
							+ ACCOUNT_NUMBER_WIDTH + " characters");
		}
		return padLeftZero(trimmed, ACCOUNT_NUMBER_WIDTH);
	}

	/**
	 * Left-zero-pads a display-numeric identifier to a fixed width, preserving
	 * the COBOL leading-zero convention. A value already at or beyond the width
	 * is returned by its trailing {@code width} characters; a {@code null} or
	 * blank value pads to all zeroes.
	 *
	 * <p><strong>Internal use only.</strong> The trailing-character behaviour is
	 * safe for the internally generated PROCTRAN reference (a monotonic counter
	 * that never exceeds the field width in practice), but it must <em>not</em>
	 * be used to normalise <em>external</em> identifiers: an over-width external
	 * account number must be rejected, not truncated. Use
	 * {@link #normaliseAccountNumber(String)} for the inbound {@code COMM-ACCNO}.</p>
	 *
	 * @param value the value to pad (may be {@code null}; surrounding whitespace
	 *              is trimmed)
	 * @param width the target fixed width
	 * @return the left-zero-padded, fixed-width string
	 */
	private static String padLeftZero(String value, int width)
	{
		String safe = (value == null) ? "" : value.trim();
		if (safe.length() >= width)
		{
			return safe.substring(safe.length() - width);
		}
		StringBuilder builder = new StringBuilder(width);
		for (int i = safe.length(); i < width; i++)
		{
			builder.append('0');
		}
		builder.append(safe);
		return builder.toString();
	}

	/**
	 * Right-pads a value with spaces to a fixed width (and truncates a longer
	 * value), matching the COBOL {@code PIC X(n)} {@code MOVE} semantics for the
	 * fixed-width description field.
	 *
	 * @param value the value to pad (may be {@code null}, treated as empty)
	 * @param width the target fixed width
	 * @return the fixed-width, right-space-padded string
	 */
	private static String rightPadSpace(String value, int width)
	{
		String safe = (value == null) ? "" : value;
		if (safe.length() >= width)
		{
			return safe.substring(0, width);
		}
		StringBuilder builder = new StringBuilder(width);
		builder.append(safe);
		while (builder.length() < width)
		{
			builder.append(' ');
		}
		return builder.toString();
	}

}
