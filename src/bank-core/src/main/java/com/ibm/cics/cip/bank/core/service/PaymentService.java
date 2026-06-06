/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.core.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ibm.cics.cip.bank.core.constants.BankConstants;
import com.ibm.cics.cip.bank.core.entity.Account;
import com.ibm.cics.cip.bank.core.entity.AccountId;
import com.ibm.cics.cip.bank.core.exception.BusinessRuleException;
import com.ibm.cics.cip.bank.core.repository.AccountRepository;
import com.ibm.cics.cip.bank.core.util.BankFormat;

/**
 * Debit/credit (payment) service, reproducing the COBOL program
 * {@code DBCRFUN} exactly &mdash; its sign convention, facility-type rules,
 * fail codes, dual-balance update and PROCTRAN audit semantics.
 *
 * <p>The operation runs inside a single {@link Transactional @Transactional}
 * boundary so that the balance update and the PROCTRAN audit append commit or
 * roll back together (review finding F-TXN-1). The account row is read under a
 * pessimistic write lock, reproducing the CICS record lock that {@code DBCRFUN}
 * holds between its {@code SELECT} and {@code UPDATE}.</p>
 *
 * <p><strong>Sign convention.</strong> A negative amount is a debit (payment
 * out); a non-negative amount (including zero) is a credit (money in). This
 * mirrors {@code IF COMM-AMT &lt; 0} in {@code DBCRFUN}.</p>
 *
 * <p><strong>Facility-type rules.</strong> Facility type
 * {@value com.ibm.cics.cip.bank.core.constants.BankConstants#PAYMENT_FACILITY_TYPE}
 * identifies a payment (as opposed to a teller movement, which uses any other
 * value). For a payment:</p>
 * <ul>
 *   <li>A debit or credit against a {@code MORTGAGE} or {@code LOAN} account
 *       fails with code {@code 4}.</li>
 *   <li>A debit that would drive the available balance below zero fails with
 *       code {@code 3} (insufficient funds). Note that, exactly as in the
 *       COBOL, the overdraft limit is <em>not</em> consulted in this check.</li>
 * </ul>
 * A teller movement (any other facility type) bypasses both the account-type
 * and the insufficient-funds checks.
 *
 * <p><strong>Balances.</strong> On success both the available and actual
 * balances are adjusted by the signed amount; they are independent values and
 * are never collapsed.</p>
 *
 * <p><strong>Audit type codes.</strong> Teller debit {@code DEB}
 * ("COUNTER WTHDRW"), teller credit {@code CRE} ("COUNTER RECVED"); payment
 * debit {@code PDR} and payment credit {@code PCR}, each with the first
 * fourteen characters of the origin as the description.</p>
 */
@Service
public class PaymentService
{

	/** Fail code: account not found. */
	private static final String FAIL_NOT_FOUND = "1";

	/** Fail code: insufficient funds on a payment debit. */
	private static final String FAIL_INSUFFICIENT_FUNDS = "3";

	/** Fail code: payment against a restricted (MORTGAGE/LOAN) account. */
	private static final String FAIL_RESTRICTED_ACCOUNT = "4";

	/** Monetary scale for balances and amounts. */
	private static final int MONEY_SCALE = 2;

	/** Maximum width of the origin text carried into a payment description. */
	private static final int ORIGIN_DESC_WIDTH = 14;

	/** Restricted account type: mortgage. */
	private static final String TYPE_MORTGAGE = "MORTGAGE";

	/** Restricted account type: loan. */
	private static final String TYPE_LOAN = "LOAN";

	/** Audit type code: teller debit. */
	private static final String AUDIT_DEBIT = "DEB";

	/** Audit type code: teller credit. */
	private static final String AUDIT_CREDIT = "CRE";

	/** Audit type code: payment debit. */
	private static final String AUDIT_PAYMENT_DEBIT = "PDR";

	/** Audit type code: payment credit. */
	private static final String AUDIT_PAYMENT_CREDIT = "PCR";

	/** Audit description: teller debit. */
	private static final String DESC_DEBIT = "COUNTER WTHDRW";

	/** Audit description: teller credit. */
	private static final String DESC_CREDIT = "COUNTER RECVED";

	private final AccountRepository accountRepository;

	private final ProcessedTransactionAppender proctranAppender;

	/**
	 * Constructs the payment service with its collaborators.
	 *
	 * @param accountRepository repository for {@link Account} persistence and
	 *                          locking
	 * @param proctranAppender  atomic PROCTRAN audit-row appender
	 */
	public PaymentService(AccountRepository accountRepository,
			ProcessedTransactionAppender proctranAppender)
	{
		this.accountRepository = accountRepository;
		this.proctranAppender = proctranAppender;
	}

