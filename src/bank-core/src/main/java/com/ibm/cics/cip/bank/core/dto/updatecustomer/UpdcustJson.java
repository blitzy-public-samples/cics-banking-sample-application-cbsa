/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.core.dto.updatecustomer;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.ibm.cics.cip.bank.core.config.JacksonConfig;
import jakarta.validation.constraints.Size;

/**
 * Inner commarea payload wire DTO for the frozen z/OS Connect
 * <em>update-customer</em> ({@code updcust}) REST contract (feature F-019).
 *
 * <p><strong>Role.</strong> This is the inner payload that the sibling
 * {@link UpdateCustomerJson} envelope wraps under the {@code UpdCust} key, so the
 * full serialised document is {@code {"UpdCust": { &hellip; }}}. The same class
 * serves <em>both</em> the request and the response, because the frozen
 * {@code CScustupdRequest.json} and {@code CScustupdResponse.json} schemas are
 * structurally identical; no separate request payload type is required.</p>
 *
 * <p><strong>Structural source of record.</strong> The ten fields, their order,
 * and their PIC types come from {@code src/base/cobol_copy/UPDCUST.cpy}; the wire
 * names, JSON types, and max-lengths are pinned by the frozen z/OS Connect
 * {@code CScustupd} request/response schemas. Each field carries an explicit
 * {@link JsonProperty}, which <em>overrides</em> the class-level
 * {@link JsonNaming} {@code substring(3)} envelope strategy
 * ({@link JacksonConfig.EnvelopeNamingStrategy}); the strategy is declared for
 * parity with the source z/OS Connect interface, while the explicit names
 * guarantee byte-for-byte wire compatibility with the preserved front ends.</p>
 *
 * <p><strong>Wire-name caveat &mdash; {@code CommAddress}.</strong> The copybook
 * field is {@code COMM-ADDR}, yet the frozen JSON wire name is
 * {@code CommAddress} (confirmed in {@code CScustupdRequest.json},
 * {@code CScustupdResponse.json}, {@code swagger.json}, and the legacy interface
 * DTO). This DTO therefore uses {@code @JsonProperty("CommAddress")}.</p>
 *
 * <p><strong>Numeric fields are JSON integers.</strong> {@code CommDob},
 * {@code CommCreditScore}, and {@code CommCsReviewDate} are typed
 * {@code integer} by the frozen schema, so they are boxed {@link Integer}s
 * (initialised to {@code 0} so they serialise as {@code 0}, not {@code null}):</p>
 * <ul>
 *   <li>{@code commDateOfBirth} and {@code commCreditScoreReviewDate} carry an
 *       8-digit value in DAY-MONTH-YEAR ({@code DDMMYYYY}) ordering &mdash; for
 *       example 15&nbsp;May&nbsp;2023 is {@code 15052023} &mdash; emitted as a
 *       JSON integer, never a slash-delimited string. Serialising these as
 *       strings would violate the frozen {@code integer} contract and break the
 *       re-pointed {@code Z-OS-Connect-Customer-Services-Interface} consumer,
 *       whose own {@code UpdcustJson} deserialises {@code CommDob} into an
 *       {@code int}.</li>
 *   <li>{@code commCreditScore} is a small integer in the range 0&ndash;999 (a
 *       score, <em>not</em> a monetary value): no fixed-point decimal money type
 *       is involved.</li>
 * </ul>
 *
 * <p><strong>Carrier only.</strong> This is a plain, mutable data holder: no
 * Spring stereotype, no persistence mapping, no business logic, and no numeric
 * rounding. Zero-padding of the {@code String} identifiers to their declared
 * widths (sort code&nbsp;&rarr;&nbsp;6, customer number&nbsp;&rarr;&nbsp;10) and
 * the {@code LocalDate}&nbsp;&rarr;&nbsp;{@code DDMMYYYY}-integer conversion are
 * performed by the populating service/mapper layer, never here. The DTO simply
 * holds the already-formatted values (AAP &sect;0.6).</p>
 *
 * @see UpdateCustomerJson
 * @see JacksonConfig.EnvelopeNamingStrategy
 */
