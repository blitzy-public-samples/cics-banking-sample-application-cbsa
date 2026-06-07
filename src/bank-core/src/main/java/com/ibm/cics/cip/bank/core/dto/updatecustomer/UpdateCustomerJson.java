/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.core.dto.updatecustomer;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.ibm.cics.cip.bank.core.config.JacksonConfig;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * Outer JSON envelope wire DTO for the frozen z/OS Connect
 * <em>update-customer</em> ({@code updcust}) REST contract (feature F-019).
 *
 * <p><strong>Role.</strong> This is a thin transport wrapper that isolates the
 * outer JSON envelope from the inner business payload. It holds a single
 * {@link UpdcustJson} field mapped to the frozen envelope key {@code UpdCust},
 * so the full serialised document is {@code {"UpdCust": { &hellip; }}}. The same
 * class serves <em>both</em> directions: it is the request body deserialised by
 * the update-customer controller ({@code PUT}) and the response envelope
 * returned after the customer service performs the UPDCUST operation, because
 * the frozen {@code CScustupdRequest.json} and {@code CScustupdResponse.json}
 * schemas are structurally identical.</p>
 *
 * <p><strong>Behavioural parity.</strong> UPDCUST changes the customer
 * <em>name</em> and <em>address</em> only and writes no PROCTRAN record
 * (feature F-011, AAP &sect;0.6); this envelope merely carries the payload and
 * holds no business logic of its own.</p>
 *
 * <p><strong>Wire-name preservation.</strong> The single field is annotated
 * {@code @JsonProperty("UpdCust")}, which pins the verbatim outer envelope key
 * and <em>overrides</em> the class-level {@link JsonNaming} {@code substring(3)}
 * strategy ({@link JacksonConfig.EnvelopeNamingStrategy}). The strategy is
 * declared for parity with the legacy z/OS Connect interface DTO and the sibling
 * {@link UpdcustJson} convention, while the explicit name guarantees
 * byte-for-byte wire compatibility with the preserved front ends.</p>
 *
 * <p><strong>Carrier only.</strong> A plain, mutable data holder: no Spring
 * stereotype, no persistence mapping, no money or date types. The nested
 * {@link UpdcustJson} owns all field-level wire names and types.</p>
 *
 * @see UpdcustJson
 * @see JacksonConfig.EnvelopeNamingStrategy
 */
@JsonNaming(JacksonConfig.EnvelopeNamingStrategy.class)
public class UpdateCustomerJson
{

	/**
	 * The nested update-customer payload, serialised under the frozen envelope
	 * key {@code UpdCust}. The explicit {@link JsonProperty} pins this wire name
	 * regardless of the class-level naming strategy.
	 *
	 * <p><strong>Validation (F-021).</strong> {@link NotNull @NotNull} rejects an
	 * explicit {@code {"UpdCust": null}} request body with HTTP&nbsp;{@code 400}
	 * (via {@code MethodArgumentNotValidException} &rarr;
	 * {@code GlobalExceptionHandler}). It does NOT fire for an empty {@code {}}
	 * body, because the no-argument constructor eagerly initialises this field to
	 * a non-{@code null} {@link UpdcustJson} (see below), so {@code {}} continues
	 * to degrade gracefully to the COBOL fail-code path at HTTP&nbsp;{@code 200}.
	 * {@link Valid @Valid} cascades Bean Validation into the inner
	 * {@link UpdcustJson} (which already carries {@code @Size} width constraints)
	 * so that over-length structural violations surface as {@code 400}. These are
	 * STRUCTURAL/FORMAT checks only; the {@code UPDCUST} business outcome remains
	 * a COBOL fail-code envelope at HTTP&nbsp;{@code 200}, never converted to
	 * {@code 400}.</p>
	 */
	@JsonProperty("UpdCust")
	@NotNull
	@Valid
	private UpdcustJson updcust;

	/**
	 * Default constructor required by Jackson for deserialisation of an inbound
	 * {@code updcust} request body.
	 *
	 * <p><strong>Eager initialisation.</strong> The inner {@link UpdcustJson}
	 * payload is eagerly instantiated so that {@link #getUpdcust()} is never
	 * {@code null} in controller/service code, mirroring the
	 * {@code CreateCustomerJson} sibling convention. This makes a request body
	 * with no {@code UpdCust} key (for example an empty {@code {}} object)
	 * degrade gracefully through the normal business path &mdash; the controller
	 * copies the (all-null) fields into the service form and the service reports
	 * the appropriate COBOL fail code at HTTP&nbsp;200 &mdash; instead of raising
	 * a {@link NullPointerException} that the global advice would mislabel as
	 * HTTP&nbsp;500. The serialised shape is unchanged: an empty inner payload
	 * still serialises under the {@code UpdCust} key.</p>
	 */
	public UpdateCustomerJson()
	{
		this.updcust = new UpdcustJson();
	}

	/**
	 * All-args constructor wrapping a fully-populated inner payload.
	 *
	 * @param updcust the nested update-customer payload to wrap under the
	 *                {@code UpdCust} envelope key
	 */
	public UpdateCustomerJson(UpdcustJson updcust)
	{
		this.updcust = updcust;
	}

	/**
	 * Returns the nested update-customer payload.
	 *
	 * @return the wrapped {@link UpdcustJson} payload (wire key {@code UpdCust})
	 */
	public UpdcustJson getUpdcust()
	{
		return updcust;
	}

	/**
	 * Sets the nested update-customer payload.
	 *
	 * @param updcust the {@link UpdcustJson} payload to wrap under the
	 *                {@code UpdCust} envelope key
	 */
	public void setUpdcust(UpdcustJson updcust)
	{
		this.updcust = updcust;
	}

	/**
	 * Returns a diagnostic string showing the nested payload under its envelope
	 * key.
	 *
	 * @return a string representation of this envelope
	 */
	@Override
	public String toString()
	{
		return "UpdateCustomerJson [UpdCust=" + updcust + "]";
	}
}
