/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.core.dto.createcustomer;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.ibm.cics.cip.bank.core.config.JacksonConfig;
import com.ibm.cics.cip.bank.core.dto.common.CommKey;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * Inner commarea payload of the frozen z/OS Connect {@code crecust} (CREATE
 * CUSTOMER) contract (feature F-019), wrapped by {@link CreateCustomerJson}
 * under the {@code "CreCust"} envelope key.
 *
 * <p><strong>Dual role.</strong> This single DTO is BOTH the request payload
 * (the client POSTs {@code CommName}, {@code CommAddress} and
 * {@code CommDateOfBirth}) AND the response payload (the server returns the
 * allocated {@link #commKey identity} &mdash; sort code plus customer number
 * &mdash; together with the agency-derived {@code CommCreditScore}, the
 * {@code CommCsReviewDate}, and the {@code CommSuccess}/{@code CommFailCode}
 * status pair). It carries data only; all business behaviour lives in
 * {@code CustomerService}, whose authoritative specification is the COBOL
 * program {@code CRECUST.cbl}.</p>
 *
 * <p><strong>Structure / PIC widths.</strong> The nine fields and their PIC
 * widths are taken verbatim from {@code CRECUST.cpy}: {@code COMM-EYECATCHER
 * X(4)}, {@code COMM-KEY} (sort code {@code 9(6)} + number {@code 9(10)}),
 * {@code COMM-NAME X(60)}, {@code COMM-ADDRESS X(160)},
 * {@code COMM-DATE-OF-BIRTH 9(8)}, {@code COMM-CREDIT-SCORE 999},
 * {@code COMM-CS-REVIEW-DATE 9(8)}, {@code COMM-SUCCESS X} and
 * {@code COMM-FAIL-CODE X}. The {@code @JsonProperty} wire names reproduce the
 * z/OS Connect envelope byte-for-byte.</p>
 *
 * <p><strong>Shared composite key.</strong> The {@code CommKey} field is the
 * shared {@link CommKey} from {@code dto.common}, which supersedes the legacy
 * interface module's nested per-envelope key class. Its number component is a
 * {@link Long}, safely holding the {@code 9(10)} customer number (maximum
 * 9,999,999,999, which exceeds {@code int} range) while still serialising as a
 * JSON integer, so the frozen {@code "CommNumber":integer} contract is
 * preserved verbatim.</p>
 *
 * <p><strong>Date fields are Strings (deliberate contract decision).</strong>
 * {@code commDateOfBirth} and {@code commCsReviewDate} are declared as
 * {@code String} (not a numeric or temporal Java type). The
 * frozen swagger types both as {@code integer}, but the preserved
 * interface-module consumer DTO &mdash; the authoritative runtime contract for
 * backward compatibility &mdash; uses {@code String} for both, so the
 * contract-faithful choice here is {@code String} (Jackson coerces a JSON
 * number&harr;String, making this wire-safe in both directions). The on-the-wire
 * value is the eight-character {@code DDMMYYYY} digit string (for example
 * {@code "15051990"}); no slashes are inserted &mdash; the "DD/MM/YYYY" form in
 * the specification is the human-readable DISPLAY format, not the wire form.
 * The controller contract integration test locks this behaviour.</p>
 *
 * <p><strong>Naming strategy.</strong> The class carries
 * {@code @JsonNaming(JacksonConfig.EnvelopeNamingStrategy.class)} to opt in to
 * the module's envelope naming behaviour; however every field also declares its
 * own explicit {@code @JsonProperty} wire name, which Jackson honours
 * authoritatively over the strategy, guaranteeing the verbatim wire names.</p>
 *
 * <p><strong>Backward-compatibility constraint.</strong> The preserved consumer
 * deserialises with a default {@code ObjectMapper}
 * ({@code FAIL_ON_UNKNOWN_PROPERTIES=true}); this DTO therefore emits exactly
 * these nine wire keys and exposes no extra serialised getter.</p>
 *
 * @see CreateCustomerJson
 * @see CommKey
 * @see JacksonConfig.EnvelopeNamingStrategy
 */
@JsonNaming(JacksonConfig.EnvelopeNamingStrategy.class)
public class CrecustJson
{

	/**
	 * Eye-catcher integrity marker ({@code COMM-EYECATCHER PIC X(4)}). Retained
	 * as a WIRE field for contract fidelity even though the eye-catcher is
	 * dropped from the JPA entity; the service sets it to {@code "CUST"} on
	 * success. Defaults to four spaces to reproduce the legacy initial value.
	 */
	@JsonProperty("CommEyecatcher")
	@Size(max = 4)
	private String commEyecatcher = "    ";

	/**
	 * Allocated identity: sort code ({@code 9(6)}) plus customer number
	 * ({@code 9(10)}). Uses the shared {@link CommKey} (whose number is a
	 * {@link Long} to hold the 10-digit customer number). Initialised eagerly so
	 * the key is never {@code null} and a freshly-constructed envelope reproduces
	 * the legacy default {@code "CommKey":{"CommSortcode":0,"CommNumber":0}}.
	 */
	@JsonProperty("CommKey")
	@Valid
	private CommKey commKey = new CommKey();

	/** Customer name ({@code COMM-NAME PIC X(60)}; first token is the title). */
	@JsonProperty("CommName")
	@Size(max = 60)
	private String commName;

	/** Customer address ({@code COMM-ADDRESS PIC X(160)}). */
	@JsonProperty("CommAddress")
	@Size(max = 160)
	private String commAddress;

	/**
	 * Date of birth. Carried as an eight-character {@code DDMMYYYY} digit string
	 * (for example {@code "15051990"}) &mdash; a {@code String}, not a numeric or
	 * temporal Java type: the swagger types it {@code integer} but
	 * the preserved consumer DTO uses {@code String}, which is the authoritative
	 * runtime contract (locked by the controller IT). No slashes on the wire.
	 */
	@JsonProperty("CommDateOfBirth")
	@Size(max = 8)
	private String commDateOfBirth;

	/**
	 * Agency-derived credit score ({@code COMM-CREDIT-SCORE PIC 999}, range
	 * 0&ndash;999). A primitive {@code int} mirroring the legacy field; serialises
	 * as a JSON number.
	 */
	@JsonProperty("CommCreditScore")
	@Min(0)
	@Max(999)
	private int commCreditScore = 0;

	/**
	 * Credit-score review date. Like {@link #commDateOfBirth}, an eight-character
	 * {@code DDMMYYYY} {@code String} (swagger says integer but the consumer DTO
	 * is authoritative). Defaults to {@code "0"} to reproduce the legacy initial
	 * value.
	 */
	@JsonProperty("CommCsReviewDate")
	@Size(max = 8)
	private String commCsReviewDate = "0";

	/** Success flag ({@code COMM-SUCCESS PIC X}: {@code 'Y'}/{@code 'N'}). */
	@JsonProperty("CommSuccess")
	@Size(max = 1)
	private String commSuccess;

	/**
	 * Single-character COBOL fail code ({@code COMM-FAIL-CODE PIC X}); blank on
	 * success, for example {@code 'C'} when no credit agency responds.
	 */
	@JsonProperty("CommFailCode")
	@Size(max = 1)
	private String commFailCode;

	/**
	 * No-argument constructor required by Jackson for deserialisation. Leaves the
	 * field-level defaults in place ({@code commEyecatcher} = four spaces,
	 * {@code commKey} = {@code new CommKey()}, {@code commCreditScore} = 0,
	 * {@code commCsReviewDate} = {@code "0"}).
	 */
	public CrecustJson()
	{
	}

	/**
	 * Convenience constructor for building a request payload, ported verbatim
	 * from the legacy interface-module {@code CrecustJson}. The
	 * {@code (String, String, String)} signature is relied upon by the
	 * create-customer wrapper that maps a submitted form onto this DTO.
	 *
	 * @param custName    the customer name (mapped to {@code CommName})
	 * @param custAddress the customer address (mapped to {@code CommAddress})
	 * @param custDob     the date of birth as an eight-character {@code DDMMYYYY}
	 *                    string (mapped to {@code CommDateOfBirth})
	 */
	public CrecustJson(String custName, String custAddress, String custDob)
	{
		commName = custName;
		commAddress = custAddress;
		commDateOfBirth = custDob;
	}

	/**
	 * Returns the eye-catcher.
	 *
	 * @return the eye-catcher (serialised as {@code CommEyecatcher})
	 */
	public String getCommEyecatcher()
	{
		return commEyecatcher;
	}

	/**
	 * Sets the eye-catcher.
	 *
	 * @param commEyecatcherIn the eye-catcher (serialised as
	 *                         {@code CommEyecatcher})
	 */
	public void setCommEyecatcher(String commEyecatcherIn)
	{
		commEyecatcher = commEyecatcherIn;
	}

	/**
	 * Returns the allocated identity key.
	 *
	 * @return the shared composite key (serialised as {@code CommKey})
	 */
	public CommKey getCommKey()
	{
		return commKey;
	}

	/**
	 * Sets the allocated identity key.
	 *
	 * @param commKeyIn the shared composite key (serialised as {@code CommKey})
	 */
	public void setCommKey(CommKey commKeyIn)
	{
		commKey = commKeyIn;
	}

	/**
	 * Returns the customer name.
	 *
	 * @return the name (serialised as {@code CommName})
	 */
	public String getCommName()
	{
		return commName;
	}

	/**
	 * Sets the customer name.
	 *
	 * @param commNameIn the name (serialised as {@code CommName})
	 */
	public void setCommName(String commNameIn)
	{
		commName = commNameIn;
	}

	/**
	 * Returns the customer address.
	 *
	 * @return the address (serialised as {@code CommAddress})
	 */
	public String getCommAddress()
	{
		return commAddress;
	}

	/**
	 * Sets the customer address.
	 *
	 * @param commAddressIn the address (serialised as {@code CommAddress})
	 */
	public void setCommAddress(String commAddressIn)
	{
		commAddress = commAddressIn;
	}

	/**
	 * Returns the date of birth as an eight-character {@code DDMMYYYY} string.
	 *
	 * @return the date of birth (serialised as {@code CommDateOfBirth})
	 */
	public String getCommDateOfBirth()
	{
		return commDateOfBirth;
	}

	/**
	 * Sets the date of birth.
	 *
	 * @param commDateOfBirthIn the eight-character {@code DDMMYYYY} date of birth
	 *                          (serialised as {@code CommDateOfBirth})
	 */
	public void setCommDateOfBirth(String commDateOfBirthIn)
	{
		commDateOfBirth = commDateOfBirthIn;
	}

	/**
	 * Returns the credit score.
	 *
	 * @return the credit score (serialised as {@code CommCreditScore})
	 */
	public int getCommCreditScore()
	{
		return commCreditScore;
	}

	/**
	 * Sets the credit score.
	 *
	 * @param commCreditScoreIn the credit score (serialised as
	 *                          {@code CommCreditScore})
	 */
	public void setCommCreditScore(int commCreditScoreIn)
	{
		commCreditScore = commCreditScoreIn;
	}

	/**
	 * Returns the credit-score review date as an eight-character {@code DDMMYYYY}
	 * string.
	 *
	 * @return the review date (serialised as {@code CommCsReviewDate})
	 */
	public String getCommCsReviewDate()
	{
		return commCsReviewDate;
	}

	/**
	 * Sets the credit-score review date.
	 *
	 * @param commCsReviewDateIn the eight-character {@code DDMMYYYY} review date
	 *                           (serialised as {@code CommCsReviewDate})
	 */
	public void setCommCsReviewDate(String commCsReviewDateIn)
	{
		commCsReviewDate = commCsReviewDateIn;
	}

	/**
	 * Returns the success flag.
	 *
	 * @return the success flag (serialised as {@code CommSuccess})
	 */
	public String getCommSuccess()
	{
		return commSuccess;
	}

	/**
	 * Sets the success flag.
	 *
	 * @param commSuccessIn the success flag (serialised as {@code CommSuccess})
	 */
	public void setCommSuccess(String commSuccessIn)
	{
		commSuccess = commSuccessIn;
	}

	/**
	 * Returns the fail code.
	 *
	 * @return the single-character fail code (serialised as {@code CommFailCode})
	 */
	public String getCommFailCode()
	{
		return commFailCode;
	}

	/**
	 * Sets the fail code.
	 *
	 * @param commFailCodeIn the single-character fail code (serialised as
	 *                       {@code CommFailCode})
	 */
	public void setCommFailCode(String commFailCodeIn)
	{
		commFailCode = commFailCodeIn;
	}

	/**
	 * Returns a diagnostic string representation. Not part of the JSON wire
	 * contract; intended for logging and debugging only, and built with no
	 * external dependencies.
	 *
	 * @return a human-readable representation of all nine fields
	 */
	@Override
	public String toString()
	{
		return "CrecustJson [CommEyecatcher=" + commEyecatcher + ", CommKey="
				+ commKey + ", CommName=" + commName + ", CommAddress="
				+ commAddress + ", CommDateOfBirth=" + commDateOfBirth
				+ ", CommCreditScore=" + commCreditScore + ", CommCsReviewDate="
				+ commCsReviewDate + ", CommSuccess=" + commSuccess
				+ ", CommFailCode=" + commFailCode + "]";
	}

}