@JsonNaming(JacksonConfig.EnvelopeNamingStrategy.class)
public class UpdcustJson
{

	/** Eye-catcher (copybook {@code COMM-EYE}, PIC X(4)); retained for wire parity. */
	@JsonProperty("CommEye")
	@Size(max = 4)
	private String commEye = "    ";

	/**
	 * Sort code (copybook {@code COMM-SCODE}, PIC X(6)); held as a
	 * left-zero-padded {@link String} of width 6 (for example {@code "987654"}).
	 */
	@JsonProperty("CommScode")
	@Size(max = 6)
	private String commSortcode = "";

	/**
	 * Customer number (copybook {@code COMM-CUSTNO}, PIC X(10)); held as a
	 * left-zero-padded {@link String} of width 10 (for example
	 * {@code "0000000123"}).
	 */
	@JsonProperty("CommCustno")
	@Size(max = 10)
	private String commCustno = " ";

	/** Customer name (copybook {@code COMM-NAME}, PIC X(60)). */
	@JsonProperty("CommName")
	@Size(max = 60)
	private String commName = " ";

	/**
	 * Customer address (copybook {@code COMM-ADDR}, PIC X(160)). Note that the
	 * Java field {@code commAddress} maps to the frozen wire name
	 * {@code CommAddress} (not {@code CommAddr}).
	 */
	@JsonProperty("CommAddress")
	@Size(max = 160)
	private String commAddress = " ";

	/**
	 * Date of birth (copybook {@code COMM-DOB}, PIC 9(8)); an 8-digit
	 * {@code DDMMYYYY} integer (frozen schema range 0&ndash;99999999). The mapper
	 * converts the entity {@code LocalDate} to this integer.
	 */
	@JsonProperty("CommDob")
	private Integer commDateOfBirth = 0;

	/**
	 * Credit score (copybook {@code COMM-CREDIT-SCORE}, PIC 9(3); frozen schema
	 * range 0&ndash;999). A small boxed {@link Integer}, never a monetary value.
	 */
	@JsonProperty("CommCreditScore")
	private Integer commCreditScore = 0;

	/**
	 * Credit-score review date (copybook {@code COMM-CS-REVIEW-DATE}, PIC 9(8));
	 * an 8-digit {@code DDMMYYYY} integer (frozen schema range 0&ndash;99999999).
	 */
	@JsonProperty("CommCsReviewDate")
	private Integer commCreditScoreReviewDate = 0;

	/** Update-success flag (copybook {@code COMM-UPD-SUCCESS}, PIC X), single char. */
	@JsonProperty("CommUpdSuccess")
	@Size(max = 1)
	private String commUpdateSuccess = " ";

	/**
	 * Update fail code (copybook {@code COMM-UPD-FAIL-CD}, PIC X), single char.
	 * Carries the COBOL single-character UPDCUST fail code; the Java field
	 * {@code commUpdateFailCode} maps to the wire name {@code CommUpdFailCd}.
	 */
	@JsonProperty("CommUpdFailCd")
	@Size(max = 1)
	private String commUpdateFailCode = " ";

	/**
	 * Default constructor required by Jackson for (de)serialisation. The field
	 * initialisers above preserve the legacy envelope's default wire shape.
	 */
	public UpdcustJson()
	{
		super();
	}

