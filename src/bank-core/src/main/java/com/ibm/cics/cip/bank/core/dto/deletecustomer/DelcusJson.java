/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.core.dto.deletecustomer;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.ibm.cics.cip.bank.core.config.JacksonConfig;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Inner commarea payload wire DTO for the frozen z/OS Connect
 * <em>delete-customer</em> ({@code delcus}) REST contract (feature F-019).
 *
 * <p><strong>Role.</strong> This is the inner payload that the sibling
 * {@link DeleteCustomerJson} envelope wraps under the {@code DelCus} key, so the
 * full serialised document is {@code {"DelCus": { &hellip; }}}. The same class
 * serves <em>both</em> the request and the response, because the frozen
 * {@code CScustdelRequest.json} and {@code CScustdelResponse.json} schemas are
 * structurally identical; no separate request payload type is required. (The
 * customer number to delete actually arrives via the controller path parameter
 * {@code /delcus/remove/{custno}} rather than a request body.)</p>
 *
 * <p><strong>Structural source of record.</strong> The ten fields, their order,
 * and their PIC types come from {@code src/base/cobol_copy/DELCUS.cpy}; the wire
 * names, types, and max-lengths are pinned by the frozen z/OS Connect
 * {@code CScustdel} request/response schemas. Each field carries an explicit
 * {@link JsonProperty}, which <em>overrides</em> the class-level
 * {@link JsonNaming} {@code substring(3)} envelope strategy
 * ({@link JacksonConfig.EnvelopeNamingStrategy}); the strategy is declared for
 * parity with the source z/OS Connect interface, while the explicit names
 * guarantee byte-for-byte wire compatibility with the preserved front ends.</p>
 *
 * <p><strong>Deliberate type corrections versus the legacy DTO.</strong> The
 * legacy interface-module {@code DelcusJson} typed {@code CommScode},
 * {@code CommCustno} and {@code CommDelFailCd} as {@code int} and
 * {@code CommCreditScore} as {@code int}. Here they are corrected to align with
 * the frozen schema and AAP &sect;0.6 (fixed-width character identifiers that
 * preserve COBOL display-numeric leading zeros):</p>
 * <ul>
 *   <li>{@code commSortcode}, {@code commCustno}, {@code commDelFailCode} are
 *       {@link String} &mdash; the copybook PICs are {@code X} and the schema
 *       types them as {@code string}; a {@code String} of digits serialises as a
 *       JSON string, matching the contract and retaining leading zeros (for
 *       example {@code "987654"}, {@code "0000000123"}).</li>
 *   <li>{@code commCreditScore} is a boxed {@link Integer} (range 0&ndash;999) so
 *       it can be {@code null}; it is a small integer, <em>not</em> a monetary
 *       value, so no fixed-point decimal money type is involved.</li>
 * </ul>
 *
 * <p><strong>Dates.</strong> {@code commDateOfBirth} and {@code commCsReviewDate}
 * are carried as already-formatted {@code DD/MM/YYYY} {@link String}s (customer
 * date format, AAP &sect;0.6). The service/mapper layer converts the entity
 * date value to this string before populating the DTO; this DTO stores no
 * date-object value and performs no formatting itself.</p>
 *
 * <p><strong>Carrier only.</strong> This is a plain, mutable data holder: no
 * Spring stereotype, no persistence mapping, no business logic, and no numeric
 * rounding. Zero-padding of identifiers and {@code DD/MM/YYYY} date formatting
 * are performed by the populating mapper, never here.</p>
 *
 * @see DeleteCustomerJson
 * @see JacksonConfig.EnvelopeNamingStrategy
 */
@JsonNaming(JacksonConfig.EnvelopeNamingStrategy.class)
public class DelcusJson
{

	/** Eye-catcher (copybook {@code COMM-EYE}, PIC X(4)); retained for wire parity. */
	@JsonProperty("CommEye")
	@Size(max = 4)
	private String commEye;

	/**
	 * Sort code (copybook {@code COMM-SCODE}, PIC X(6)); held as a
	 * left-zero-padded {@link String} of width 6 (for example {@code "987654"}).
	 */
	@JsonProperty("CommScode")
	@Size(max = 6)
	private String commSortcode;

	/**
	 * Customer number (copybook {@code COMM-CUSTNO}, PIC X(10)); held as a
	 * left-zero-padded {@link String} of width 10 (for example
	 * {@code "0000000123"}).
	 */
	@JsonProperty("CommCustno")
	@Size(max = 10)
	private String commCustno;

