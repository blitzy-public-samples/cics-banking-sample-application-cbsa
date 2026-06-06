/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.core.dto.customerenquiry;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.ibm.cics.cip.bank.core.config.JacksonConfig;

/**
 * Frozen z/OS Connect customer <em>date-of-birth</em> component object,
 * reproduced verbatim from the Customer-Services interface module's
 * {@code InqCustDob} (feature F-019). It models the {@code INQCUST-DOB} group of
 * {@code INQCUST.cpy} (lines 12&ndash;15) and is nested inside the
 * INQUIRE&nbsp;CUSTOMER ({@code inqcustz}) response envelope under the parent key
 * {@code InqCustDob}.
 *
 * <p><strong>Component-split shape is the contract.</strong> The frozen
 * {@code inqcustz} swagger declares {@code InqCustDob} as an object made of three
 * independent integer components &mdash; day, month and four-digit year &mdash;
 * rather than a single date string. This DTO therefore carries three primitive
 * {@code int} fields and is deliberately <em>not</em> collapsed into a single
 * date value or a formatted string; doing so would break the frozen wire schema
 * and the preserved front ends that reassemble the three integers. The COBOL
 * origin is:</p>
 *
 * <pre>
 *   03 INQCUST-DOB.
 *     05 INQCUST-DOB-DD             PIC 99.
 *     05 INQCUST-DOB-MM             PIC 99.
 *     05 INQCUST-DOB-YYYY           PIC 9999.
 * </pre>
 *
 * <p><strong>Wire names are pinned.</strong> Each field carries an explicit
 * {@link JsonProperty @JsonProperty} fixing its verbatim wire name
 * ({@code InqCustDobDd}, {@code InqCustDobMm}, {@code InqCustDobYyyy}). The class
 * is annotated {@link JsonNaming @JsonNaming} with
 * {@link JacksonConfig.EnvelopeNamingStrategy} for consistency with the other
 * {@code core/dto} envelopes; because every member also declares an explicit
 * {@code @JsonProperty}, Jackson honours that explicit name and the
 * {@code substring(3)} strategy does not alter it &mdash; matching the legacy
 * behaviour precisely and keeping the serialised object byte-for-byte compatible
 * with the frozen {@code InqCustDob} block. Serialising an instance therefore
 * yields exactly the three integer keys
 * {@code {"InqCustDobDd":n,"InqCustDobMm":n,"InqCustDobYyyy":n}} and no others.</p>
 *
 * <p><strong>Date-format note (&sect;0.6).</strong> Customer dates in the legacy
 * system are rendered in {@code DD/MM/YYYY} order; on the wire, however, the date
 * of birth stands as the three component integers above and the {@code DD/MM/YYYY}
 * ordering is reflected only in {@link #toString()}.</p>
 *
 * <p><strong>No business logic.</strong> This is a plain, mutable wire DTO. Any
 * date-of-birth validation (for example the title/DOB rules applied during
 * customer creation, F-006) lives in the customer service layer, never in this
 * object.</p>
 *
 * @see JacksonConfig.EnvelopeNamingStrategy
 */
@JsonNaming(JacksonConfig.EnvelopeNamingStrategy.class)
public class InqCustDob
{

	/**
	 * Day component of the customer date of birth. {@code INQCUST-DOB-DD PIC 99}
	 * &rarr; {@code int} (frozen schema {@code type=integer}, range 0&ndash;99).
	 * Serialised verbatim as {@code InqCustDobDd}.
	 */
	@JsonProperty("InqCustDobDd")
	private int inqCustDobDd;

	/**
	 * Month component of the customer date of birth. {@code INQCUST-DOB-MM PIC 99}
	 * &rarr; {@code int} (frozen schema {@code type=integer}, range 0&ndash;99).
	 * Serialised verbatim as {@code InqCustDobMm}.
	 */
	@JsonProperty("InqCustDobMm")
	private int inqCustDobMm;

	/**
	 * Four-digit year component of the customer date of birth.
	 * {@code INQCUST-DOB-YYYY PIC 9999} &rarr; {@code int} (frozen schema
	 * {@code type=integer}, range 0&ndash;9999). Serialised verbatim as
	 * {@code InqCustDobYyyy}.
	 */
	@JsonProperty("InqCustDobYyyy")
	private int inqCustDobYyyy;

	/**
	 * No-argument constructor required for Jackson deserialisation. Leaves all
	 * three components at their primitive default of {@code 0} until populated by
	 * the setters or by deserialisation.
	 */
	public InqCustDob()
	{
		super();
	}

	/**
	 * Returns the day component of the date of birth.
	 *
	 * @return the day component ({@code InqCustDobDd})
	 */
	public int getInqCustDobDd()
	{
		return inqCustDobDd;
	}

	/**
	 * Sets the day component of the date of birth.
	 *
	 * @param inqCustDobDdIn the day component
	 */
	public void setInqCustDobDd(int inqCustDobDdIn)
	{
		inqCustDobDd = inqCustDobDdIn;
	}

	/**
	 * Returns the month component of the date of birth.
	 *
	 * @return the month component ({@code InqCustDobMm})
	 */
	public int getInqCustDobMm()
	{
		return inqCustDobMm;
	}

	/**
	 * Sets the month component of the date of birth.
	 *
	 * @param inqCustDobMmIn the month component
	 */
	public void setInqCustDobMm(int inqCustDobMmIn)
	{
		inqCustDobMm = inqCustDobMmIn;
	}

	/**
	 * Returns the four-digit year component of the date of birth.
	 *
	 * @return the year component ({@code InqCustDobYyyy})
	 */
	public int getInqCustDobYyyy()
	{
		return inqCustDobYyyy;
	}

	/**
	 * Sets the four-digit year component of the date of birth.
	 *
	 * @param inqCustDobYyyyIn the year component
	 */
	public void setInqCustDobYyyy(int inqCustDobYyyyIn)
	{
		inqCustDobYyyy = inqCustDobYyyyIn;
	}

	/**
	 * Renders the date of birth in {@code DD/MM/YYYY} order (&sect;0.6),
	 * reproducing the legacy {@code toString()} exactly. This is a diagnostic
	 * representation only; it is not a Jackson getter and therefore does not
	 * affect the serialised wire form, which remains the three integer
	 * components.
	 *
	 * @return the date of birth rendered as {@code dd + "/" + mm + "/" + yyyy}
	 */
	@Override
	public String toString()
	{
		return inqCustDobDd + "/" + inqCustDobMm + "/" + inqCustDobYyyy;
	}

}
