/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.core.dto.createcustomer;

import com.ibm.cics.cip.bank.core.domain.Title;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
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
 *       {@link Size}{@code (min = 8, max = 8)} plus an eight-digit
 *       {@link Pattern} (the compact 8-character {@code DDMMYYYY} form produced
 *       by {@link #setCustDob(String)}).</li>
 * </ul>
 *
 * <p>Behavioural parity with the COBOL specification of record is the contract;
 * the {@code isValidTitle()} honorific check delegates to
 * {@link Title#isValidTitle(String)} so a blank title is accepted exactly as the
 * COBOL {@code CRECUST} {@code EVALUATE} accepts it, and the deliberate
 * {@code +1} validation widths are legacy parity details preserved rather than
 * "improved".</p>
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
	 * the 8-character {@code DDMMYYYY} form. Annotated {@link NotNull},
	 * {@link Size}{@code (min = 8, max = 8)}, and a {@link Pattern} of eight
	 * digits so only a compact 8-character all-digits value passes validation,
	 * matching the numeric COBOL {@code PIC 9(8)} field and rejecting non-date
	 * input rather than fabricating a valid-length date from arbitrary text.</p>
	 */
	@NotNull
	@Size(min = 8, max = 8)
	@Pattern(regexp = "\\d{8}")
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
	 * Sets the customer name by direct, null-safe assignment. The value is stored
	 * exactly as supplied (an empty string is kept as an empty string and is not
	 * blank-trimmed); a {@code null} is assigned unchanged rather than triggering
	 * an {@code equals} comparison, so a missing name is reported by the
	 * field-level {@link NotNull} constraint during Bean Validation instead of
	 * throwing a {@link NullPointerException} from the setter.
	 *
	 * @param custName the customer name
	 */
	public void setCustName(String custName)
	{
		this.custName = custName;
	}

	/**
	 * @return the customer postal address
	 */
	public String getCustAddress()
	{
		return custAddress;
	}

	/**
	 * Sets the customer address by direct, null-safe assignment. The value is
	 * stored exactly as supplied (an empty string is kept as an empty string and
	 * is not blank-trimmed); a {@code null} is assigned unchanged rather than
	 * triggering an {@code equals} comparison, so a missing address is reported by
	 * the field-level {@link NotNull} constraint during Bean Validation instead of
	 * throwing a {@link NullPointerException} from the setter.
	 *
	 * @param custAddress the customer postal address
	 */
	public void setCustAddress(String custAddress)
	{
		this.custAddress = custAddress;
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
	 * {@code "1990-05-15"} becomes {@code "15051990"}. Only a well-formed ISO
	 * {@code YYYY-MM-DD} value (ten characters of digit-digit-digit-digit,
	 * hyphen, digit-digit, hyphen, digit-digit) is reordered; any other value
	 * &mdash; including {@code null}, an already-compact 8-character
	 * {@code DDMMYYYY} value, or arbitrary text &mdash; is assigned as-is, which
	 * avoids a {@link StringIndexOutOfBoundsException} and never fabricates a
	 * valid-length date from non-date input (the eight-digit {@link Pattern}
	 * constraint then rejects any stored value that is not all digits).</p>
	 *
	 * @param custDob the date of birth, either as a 10-character
	 *                {@code YYYY-MM-DD} string (which is reordered) or already in
	 *                the compact form (which is assigned as-is)
	 */
	public void setCustDob(String custDob)
	{
		if (custDob != null && custDob.matches("\\d{4}-\\d{2}-\\d{2}"))
		{
			// Reorder a well-formed ISO YYYY-MM-DD value into the compact COBOL
			// DDMMYYYY display form: day (8,10) + month (5,7) + year (0,4).
			this.custDob = custDob.substring(8, 10) + custDob.substring(5, 7)
					+ custDob.substring(0, 4);
		}
		else
		{
			// Not a well-formed ISO date (e.g. null, an already-compact
			// 8-character DDMMYYYY value, or arbitrary text): assign as-is so the
			// setter neither throws nor fabricates a valid-length DOB from
			// non-date input. Bean Validation then reports any invalid value.
			this.custDob = custDob;
		}
	}

	/**
	 * Reports whether the honorific title embedded in the customer name is
	 * acceptable to the COBOL {@code CRECUST} title check (feature F-006, fail
	 * code {@code 'T'}).
	 *
	 * <p>The first space-delimited token of {@link #custName} is treated as the
	 * title, mirroring the COBOL
	 * {@code UNSTRING COMM-NAME DELIMITED BY SPACE INTO WS-UNSTR-TITLE}, and is
	 * validated by delegating to the authoritative
	 * {@link Title#isValidTitle(String)} helper. Consistent with the COBOL
	 * {@code EVALUATE} &mdash; whose {@code WHEN '         '} branch accepts a
	 * missing title &mdash; a {@code null} or blank name (and therefore a
	 * {@code null}/blank title token) is treated as <strong>valid</strong>; a
	 * non-blank token is accepted only when it matches one of the recognised
	 * titles case-sensitively. The earlier requirement for at least three name
	 * components was a UI-only heuristic with no COBOL counterpart and is
	 * deliberately not reproduced, so this helper now agrees exactly with
	 * {@link Title#isValidTitle(String)} and with the {@code CRECUST}
	 * specification of record.</p>
	 *
	 * @return {@code true} if the title token is blank/{@code null} or a
	 *         recognised title; {@code false} otherwise
	 */
	public boolean isValidTitle()
	{
		// Extract the first space-delimited token of the customer name, mirroring
		// the COBOL UNSTRING COMM-NAME DELIMITED BY SPACE INTO WS-UNSTR-TITLE,
		// then delegate to the authoritative Title.isValidTitle helper. A null or
		// blank name yields a null/blank token, which Title.isValidTitle accepts
		// as valid (the COBOL WHEN '         ' branch that allows a missing title).
		String title = null;
		if (this.custName != null)
		{
			int firstSpace = this.custName.indexOf(' ');
			title = (firstSpace >= 0) ? this.custName.substring(0, firstSpace)
					: this.custName;
		}
		return Title.isValidTitle(title);
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
