/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.core.dto.customerenquiry;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.ibm.cics.cip.bank.core.config.JacksonConfig;

/**
 * Outer JSON envelope for the frozen z/OS Connect {@code inqcustz}
 * (INQUIRE&nbsp;CUSTOMER) response contract (feature F-019). This wrapper carries
 * a single nested object &mdash; the inner {@link InqCustZJson} commarea payload
 * &mdash; under the outer key {@code INQCUSTZ}, reproducing the z/OS Connect
 * {@code getCScustenq_response_200} envelope VERBATIM so that the preserved
 * Customer-Services interface module consumes a {@code bank-core} response with
 * ZERO client change.
 *
 * <p><strong>Outer key is {@code INQCUSTZ} (all caps), NOT {@code InqCustZ}.</strong>
 * Two authoritative sources disagree on the casing of this single envelope key:
 * the frozen swagger ({@code src/zosconnect_artefacts/apis/inqcustz/api-docs/swagger.json},
 * object {@code getCScustenq_response_200.InqCustZ}) documents the mixed-case
 * {@code InqCustZ}, whereas the preserved on-the-wire consumer &mdash; the legacy
 * interface-module {@code CustomerEnquiryJson} &mdash; hard-codes
 * {@code @JsonProperty("INQCUSTZ")} on its single field and is deserialised by
 * {@code WebController} through a plain default {@code new ObjectMapper()}
 * ({@code FAIL_ON_UNKNOWN_PROPERTIES = true}, with no
 * {@code @JsonIgnoreProperties}). Because the actual Java consumer is the binding
 * authority for backward compatibility, the all-caps {@code INQCUSTZ} form is
 * emitted here and the swagger's {@code InqCustZ} casing is intentionally
 * overridden. This choice is locked by the REQUIRED controller contract
 * integration test (IT) under {@code src/bank-core/src/test/java/**}, which
 * round-trips a {@code bank-core}-serialised response into the preserved
 * interface-module {@code CustomerEnquiryJson} via
 * {@code new ObjectMapper().readValue(...)} and asserts that {@code getInqCustZ()}
 * returns a non-{@code null} inner payload.</p>
 *
 * <p><strong>Exactly one top-level key.</strong> The preserved consumer
 * deserialises with a default mapper that fails on unknown properties, so the
 * serialised envelope MUST contain ONLY the single key {@code INQCUSTZ} &mdash;
 * never any additional metadata, status, or helper field at this level. The
 * inner payload obeys the same single-surface rule in {@link InqCustZJson}.</p>
 *
 * <p><strong>Wire name pinning.</strong> The class is annotated
 * {@link JsonNaming @JsonNaming} with {@link JacksonConfig.EnvelopeNamingStrategy}
 * for consistency with the other {@code core/dto} envelopes (it mirrors how the
 * legacy class declared {@code @JsonNaming(JsonPropertyNamingStrategy.class)}).
 * The strategy strips a conventional 3-character member-name prefix
 * ({@code substring(3)}); however, the single field carries an explicit
 * {@link JsonProperty @JsonProperty("INQCUSTZ")}, and an explicit
 * {@code @JsonProperty} always wins over the class-level strategy. The emitted
 * outer key is therefore exactly {@code INQCUSTZ}, regardless of the Java field
 * name or the {@code substring(3)} rule &mdash; the strategy can never produce an
 * unexpected key here.</p>
 *
 * <p><strong>No business logic.</strong> This is a plain, mutable wire DTO with
 * no Spring stereotype, no persistence, no money or date fields, and no
 * client-side display helpers. The INQUIRE&nbsp;CUSTOMER behaviour (sentinel
 * resolution, validation, fail codes) lives in {@code CustomerService}, and the
 * {@code InquireCustomerController} populates this envelope via
 * {@link #setInqCustZ(InqCustZJson)}.</p>
 *
 * @see InqCustZJson
 * @see JacksonConfig.EnvelopeNamingStrategy
 */
@JsonNaming(JacksonConfig.EnvelopeNamingStrategy.class)
public class CustomerEnquiryJson
{

	/**
	 * The single nested commarea payload of the {@code inqcustz} response. Pinned
	 * to the verbatim outer wire key {@code INQCUSTZ} (all caps) by the explicit
	 * {@link JsonProperty @JsonProperty}, which takes precedence over the
	 * class-level {@code substring(3)} naming strategy. This is the only
	 * serialised member of the envelope.
	 */
	@JsonProperty("INQCUSTZ")
	private InqCustZJson inqCustZ;

	/**
	 * No-argument constructor required by Jackson for (de)serialisation and used
	 * by the populating {@code InquireCustomerController}.
	 */
	public CustomerEnquiryJson()
	{
		super();
	}

	/**
	 * Returns the inner {@link InqCustZJson} commarea payload wrapped by this
	 * envelope.
	 *
	 * @return the nested {@code INQCUSTZ} payload, or {@code null} if not yet set
	 */
	public InqCustZJson getInqCustZ()
	{
		return inqCustZ;
	}

	/**
	 * Sets the inner {@link InqCustZJson} commarea payload wrapped by this
	 * envelope.
	 *
	 * @param inqCustZIn the nested {@code INQCUSTZ} payload to wrap
	 */
	public void setInqCustZ(InqCustZJson inqCustZIn)
	{
		inqCustZ = inqCustZIn;
	}

	/**
	 * Returns a diagnostic string representation that prints the inner payload.
	 *
	 * @return a human-readable representation of this envelope
	 */
	@Override
	public String toString()
	{
		return "CustomerEnquiryJson [INQCUSTZ=" + inqCustZ + "]";
	}

}