	/**
	 * Applies a debit or credit to an account, reproducing {@code DBCRFUN}.
	 *
	 * @param accountNumber the account to debit/credit
	 * @param amount        the signed amount (negative debit, non-negative
	 *                      credit)
	 * @param facilityType  the facility type; {@code 496} denotes a payment, any
	 *                      other value denotes a teller movement
	 * @param origin        the origin text used as the payment description's
	 *                      first fourteen characters (ignored for teller
	 *                      movements)
	 * @return the updated {@link Account}, carrying the new available and actual
	 *         balances
	 * @throws BusinessRuleException {@code 1} if the account does not exist,
	 *                               {@code 4} for a payment against a
	 *                               MORTGAGE/LOAN account, or {@code 3} for a
	 *                               payment debit with insufficient funds
	 */
	@Transactional
	public Account processDebitCredit(long accountNumber, BigDecimal amount,
			int facilityType, String origin)
	{
		BigDecimal signedAmount = amount.setScale(MONEY_SCALE,
				RoundingMode.HALF_UP);

		// Read the account under a write lock (DBCRFUN holds the record lock
		// from SELECT through UPDATE). Not found -> fail '1'.
		Account account = accountRepository
				.findByIdForUpdate(new AccountId(BankConstants.SORT_CODE,
						BankFormat.accountNumber(accountNumber)))
				.orElseThrow(() -> new BusinessRuleException(FAIL_NOT_FOUND,
						"Account not found: " + accountNumber));

		boolean isPayment = facilityType == BankConstants.PAYMENT_FACILITY_TYPE;
		boolean isDebit = signedAmount.signum() < 0;
		String accountType = account.getAccountType() == null ? ""
				: account.getAccountType().trim();
		boolean restrictedType = TYPE_MORTGAGE.equals(accountType)
				|| TYPE_LOAN.equals(accountType);

		if (isDebit)
		{
			// Payment debit against a MORTGAGE/LOAN account -> fail '4'.
			if (restrictedType && isPayment)
			{
				throw new BusinessRuleException(FAIL_RESTRICTED_ACCOUNT,
						"Payment debit not permitted on " + accountType
								+ " account " + accountNumber);
			}

			// Insufficient funds: available + amount < 0, payments only.
			// The overdraft limit is intentionally NOT consulted (DBCRFUN).
			BigDecimal difference = account.getAvailableBalance()
					.add(signedAmount);
			if (difference.signum() < 0 && isPayment)
			{
				throw new BusinessRuleException(FAIL_INSUFFICIENT_FUNDS,
						"Insufficient funds on account " + accountNumber);
			}
		}

		// Payment credit against a MORTGAGE/LOAN account -> fail '4'.
		// (For a debit this condition was already handled above.)
		if (restrictedType && isPayment)
		{
			throw new BusinessRuleException(FAIL_RESTRICTED_ACCOUNT,
					"Payment not permitted on " + accountType + " account "
							+ accountNumber);
		}

		// Update both balances by the signed amount (independent values).
		BigDecimal newAvailable = account.getAvailableBalance().add(signedAmount)
				.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
		BigDecimal newActual = account.getActualBalance().add(signedAmount)
				.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
		account.setAvailableBalance(newAvailable);
		account.setActualBalance(newActual);
		accountRepository.save(account);

		// Resolve the audit type code and description, then append atomically.
		String typeCode = resolveTypeCode(isDebit, isPayment);
		String description = resolveDescription(isDebit, isPayment, origin);
		proctranAppender.appendPayment(BankConstants.SORT_CODE, accountNumber,
				typeCode, description, signedAmount);

		return account;
	}

	/**
	 * Resolves the PROCTRAN type code from the movement direction and facility.
	 *
	 * @param isDebit   {@code true} for a debit, {@code false} for a credit
	 * @param isPayment {@code true} for a payment, {@code false} for a teller
	 *                  movement
	 * @return {@code DEB}/{@code CRE} for teller movements, {@code PDR}/
	 *         {@code PCR} for payments
	 */
	private String resolveTypeCode(boolean isDebit, boolean isPayment)
	{
		if (isDebit)
		{
			return isPayment ? AUDIT_PAYMENT_DEBIT : AUDIT_DEBIT;
		}
		return isPayment ? AUDIT_PAYMENT_CREDIT : AUDIT_CREDIT;
	}

	/**
	 * Resolves the PROCTRAN description. Teller movements use the fixed counter
	 * strings; payments use the first fourteen characters of the origin
	 * ({@code COMM-ORIGIN(1:14)} in {@code DBCRFUN}).
	 *
	 * @param isDebit   {@code true} for a debit, {@code false} for a credit
	 * @param isPayment {@code true} for a payment, {@code false} for a teller
	 *                  movement
	 * @param origin    the origin text (used only for payments)
	 * @return the resolved description text
	 */
	private String resolveDescription(boolean isDebit, boolean isPayment,
			String origin)
	{
		if (isPayment)
		{
			String safeOrigin = (origin == null) ? "" : origin;
			return safeOrigin.length() > ORIGIN_DESC_WIDTH
					? safeOrigin.substring(0, ORIGIN_DESC_WIDTH)
					: safeOrigin;
		}
		return isDebit ? DESC_DEBIT : DESC_CREDIT;
	}

}
