/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.core.dto.payment;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Set;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Bean Validation parity test for the frozen {@code makepayment} (debit/credit)
 * request envelope &mdash; {@link PaymentJson} &rarr; {@link DbcrJson} &rarr;
 * {@link OriginJson} (feature <strong>F-019</strong> contract, <strong>F-021</strong>
 * field validation). It pins the Jakarta Bean Validation constraints that the
 * {@code PaymentController}'s {@code @Valid @RequestBody} relies upon to reject a
 * contract-invalid {@code PAYDBCR} payload at HTTP&nbsp;400 <em>before</em> any
 * money movement, closing the input-validation / financial-integrity gap raised
 * in the Checkpoint&nbsp;4 review (an over-width {@code CommAccno} could
 * otherwise have been silently truncated and aliased onto a different account).
 *
 * <h2>Authoritative contract pinned by these tests</h2>
 * <p>Every constraint below is the verbatim bound declared by the frozen
 * {@code src/zosconnect_artefacts/apis/makepayment/api-docs/swagger.json}
 * reconciled with {@code PAYDBCR.cpy}:</p>
 * <ul>
 *   <li>{@code CommAccno} &mdash; {@code maxLength 8} ({@code PIC X(8)});</li>
 *   <li>{@code CommAmt}/{@code CommAvBal}/{@code CommActBal} &mdash; decimal in
 *       {@code [-9999999999.99, 9999999999.99]} with scale&nbsp;2
 *       ({@code S9(10)V99});</li>
 *   <li>{@code mSortC} &mdash; integer in {@code [0, 999999]} ({@code PIC 9(6)});</li>
 *   <li>{@code CommSuccess}/{@code CommFailCode} &mdash; {@code maxLength 1}
 *       ({@code PIC X});</li>
 *   <li>{@code CommOrigin} sub-fields reached by the {@code @Valid} cascade
 *       ({@code CommApplid} {@code maxLength 8}, etc.).</li>
 * </ul>
 *
 * <h2>Why a pure {@code Validator} unit test (no Spring, no MockMvc)</h2>
 * <p>This bootstraps a plain {@link Validator} from
 * {@link Validation#buildDefaultValidatorFactory()} (Hibernate Validator,
 * supplied by {@code spring-boot-starter-validation}) and validates POJOs
 * directly &mdash; it exercises the <em>same</em> constraint metadata Spring
 * MVC evaluates for {@code @Valid @RequestBody}, but without an
 * {@code ApplicationContext}. The end-to-end controller integration test
 * ({@code PaymentControllerIT}, MockMvc) belongs to the later checkpoint; this
 * focused unit test guards the constraints that make the controller's
 * {@code @Valid} effective. Assertions check only whether violations are present
 * (not brittle property-path strings), so they remain stable across validator
 * versions.</p>
 */
class PaymentJsonValidationTest
{

	/** Shared validator factory (closed in {@link #tearDown()}). */
	private static ValidatorFactory factory;

	/** The Bean Validation validator under test. */
	private static Validator validator;

	/**
	 * Builds the default (Hibernate) validator once for the whole suite.
	 */
	@BeforeAll
	static void setUp()
	{
		factory = Validation.buildDefaultValidatorFactory();
		validator = factory.getValidator();
	}

	/**
	 * Closes the validator factory after the suite.
	 */
	@AfterAll
	static void tearDown()
	{
		if (factory != null)
		{
			factory.close();
		}
	}

	/**
	 * Builds a fully contract-valid {@link DbcrJson} payload: an eight-character
	 * account number, a scale-2 in-range amount, a six-digit sort code, scale-2
	 * zero balances, and an origin whose facility type and space-padded defaults
	 * are all within range.
	 *
	 * @return a valid {@link DbcrJson} payload
	 */
	private static DbcrJson validDbcr()
	{
		DbcrJson dbcr = new DbcrJson();
		dbcr.setCommAccno("00000123");
		dbcr.setCommAmt(new BigDecimal("100.00"));
		dbcr.setCommSortC(987654);
		dbcr.setCommAvBal(new BigDecimal("0.00"));
		dbcr.setCommActBal(new BigDecimal("0.00"));

		OriginJson origin = new OriginJson();
		origin.setCommFaciltype(496);
		dbcr.setCommOrigin(origin);
		return dbcr;
	}

	/**
	 * Wraps a valid {@link DbcrJson} in the {@code PAYDBCR} envelope.
	 *
	 * @return a valid {@link PaymentJson} request envelope
	 */
	private static PaymentJson validEnvelope()
	{
		PaymentJson envelope = new PaymentJson();
		envelope.setPAYDBCR(validDbcr());
		return envelope;
	}

	/**
	 * A fully valid envelope produces zero violations &mdash; the baseline that
	 * proves the constraints do not reject legitimate, contract-compliant input
	 * (including the space-padded {@code OriginJson} defaults).
	 */
	@Test
	@DisplayName("A fully valid PAYDBCR envelope produces no violations")
	void validEnvelope_noViolations()
	{
		Set<ConstraintViolation<PaymentJson>> violations = validator
				.validate(validEnvelope());
		assertThat(violations).isEmpty();
	}

