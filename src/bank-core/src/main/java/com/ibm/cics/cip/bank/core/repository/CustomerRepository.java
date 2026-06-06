/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.core.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ibm.cics.cip.bank.core.entity.Customer;
import com.ibm.cics.cip.bank.core.entity.CustomerId;

/**
 * Spring Data JPA repository for {@link Customer}, keyed by the composite
 * {@link CustomerId} (sort code + customer number).
 *
 * <p>This repository replaces the legacy JCICS file control / in-memory VSAM
 * browse for the {@code CUSTOMER} dataset (the legacy
 * {@code com.ibm.cics.cip.bankliberty.web.vsam.Customer} read the whole file
 * and filtered it in Java). Those browse-and-filter access paths are recast as
 * declarative Spring Data derived queries so the database does the filtering,
 * scoping, and ordering.</p>
 *
 * <h2>Collapsed name/address columns</h2>
 * <p>The target {@code Customer} entity stores the customer's full name in a
 * single {@code name VARCHAR(60)} column and the full address in a single
 * {@code address VARCHAR(160)} column &mdash; the COBOL/VSAM sub-fields
 * (title, given name, surname, town, postcode) are collapsed into these two
 * free-text columns and are <em>not</em> modelled as separate fields.
 * Consequently the legacy searches become substring matches:</p>
 * <ul>
 *   <li><b>search by surname</b> &rarr; a {@code CONTAINING} match on
 *       {@code name} ({@code LIKE %surname%}), reproducing the legacy
 *       {@code getCustomersBySurname} ({@code name.contains(surname)});</li>
 *   <li><b>search by town</b> &rarr; a {@code CONTAINING} match on
 *       {@code address} ({@code LIKE %town%}), reproducing the legacy
 *       {@code getCustomersByTown} ({@code address.contains(town)});</li>
 *   <li><b>search by age</b> &rarr; a {@code BETWEEN} match on
 *       {@code dateOfBirth}, reproducing the legacy {@code getCustomersByAge}
 *       ({@code customerAgeInYears(dob) == age}). Because a derived query
 *       cannot compute "today", the age is converted to an inclusive
 *       {@code [fromInclusive, toInclusive]} date-of-birth window by the
 *       calling {@code CustomerService}, which passes the pre-computed bounds
 *       here.</li>
 * </ul>
 *
 * <h2>Sort-code scoping and ordering</h2>
 * <p>Every search is scoped to a single branch by its {@code sort_code} (the
 * first component of the embedded key, navigated as the {@code IdSortCode}
 * property path) and ordered by ascending customer number
 * ({@code IdCustomerNumber}) to give the stable, COBOL-like browse order the
 * UI expects.</p>
 *
 * <h2>Case sensitivity</h2>
 * <p>The substring finders use {@code IgnoreCase}: the legacy
 * {@code String.contains} was case-sensitive, but a case-insensitive match is
 * the conventional, friendlier behaviour for a web search box. The
 * create/inquiry-by-number flows do not use these search methods, so this does
 * not affect behavioural parity of those paths.</p>
 *
 * <h2>Out of scope here</h2>
 * <p>The {@code INQCUST} {@code 9999999999} ("highest customer") and
 * {@code 0000000000} ("random pick") sentinels are <em>not</em> resolved by a
 * {@code MAX()}/table scan in this repository; the highest customer number is
 * read from the {@code customer_control} counter row via
 * {@code CustomerControlRepository} in the service layer (AAP sec. 0.6).</p>
 *
 * <p>Single-customer-by-key access ({@code findById}), persistence
 * ({@code save}/{@code saveAll}), existence ({@code existsById}), counting, and
 * deletion ({@code delete}/{@code deleteById}) are inherited unchanged from
 * {@link JpaRepository}.</p>
 */
public interface CustomerRepository extends JpaRepository<Customer, CustomerId>
{

	/**
	 * Finds the customers of a branch whose name contains the given fragment,
	 * case-insensitively, ordered by ascending customer number.
	 *
	 * <p>Reproduces the legacy {@code getCustomersBySurname} browse-and-filter:
	 * because the full name is held in the single {@code name} column, a
	 * surname search is a substring match on {@code name}
	 * ({@code WHERE name ILIKE %surname%}).</p>
	 *
	 * @param sortCode the six-digit, zero-padded branch sort code to scope the
	 *                 search to (the {@code id.sortCode} key component)
	 * @param surname  the name fragment to match anywhere within the customer
	 *                 name (matched case-insensitively); an empty string
	 *                 matches every customer of the branch
	 * @return the matching customers in ascending customer-number order
	 *         (possibly empty), never {@code null}
	 */
	List<Customer> findByIdSortCodeAndNameContainingIgnoreCaseOrderByIdCustomerNumberAsc(
			String sortCode, String surname);

	/**
	 * Finds the customers of a branch whose address contains the given
	 * fragment, case-insensitively, ordered by ascending customer number.
	 *
	 * <p>Reproduces the legacy {@code getCustomersByTown} browse-and-filter:
	 * because the full address is held in the single {@code address} column, a
	 * town search is a substring match on {@code address}
	 * ({@code WHERE address ILIKE %town%}).</p>
	 *
	 * @param sortCode the six-digit, zero-padded branch sort code to scope the
	 *                 search to (the {@code id.sortCode} key component)
	 * @param town     the address fragment to match anywhere within the
	 *                 customer address (matched case-insensitively); an empty
	 *                 string matches every customer of the branch
	 * @return the matching customers in ascending customer-number order
	 *         (possibly empty), never {@code null}
	 */
	List<Customer> findByIdSortCodeAndAddressContainingIgnoreCaseOrderByIdCustomerNumberAsc(
			String sortCode, String town);

	/**
	 * Finds the customers of a branch whose date of birth falls within the
	 * inclusive {@code [fromInclusive, toInclusive]} window, ordered by
	 * ascending customer number.
	 *
	 * <p>Reproduces the legacy {@code getCustomersByAge} filter
	 * ({@code customerAgeInYears(dob) == age}). A derived query cannot compute
	 * "today", so the requested age is translated into a date-of-birth range by
	 * the calling {@code CustomerService}, which supplies the two bounds. Both
	 * bounds are inclusive ({@code Between} maps to SQL
	 * {@code dateOfBirth BETWEEN ? AND ?}); the caller is responsible for
	 * ordering them so that {@code fromInclusive <= toInclusive}.</p>
	 *
	 * @param sortCode      the six-digit, zero-padded branch sort code to scope
	 *                      the search to (the {@code id.sortCode} key component)
	 * @param fromInclusive the earliest date of birth to include (the older
	 *                      bound of the age window)
	 * @param toInclusive   the latest date of birth to include (the younger
	 *                      bound of the age window)
	 * @return the matching customers in ascending customer-number order
	 *         (possibly empty), never {@code null}
	 */
	List<Customer> findByIdSortCodeAndDateOfBirthBetweenOrderByIdCustomerNumberAsc(
			String sortCode, LocalDate fromInclusive, LocalDate toInclusive);

}
