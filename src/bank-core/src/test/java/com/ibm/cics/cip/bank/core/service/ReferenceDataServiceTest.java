/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.core.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.ibm.cics.cip.bank.core.constants.BankConstants;

/**
 * Parity unit test for {@link ReferenceDataService} &mdash; the Java rendering of
 * the COBOL {@code GETCOMPY} and {@code GETSCODE} programs (feature F-001).
 *
 * <p>Behavioural parity with the legacy COBOL is the migration contract, so this
 * test pins the two reference-data values to their exact COBOL-derived literals
 * rather than to any "improved" form:</p>
 * <ul>
 *   <li>{@code GETCOMPY.cbl} L38 &mdash;
 *       {@code move 'CICS Bank Sample Application' to COMPANY-NAME} &rarr;
 *       {@link ReferenceDataService#getCompanyName()} must return
 *       {@code "CICS Bank Sample Application"}.</li>
 *   <li>{@code GETSCODE.cbl} L39-40 (moving {@code LITERAL-SORTCODE} from
 *       {@code SORTCODE.cpy}, value {@code 987654}) &rarr;
 *       {@link ReferenceDataService#getSortCode()} must return the fixed-width,
 *       six-digit display string {@code "987654"}.</li>
 * </ul>
 *
 * <p>{@code ReferenceDataService} has no collaborators (no repositories, no other
 * services, no data access), so this is a fast, pure unit test: the service is
 * instantiated directly with {@code new ReferenceDataService()} and exercised
 * without Mockito and without bootstrapping a Spring application context. Each
 * value is asserted both against its literal and against the corresponding
 * {@link BankConstants} field, so the test fails loudly if either the constant or
 * the service wiring drifts.</p>
 */
class ReferenceDataServiceTest
{

	/**
	 * Service under test. The bean is stateless and dependency-free, so a single
	 * directly-constructed instance is shared by every test method &mdash; no
	 * Spring context, no mocks, no setup fixture is required.
	 */
	private final ReferenceDataService service = new ReferenceDataService();

	/**
	 * GETCOMPY parity (F-001): {@link ReferenceDataService#getCompanyName()}
	 * returns the exact company-name literal moved into {@code COMPANY-NAME} by
	 * {@code GETCOMPY.cbl}, and that literal is sourced from
	 * {@link BankConstants#COMPANY_NAME} rather than re-hard-coded in the service.
	 */
	@Test
	void getCompanyName_returnsCbsaCompanyName()
	{
		assertThat(service.getCompanyName())
				.isEqualTo("CICS Bank Sample Application")
				.isEqualTo(BankConstants.COMPANY_NAME);
	}

	/**
	 * GETSCODE parity (F-001): {@link ReferenceDataService#getSortCode()} returns
	 * the exact bank sort code moved into the response by {@code GETSCODE.cbl}
	 * (from {@code SORTCODE.cpy}), and that value is sourced from
	 * {@link BankConstants#SORT_CODE} rather than re-hard-coded in the service.
	 */
	@Test
	void getSortCode_returnsCbsaSortCode()
	{
		assertThat(service.getSortCode())
				.isEqualTo("987654")
				.isEqualTo(BankConstants.SORT_CODE);
	}

	/**
	 * Fixed-width parity guard (AAP &sect;0.6): the COBOL sort code is a
	 * six-digit display-numeric field, and the Java module preserves identifiers
	 * as fixed-width {@link String}s to keep COBOL leading-zero semantics. The
	 * returned sort code must therefore be exactly six characters long and
	 * composed only of decimal digits.
	 */
	@Test
	void getSortCode_isSixDigitNumericString()
	{
		assertThat(service.getSortCode())
				.hasSize(6)
				.matches("\\d{6}");
	}

	/**
	 * Documents that the reference-data lookup is a pure, side-effect-free
	 * operation (&sect;0.3.3 "Return company name and sort code as constants"):
	 * repeated invocations of each getter yield equal values because there is no
	 * underlying database access or mutable state. This mirrors the COBOL
	 * programs, which simply move a constant into the commarea on every call.
	 */
	@Test
	void referenceData_isStableAcrossCalls()
	{
		assertThat(service.getCompanyName()).isEqualTo(service.getCompanyName());
		assertThat(service.getSortCode()).isEqualTo(service.getSortCode());
	}

}
