/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.core.dto.createcustomer;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.ibm.cics.cip.bank.core.config.JacksonConfig;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * Outer JSON envelope for the frozen z/OS Connect {@code crecust} (CREATE
 * CUSTOMER) contract (feature F-019).
 *
 * <p><strong>Role.</strong> This is a thin wire wrapper that reproduces verbatim
 * the single-key envelope object <code>{ "CreCust": { ...inner commarea... } }</code>.
 * It wraps the inner {@link CrecustJson} commarea payload under the single wire
 * key {@code "CreCust"}, which is confirmed identical by both the legacy
 * interface-module {@code CreateCustomerJson} and the {@code CScustcre} swagger
 * schemas ({@code CScustcreRequest.json} / {@code CScustcreResponse.json}, which
 * are structurally identical). Unlike the {@code customerenquiry} sibling (which
 * had an {@code INQCUSTZ}-vs-{@code InqCustZ} casing conflict), {@code crecust}
 * has no discrepancy &mdash; both sources agree on {@code CreCust}. It is
 * consumed as the request body and produced as the response body by
 * {@code CreateCustomerController} ({@code POST /crecust/insert}). This DTO
 * carries data only; it holds <em>no</em> business logic &mdash; behavioural
 * parity with the COBOL {@code CRECUST.cbl} lives in {@code CustomerService}.</p>
 *
 * <p><strong>Naming strategy.</strong> The class carries
 * {@code @JsonNaming(JacksonConfig.EnvelopeNamingStrategy.class)} to opt in to
 * the module's shared envelope naming behaviour (the {@code substring(3)}
 * strategy ported from the legacy interface module's property-naming strategy)
 * for consistency with the legacy class and the {@code createaccount} /
 * {@code customerenquiry} siblings. However, the explicit
 * {@code @JsonProperty("CreCust")} on the single field is authoritative and
 * Jackson honours it over the strategy, so the outer wire key is always exactly
 * {@code CreCust}.</p>
 *
 * <p><strong>Backward-compatibility constraint.</strong> At runtime the
 * preserved interface module deserialises the response into its own
 * identically-shaped {@code CreateCustomerJson} using a default
 * {@code new ObjectMapper()} ({@code FAIL_ON_UNKNOWN_PROPERTIES = true}, no
 * {@code @JsonIgnoreProperties}). This envelope therefore serialises
 * <strong>exactly one</strong> top-level key &mdash; {@code "CreCust"} &mdash;
 * and never any extra top-level field; no metadata, status, or helper field is
 * added at this level. The controller contract integration test under
 * {@code src/test/java} locks this round-trip behaviour: a bank-core-serialised
 * instance must read back into the preserved
 * {@code ...customerservices.jsonclasses.createcustomer.CreateCustomerJson} with
 * zero client change, and {@link #getCreCust()} must return a non-null payload.</p>
 *
 * <p><strong>Eager initialisation.</strong> The no-argument constructor eagerly
 * instantiates the inner {@link CrecustJson} payload so that
 * {@link #getCreCust()} is never {@code null} in controller/service code &mdash;
 * an intentional improvement over the legacy empty constructor that keeps the
 * serialised shape byte-for-byte identical (an empty inner payload still
 * serialises under the {@code CreCust} key). This mirrors the
 * {@code createaccount} sibling's {@code CreateAccountJson()} convention.</p>
 *
 * @see CrecustJson
 * @see CreateCustomerForm
 * @see JacksonConfig.EnvelopeNamingStrategy
 */
@JsonNaming(JacksonConfig.EnvelopeNamingStrategy.class)
public class CreateCustomerJson
{

	/**
	 * The inner create-customer commarea payload, serialised under the frozen
	 * single envelope key {@code "CreCust"}. The Java field name is
	 * {@code creCust}; the explicit {@link JsonProperty} pins the verbatim wire
	 * key and takes precedence over the class-level naming strategy.
	 *
	 * <p><strong>Validation (F-021).</strong> {@link NotNull @NotNull} rejects an
	 * explicit {@code {"CreCust": null}} request body with HTTP&nbsp;{@code 400}
	 * (via {@code MethodArgumentNotValidException} &rarr;
	 * {@code GlobalExceptionHandler}) BEFORE the controller dereferences the
	 * payload, eliminating the {@code NullPointerException}&rarr;{@code 500} path.
	 * {@link Valid @Valid} cascades Bean Validation into the inner
	 * {@link CrecustJson} so that over-width structural violations also surface as
	 * {@code 400}. These are STRUCTURAL/FORMAT checks only; business rules
	 * (invalid title {@code 'T'}, DOB-range {@code 'O'}, no-agency {@code 'C'})
	 * remain COBOL fail-code envelopes at HTTP&nbsp;{@code 200}, never converted to
	 * {@code 400}.</p>
	 */
	@JsonProperty("CreCust")
	@NotNull
	@Valid
	private CrecustJson creCust;

	/**
	 * Creates an envelope with an eagerly-instantiated inner payload.
	 *
	 * <p>Required by Jackson for request deserialisation. The inner
	 * {@link CrecustJson} is initialised to a fresh instance so that
	 * {@link #getCreCust()} never returns {@code null}; the serialised shape is
	 * unchanged (the empty inner payload still appears under the {@code CreCust}
	 * key).</p>
	 */
	public CreateCustomerJson()
	{
		this.creCust = new CrecustJson();
	}

	/**
	 * Creates an envelope from a validated request-input form, building the
	 * inner {@link CrecustJson} request payload from the form's name, address,
	 * and date-of-birth values.
	 *
	 * <p>Ported verbatim from the legacy interface-module {@code CreateCustomerJson}:
	 * it delegates to the sibling {@code CrecustJson(String, String, String)}
	 * convenience constructor using {@link CreateCustomerForm#getCustName()},
	 * {@link CreateCustomerForm#getCustAddress()}, and
	 * {@link CreateCustomerForm#getCustDob()} (all {@link String}).</p>
	 *
	 * @param form the validated create-customer form supplying the customer
	 *             name, address, and date of birth
	 */
	public CreateCustomerJson(CreateCustomerForm form)
	{
		this.creCust = new CrecustJson(form.getCustName(),
				form.getCustAddress(), form.getCustDob());
	}

	/**
	 * Creates an envelope wrapping an already-built inner payload.
	 *
	 * <p>Used by {@code CreateCustomerController} to wrap the success/failure
	 * response payload it constructs, mirroring the {@code createaccount}
	 * sibling's {@code CreateAccountJson(CreaccJson)} convenience constructor.</p>
	 *
	 * @param creCustIn the inner commarea payload to wrap under the
	 *                  {@code CreCust} envelope key
	 */
	public CreateCustomerJson(CrecustJson creCustIn)
	{
		this.creCust = creCustIn;
	}

	/**
	 * Returns the inner create-customer commarea payload.
	 *
	 * @return the inner payload (serialised under the {@code CreCust} key)
	 */
	public CrecustJson getCreCust()
	{
		return creCust;
	}

	/**
	 * Sets the inner create-customer commarea payload.
	 *
	 * @param creCustIn the inner payload (serialised under the {@code CreCust}
	 *                  key)
	 */
	public void setCreCust(CrecustJson creCustIn)
	{
		creCust = creCustIn;
	}

	/**
	 * Returns a debug representation of this envelope. Uses no external
	 * dependencies; intended for logging/diagnostics only and never for wire
	 * serialisation.
	 *
	 * @return a human-readable description of the envelope and its inner payload
	 */
	@Override
	public String toString()
	{
		return "CreateCustomerJson [CreCust=" + creCust + "]";
	}
}
