/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.core.dto.deleteaccount;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.ibm.cics.cip.bank.core.config.JacksonConfig;

/**
 * Outer JSON <em>envelope</em> wire DTO for the frozen z/OS Connect
 * delete-account ({@code delacc}) REST contract (feature F-019).
 *
 * <p><strong>Role.</strong> This is a thin transport wrapper that isolates the
 * outer JSON envelope from the inner business payload. It holds a single field
 * of type {@link DelaccJson} (the sibling inner DTO in this same package) mapped
 * to the frozen envelope key {@code "DelAcc"}, so the full serialised document
 * is {@code {"DelAcc": { &hellip; }}}. It is the response envelope produced by
 * {@code DeleteAccountController} (DELETE {@code /delacc/remove/{accno}}) after
 * {@code AccountService} performs the DELACC operation. The same shape also
 * matches the request envelope, because the frozen {@code CSaccdelRequest.json}
 * and {@code CSaccdelResponse.json} schemas share an identical {@code DelAcc}
 * top-level structure.</p>
 *
 * <p><strong>Terminal balances (F-013).</strong> {@code DELACC.cbl} (via
 * {@code AccountService}) physically removes the account row but appends a
 * PROCTRAN record capturing the account's terminal (closing) balances before it
 * disappears. Consequently the nested {@link DelaccJson} payload echoed back
 * carries the deleted account's full detail including its closing
 * {@code DelAccAvailBal}/{@code DelAccActualBal}. This envelope simply nests
 * that payload; it performs no logic of its own.</p>
 *
 * <p><strong>Wire-name fidelity.</strong> The class is annotated with
 * {@link JsonNaming} bound to {@link JacksonConfig.EnvelopeNamingStrategy} (the
 * {@code substring(3)} strategy ported from the legacy z/OS Connect interface)
 * so the envelope's derived property naming matches the source module. The
 * single field additionally carries an explicit {@code @JsonProperty("DelAcc")},
 * which <em>overrides</em> the naming strategy and pins the wire key to exactly
 * {@code DelAcc} (capital D, capital A), keeping serialisation byte-compatible
 * with the frozen contract and the preserved front ends.</p>
 *
 * <p><strong>Carrier only.</strong> This is a plain, mutable data holder: no
 * Spring stereotype, no persistence mapping, and no business logic. All money
 * ({@code BigDecimal}), date, and identifier concerns live inside the nested
 * {@link DelaccJson}.</p>
 *
 * @see DelaccJson
 * @see JacksonConfig.EnvelopeNamingStrategy
 */
@JsonNaming(JacksonConfig.EnvelopeNamingStrategy.class)
public class DeleteAccountJson
{

	/**
	 * Nested delete-account commarea payload, serialised under the verbatim
	 * envelope key {@code "DelAcc"}.
	 */
	@JsonProperty("DelAcc")
	private DelaccJson delAcc;

	/**
	 * Default constructor required by Jackson for deserialisation.
	 */
	public DeleteAccountJson()
	{
		super();
	}

	/**
	 * Convenience constructor wrapping an existing inner commarea payload.
	 *
	 * @param delAcc the nested {@link DelaccJson} payload
	 */
	public DeleteAccountJson(DelaccJson delAcc)
	{
		this.delAcc = delAcc;
	}

	/**
	 * Returns the nested delete-account commarea payload.
	 *
	 * @return the nested {@link DelaccJson} payload (wire key {@code DelAcc})
	 */
	public DelaccJson getDelAcc()
	{
		return delAcc;
	}

	/**
	 * Sets the nested delete-account commarea payload.
	 *
	 * @param delAccIn the nested {@link DelaccJson} payload
	 */
	public void setDelAcc(DelaccJson delAccIn)
	{
		delAcc = delAccIn;
	}

	/**
	 * Returns a diagnostic representation of this envelope showing the nested
	 * payload. Null-safe: a {@code null} payload renders as {@code null}.
	 *
	 * @return a string representation of this envelope
	 */
	@Override
	public String toString()
	{
		return "DeleteAccountJson [DelAcc=" + delAcc + "]";
	}
}