	/**
	 * All-args constructor taking the ten fields in copybook order.
	 *
	 * @param commEye                   the eye-catcher
	 * @param commSortcode              the sort code (left-zero-padded to width 6)
	 * @param commCustno                the customer number (left-zero-padded to
	 *                                  width 10)
	 * @param commName                  the customer name
	 * @param commAddress               the customer address
	 * @param commDateOfBirth           the date of birth as a {@code DDMMYYYY}
	 *                                  integer (0&ndash;99999999)
	 * @param commCreditScore           the credit score (0&ndash;999)
	 * @param commCreditScoreReviewDate the credit-score review date as a
	 *                                  {@code DDMMYYYY} integer (0&ndash;99999999)
	 * @param commUpdateSuccess         the update-success flag (single char)
	 * @param commUpdateFailCode        the update fail code (single char)
	 */
	public UpdcustJson(String commEye, String commSortcode, String commCustno,
			String commName, String commAddress, Integer commDateOfBirth,
			Integer commCreditScore, Integer commCreditScoreReviewDate,
			String commUpdateSuccess, String commUpdateFailCode)
	{
		this.commEye = commEye;
		this.commSortcode = commSortcode;
		this.commCustno = commCustno;
		this.commName = commName;
		this.commAddress = commAddress;
		this.commDateOfBirth = commDateOfBirth;
		this.commCreditScore = commCreditScore;
		this.commCreditScoreReviewDate = commCreditScoreReviewDate;
		this.commUpdateSuccess = commUpdateSuccess;
		this.commUpdateFailCode = commUpdateFailCode;
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
	 * @return the customer address (wire name {@code CommAddress})
	 */
	public String getCommAddress()
	{
		return commAddress;
	}

	/**
	 * Sets the customer address.
	 *
	 * @param commAddressIn the customer address
	 */
	public void setCommAddress(String commAddressIn)
	{
		commAddress = commAddressIn;
	}

	/**
	 * Returns the date of birth as a {@code DDMMYYYY} integer.
	 *
	 * @return the date of birth (wire name {@code CommDob})
	 */
	public Integer getCommDateOfBirth()
	{
		return commDateOfBirth;
	}

	/**
	 * Sets the date of birth (expects a {@code DDMMYYYY} integer; the mapper
	 * performs the {@code LocalDate} conversion).
	 *
	 * @param commDateOfBirthIn the date of birth
	 */
	public void setCommDateOfBirth(Integer commDateOfBirthIn)
	{
		commDateOfBirth = commDateOfBirthIn;
	}

	/**
	 * Returns the credit score (0&ndash;999).
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
	 * Returns the credit-score review date as a {@code DDMMYYYY} integer.
	 *
	 * @return the review date (wire name {@code CommCsReviewDate})
	 */
	public Integer getCommCreditScoreReviewDate()
	{
		return commCreditScoreReviewDate;
	}

	/**
	 * Sets the credit-score review date (expects a {@code DDMMYYYY} integer; the
	 * mapper performs the {@code LocalDate} conversion).
	 *
	 * @param commCreditScoreReviewDateIn the review date
	 */
	public void setCommCreditScoreReviewDate(Integer commCreditScoreReviewDateIn)
	{
		commCreditScoreReviewDate = commCreditScoreReviewDateIn;
	}

	/**
	 * Returns the update-success flag.
	 *
	 * @return the success flag (wire name {@code CommUpdSuccess})
	 */
	public String getCommUpdateSuccess()
	{
		return commUpdateSuccess;
	}

	/**
	 * Sets the update-success flag.
	 *
	 * @param commUpdateSuccessIn the success flag
	 */
	public void setCommUpdateSuccess(String commUpdateSuccessIn)
	{
		commUpdateSuccess = commUpdateSuccessIn;
	}

	/**
	 * Returns the update fail code.
	 *
	 * @return the fail code (wire name {@code CommUpdFailCd})
	 */
	public String getCommUpdateFailCode()
	{
		return commUpdateFailCode;
	}

	/**
	 * Sets the update fail code.
	 *
	 * @param commUpdateFailCodeIn the fail code
	 */
	public void setCommUpdateFailCode(String commUpdateFailCodeIn)
	{
		commUpdateFailCode = commUpdateFailCodeIn;
	}

	/**
	 * Returns a diagnostic string listing all ten fields by their wire names.
	 *
	 * @return a string representation of this payload
	 */
	@Override
	public String toString()
	{
		return "UpdcustJson [CommEye=" + commEye + ", CommScode=" + commSortcode
				+ ", CommCustno=" + commCustno + ", CommName=" + commName
				+ ", CommAddress=" + commAddress + ", CommDob=" + commDateOfBirth
				+ ", CommCreditScore=" + commCreditScore + ", CommCsReviewDate="
				+ commCreditScoreReviewDate + ", CommUpdSuccess="
				+ commUpdateSuccess + ", CommUpdFailCd=" + commUpdateFailCode
				+ "]";
	}

}
