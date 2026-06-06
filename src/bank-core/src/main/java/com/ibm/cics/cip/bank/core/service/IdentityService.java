/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.core.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.ibm.cics.cip.bank.core.entity.AccountControl;
import com.ibm.cics.cip.bank.core.entity.CustomerControl;
import com.ibm.cics.cip.bank.core.exception.BusinessRuleException;
import com.ibm.cics.cip.bank.core.repository.AccountControlRepository;
import com.ibm.cics.cip.bank.core.repository.CustomerControlRepository;

/**
 * Identity-allocation service &mdash; the Java rendering of the COBOL
 * {@code NEWACCNO} / {@code NEWCUSNO} named-counter mechanism (feature F-005)
 * used by {@code CREACC} and {@code CRECUST}.
 *
 * <h2>Gap-free, roll-back-able allocation</h2>
 * <p>Account and customer numbers are allocated by reading the relevant control
 * row under a {@code PESSIMISTIC_WRITE} row lock and incrementing its
 * {@code last_*_number} counter. Every method here is
 * {@code @Transactional(propagation = MANDATORY)}, so it MUST run inside the
 * caller's transaction (the create/delete operation). That is what reproduces
 * the COBOL get/current/rollback semantics: because the counter is consumed in
 * the same unit of work as the insert it feeds, a rollback of the enclosing
 * transaction restores the counter automatically and the explicit COBOL
 * "rollback" function is no longer needed. A database
 * {@code IDENTITY}/{@code SEQUENCE} is deliberately not used because a consumed
 * generated value could not be rolled back (ADR-003).</p>
 */
@Service
public class IdentityService
{

	private final AccountControlRepository accountControlRepository;

	private final CustomerControlRepository customerControlRepository;

	/**
	 * Constructs the service with its control-row repositories.
	 *
	 * @param accountControlRepository  the account-control repository
	 * @param customerControlRepository the customer-control repository
	 */
	public IdentityService(AccountControlRepository accountControlRepository,
			CustomerControlRepository customerControlRepository)
	{
		this.accountControlRepository = accountControlRepository;
		this.customerControlRepository = customerControlRepository;
	}

	/**
	 * Allocates the next account number for the given sort code, incrementing the
	 * control row's {@code last_account_number} and {@code number_of_accounts}
	 * under a pessimistic lock.
	 *
	 * @param sortCode the six-digit, zero-padded sort code
	 * @return the newly allocated account number
	 * @throws BusinessRuleException fail code {@code "5"} if no account-control
	 *                               row exists for the sort code
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public long allocateAccountNumber(String sortCode)
	{
		AccountControl control = accountControlRepository
				.findBySortCodeForUpdate(sortCode)
				.orElseThrow(() -> new BusinessRuleException("5",
						"No account-control row for sort code " + sortCode));
		long next = control.getLastAccountNumber() + 1L;
		control.setLastAccountNumber(next);
		control.setNumberOfAccounts(control.getNumberOfAccounts() + 1L);
		accountControlRepository.save(control);
		return next;
	}

	/**
	 * Allocates the next customer number for the given sort code, incrementing
	 * the control row's {@code last_customer_number} and
	 * {@code number_of_customers} under a pessimistic lock.
	 *
	 * @param sortCode the six-digit, zero-padded sort code
	 * @return the newly allocated customer number
	 * @throws BusinessRuleException fail code {@code "5"} if no customer-control
	 *                               row exists for the sort code
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public long allocateCustomerNumber(String sortCode)
	{
		CustomerControl control = customerControlRepository
				.findBySortCodeForUpdate(sortCode)
				.orElseThrow(() -> new BusinessRuleException("5",
						"No customer-control row for sort code " + sortCode));
		long next = control.getLastCustomerNumber() + 1L;
		control.setLastCustomerNumber(next);
		control.setNumberOfCustomers(control.getNumberOfCustomers() + 1L);
		customerControlRepository.save(control);
		return next;
	}

	/**
	 * Decrements the account count for the given sort code when an account is
	 * deleted. The {@code last_account_number} high-water mark is deliberately
	 * NOT decreased, so numbers are never reused (gap-free, monotonic), matching
	 * {@code DELACC}.
	 *
	 * @param sortCode the six-digit, zero-padded sort code
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public void releaseAccount(String sortCode)
	{
		AccountControl control = accountControlRepository
				.findBySortCodeForUpdate(sortCode)
				.orElseThrow(() -> new BusinessRuleException("5",
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
	 * deleted. The {@code last_customer_number} high-water mark is deliberately
	 * NOT decreased, matching {@code DELCUS}.
	 *
	 * @param sortCode the six-digit, zero-padded sort code
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public void releaseCustomer(String sortCode)
	{
		CustomerControl control = customerControlRepository
				.findBySortCodeForUpdate(sortCode)
				.orElseThrow(() -> new BusinessRuleException("5",
						"No customer-control row for sort code " + sortCode));
		long count = control.getNumberOfCustomers();
		if (count > 0L)
		{
			control.setNumberOfCustomers(count - 1L);
			customerControlRepository.save(control);
		}
	}

}