	/**
	 * A missing {@code PAYDBCR} payload violates {@code @NotNull} on the envelope,
	 * so the controller's {@code @Valid} rejects {@code {"PAYDBCR":null}} (or an
	 * empty object) at HTTP&nbsp;400 rather than passing {@code null} into the
	 * service.
	 */
	@Test
	@DisplayName("Null PAYDBCR payload violates @NotNull on the envelope")
	void nullPayload_violatesNotNull()
	{
		PaymentJson envelope = new PaymentJson(); // payDbCr left null
		Set<ConstraintViolation<PaymentJson>> violations = validator
				.validate(envelope);
		assertThat(violations).isNotEmpty();
	}

	/**
	 * An over-width {@code CommAccno} (nine characters) violates
	 * {@code @Size(max = 8)} via the {@code @Valid} cascade &mdash; the core
	 * security fix: a contract-invalid account number is rejected, never
	 * truncated and aliased.
	 */
	@Test
	@DisplayName("Over-width CommAccno (9 chars) violates @Size(max=8) via cascade")
	void overWidthAccno_violatesSize()
	{
		PaymentJson envelope = validEnvelope();
		envelope.getPAYDBCR().setCommAccno("123456789");
		assertThat(validator.validate(envelope)).isNotEmpty();
	}

	/**
	 * A sort code above {@code 999999} violates {@code @Max(999999)} via the
	 * cascade ({@code PIC 9(6)} range).
	 */
	@Test
	@DisplayName("Out-of-range mSortC (>999999) violates @Max via cascade")
	void outOfRangeSortCode_violatesMax()
	{
		PaymentJson envelope = validEnvelope();
		envelope.getPAYDBCR().setCommSortC(1_000_000);
		assertThat(validator.validate(envelope)).isNotEmpty();
	}

	/**
	 * A negative sort code violates {@code @Min(0)} via the cascade.
	 */
	@Test
	@DisplayName("Negative mSortC (<0) violates @Min via cascade")
	void negativeSortCode_violatesMin()
	{
		PaymentJson envelope = validEnvelope();
		envelope.getPAYDBCR().setCommSortC(-1);
		assertThat(validator.validate(envelope)).isNotEmpty();
	}

	/**
	 * An amount with eleven integer digits exceeds both {@code @DecimalMax} and
	 * {@code @Digits(integer = 10, ...)} via the cascade.
	 */
	@Test
	@DisplayName("Over-range CommAmt violates @DecimalMax/@Digits via cascade")
	void overRangeAmount_violatesMoneyConstraints()
	{
		PaymentJson envelope = validEnvelope();
		envelope.getPAYDBCR().setCommAmt(new BigDecimal("10000000000.00"));
		assertThat(validator.validate(envelope)).isNotEmpty();
	}

	/**
	 * An amount with three fraction digits violates {@code @Digits(fraction = 2)}
	 * via the cascade ({@code S9(10)V99} holds exactly two decimals).
	 */
	@Test
	@DisplayName("Excess-scale CommAmt (3 decimals) violates @Digits via cascade")
	void excessScaleAmount_violatesDigits()
	{
		PaymentJson envelope = validEnvelope();
		envelope.getPAYDBCR().setCommAmt(new BigDecimal("1.234"));
		assertThat(validator.validate(envelope)).isNotEmpty();
	}

	/**
	 * A multi-character {@code CommSuccess} violates {@code @Size(max = 1)} via
	 * the cascade ({@code PIC X}).
	 */
	@Test
	@DisplayName("Multi-char CommSuccess violates @Size(max=1) via cascade")
	void multiCharSuccess_violatesSize()
	{
		PaymentJson envelope = validEnvelope();
		envelope.getPAYDBCR().setCommSuccess("YY");
		assertThat(validator.validate(envelope)).isNotEmpty();
	}

	/**
	 * A multi-character {@code CommFailCode} violates {@code @Size(max = 1)} via
	 * the cascade ({@code PIC X}).
	 */
	@Test
	@DisplayName("Multi-char CommFailCode violates @Size(max=1) via cascade")
	void multiCharFailCode_violatesSize()
	{
		PaymentJson envelope = validEnvelope();
		envelope.getPAYDBCR().setCommFailCode("99");
		assertThat(validator.validate(envelope)).isNotEmpty();
	}

	/**
	 * An over-width {@code CommApplid} inside the nested {@code CommOrigin}
	 * violates {@code @Size(max = 8)} via the <em>deep</em> two-level cascade
	 * ({@code PaymentJson} &rarr; {@code DbcrJson} &rarr; {@code OriginJson}),
	 * proving the {@code @Valid} chain reaches the origin sub-fields.
	 */
	@Test
	@DisplayName("Over-width CommApplid in nested CommOrigin violates @Size via deep cascade")
	void overWidthOriginApplid_violatesSize()
	{
		PaymentJson envelope = validEnvelope();
		envelope.getPAYDBCR().getCommOrigin().setCommApplid("123456789");
		assertThat(validator.validate(envelope)).isNotEmpty();
	}
}
