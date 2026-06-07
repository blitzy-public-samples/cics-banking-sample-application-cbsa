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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ibm.cics.cip.bank.core.constants.BankConstants;
import com.ibm.cics.cip.bank.core.dto.createcustomer.CreateCustomerForm;
import com.ibm.cics.cip.bank.core.dto.updatecustomer.UpdateCustomerForm;
import com.ibm.cics.cip.bank.core.entity.Customer;
import com.ibm.cics.cip.bank.core.entity.CustomerId;
import com.ibm.cics.cip.bank.core.exception.BusinessRuleException;
import com.ibm.cics.cip.bank.core.repository.AccountRepository;
import com.ibm.cics.cip.bank.core.repository.CustomerControlRepository;
import com.ibm.cics.cip.bank.core.repository.CustomerRepository;

/**
 * Parity unit test for {@link CustomerService} focused on the two
 * <em>service-layer</em> robustness behaviours hardened in response to QA
 * checkpoint CP3 &mdash; both expressed as exact COBOL parity, never "improved"
 * behaviour:
 *
 * <ul>
 *   <li><strong>Over-length input is truncated, not rejected (CRECUST /
 *       UPDCUST).</strong> The legacy commarea fields {@code COMM-NAME PIC
 *       X(60)} and {@code COMM-ADDRESS PIC X(160)} are fixed width, so a COBOL
 *       {@code MOVE} of a longer value silently keeps the leftmost characters
 *       and never errors. The Java service reproduces this by truncating the
 *       name to 60 and the address to 160 characters before persisting, so an
 *       over-length value can never reach the {@code VARCHAR(60)} /
 *       {@code VARCHAR(160)} column and be rejected by the database (which would
 *       otherwise surface as an unexpected HTTP&nbsp;500).</li>
 *   <li><strong>Credit-agency executor saturation degrades to fail {@code 'C'}
 *       (CRECUST, F-006/F-017).</strong> When the dedicated fan-out executor is
 *       saturated it rejects a submission with a
 *       {@link RejectedExecutionException} (Spring's {@code TaskRejectedException}
 *       is one). A rejected agency is treated exactly like an agency that did
 *       not reply: if every agency is rejected the create degrades to fail code
 *       {@code 'C'} &mdash; the canonical "no agency replied" outcome &mdash;
 *       rather than surfacing the raw rejection as an HTTP&nbsp;500.</li>
 * </ul>
 *
 * <p>The collaborators are mocked so the tests are deterministic and fast: the
 * credit-agency futures are pre-completed (or pre-rejected), and the identity
 * allocation returns a fixed number.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CustomerService — CP3 robustness parity (truncation, fail 'C' on saturation)")
class CustomerServiceTest
{

	/** A valid honorific title prefix so the title check (fail 'T') passes. */
	private static final String VALID_TITLE_PREFIX = "Mr ";

	/** A valid compact DDMMYYYY date of birth (15/06/1985). */
	private static final String VALID_DOB = "15061985";

	/** COBOL CUSTOMER-NAME / COMM-NAME fixed width. */
	private static final int NAME_WIDTH = 60;

	/** COBOL CUSTOMER-ADDRESS / COMM-ADDRESS fixed width. */
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

	@Mock
	private ProcessedTransactionAppender proctranAppender;

	@InjectMocks
	private CustomerService customerService;

	@Test
	@DisplayName("createCustomer truncates an over-length name and address to the COBOL fixed-field widths before persist")
	void createCustomerTruncatesOverLengthNameAndAddress()
	{
		// Every agency replies instantly with a fixed score so the credit check
		// completes well inside the deadline (deterministic, no real delay).
		when(creditAgencyService.requestCreditScore())
				.thenAnswer(invocation -> CompletableFuture.completedFuture(500));
		when(identityService.allocateCustomerNumber()).thenReturn(1L);

		// Name = "Mr " + 58 chars = 61 chars (one over the 60 ceiling);
		// address = 161 chars (one over the 160 ceiling).
		String overLongName = VALID_TITLE_PREFIX + "A".repeat(58);
		String overLongAddress = "B".repeat(ADDRESS_WIDTH + 1);
		assertThat(overLongName).hasSize(NAME_WIDTH + 1);
		assertThat(overLongAddress).hasSize(ADDRESS_WIDTH + 1);

		CreateCustomerForm form = new CreateCustomerForm(overLongName,
				overLongAddress, VALID_DOB);

		customerService.createCustomer(form);

		// The persisted entity must carry the truncated values, exactly as a
		// COBOL MOVE into PIC X(60) / PIC X(160) would.
		ArgumentCaptor<Customer> savedCustomer = ArgumentCaptor
				.forClass(Customer.class);
		verify(customerRepository).save(savedCustomer.capture());
		assertThat(savedCustomer.getValue().getName())
				.hasSize(NAME_WIDTH)
				.isEqualTo(overLongName.substring(0, NAME_WIDTH));
		assertThat(savedCustomer.getValue().getAddress())
				.hasSize(ADDRESS_WIDTH)
				.isEqualTo(overLongAddress.substring(0, ADDRESS_WIDTH));
	}

	@Test
	@DisplayName("createCustomer leaves a within-width name and address unchanged")
	void createCustomerLeavesWithinWidthValuesUnchanged()
	{
		when(creditAgencyService.requestCreditScore())
				.thenAnswer(invocation -> CompletableFuture.completedFuture(500));
		when(identityService.allocateCustomerNumber()).thenReturn(1L);

		String name = "Mr John Smith";
		String address = "1 High Street, Anytown";
		CreateCustomerForm form = new CreateCustomerForm(name, address,
				VALID_DOB);

		customerService.createCustomer(form);

		ArgumentCaptor<Customer> savedCustomer = ArgumentCaptor
				.forClass(Customer.class);
		verify(customerRepository).save(savedCustomer.capture());
		assertThat(savedCustomer.getValue().getName()).isEqualTo(name);
		assertThat(savedCustomer.getValue().getAddress()).isEqualTo(address);
	}

	@Test
	@DisplayName("createCustomer degrades to fail 'C' (no HTTP 500) when every credit-agency submission is rejected by a saturated executor")
	void createCustomerDegradesToFailCWhenExecutorSaturated()
	{
		// Simulate a saturated executor: every submission is rejected. A
		// RejectedExecutionException (the superclass of Spring's
		// TaskRejectedException) is thrown synchronously at submission time.
		when(creditAgencyService.requestCreditScore())
				.thenThrow(new RejectedExecutionException("executor saturated"));

		CreateCustomerForm form = new CreateCustomerForm("Mr John Smith",
				"1 High Street", VALID_DOB);

		// The create must fail with the canonical "no agency replied" code 'C',
		// not propagate the raw rejection (which the global advice would render
		// as HTTP 500).
		assertThatThrownBy(() -> customerService.createCustomer(form))
				.isInstanceOf(BusinessRuleException.class)
				.extracting(ex -> ((BusinessRuleException) ex).getFailCode())
				.isEqualTo("C");

		// A failed credit check happens before number allocation, so no customer
		// number is consumed and nothing is persisted (gap-free identity).
		verify(identityService, never()).allocateCustomerNumber();
		verify(customerRepository, never()).save(any());
	}

	@Test
	@DisplayName("updateCustomer truncates an over-length name and address to the COBOL fixed-field widths before rewrite")
	void updateCustomerTruncatesOverLengthNameAndAddress()
	{
		Customer existing = new Customer();
		existing.setId(new CustomerId(BankConstants.SORT_CODE, "0000000001"));
		when(customerRepository.findById(any()))
				.thenReturn(java.util.Optional.of(existing));
		when(customerRepository.save(any(Customer.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));

		String overLongName = VALID_TITLE_PREFIX + "A".repeat(58);
		String overLongAddress = "B".repeat(ADDRESS_WIDTH + 1);

		UpdateCustomerForm form = new UpdateCustomerForm();
		form.setCustNumber("1");
		form.setCustName(overLongName);
		form.setCustAddress(overLongAddress);

		customerService.updateCustomer(form);

		ArgumentCaptor<Customer> savedCustomer = ArgumentCaptor
				.forClass(Customer.class);
		verify(customerRepository).save(savedCustomer.capture());
		assertThat(savedCustomer.getValue().getName())
				.hasSize(NAME_WIDTH)
				.isEqualTo(overLongName.substring(0, NAME_WIDTH));
		assertThat(savedCustomer.getValue().getAddress())
				.hasSize(ADDRESS_WIDTH)
				.isEqualTo(overLongAddress.substring(0, ADDRESS_WIDTH));
	}
}
