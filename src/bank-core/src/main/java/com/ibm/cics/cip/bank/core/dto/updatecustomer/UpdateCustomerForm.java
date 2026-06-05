/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.core.dto.updatecustomer;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request input form for the update-customer flow (legacy COBOL program
 * {@code UPDCUST}, copybook {@code UPDCUST.cpy}).
 *
 * <p>This is a plain, mutable JavaBean used for Spring MVC request binding and
 * Jakarta Bean Validation. It captures the user-supplied data for an
 * update-customer request: the customer <em>identifier</em> needed to locate the
 * record, plus the <em>updatable</em> fields. Per the behavioral specification of
 * record, {@code UPDCUST} changes only the customer <strong>name</strong> and
 * <strong>address</strong> (feature F-011) and writes no PROCTRAN audit record;
 * those two are therefore the only genuinely mutable values in the flow. The
 * remaining fields ({@code custDoB}, {@code custCreditScore},
 * {@code custReviewDate}) are carried forward for legacy form parity so callers
 * that previously populated them continue to bind without change; they are not
 * altered by the update operation.</p>
 *
 * <p><strong>This class is NOT part of the frozen wire contract.</strong> It is a
 * request input form, not a wire DTO, so it deliberately carries <em>no</em>
 * Jackson serialization annotations and is bound to no envelope naming
 * strategy. The frozen JSON envelope lives on the wire DTOs
 * {@code UpdcustJson} / {@code UpdateCustomerJson}; keeping Jackson annotations
 * off this form prevents accidental coupling to the wire contract. The class is
 * likewise not a JPA entity and is never persisted &mdash; the controller
 * validates an instance of this form, then the service / mapper builds the
 * response payload from it.</p>
 *
 * <p>The Bean Validation constraints reproduce the legacy / BMS field-validation
 * rules (feature F-021), which for this flow are length limits only. They mirror
 * the COBOL field widths declared in {@code UPDCUST.cpy}:</p>
 * <ul>
 *   <li>{@code custNumber} &rarr; {@code COMM-CUSTNO PIC X(10)} &rarr;
 *       {@code @Size(max = 10)};</li>
 *   <li>{@code custName} &rarr; {@code COMM-NAME PIC X(60)} &rarr;
 *       {@code @Size(max = 60)};</li>
 *   <li>{@code custAddress} &rarr; {@code COMM-ADDR PIC X(160)} &rarr;
 *       {@code @Size(max = 160)}.</li>
 * </ul>
 *
 * <p><strong>Width correction (65/165 &rarr; 60/160).</strong> The legacy form
 * used a wider ceiling of 65 for the name and 165 for the address
 * &mdash; five characters wider than the copybook in each case. That was a known
 * legacy discrepancy. This port applies the contract-correct widths of
 * <strong>60</strong> and <strong>160</strong>, matching {@code UPDCUST.cpy}
 * ({@code COMM-NAME X(60)}, {@code COMM-ADDR X(160)}) and the frozen schema
 * ({@code CommName} maxLength 60, {@code CommAddress} maxLength 160). Aligning the
 * form with the authoritative copybook prevents the form from accepting values
 * that the downstream entity column (60 / 160) and the frozen schema
 * (&le;60 / &le;160) would otherwise silently truncate or reject.</p>
 *
 * <p>No regular-expression constraint is applied. The COBOL / legacy form accepted
 * any characters within the length limits; adding a restrictive regular
 * expression would reject inputs the legacy accepted and break behavioral
 * parity. The project contract is parity, not enhancement.</p>
 */
public class UpdateCustomerForm
{

	/**
	 * Identifier of the customer to update.
	 *
	 * <p>Maps to {@code COMM-CUSTNO PIC X(10)} in {@code UPDCUST.cpy}; the
	 * {@link Size} ceiling of 10 reproduces that fixed COBOL width. This value
	 * locates the record and is required &mdash; it is annotated {@link NotNull}
	 * so a missing identifier fails validation before the service is reached.</p>
	 */
	@NotNull
	@Size(max = 10)
	private String custNumber;

	/**
	 * Updatable customer name (feature F-011).
	 *
	 * <p>Maps to {@code COMM-NAME PIC X(60)} in {@code UPDCUST.cpy}. The
	 * {@link Size} ceiling is <strong>60</strong>, the contract-correct width;
	 * the legacy form used a wider ceiling of 65, which this port corrects so the
	 * value cannot exceed the downstream entity column and frozen-schema limit.
	 * Annotated {@link NotNull} (the legacy form left it {@link Size}-only with an
	 * empty-string default) so a {@code null} name fails validation.</p>
	 */
	@NotNull
	// Width corrected 65 -> 60 to match UPDCUST.cpy COMM-NAME X(60) and the frozen schema (CommName maxLength 60).
	@Size(max = 60)
	private String custName = "";

