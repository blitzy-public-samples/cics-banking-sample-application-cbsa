/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.core.dto.createcustomer;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request-input form for the CREATE CUSTOMER ({@code crecust}) operation
 * (legacy COBOL program {@code CRECUST.cbl}, interface copybook
 * {@code CRECUST.cpy}).
 *
 * <p>This is a plain, mutable JavaBean used for Spring MVC request binding and
 * Jakarta Bean Validation. It captures the raw, client-supplied inputs for a
 * CREATE CUSTOMER request &mdash; the customer <em>name</em>, <em>address</em>,
 * and <em>date of birth</em> &mdash; and enforces the legacy / BMS
 * field-validation rules (feature F-021) before the request reaches the service
 * tier. It is the foundational (least-dependent) type in the
 * {@code createcustomer} DTO package: the outer wire envelope
 * {@code CreateCustomerJson(CreateCustomerForm form)} consumes a validated
 * instance of this form to build the {@code crecust} payload that ultimately
 * feeds {@code CrecustJson}.</p>
 *
 * <p><strong>This class is NOT part of the frozen wire contract.</strong> It is a
 * request-input form, not a wire-envelope DTO, so it deliberately carries
 * <em>no</em> Jackson serialization annotations and is bound to no envelope
 * naming strategy. The frozen JSON envelope for the create-customer operation
 * lives on the wire DTOs ({@code CrecustJson} / {@code CreateCustomerJson});
 * keeping Jackson annotations off this form prevents accidental coupling to the
 * wire contract. The class is likewise not a JPA entity and is never persisted
 * &mdash; the controller validates an instance of this form, then the service /
 * mapper builds the downstream payload from it.</p>
 *
 * <p>All three fields are held as {@link String} values. The
 * {@link #setCustDob(String)} setter reorders an ISO {@code YYYY-MM-DD} date
 * input into the compact COBOL {@code DDMMYYYY} display form so that the envelope
 * feeds an 8-character date string into {@code CrecustJson.commDateOfBirth},
 * consistent with the String-date decision made for {@code CrecustJson}.</p>
 *
 * <p>The Bean Validation constraints mirror the COBOL field widths declared in
 * {@code CRECUST.cpy}, reproduced verbatim from the legacy customer-services
 * form for backward-compatible validation behaviour:</p>
 * <ul>
 *   <li>{@code custName} &rarr; {@code COMM-NAME PIC X(60)} &rarr;
 *       {@link Size}{@code (max = 61)} (the legacy form's deliberate +1 over the
 *       copybook width is preserved);</li>
 *   <li>{@code custAddress} &rarr; {@code COMM-ADDRESS PIC X(160)} &rarr;
 *       {@link Size}{@code (max = 161)} (same deliberate +1);</li>
 *   <li>{@code custDob} &rarr; {@code COMM-DATE-OF-BIRTH PIC 9(8)} &rarr;
 *       {@link Size}{@code (min = 8, max = 8)} (the compact 8-character
 *       {@code DDMMYYYY} form produced by {@link #setCustDob(String)}).</li>
 * </ul>
 *
 * <p>Behavioural parity with the COBOL specification of record is the contract;
 * the {@code isValidTitle()} honorific check and the deliberate {@code +1}
 * validation widths are legacy parity details that are reproduced here rather
 * than "improved".</p>
 */
public class CreateCustomerForm
{

	/**
	 * Customer name supplied by the client.
	 *
	 * <p>Maps to {@code COMM-NAME PIC X(60)} in {@code CRECUST.cpy}. The
	 * {@link Size} ceiling of <strong>61</strong> reproduces the legacy form's
	 * deliberate +1 over the copybook width, preserving the established
	 * validation behaviour. Annotated {@link NotNull} so a missing name fails
	 * validation before the service is reached. The first space-delimited token
	 * is expected to be an honorific title (see {@link #isValidTitle()}).</p>
	 */
	@NotNull
	@Size(max = 61)
	private String custName;

	/**
	 * Customer postal address supplied by the client.
	 *
	 * <p>Maps to {@code COMM-ADDRESS PIC X(160)} in {@code CRECUST.cpy}. The
	 * {@link Size} ceiling of <strong>161</strong> reproduces the legacy form's
	 * deliberate +1 over the copybook width. Annotated {@link NotNull} so a
	 * missing address fails validation before the service is reached.</p>
	 */
	@NotNull
	@Size(max = 161)
	private String custAddress;

	/**
	 * Customer date of birth, stored in the compact COBOL {@code DDMMYYYY}
	 * display form.
	 *
	 * <p>Maps to {@code COMM-DATE-OF-BIRTH PIC 9(8)} in {@code CRECUST.cpy}
	 * (whose {@code COMM-DOB-GROUP} redefinition is day + month + year, i.e.
	 * {@code DDMMYYYY}). Held as a {@link String} rather than a calendar
	 * date-time type so that the fixed 8-character display form is preserved
	 * exactly. The HTML date input arrives as a 10-character ISO
	 * {@code YYYY-MM-DD} string and {@link #setCustDob(String)} reorders it into
	 * the 8-character {@code DDMMYYYY} form. Annotated {@link NotNull} and
	 * {@link Size}{@code (min = 8, max = 8)} so only the compact 8-character form
	 * passes validation.</p>
	 */
	@NotNull
	@Size(min = 8, max = 8)
	private String custDob;

	/**
	 * Creates an empty form. Required for Spring MVC form binding and
	 * deserialization, which instantiate the object and then populate it through
	 * the setters.
	 */
	public CreateCustomerForm()
	{
		super();
	}

	/**
	 * Creates a form with the supplied values, assigning each field
	 * <strong>directly</strong> &mdash; the normalizing setters are deliberately
	 * bypassed so that a test or caller can construct an instance with exact
	 * values (for example an already-compact 8-character {@code DDMMYYYY} date of
	 * birth that must not be re-reordered). Mirrors the all-args pattern adopted
	 * by the sibling {@code createaccount} / {@code customerenquiry} forms.
	 *
	 * @param custName    the customer name (first token is the honorific title)
	 * @param custAddress the customer postal address
	 * @param custDob     the customer date of birth, supplied exactly as it is to
	 *                    be stored (no reordering is applied)
	 */
	public CreateCustomerForm(String custName, String custAddress, String custDob)
	{
		this.custName = custName;
		this.custAddress = custAddress;
		this.custDob = custDob;
	}

	/**
	 * @return the customer name
	 */
	public String getCustName()
	{
		return custName;
	}

	/**
	 * Sets the customer name, preserving the legacy empty-string handling: an
	 * empty string is kept as an empty string (it is not blank-trimmed away) and
	 * any other value is assigned unchanged. Ported verbatim from the legacy
	 * customer-services form.
	 *
	 * @param custName the customer name
	 */
	public void setCustName(@NotNull String custName)
	{
		this.custName = custName.equals("") ? "" : custName;
	}

	/**
	 * @return the customer postal address
	 */
	public String getCustAddress()
	{
		return custAddress;
	}

	/**
	 * Sets the customer address, preserving the legacy empty-string handling: an
	 * empty string is kept as an empty string (it is not blank-trimmed away) and
	 * any other value is assigned unchanged. Ported verbatim from the legacy
	 * customer-services form.
	 *
	 * @param custAddress the customer postal address
	 */
	public void setCustAddress(@NotNull String custAddress)
	{
		this.custAddress = custAddress.equals("") ? "" : custAddress;
	}

	/**
	 * @return the customer date of birth in the stored {@code DDMMYYYY} form
	 */
	public String getCustDob()
	{
		return custDob;
	}

	/**
	 * Sets the date of birth, reordering a 10-character ISO {@code YYYY-MM-DD}
	 * input into the compact 8-character COBOL {@code DDMMYYYY} display form.
	 *
	 * <p>The reorder reproduces the legacy substring logic verbatim: day +
	 * month + year, i.e.
	 * {@code substring(8, 10) + substring(5, 7) + substring(0, 4)}. For example
	 * {@code "1990-05-15"} becomes {@code "15051990"}. Any value that is not
	 * exactly 10 characters long &mdash; including {@code null} or a value that
	 * is already in the compact 8-character form &mdash; is assigned as-is,
	 * which both avoids a {@link StringIndexOutOfBoundsException} and lets an
	 * already-{@code DDMMYYYY} value pass through unchanged.</p>
	 *
	 * @param custDob the date of birth, either as a 10-character
	 *                {@code YYYY-MM-DD} string (which is reordered) or already in
	 *                the compact form (which is assigned as-is)
	 */
	public void setCustDob(String custDob)
	{
		if (custDob != null && custDob.length() == 10)
		{
			// Reorder ISO YYYY-MM-DD (length 10) into the compact COBOL
			// DDMMYYYY display form: day (8,10) + month (5,7) + year (0,4).
			this.custDob = custDob.substring(8, 10) + custDob.substring(5, 7)
					+ custDob.substring(0, 4);
		}
		else
		{
			// Not the expected 10-character ISO form (e.g. null or an
			// already-compact 8-character DDMMYYYY value): assign as-is.
			this.custDob = custDob;
		}
	}

	/**
	 * Reports whether the customer name begins with a recognised honorific
	 * title and carries enough name components to be well-formed.
	 *
	 * <p>Ported verbatim from the legacy customer-services form; it supports the
	 * title-validation rule that {@code CRECUST} applies (feature F-006, fail
	 * code {@code 'T'}). The check returns {@code true} only when the name is
	 * non-{@code null}, has at least three space-separated components
	 * (title + given name + family name), and the first component is one of the
	 * recognised honorifics: {@code Mr}, {@code Mrs}, {@code Miss}, {@code Ms},
	 * {@code Dr}, {@code Professor}, {@code Drs}, {@code Lord}, {@code Sir}, or
	 * {@code Lady}. The comparison is case-sensitive, matching the COBOL
	 * {@code EVALUATE} of mixed-case literals.</p>
	 *
	 * @return {@code true} if the first name component is a recognised honorific
	 *         and the name has at least three components; {@code false} otherwise
	 */
	public boolean isValidTitle()
	{
		if (this.custName == null)
		{
			return false;
		}
		String[] elements = custName.split(" ");
		if (elements.length < 3)
		{
			return false;
		}
		String title = elements[0];
		return title.contentEquals("Mr") || title.contentEquals("Mrs")
				|| title.contentEquals("Miss") || title.contentEquals("Ms")
				|| title.contentEquals("Dr") || title.contentEquals("Professor")
				|| title.contentEquals("Drs") || title.contentEquals("Lord")
				|| title.contentEquals("Sir") || title.contentEquals("Lady");
	}

	/**
	 * Renders the form's three fields with correctly matched labels.
	 *
	 * @return a human-readable representation of this form
	 */
	@Override
	public String toString()
	{
		return "CreateCustomerForm [custName=" + custName + ", custAddress="
				+ custAddress + ", custDob=" + custDob + "]";
	}

}