	/** Customer name (copybook {@code COMM-NAME}, PIC X(60)). */
	@JsonProperty("CommName")
	@Size(max = 60)
	private String commName;

	/**
	 * Customer address (copybook {@code COMM-ADDR}, PIC X(160)). Note the Java
	 * field name {@code commAddress} maps to the wire name {@code CommAddr}.
	 */
	@JsonProperty("CommAddr")
	@Size(max = 160)
	private String commAddress;

	/**
	 * Date of birth (copybook {@code COMM-DOB}, PIC 9(8)); carried as a
	 * {@code DD/MM/YYYY} {@link String}. The mapper converts the entity
	 * {@code LocalDate} to this format.
	 */
	@JsonProperty("CommDob")
	@Pattern(regexp = "\\d{2}/\\d{2}/\\d{4}")
	private String commDateOfBirth;

	/**
	 * Credit score (copybook {@code COMM-CREDIT-SCORE}, PIC 9(3); range
	 * 0&ndash;999). A small boxed {@link Integer}, never a monetary value.
	 */
	@JsonProperty("CommCreditScore")
	private Integer commCreditScore;

	/**
	 * Credit-score review date (copybook {@code COMM-CS-REVIEW-DATE},
	 * PIC 9(8)); carried as a {@code DD/MM/YYYY} {@link String}.
	 */
	@JsonProperty("CommCsReviewDate")
	@Pattern(regexp = "\\d{2}/\\d{2}/\\d{4}")
	private String commCsReviewDate;

	/** Delete-success flag (copybook {@code COMM-DEL-SUCCESS}, PIC X), single char. */
	@JsonProperty("CommDelSuccess")
	@Size(max = 1)
	private String commDelSuccess;

	/**
	 * Delete fail code (copybook {@code COMM-DEL-FAIL-CD}, PIC X), single char.
	 * Carries the COBOL single-character DELCUS fail code; the Java field name
	 * {@code commDelFailCode} maps to the wire name {@code CommDelFailCd}.
	 */
	@JsonProperty("CommDelFailCd")
	@Size(max = 1)
	private String commDelFailCode;

	/**
	 * Default constructor required by Jackson for (de)serialisation.
	 */
	public DelcusJson()
	{
		super();
	}

	/**
	 * All-args constructor taking the ten fields in copybook order.
	 *
	 * @param commEye          the eye-catcher
	 * @param commSortcode     the sort code (left-zero-padded to width 6)
	 * @param commCustno       the customer number (left-zero-padded to width 10)
	 * @param commName         the customer name
	 * @param commAddress      the customer address
	 * @param commDateOfBirth  the date of birth as a {@code DD/MM/YYYY} string
	 * @param commCreditScore  the credit score (0&ndash;999), or {@code null}
	 * @param commCsReviewDate the review date as a {@code DD/MM/YYYY} string
	 * @param commDelSuccess   the delete-success flag (single char)
	 * @param commDelFailCode  the delete fail code (single char)
	 */
	public DelcusJson(String commEye, String commSortcode, String commCustno,
			String commName, String commAddress, String commDateOfBirth,
			Integer commCreditScore, String commCsReviewDate,
			String commDelSuccess, String commDelFailCode)
	{
		this.commEye = commEye;
		this.commSortcode = commSortcode;
		this.commCustno = commCustno;
		this.commName = commName;
		this.commAddress = commAddress;
		this.commDateOfBirth = commDateOfBirth;
		this.commCreditScore = commCreditScore;
		this.commCsReviewDate = commCsReviewDate;
		this.commDelSuccess = commDelSuccess;
		this.commDelFailCode = commDelFailCode;
	}

	/**
	 * Returns the eye-catcher.
	 *
	 * @return the eye-catcher (wire name {@code CommEye})
	 */
	public String getCommEye()
	{
		return commEye;
	}

	/**
	 * Sets the eye-catcher.
	 *
	 * @param commEyeIn the eye-catcher
	 */
	public void setCommEye(String commEyeIn)
	{
		commEye = commEyeIn;
	}

	/**
	 * Returns the sort code (left-zero-padded to width 6).
	 *
	 * @return the sort code (wire name {@code CommScode})
	 */
	public String getCommSortcode()
	{
		return commSortcode;
	}

	/**
	 * Sets the sort code (expects an already left-zero-padded width-6 value).
	 *
	 * @param commSortcodeIn the sort code
	 */
	public void setCommSortcode(String commSortcodeIn)
	{
		commSortcode = commSortcodeIn;
	}

