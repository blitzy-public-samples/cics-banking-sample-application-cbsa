/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.core.dto.accountenquiry;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.ibm.cics.cip.bank.core.config.JacksonConfig;

/**
 * Outer JSON <em>envelope</em> wire DTO for the frozen z/OS Connect
 * <em>inquire-account</em> ({@code inqaccz}, INQUIRE&nbsp;ACCOUNT) REST contract
 * (feature F-019).
 *
 * <p><strong>Role.</strong> This is a thin transport wrapper that isolates the
 * outer JSON envelope from the inner business payload. It holds a single field
 * of type {@link InqaccJson} (the sibling inner DTO in this same package) mapped
 * to the frozen envelope key {@code "InqAcc"}, so the full serialised document
 * is {@code {"InqAcc": { &hellip; }}}. It is the response envelope produced by
 * {@code InquireAccountController} (HTTP {@code GET}) and maps onto
 * {@code AccountService}, whose behaviour is the authoritative {@code INQACC.cbl}
 * program (F-009: the {@code 99999999} sentinel resolves the highest account via
 * the control-row {@code LAST-ACCOUNT-NUMBER} rather than a {@code MAX()} scan).
 * The inner payload carries the result of the account enquiry; this envelope
 * simply nests it and performs no logic of its own.</p>
 *
 * <h2>The outer key is {@code "InqAcc"} &mdash; no casing ambiguity</h2>
 * <p>Unlike the sibling customer-enquiry envelope (whose swagger and consumer
 * disagreed on casing), BOTH authoritative sources agree here: the frozen
 * swagger {@code src/zosconnect_artefacts/apis/inqaccz/api-docs/swagger.json}
 * defines {@code getCSaccenq_response_200} as an object whose single property is
 * {@code InqAcc}, and the preserved interface-module consumer (the legacy
 * {@code AccountEnquiryJson} in the Customer-Services interface package)
 * hard-codes {@code @JsonProperty("InqAcc")}. The wire key is therefore pinned
 * verbatim to {@code InqAcc} (capital&nbsp;{@code I}, capital&nbsp;{@code A}) and
 * is never uppercased or renamed.</p>
 *
 * <h2>Wire-name strategy</h2>
 * <p>The class is annotated
 * {@code @JsonNaming(}{@link JacksonConfig.EnvelopeNamingStrategy}{@code .class)}
 * to carry the frozen z/OS Connect envelope naming behaviour forward into the
 * pure-Java {@code bank-core} module, mirroring how the legacy source class
 * declared {@code @JsonNaming(JsonPropertyNamingStrategy.class)}. The strategy
 * strips the conventional 3-character member-name prefix via {@code substring(3)}.
 * The single field additionally declares an explicit {@link JsonProperty}; an
 * explicit {@code @JsonProperty} always overrides the naming strategy, so the
 * wire key is pinned verbatim to {@code InqAcc} regardless of the Java field
 * name or the {@code substring(3)} strategy. That explicit annotation is the
 * contract guarantee; the {@code @JsonNaming} is present only to mirror the
 * source class shape exactly.</p>
 *
 * <h2>Backward-compatibility constraint</h2>
 * <p>The preserved consumer deserialises with a default {@code new ObjectMapper()}
 * ({@code FAIL_ON_UNKNOWN_PROPERTIES = true}; neither the legacy envelope nor its
 * inner payload declares {@code @JsonIgnoreProperties(ignoreUnknown=true)}).
 * Consequently this envelope MUST serialise to EXACTLY ONE top-level key
 * ({@code InqAcc}) and never emit any additional top-level field (no metadata,
 * status, or helper field at this level); the inner payload obeys the same rule,
 * enforced in {@link InqaccJson}. A required contract integration test locks this
 * in: a {@code bank-core}-serialised {@code AccountEnquiryJson} round-trips into
 * the preserved interface-module {@code AccountEnquiryJson} via
 * {@code new ObjectMapper().readValue(...)} with zero client change, and
 * {@code getInqaccCommarea()} returns a non-null inner payload.</p>
 *
 * <p><strong>Carrier only.</strong> This is a plain, mutable data holder: no
 * Spring stereotype, no persistence mapping, and no business logic. The legacy
 * client-side display helper that formatted fields for a 3270 terminal is
 * deliberately dropped &mdash; {@code bank-core} is the SERVER producing JSON,
 * not a terminal renderer. All money, date, and identifier concerns live inside
 * the nested {@link InqaccJson}.</p>
 *
 * @see InqaccJson
 * @see JacksonConfig.EnvelopeNamingStrategy
 */
@JsonNaming(JacksonConfig.EnvelopeNamingStrategy.class)
public class AccountEnquiryJson
{

	/**
	 * Nested inquire-account commarea payload, serialised under the verbatim
	 * envelope key {@code "InqAcc"}. The explicit {@code @JsonProperty("InqAcc")}
	 * pins the contract-critical top-level envelope key, overriding the
	 * class-level {@code substring(3)} naming strategy.
	 */
	@JsonProperty("InqAcc")
	private InqaccJson inqaccCommarea;

	/**
	 * Default constructor required by Jackson for deserialisation. Eagerly
	 * instantiates the nested {@link InqaccJson} payload so the envelope starts
	 * usable, matching the legacy source class.
	 */
	public AccountEnquiryJson()
	{
		inqaccCommarea = new InqaccJson();
	}

	/**
	 * Returns the nested inquire-account commarea payload.
	 *
	 * @return the nested {@link InqaccJson} payload (wire key {@code InqAcc})
	 */
	public InqaccJson getInqaccCommarea()
	{
		return inqaccCommarea;
	}

	/**
	 * Sets the nested inquire-account commarea payload.
	 *
	 * @param inqaccCommareaIn the nested {@link InqaccJson} payload to wrap
	 */
	public void setInqaccCommarea(InqaccJson inqaccCommareaIn)
	{
		inqaccCommarea = inqaccCommareaIn;
	}

	/**
	 * Returns a diagnostic representation of this envelope showing the nested
	 * payload.
	 *
	 * @return a string of the form {@code AccountEnquiryJson [InqAcc=...]}
	 */
	@Override
	public String toString()
	{
		return "AccountEnquiryJson [InqAcc=" + inqaccCommarea + "]";
	}
}