	/**
	 * Updatable customer address (feature F-011).
	 *
	 * <p>Maps to {@code COMM-ADDR PIC X(160)} in {@code UPDCUST.cpy}. The
	 * {@link Size} ceiling is <strong>160</strong>, the contract-correct width;
	 * the legacy form used a wider ceiling of 165, which this port corrects so the
	 * value cannot exceed the downstream entity column and frozen-schema limit.
	 * Annotated {@link NotNull} (the legacy form left it {@link Size}-only with an
	 * empty-string default) so a {@code null} address fails validation.</p>
	 */
	@NotNull
	// Width corrected 165 -> 160 to match UPDCUST.cpy COMM-ADDR X(160) and the frozen schema (CommAddress maxLength 160).
	@Size(max = 160)
	private String custAddress = "";

	/**
	 * Customer date of birth, retained for legacy form parity.
	 *
	 * <p>Not modified by {@code UPDCUST} (F-011 updates name and address only),
	 * but carried forward so existing callers bind unchanged. Held as a
	 * {@link String} rather than a calendar date-time type: the HTML date input
	 * arrives as {@code YYYY-MM-DD} and {@link #setCustDoB(String)} reorders it to
	 * the COBOL {@code DDMMYYYY} display form. Any conversion to / from a calendar
	 * date type is the responsibility of the service / mapper, not this
	 * form.</p>
	 */
	private String custDoB = "";

	/**
	 * Customer credit score, retained for legacy form parity.
	 *
	 * <p>Maps to {@code COMM-CREDIT-SCORE PIC 9(3)} in {@code UPDCUST.cpy}, a
	 * small whole number, so it is modeled as a primitive {@code int} (never a
	 * binary numeric type and never a fixed-point decimal money type &mdash; this
	 * is not a monetary field). Not modified by {@code UPDCUST}; carried forward
	 * only for legacy form parity. Defaults to {@code 0}.</p>
	 */
	private int custCreditScore = 0;

	/**
	 * Credit-score review date, retained for legacy form parity.
	 *
	 * <p>Not modified by {@code UPDCUST}; carried forward only for legacy form
	 * parity. Held as a {@link String} for the same reason as {@link #custDoB}
	 * &mdash; date conversion is the service / mapper's job,
	 * not this form's. Defaults to the empty string.</p>
	 */
	private String custReviewDate = "";

	/**
	 * @return the identifier of the customer to update
	 */
	public String getCustNumber()
	{
		return custNumber;
	}

	/**
	 * @param custNumber the identifier of the customer to update
	 */
	public void setCustNumber(String custNumber)
	{
		this.custNumber = custNumber;
	}

	/**
	 * @return the updatable customer name
	 */
	public String getCustName()
	{
		return custName;
	}

	/**
	 * @param custName the updatable customer name
	 */
	public void setCustName(String custName)
	{
		this.custName = custName;
	}

	/**
	 * @return the updatable customer address
	 */
	public String getCustAddress()
	{
		return custAddress;
	}

	/**
	 * @param custAddress the updatable customer address
	 */
	public void setCustAddress(String custAddress)
	{
		this.custAddress = custAddress;
	}

	/**
	 * @return the customer date of birth in the stored {@code DDMMYYYY} form
	 */
	public String getCustDoB()
	{
		return custDoB;
	}

	/**
	 * Sets the date of birth, reordering an ISO {@code YYYY-MM-DD} input into the
	 * COBOL {@code DDMMYYYY} display form. Reproduces the legacy setter verbatim:
	 * an empty-string argument is a no-op that leaves the current value unchanged,
	 * and any non-empty value is rebuilt as
	 * {@code substring(8,10) + substring(5,7) + substring(0,4)} (day + month +
	 * year). For example {@code "2023-05-15"} becomes {@code "15052023"}.
	 *
	 * @param custDoB the date of birth as {@code YYYY-MM-DD}, or the empty string
	 *                to leave the current value unchanged
	 */
	public void setCustDoB(String custDoB)
	{
		if (custDoB.equals(""))
		{
			return;
		}
		this.custDoB = "";
		this.custDoB += custDoB.substring(8, 10) + custDoB.substring(5, 7)
				+ custDoB.substring(0, 4);
	}

	/**
	 * @return the customer credit score
	 */
	public int getCustCreditScore()
	{
		return custCreditScore;
	}

	/**
	 * @param custCreditScore the customer credit score
	 */
	public void setCustCreditScore(int custCreditScore)
	{
		this.custCreditScore = custCreditScore;
	}

	/**
	 * @return the credit-score review date
	 */
	public String getCustReviewDate()
	{
		return custReviewDate;
	}

	/**
	 * @param custReviewDate the credit-score review date
	 */
	public void setCustReviewDate(String custReviewDate)
	{
		this.custReviewDate = custReviewDate;
	}

	/**
	 * Renders every field of the form. The field order mirrors the legacy
	 * {@code toString} output for parity.
	 *
	 * @return a human-readable representation of this form
	 */
	@Override
	public String toString()
	{
		return "UpdateCustomerForm [custAddress=" + custAddress
				+ ", custCreditScore=" + custCreditScore + ", custDoB="
				+ custDoB + ", custName=" + custName + ", custNumber="
				+ custNumber + ", custReviewDate=" + custReviewDate + "]";
	}

}