	/**
	 * Returns the customer number (left-zero-padded to width 10).
	 *
	 * @return the customer number (wire name {@code CommCustno})
	 */
	public String getCommCustno()
	{
		return commCustno;
	}

	/**
	 * Sets the customer number (expects an already left-zero-padded width-10
	 * value).
	 *
	 * @param commCustnoIn the customer number
	 */
	public void setCommCustno(String commCustnoIn)
	{
		commCustno = commCustnoIn;
	}

	/**
	 * Returns the customer name.
	 *
	 * @return the customer name (wire name {@code CommName})
	 */
	public String getCommName()
	{
		return commName;
	}

	/**
	 * Sets the customer name.
	 *
	 * @param commNameIn the customer name
	 */
	public void setCommName(String commNameIn)
	{
		commName = commNameIn;
	}

	/**
	 * Returns the customer address.
	 *
	 * @return the address (wire name {@code CommAddr})
	 */
	public String getCommAddress()
	{
		return commAddress;
	}

	/**
	 * Sets the customer address.
	 *
	 * @param commAddressIn the address
	 */
	public void setCommAddress(String commAddressIn)
	{
		commAddress = commAddressIn;
	}

	/**
	 * Returns the date of birth as a {@code DD/MM/YYYY} string.
	 *
	 * @return the date of birth (wire name {@code CommDob})
	 */
	public String getCommDateOfBirth()
	{
		return commDateOfBirth;
	}

	/**
	 * Sets the date of birth (expects an already-formatted {@code DD/MM/YYYY}
	 * string).
	 *
	 * @param commDateOfBirthIn the date of birth
	 */
	public void setCommDateOfBirth(String commDateOfBirthIn)
	{
		commDateOfBirth = commDateOfBirthIn;
	}

	/**
	 * Returns the credit score (0&ndash;999), or {@code null} if unset.
	 *
	 * @return the credit score (wire name {@code CommCreditScore})
	 */
	public Integer getCommCreditScore()
	{
		return commCreditScore;
	}

	/**
	 * Sets the credit score (0&ndash;999).
	 *
	 * @param commCreditScoreIn the credit score
	 */
	public void setCommCreditScore(Integer commCreditScoreIn)
	{
		commCreditScore = commCreditScoreIn;
	}

	/**
	 * Returns the credit-score review date as a {@code DD/MM/YYYY} string.
	 *
	 * @return the review date (wire name {@code CommCsReviewDate})
	 */
	public String getCommCsReviewDate()
	{
		return commCsReviewDate;
	}

	/**
	 * Sets the credit-score review date (expects an already-formatted
	 * {@code DD/MM/YYYY} string).
	 *
	 * @param commCsReviewDateIn the review date
	 */
	public void setCommCsReviewDate(String commCsReviewDateIn)
	{
		commCsReviewDate = commCsReviewDateIn;
	}

	/**
	 * Returns the delete-success flag (single char).
	 *
	 * @return the delete-success flag (wire name {@code CommDelSuccess})
	 */
	public String getCommDelSuccess()
	{
		return commDelSuccess;
	}

	/**
	 * Sets the delete-success flag (single char).
	 *
	 * @param commDelSuccessIn the delete-success flag
	 */
	public void setCommDelSuccess(String commDelSuccessIn)
	{
		commDelSuccess = commDelSuccessIn;
	}

	/**
	 * Returns the delete fail code (single COBOL fail-code char).
	 *
	 * @return the fail code (wire name {@code CommDelFailCd})
	 */
	public String getCommDelFailCode()
	{
		return commDelFailCode;
	}

	/**
	 * Sets the delete fail code (single COBOL fail-code char).
	 *
	 * @param commDelFailCodeIn the fail code
	 */
	public void setCommDelFailCode(String commDelFailCodeIn)
	{
		commDelFailCode = commDelFailCodeIn;
	}

	/**
	 * Returns a diagnostic representation listing all ten fields in copybook
	 * order using their wire names.
	 *
	 * @return a string representation of this payload
	 */
	@Override
	public String toString()
	{
		return "DelcusJson [CommEye=" + commEye + ", CommScode=" + commSortcode
				+ ", CommCustno=" + commCustno + ", CommName=" + commName
				+ ", CommAddr=" + commAddress + ", CommDob=" + commDateOfBirth
				+ ", CommCreditScore=" + commCreditScore + ", CommCsReviewDate="
				+ commCsReviewDate + ", CommDelSuccess=" + commDelSuccess
				+ ", CommDelFailCd=" + commDelFailCode + "]";
	}

}
