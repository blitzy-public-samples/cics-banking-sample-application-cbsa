/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.core.dto.payment;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.ibm.cics.cip.bank.core.config.JacksonConfig;

/**
 * Frozen z/OS Connect payment <em>origin/channel</em> object ({@code CommOrigin})
 * of the make-payment (debit/credit) payload, reproduced verbatim from the
 * Payment-Interface module's {@code OriginJson} (feature F-019). It maps the
 * {@code COMM-ORIGIN} group of {@code PAYDBCR.cpy} and is nested inside
 * {@link DbcrJson} under the {@code CommOrigin} key on both request and response.
 *
 * <p><strong>Wire contract is frozen.</strong> Every field carries an explicit
 * {@link JsonProperty @JsonProperty} pinning its verbatim wire name
 * ({@code CommApplid}, {@code CommUserid}, {@code CommFacilityName},
 * {@code CommNetwrkId}, {@code CommFaciltype}, {@code Fill0}). The class is
 * annotated {@link JsonNaming @JsonNaming} with
 * {@link JacksonConfig.EnvelopeNamingStrategy} for consistency with the other
 * {@code core/dto} envelopes; because each member also declares an explicit
 * {@code @JsonProperty}, Jackson honours that explicit name and the strategy
 * does not alter it &mdash; matching the legacy behaviour precisely and keeping
 * the serialised object byte-for-byte compatible with the
 * {@code CommOrigin} block of {@code PayRequest.json} / {@code PayResponse.json}.</p>
 *
 * <h2>Channel identity conveyed</h2>
 * <ul>
 *   <li>the <strong>facility type</strong> from {@code CommFaciltype} (defaulted
 *       to {@code 496}), which {@code PaymentService} uses to apply the
 *       facility-type-496 debit/credit channel restrictions reproduced from
 *       {@code DBCRFUN};</li>
 *   <li>the <strong>origin string</strong> from {@code CommApplid} concatenated
 *       with {@code CommUserid} (16 characters), which the payment service slices
 *       for the PROCTRAN description of payment-channel movements.</li>
 * </ul>
 *
 * <h2>Type fidelity ({@code CommFaciltype})</h2>
 * <p>The COBOL field is {@code COMM-FACILTYPE PIC S9(8) COMP} (a binary integer)
 * and the frozen schema declares {@code CommFaciltype} as
 * {@code {"type":"integer","minimum":-99999999,"maximum":99999999}}. This DTO
 * therefore models it as an {@link Integer} (defaulted to {@code 496}), unlike
 * the legacy interface module which carried it as the {@code String "0496"}. A
 * {@code String} would serialise as {@code "CommFaciltype":"496"} and fail the
 * integer schema; an {@code Integer} renders as the bare JSON number
 * {@code 496}, satisfying the contract.</p>
 *
 * <p>The remaining fields ({@code CommFacilityName}, {@code CommNetwrkId},
 * {@code Fill0}) are preserved verbatim with their legacy space-padded default
 * values so the envelope round-trips unchanged.</p>
 *
 * <h2>Input validation against the frozen schema (F-019/F-021)</h2>
 * <p>Each field carries the Jakarta Bean Validation constraint declared by the
 * frozen {@code makepayment} swagger {@code CommOrigin} object: the four string
 * members ({@code CommApplid}, {@code CommUserid}, {@code CommFacilityName},
 * {@code CommNetwrkId}) are {@link Size @Size(max = 8)}; {@code CommFaciltype}
 * is {@link Min @Min(-99999999)}/{@link Max @Max(99999999)} (the
 * {@code S9(8) COMP} range); and {@code Fill0} is {@link Size @Size(max = 4)}.
 * These are reached by the {@code @Valid} cascade on {@code DbcrJson}'s
 * {@code commOrigin} field, so the whole {@code PAYDBCR} payload is validated
 * before the {@code PaymentService} runs. The space-padded defaults all satisfy
 * their constraints, so a round-tripped envelope never trips validation.</p>
 *
 * @see JacksonConfig.EnvelopeNamingStrategy
 * @see DbcrJson
 */
@JsonNaming(JacksonConfig.EnvelopeNamingStrategy.class)
public class OriginJson
{

	/**
	 * Application id &mdash; the first eight characters of the inbound
	 * organisation. {@code COMM-APPLID PIC X(8)}. Left {@code null} by the no-arg
	 * constructor; populated by the organisation split.
	 */
	@JsonProperty("CommApplid")
	@Size(max = 8)
	private String commApplid;

	/**
	 * User id &mdash; the second eight characters of the inbound organisation.
	 * {@code COMM-USERID PIC X(8)}. Left {@code null} by the no-arg constructor;
	 * populated by the organisation split.
	 */
	@JsonProperty("CommUserid")
	@Size(max = 8)
	private String commUserid;

	/**
	 * Facility name. {@code COMM-FACILITY-NAME PIC X(8)}. Defaulted to eight
	 * spaces to match the legacy wire value.
	 */
	@JsonProperty("CommFacilityName")
	@Size(max = 8)
	private String commFacilityName = "        ";

	/**
	 * Network id. {@code COMM-NETWRK-ID PIC X(8)}. Defaulted to eight spaces to
	 * match the legacy wire value.
	 */
	@JsonProperty("CommNetwrkId")
	@Size(max = 8)
	private String commNetwrkId = "        ";

