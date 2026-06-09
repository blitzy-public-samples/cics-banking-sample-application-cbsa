/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.core.dto.customerenquiry;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.ibm.cics.cip.bank.core.config.JacksonConfig;

/**
 * Frozen z/OS Connect credit-score <em>review-date</em> component object,
 * reproduced verbatim from the Customer-Services interface module's
 * {@code InqCustReviewDate} (feature F-019). It models the
 * {@code INQCUST-CS-REVIEW-DT} group of {@code INQCUST.cpy} (lines 17&ndash;20)
 * and is nested inside the INQUIRE&nbsp;CUSTOMER ({@code inqcustz}) response
 * envelope under the parent key {@code InqCustCsReviewDt}.
 *
 * <p><strong>Component-split shape is the contract.</strong> The frozen
 * {@code inqcustz} swagger declares {@code InqCustCsReviewDt} as an object made
 * of three independent integer components &mdash; day, month and four-digit year
 * &mdash; rather than a single date string. This DTO therefore carries three
 * primitive {@code int} fields and is <em>not</em> collapsed to a single date
 * value or a formatted string; doing so would break the frozen wire schema. The
 * COBOL origin is:</p>
 *
 * <pre>
 *   03 INQCUST-CS-REVIEW-DT.
 *     05 INQCUST-CS-REVIEW-DD       PIC 99.
 *     05 INQCUST-CS-REVIEW-MM       PIC 99.
 *     05 INQCUST-CS-REVIEW-YYYY     PIC 9999.
 * </pre>
 *
 * <p><strong>Wire names are pinned.</strong> Each field carries an explicit
 * {@link JsonProperty @JsonProperty} fixing its verbatim wire name
 * ({@code InqCustCsReviewDd}, {@code InqCustCsReviewMm},
 * {@code InqCustCsReviewYyyy}). The class is annotated
 * {@link JsonNaming @JsonNaming} with {@link JacksonConfig.EnvelopeNamingStrategy}
 * for consistency with the other {@code core/dto} envelopes; because every
 * member also declares an explicit {@code @JsonProperty}, Jackson honours that
 * explicit name and the {@code substring(3)} strategy does not alter it &mdash;
 * matching the legacy behaviour precisely and keeping the serialised object
 * byte-for-byte compatible with the frozen {@code InqCustCsReviewDt} block.</p>
 *
 * <p><strong>Naming note.</strong> The Java class is named
 * {@code InqCustReviewDate}, whereas the parent field that holds it (declared in
 * the parent {@code InqCustZJson} envelope) is keyed
 * {@code @JsonProperty("InqCustCsReviewDt")}. The class name and the parent's
 * wire key intentionally differ; this mirrors the legacy 1:1 file layout and is
 * correct.</p>
 *
 * <p><strong>No business logic.</strong> This is a plain, mutable wire DTO. The
 * review-date business rule (review date = today plus a random 1&ndash;21 days,
 * &sect;0.6 / F-006) lives in the customer service layer, never in this object.</p>
 *
 * @see JacksonConfig.EnvelopeNamingStrategy
 */
@JsonNaming(JacksonConfig.EnvelopeNamingStrategy.class)
public class InqCustReviewDate
{

	/**
	 * Day component of the credit-score review date. {@code INQCUST-CS-REVIEW-DD
	 * PIC 99} &rarr; {@code int} (frozen schema {@code type=integer}, range
	 * 0&ndash;99). Serialised verbatim as {@code InqCustCsReviewDd}.
	 */
	@JsonProperty("InqCustCsReviewDd")
	private int inqCustCsReviewDd;

	/**
	 * Month component of the credit-score review date. {@code INQCUST-CS-REVIEW-MM
	 * PIC 99} &rarr; {@code int} (frozen schema {@code type=integer}, range
	 * 0&ndash;99). Serialised verbatim as {@code InqCustCsReviewMm}.
	 */
	@JsonProperty("InqCustCsReviewMm")
	private int inqCustCsReviewMm;

	/**
	 * Four-digit year component of the credit-score review date.
	 * {@code INQCUST-CS-REVIEW-YYYY PIC 9999} &rarr; {@code int} (frozen schema
	 * {@code type=integer}, range 0&ndash;9999). Serialised verbatim as
	 * {@code InqCustCsReviewYyyy}.
	 */
	@JsonProperty("InqCustCsReviewYyyy")
	private int inqCustCsReviewYyyy;

	/**
	 * No-argument constructor required for Jackson deserialisation. Leaves all
	 * three components at their primitive default of {@code 0} until populated by
	 * the setters or by deserialisation.
	 */
	public InqCustReviewDate()
	{
		super();
	}

	/**
	 * Returns the day component of the review date.
	 *
	 * @return the day component ({@code InqCustCsReviewDd})
	 */
	public int getInqCustCsReviewDd()
	{
		return inqCustCsReviewDd;
	}

	/**
	 * Sets the day component of the review date.
	 *
	 * @param inqCustCsReviewDdIn the day component
	 */
	public void setInqCustCsReviewDd(int inqCustCsReviewDdIn)
	{
		inqCustCsReviewDd = inqCustCsReviewDdIn;
	}

	/**
	 * Returns the month component of the review date.
	 *
	 * @return the month component ({@code InqCustCsReviewMm})
	 */
	public int getInqCustCsReviewMm()
	{
		return inqCustCsReviewMm;
	}

	/**
	 * Sets the month component of the review date.
	 *
	 * @param inqCustCsReviewMmIn the month component
	 */
	public void setInqCustCsReviewMm(int inqCustCsReviewMmIn)
	{
		inqCustCsReviewMm = inqCustCsReviewMmIn;
	}

	/**
	 * Returns the four-digit year component of the review date.
	 *
	 * @return the year component ({@code InqCustCsReviewYyyy})
	 */
	public int getInqCustCsReviewYyyy()
	{
		return inqCustCsReviewYyyy;
	}

	/**
	 * Sets the four-digit year component of the review date.
	 *
	 * @param inqCustCsReviewYyyyIn the year component
	 */
	public void setInqCustCsReviewYyyy(int inqCustCsReviewYyyyIn)
	{
		inqCustCsReviewYyyy = inqCustCsReviewYyyyIn;
	}

	/**
	 * Renders the review date in {@code DD/MM/YYYY} order (&sect;0.6),
	 * reproducing the legacy {@code toString()} exactly. This is a diagnostic
	 * representation only; it is not a Jackson getter and therefore does not
	 * affect the serialised wire form, which remains the three integer
	 * components.
	 *
	 * @return the review date rendered as {@code dd + "/" + mm + "/" + yyyy}
	 */
	@Override
	public String toString()
	{
		return inqCustCsReviewDd + "/" + inqCustCsReviewMm + "/"
				+ inqCustCsReviewYyyy;
	}

}
