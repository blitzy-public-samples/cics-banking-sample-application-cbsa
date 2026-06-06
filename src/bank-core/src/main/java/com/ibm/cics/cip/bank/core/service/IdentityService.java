/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.core.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.ibm.cics.cip.bank.core.constants.BankConstants;
import com.ibm.cics.cip.bank.core.entity.AccountControl;
import com.ibm.cics.cip.bank.core.entity.CustomerControl;
import com.ibm.cics.cip.bank.core.exception.BusinessRuleException;
import com.ibm.cics.cip.bank.core.repository.AccountControlRepository;
import com.ibm.cics.cip.bank.core.repository.CustomerControlRepository;

/**
 * Identity-allocation service &mdash; the Java rendering of the COBOL
 * {@code NEWACCNO} / {@code NEWCUSNO} named-counter mechanism (feature F-005,
 * ADR-003) used by {@code CREACC.cbl} and {@code CRECUST.cbl}.
 *
 * <h2>What the COBOL did</h2>
 * <p>In the legacy system {@code CREACC} obtained the next account number, and
 * {@code CRECUST} the next customer number, through the {@code NEWACCNO} /
 * {@code NEWCUSNO} commareas. Those copybooks expose a single
 * {@code FUNCTION} byte with three values &mdash; {@code 'G'} (get-new),
 * {@code 'C'} (current), and {@code 'R'} (rollback) &mdash; operating against the
 * {@code ACCTCTRL} / {@code CUSTCTRL} control records. Get-new read the control
 * record's {@code LAST-ACCOUNT-NUMBER} / {@code LAST-CUSTOMER-NUMBER}, added one
 * (e.g. {@code CREACC.cbl} L525 {@code ADD 1 TO HV-CONTROL-VALUE-NUM GIVING
 * ACCOUNT-NUMBER}; {@code CRECUST.cbl} L1381 {@code ADD 1 TO
 * LAST-CUSTOMER-NUMBER}), incremented the corresponding count, and rewrote the
 * control record. The explicit {@code 'R'} rollback function existed only
 * because, under CICS, a consumed counter could otherwise be committed
 * independently of the work that consumed it.</p>
 *
 * <h2>How the Java target reproduces it (gap-free &amp; roll-back-able)</h2>
 * <p>The three COBOL functions collapse into a counter-row increment performed
 * under a {@code PESSIMISTIC_WRITE} row lock <em>inside the same
 * {@code @Transactional} boundary as the entity insert it feeds</em>:</p>
 * <ul>
 *   <li><strong>get-new</strong> &rarr; {@link #allocateAccountNumber(String)} /
 *       {@link #allocateCustomerNumber(String)}: read the locked control row,
 *       add one to the high-water counter, bump the count, persist, and return
 *       the new number.</li>
 *   <li><strong>current</strong> &rarr; {@link #currentAccountNumber()} /
 *       {@link #currentCustomerNumber()}: read the current high-water counter
 *       <em>without</em> incrementing it and without taking a write lock.</li>
 *   <li><strong>rollback</strong> &rarr; intentionally <em>not</em> implemented:
 *       because the increment shares the caller's transaction, any later failure
 *       (validation, insert error, abend translation) rolls the counter back
 *       automatically when the enclosing transaction rolls back.</li>
 * </ul>
 *
 * <h2>Why {@code PESSIMISTIC_WRITE} and why {@code MANDATORY}</h2>
 * <p>The pessimistic row lock acquired by
 * {@code findBySortCodeForUpdate} serialises concurrent allocators so that no
 * two transactions can read the same {@code last_*_number} and collide &mdash;
 * which is what keeps allocation gap-free and duplicate-free under load. The
 * allocation and release methods are annotated
 * {@code @Transactional(propagation = }{@link Propagation#MANDATORY}{@code )} so
 * that they MUST run inside the caller's transaction (the create/delete
 * operation in {@link com.ibm.cics.cip.bank.core.service.AccountService} /
 * {@link com.ibm.cics.cip.bank.core.service.CustomerService}). That guarantees
 * the lock is held, that the counter increment and the entity insert commit or
 * roll back atomically, and &mdash; crucially &mdash; that a database
 * {@code IDENTITY}/{@code SEQUENCE} is never needed (a generated value could not
 * be rolled back, hence ADR-003 forbids it). The isolation level is
 * {@link Isolation#READ_COMMITTED}, matching the rest of the service layer and
 * reproducing the CICS {@code SYNCPOINT}/{@code ROLLBACK} boundary.</p>
 *
 * <p>All collaborators are supplied by constructor injection, and no IBM
 * mainframe library, database identity generator, or floating-point type is used
 * anywhere in this service.</p>
 */
@Service
public class IdentityService
{