	/**
	 * Facility type. {@code COMM-FACILTYPE PIC S9(8) COMP} &rarr; {@link Integer}
	 * (frozen schema {@code type=integer}). Defaulted to {@code 496}, the
	 * facility-type sentinel that drives the FACILTYPE-496 channel rules in
	 * {@code PaymentService}.
	 */
	@JsonProperty("CommFaciltype")
	@Min(-99999999)
	@Max(99999999)
	private Integer commFaciltype = 496;

	/**
	 * Trailing filler. {@code FILLER PIC X(4)}. Defaulted to four spaces to match
	 * the legacy wire value.
	 */
	@JsonProperty("Fill0")
	@Size(max = 4)
	private String fill0 = "    ";

	/**
	 * No-argument constructor required for Jackson deserialisation. Leaves
	 * {@code commApplid} and {@code commUserid} {@code null} and the remaining
	 * fields at their space-padded / {@code 496} defaults.
	 */
	public OriginJson()
	{
		// Field defaults preserve the legacy envelope's initial values.
	}

	/**
	 * Constructs the origin from an inbound organisation string. Both the
	 * application id and the user id carry the organisation data, so it is
	 * left-justified into sixteen characters (right-padded with spaces) and split
	 * into two eight-character halves: characters {@code 0..7} become the
	 * application id and characters {@code 8..15} become the user id.
	 *
	 * <p>An organisation of sixteen or more characters is truncated to its first
	 * sixteen by the {@code substring} calls, reproducing the legacy behaviour
	 * exactly. (Callers upstream constrain the organisation to at most sixteen
	 * characters, so the truncation is defensive.)</p>
	 *
	 * @param organisation the inbound organisation string (may be shorter than
	 *                      sixteen characters; will be right-padded with spaces)
	 */
	public OriginJson(String organisation)
	{
		// APPLID and USERID both carry the organisation data, so it is split
		// into two 8-character strings.
		String paddedOrg = String.format("%-16s", organisation);
		commApplid = paddedOrg.substring(0, 8);
		commUserid = paddedOrg.substring(8);
	}

	/**
	 * Re-applies the organisation split after construction, using the identical
	 * idiom as {@link #OriginJson(String)}: the organisation is left-justified
	 * into sixteen characters and split into the eight-character application id
	 * and the eight-character user id.
	 *
	 * @param organisation the inbound organisation string (may be shorter than
	 *                      sixteen characters; will be right-padded with spaces)
	 */
	public void setOrganisation(String organisation)
	{
		String paddedOrg = String.format("%-16s", organisation);
		commApplid = paddedOrg.substring(0, 8);
		commUserid = paddedOrg.substring(8);
	}

	/**
	 * Returns the application id (first eight characters of the origin).
	 *
	 * @return the application id, or {@code null} if not yet set
	 */
	public String getCommApplid()
	{
		return commApplid;
	}

	/**
	 * Sets the application id.
	 *
	 * @param commApplidIn the application id
	 */
	public void setCommApplid(String commApplidIn)
	{
		commApplid = commApplidIn;
	}

	/**
	 * Returns the user id (second eight characters of the origin).
	 *
	 * @return the user id, or {@code null} if not yet set
	 */
	public String getCommUserid()
	{
		return commUserid;
	}

	/**
	 * Sets the user id.
	 *
	 * @param commUseridIn the user id
	 */
	public void setCommUserid(String commUseridIn)
	{
		commUserid = commUseridIn;
	}

	/**
	 * Returns the facility name.
	 *
	 * @return the facility name (defaults to eight spaces)
	 */
	public String getCommFacilityName()
	{
		return commFacilityName;
	}

	/**
	 * Sets the facility name.
	 *
	 * @param commFacilityNameIn the facility name
	 */
	public void setCommFacilityName(String commFacilityNameIn)
	{
		commFacilityName = commFacilityNameIn;
	}

	/**
	 * Returns the network id.
	 *
	 * @return the network id (defaults to eight spaces)
	 */
	public String getCommNetwrkId()
	{
		return commNetwrkId;
	}

	/**
	 * Sets the network id.
	 *
	 * @param commNetwrkIdIn the network id
	 */
	public void setCommNetwrkId(String commNetwrkIdIn)
	{
		commNetwrkId = commNetwrkIdIn;
	}

	/**
	 * Returns the facility type. Defaults to {@code 496}, the facility-type
	 * sentinel keyed by {@code PaymentService}'s channel rules.
	 *
	 * @return the facility type
	 */
	public Integer getCommFaciltype()
	{
		return commFaciltype;
	}

	/**
	 * Sets the facility type.
	 *
	 * @param commFaciltypeIn the facility type
	 */
	public void setCommFaciltype(Integer commFaciltypeIn)
	{
		commFaciltype = commFaciltypeIn;
	}

	/**
	 * Returns the trailing filler.
	 *
	 * @return the filler (defaults to four spaces)
	 */
	public String getFill0()
	{
		return fill0;
	}

	/**
	 * Sets the trailing filler.
	 *
	 * @param fill0In the filler
	 */
	public void setFill0(String fill0In)
	{
		fill0 = fill0In;
	}

	/**
	 * Returns a diagnostic string rendering all six fields of the origin object.
	 *
	 * @return a string representation of this {@code OriginJson}
	 */
	@Override
	public String toString()
	{
		return "OriginJson [CommApplid=" + commApplid + ", CommUserid="
				+ commUserid + ", CommFacilityName=" + commFacilityName
				+ ", CommNetwrkId=" + commNetwrkId + ", CommFaciltype="
				+ commFaciltype + ", Fill0=" + fill0 + "]";
	}
}
