/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.core.dto.common;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.ibm.cics.cip.bank.core.config.JacksonConfig;

/**
 * Shared, envelope-preserving composite-key wire object &mdash; the two-part
 * identity (bank sort code + allocated record number) carried by the
 * create-customer and create-account JSON envelopes of the frozen z/OS Connect
 * REST contract (feature F-019).
 *
 * <p><strong>Single shared key.</strong> This is the modern successor of the
 * legacy interface module's {@code CreaccKeyJson}. Analysis of the legacy code
 * confirmed a deliberate single-shared-key design: the account envelope
 * ({@code CreaccJson}) declared a {@code CreaccKeyJson commKey}, and the
 * customer envelope ({@code CrecustJson}) <em>literally imported and reused the
 * same {@code CreaccKeyJson} class</em>. {@code CommKey} formalises that one
 * shared type here in {@code dto.common} so the key is not duplicated across
 * the {@code createcustomer} and {@code createaccount} DTO packages. It maps
 * the COBOL {@code COMM-KEY} group that appears, identically named, in both
 * {@code CRECUST.cpy} and {@code CREACC.cpy}.</p>
 *
 * <p><strong>Field ranges (COBOL / swagger).</strong></p>
 * <ul>
 *   <li>{@code COMM-SORTCODE PIC 9(6)} &rarr; {@code commSortcode}, JSON
 *       {@code CommSortcode}, maximum 999,999 (both schemas).</li>
 *   <li>{@code COMM-NUMBER PIC 9(8)} for accounts (max 99,999,999) and
 *       {@code PIC 9(10)} for customers (max 9,999,999,999) &rarr;
 *       {@code commNumber}, JSON {@code CommNumber}. A single
 *       widest-safe field serves both operations; the two frozen swagger
 *       {@code CommKey} schemas are shape-compatible (a JSON object with two
 *       integer properties) and differ only in the customer number's larger
 *       range.</li>
 * </ul>
 *
 * <p><strong>Wire shape.</strong> The class-level
 * {@link JsonNaming @JsonNaming} applies the module's
 * {@link JacksonConfig.EnvelopeNamingStrategy} (which strips the conventional
 * 3-character member-name prefix); however the explicit
 * {@link JsonProperty @JsonProperty} names take precedence and pin the verbatim
 * wire names {@code "CommSortcode"} and {@code "CommNumber"}, honouring the
 * frozen contract byte-for-byte. This class is a plain mutable carrier of raw
 * numeric values: it performs <em>no</em> zero-padding or formatting (in the
 * legacy that happened at the wrapper/pretty-print layer, not in the key), and
 * holds no monetary or date fields.</p>
 *
 * @see JacksonConfig.EnvelopeNamingStrategy
 */
@JsonNaming(JacksonConfig.EnvelopeNamingStrategy.class)
public class CommKey
{

	/**
	 * Bank sort code, serialised under the frozen wire name
	 * {@code CommSortcode}. Models COBOL {@code COMM-SORTCODE PIC 9(6) DISPLAY}
	 * (maximum 999,999), which fits comfortably within {@code int}; the boxed
	 * {@link Integer} is used for consistency with {@link #commNumber} and for
	 * nullability flexibility. Defaults to {@code 0} so a freshly-constructed
	 * key reproduces the legacy default envelope {@code {"CommSortcode":0, ...}}
	 * rather than emitting {@code null}.
	 */
	@JsonProperty("CommSortcode")
	private Integer commSortcode = 0;

	/**
	 * Allocated account or customer number, serialised under the frozen wire
	 * name {@code CommNumber}.
	 *
	 * <p><strong>Widened from {@code int} to {@link Long} (deliberate
	 * correctness fix).</strong> The legacy {@code CreaccKeyJson} declared this
	 * as a primitive {@code int}. That is safe for the 9(8) account number
	 * (maximum 99,999,999) but is a latent overflow for the 9(10) customer
	 * number it was reused for: the customer number's maximum, 9,999,999,999,
	 * <em>exceeds</em> {@link Integer#MAX_VALUE} (2,147,483,647). Using
	 * {@link Long} safely covers both the 8-digit account number and the
	 * 10-digit customer number. A {@link Long} serialises as a JSON
	 * integer/number with no decimal point &mdash; byte-for-byte identical to
	 * how an {@code int} serialises &mdash; so the frozen {@code "type":
	 * "integer"} contract is preserved verbatim. Defaults to {@code 0L} to
	 * reproduce the legacy default envelope {@code {..., "CommNumber":0}}.
	 */
	@JsonProperty("CommNumber")
	private Long commNumber = 0L;

	/**
	 * No-argument constructor required by Jackson for deserialisation. Leaves
	 * the fields at their initialised defaults ({@code 0} / {@code 0L}), which
	 * reproduces the legacy {@code CreaccKeyJson} default wire shape
	 * {@code {"CommSortcode":0,"CommNumber":0}}.
	 */
	public CommKey()
	{
	}

	/**
	 * All-arguments constructor.
	 *
	 * @param commSortcode the bank sort code (9(6); {@code null} permitted but
	 *                      normally the constant sort code, e.g. 987654)
	 * @param commNumber   the allocated account (9(8)) or customer (9(10))
	 *                      number; typed {@link Long} to hold 10-digit customer
	 *                      numbers without overflow
	 */
	public CommKey(Integer commSortcode, Long commNumber)
	{
		this.commSortcode = commSortcode;
		this.commNumber = commNumber;
	}

	/**
	 * Returns the sort code.
	 *
	 * @return the sort code (serialised as {@code CommSortcode})
	 */
	public Integer getCommSortcode()
	{
		return commSortcode;
	}

	/**
	 * Sets the sort code.
	 *
	 * @param commSortcode the sort code (serialised as {@code CommSortcode})
	 */
	public void setCommSortcode(Integer commSortcode)
	{
		this.commSortcode = commSortcode;
	}

	/**
	 * Returns the account/customer number.
	 *
	 * @return the record number (serialised as {@code CommNumber}); {@link Long}
	 *         to safely represent 10-digit customer numbers
	 */
	public Long getCommNumber()
	{
		return commNumber;
	}

	/**
	 * Sets the account/customer number.
	 *
	 * @param commNumber the record number (serialised as {@code CommNumber});
	 *                   {@link Long} to safely represent 10-digit customer
	 *                   numbers
	 */
	public void setCommNumber(Long commNumber)
	{
		this.commNumber = commNumber;
	}

	/**
	 * Returns a diagnostic string representation of this key. Not part of the
	 * JSON wire contract; intended for logging and debugging only.
	 *
	 * @return a human-readable representation of the sort code and number
	 */
	@Override
	public String toString()
	{
		return "CommKey [CommSortcode=" + commSortcode + ", CommNumber="
				+ commNumber + "]";
	}

}