	/**
	 * Fail code surfaced when a required control row is absent so allocation
	 * cannot proceed. {@code CREACC} reports {@code '5'} when the
	 * allocation/write step fails; the carried code is mapped onto the frozen
	 * response envelope by {@code GlobalExceptionHandler}.
	 */
	private static final String FAIL_ALLOCATION = "5";

	/** Repository for the {@code account_control} counter row. */
	private final AccountControlRepository accountControlRepository;

	/** Repository for the {@code customer_control} counter row. */
	private final CustomerControlRepository customerControlRepository;

	/**
	 * Constructs the service with its control-row repositories (constructor
	 * dependency injection).
	 *
	 * @param accountControlRepository  the account-control repository, used to
	 *                                  read/lock and persist the account counter
	 * @param customerControlRepository the customer-control repository, used to
	 *                                  read/lock and persist the customer counter
	 */
	public IdentityService(AccountControlRepository accountControlRepository,
			CustomerControlRepository customerControlRepository)
	{
		this.accountControlRepository = accountControlRepository;
		this.customerControlRepository = customerControlRepository;
	}

	/**
	 * Allocates the next account number for the default bank sort code
	 * ({@link BankConstants#SORT_CODE}). Convenience overload of
	 * {@link #allocateAccountNumber(String)} mirroring the COBOL {@code NEWACCNO}
	 * get-new function, which always operates on the single institution sort
	 * code.
	 *
	 * <p>Annotated {@link Propagation#MANDATORY} so the proxy enforces that an
	 * enclosing transaction already exists before any pessimistic-lock work is
	 * attempted.</p>
	 *
	 * @return the newly allocated account number (caller zero-pads to width 8)
	 * @throws BusinessRuleException fail code {@code "5"} if no account-control
	 *                               row exists for the sort code
	 */
	@Transactional(propagation = Propagation.MANDATORY,
			isolation = Isolation.READ_COMMITTED)
	public long allocateAccountNumber()
	{
		return allocateAccountNumber(BankConstants.SORT_CODE);
	}

	/**
	 * Allocates the next account number for the given sort code, reproducing the
	 * COBOL {@code NEWACCNO} get-new function ({@code CREACC.cbl}).
	 *
	 * <p>Reads the account-control row under a {@code PESSIMISTIC_WRITE} row lock,
	 * adds one to {@code last_account_number} to derive the new number, bumps
	 * {@code number_of_accounts}, and persists the row &mdash; all within the
	 * caller's transaction so the consumed number rolls back automatically if the
	 * enclosing operation fails.</p>
	 *
	 * @param sortCode the six-digit, zero-padded sort code (control-row key)
	 * @return the newly allocated account number (caller zero-pads to width 8)
	 * @throws BusinessRuleException fail code {@code "5"} if no account-control
	 *                               row exists for the sort code
	 */
	@Transactional(propagation = Propagation.MANDATORY,
			isolation = Isolation.READ_COMMITTED)
	public long allocateAccountNumber(String sortCode)
	{
		AccountControl control = accountControlRepository
				.findBySortCodeForUpdate(sortCode)
				.orElseThrow(() -> new BusinessRuleException(FAIL_ALLOCATION,
						"No account-control row for sort code " + sortCode));
		long next = control.getLastAccountNumber() + 1L;
		control.setLastAccountNumber(next);
		control.setNumberOfAccounts(control.getNumberOfAccounts() + 1L);
		accountControlRepository.save(control);
		return next;
	}

	/**
	 * Allocates the next customer number for the default bank sort code
	 * ({@link BankConstants#SORT_CODE}). Convenience overload of
	 * {@link #allocateCustomerNumber(String)} mirroring the COBOL
	 * {@code NEWCUSNO} get-new function.
	 *
	 * <p>Annotated {@link Propagation#MANDATORY} so the proxy enforces that an
	 * enclosing transaction already exists before any pessimistic-lock work is
	 * attempted.</p>
	 *
	 * @return the newly allocated customer number (caller zero-pads to width 10)
	 * @throws BusinessRuleException fail code {@code "5"} if no customer-control
	 *                               row exists for the sort code
	 */
	@Transactional(propagation = Propagation.MANDATORY,
			isolation = Isolation.READ_COMMITTED)
	public long allocateCustomerNumber()
	{
		return allocateCustomerNumber(BankConstants.SORT_CODE);
	}

