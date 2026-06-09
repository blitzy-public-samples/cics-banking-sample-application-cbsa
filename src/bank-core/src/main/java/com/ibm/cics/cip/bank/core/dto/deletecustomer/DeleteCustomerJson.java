/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.core.dto.deletecustomer;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.ibm.cics.cip.bank.core.config.JacksonConfig;

/**
 * Outer JSON <em>envelope</em> wire DTO for the frozen z/OS Connect
 * delete-customer ({@code delcus}) REST contract (feature F-019).
 *
 * <p><strong>Role.</strong> This is a thin transport wrapper that isolates the
 * outer JSON envelope from the inner business payload. It holds a single field
 * of type {@link DelcusJson} (the sibling inner DTO in this same package) mapped
 * to the frozen envelope key {@code "DelCus"}, so the full serialised document
 * is {@code {"DelCus": { &hellip; }}}. It is the response envelope produced by
 * {@code DeleteCustomerController} (DELETE {@code /delcus/remove/{custno}}) after
 * the DELCUS cascade delete removes the customer and all their accounts, appends
 * a customer-close PROCTRAN record, and returns the deleted customer's detail.
 * The same shape also matches the request envelope, because the frozen
 * {@code CScustdelRequest.json} and {@code CScustdelResponse.json} schemas share
 * an identical {@code DelCus} top-level structure.</p>
 *
 * <p><strong>Wire-name fidelity.</strong> The class is annotated with
 * {@link JsonNaming} bound to {@link JacksonConfig.EnvelopeNamingStrategy} (the
 * {@code substring(3)} strategy ported from the legacy z/OS Connect interface)
 * so the envelope's derived property naming matches the source module. The
 * single field additionally carries an explicit {@code @JsonProperty("DelCus")},
 * which <em>overrides</em> the naming strategy and pins the wire key to exactly
 * {@code DelCus} (capital D, capital C), keeping serialisation byte-compatible
 * with the frozen contract and the preserved front ends.</p>
 *
 * <p><strong>Carrier only.</strong> This is a plain, mutable data holder: no
 * Spring stereotype, no persistence mapping, and no business logic. All money,
 * date, and identifier concerns live inside the nested {@link DelcusJson}.</p>
 *
 * @see DelcusJson
 * @see JacksonConfig.EnvelopeNamingStrategy
 */
@JsonNaming(JacksonConfig.EnvelopeNamingStrategy.class)
public class DeleteCustomerJson
{

	/**
	 * Nested delete-customer commarea payload, serialised under the verbatim
	 * envelope key {@code "DelCus"}.
	 */
	@JsonProperty("DelCus")
	private DelcusJson delCus;

	/**
	 * Default constructor required by Jackson for deserialisation.
	 */
	public DeleteCustomerJson()
	{
		super();
	}

	/**
	 * Convenience constructor wrapping an existing inner commarea payload.
	 *
	 * @param delCus the nested {@link DelcusJson} payload
	 */
	public DeleteCustomerJson(DelcusJson delCus)
	{
		this.delCus = delCus;
	}

	/**
	 * Returns the nested delete-customer commarea payload.
	 *
	 * @return the nested {@link DelcusJson} payload (wire key {@code DelCus})
	 */
	public DelcusJson getDelCus()
	{
		return delCus;
	}

	/**
	 * Sets the nested delete-customer commarea payload.
	 *
	 * @param delCusIn the nested {@link DelcusJson} payload
	 */
	public void setDelCus(DelcusJson delCusIn)
	{
		delCus = delCusIn;
	}

	/**
	 * Returns a diagnostic representation of this envelope showing the nested
	 * payload. Null-safe: a {@code null} payload renders as {@code null}.
	 *
	 * @return a string representation of this envelope
	 */
	@Override
	public String toString()
	{
		return "DeleteCustomerJson [DelCus=" + delCus + "]";
	}

	/**
	 * Renders a human-readable, multi-line summary of the deleted customer for
	 * logging and diagnostics.
	 *
	 * <p>This is <strong>display/logging only and is not part of the wire
	 * contract</strong>; the serialised JSON is governed solely by the field
	 * mapping above. The customer number is left-zero-padded to width&nbsp;10
	 * using a self-contained inline helper (no external formatting utility is
	 * imported), and the date-of-birth and review-date values are rendered
	 * directly because the nested {@link DelcusJson} already carries them as
	 * {@code DD/MM/YYYY} strings (customer date format, AAP &sect;0.6).</p>
	 *
	 * @return a multi-line human-readable summary, or an empty string when the
	 *         nested payload is {@code null}
	 */
	public String toPrettyString()
	{
		if (delCus == null)
		{
			return "";
		}
		StringBuilder output = new StringBuilder();
		output.append("Customer Number:       ")
				.append(leftZeroPad(delCus.getCommCustno(), 10)).append("\n")
				.append("Sort Code:      ").append(delCus.getCommSortcode())
				.append("\n").append("Customer Name:         ")
				.append(delCus.getCommName()).append("\n")
				.append("Customer Address:    ").append(delCus.getCommAddress())
				.append("\n").append("Date of Birth:       ")
				.append(delCus.getCommDateOfBirth()).append("\n")
				.append("Credit score:        ")
				.append(delCus.getCommCreditScore()).append("\n")
				.append("Next review date:            ")
				.append(delCus.getCommCsReviewDate()).append("\n");
		return output.toString();
	}

	/**
	 * Left-zero-pads the supplied value to the requested minimum width,
	 * reproducing the legacy {@code leadingZeroes} behaviour inline so that no
	 * external formatting utility is imported. A value already at or beyond the
	 * width is returned unchanged; a {@code null} value is returned unchanged.
	 *
	 * @param input the raw value (may be {@code null})
	 * @param width the target minimum width
	 * @return the left-zero-padded value, or {@code null} when {@code input} is
	 *         {@code null}
	 */
	private static String leftZeroPad(String input, int width)
	{
		if (input == null)
		{
			return null;
		}
		return String.format("%" + width + "s", input).replace(" ", "0");
	}
}