	/**
	 * Allocates the next customer number for the given sort code, reproducing the
	 * COBOL {@code NEWCUSNO} get-new function ({@code CRECUST.cbl} L1381-1382,
	 * L1414).
	 *
	 * <p>Reads the customer-control row under a {@code PESSIMISTIC_WRITE} row
	 * lock, adds one to {@code last_customer_number} to derive the new number,
	 * bumps {@code number_of_customers}, and persists the row &mdash; all within
	 * the caller's transaction so the consumed number rolls back automatically if
	 * the enclosing operation fails.</p>
	 *
	 * @param sortCode the six-digit, zero-padded sort code (control-row key)
	 * @return the newly allocated customer number (caller zero-pads to width 10)
	 * @throws BusinessRuleException fail code {@code "5"} if no customer-control
	 *                               row exists for the sort code
	 */
	@Transactional(propagation = Propagation.MANDATORY,
			isolation = Isolation.READ_COMMITTED)
	public long allocateCustomerNumber(String sortCode)
	{
		CustomerControl control = customerControlRepository
				.findBySortCodeForUpdate(sortCode)
				.orElseThrow(() -> new BusinessRuleException(FAIL_ALLOCATION,
						"No customer-control row for sort code " + sortCode));
		long next = control.getLastCustomerNumber() + 1L;
		control.setLastCustomerNumber(next);
		control.setNumberOfCustomers(control.getNumberOfCustomers() + 1L);
		customerControlRepository.save(control);
		return next;
	}

	/**
	 * Returns the current (highest allocated) account number for the default bank
	 * sort code <em>without</em> incrementing it &mdash; the COBOL
	 * {@code NEWACCNO} {@code 'C'} (current) function.
	 *
	 * <p>This is a read-only query: it performs a plain primary-key lookup with
	 * no pessimistic write lock and runs read-only so it may be invoked
	 * standalone or join an existing transaction.</p>
	 *
	 * @return the current high-water account number
	 * @throws BusinessRuleException fail code {@code "5"} if no account-control
	 *                               row exists for the sort code
	 */
	@Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
	public long currentAccountNumber()
	{
		return accountControlRepository.findById(BankConstants.SORT_CODE)
				.map(AccountControl::getLastAccountNumber)
				.orElseThrow(() -> new BusinessRuleException(FAIL_ALLOCATION,
						"No account-control row for sort code "
								+ BankConstants.SORT_CODE));
	}

	/**
	 * Returns the current (highest allocated) customer number for the default
	 * bank sort code <em>without</em> incrementing it &mdash; the COBOL
	 * {@code NEWCUSNO} {@code 'C'} (current) function.
	 *
	 * <p>This is a read-only query: it performs a plain primary-key lookup with
	 * no pessimistic write lock and runs read-only so it may be invoked
	 * standalone or join an existing transaction.</p>
	 *
	 * @return the current high-water customer number
	 * @throws BusinessRuleException fail code {@code "5"} if no customer-control
	 *                               row exists for the sort code
	 */
	@Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
	public long currentCustomerNumber()
	{
		return customerControlRepository.findById(BankConstants.SORT_CODE)
				.map(CustomerControl::getLastCustomerNumber)
				.orElseThrow(() -> new BusinessRuleException(FAIL_ALLOCATION,
						"No customer-control row for sort code "
								+ BankConstants.SORT_CODE));
	}

	/**
	 * Decrements the account count for the given sort code when an account is
	 * deleted ({@code DELACC}). The {@code last_account_number} high-water mark
	 * is deliberately <em>not</em> decreased, so numbers are never reused
	 * (gap-free, monotonic). Runs under {@link Propagation#MANDATORY} inside the
	 * caller's delete transaction.
	 *
	 * @param sortCode the six-digit, zero-padded sort code (control-row key)
	 * @throws BusinessRuleException fail code {@code "5"} if no account-control
	 *                               row exists for the sort code
	 */
	@Transactional(propagation = Propagation.MANDATORY,
			isolation = Isolation.READ_COMMITTED)
	public void releaseAccount(String sortCode)
	{
		AccountControl control = accountControlRepository
				.findBySortCodeForUpdate(sortCode)
				.orElseThrow(() -> new BusinessRuleException(FAIL_ALLOCATION,
						"No account-control row for sort code " + sortCode));
		long count = control.getNumberOfAccounts();
		if (count > 0L)
		{
			control.setNumberOfAccounts(count - 1L);
			accountControlRepository.save(control);
		}
	}

	/**
	 * Decrements the customer count for the given sort code when a customer is
	 * deleted ({@code DELCUS}). The {@code last_customer_number} high-water mark
	 * is deliberately <em>not</em> decreased. Runs under
	 * {@link Propagation#MANDATORY} inside the caller's delete transaction.
	 *
	 * @param sortCode the six-digit, zero-padded sort code (control-row key)
	 * @throws BusinessRuleException fail code {@code "5"} if no customer-control
	 *                               row exists for the sort code
	 */
	@Transactional(propagation = Propagation.MANDATORY,
			isolation = Isolation.READ_COMMITTED)
	public void releaseCustomer(String sortCode)
	{
		CustomerControl control = customerControlRepository
				.findBySortCodeForUpdate(sortCode)
				.orElseThrow(() -> new BusinessRuleException(FAIL_ALLOCATION,
						"No customer-control row for sort code " + sortCode));
		long count = control.getNumberOfCustomers();
		if (count > 0L)
		{
			control.setNumberOfCustomers(count - 1L);
			customerControlRepository.save(control);
		}
	}

}
