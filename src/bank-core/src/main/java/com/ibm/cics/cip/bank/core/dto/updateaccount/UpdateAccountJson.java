/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.core.dto.updateaccount;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.ibm.cics.cip.bank.core.config.JacksonConfig;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * Outer <em>envelope</em> wire DTO for the frozen z/OS Connect
 * <em>update-account</em> ({@code updacc}) contract (feature F-019). It wraps
 * the inner commarea payload {@link UpdaccJson} under the single top-level
 * envelope key {@code UpdAcc}, so the serialised JSON is
 * {@code {"UpdAcc": { ... }}} on both request and response &mdash; byte-for-byte
 * identical to the legacy z/OS Connect {@code CSaccupd} service (confirmed
 * against {@code swagger.json}, {@code CSaccupdRequest.json} and
 * {@code CSaccupdResponse.json}).
 *
 * <p>This envelope is consumed by {@code UpdateAccountController} (HTTP
 * {@code PUT}) and maps onto {@code AccountService}, whose behaviour is the
 * authoritative {@code UPDACC.cbl} program (F-012 RESTRICTED update: only the
 * account type, interest rate, and overdraft limit change; balances are never
 * mutated and no PROCTRAN record is written).</p>
 *
 * <h2>Wire-name strategy</h2>
 * <p>The class is annotated
 * {@code @JsonNaming(}{@link JacksonConfig.EnvelopeNamingStrategy}{@code .class)}
 * to carry the frozen z/OS Connect envelope naming behaviour forward into the
 * pure-Java {@code bank-core} module. The single field additionally declares an
 * explicit {@link JsonProperty}; an explicit {@code @JsonProperty} always
 * overrides the {@code substring(3)} naming strategy, so the wire key is pinned
 * verbatim to {@code UpdAcc} (capital {@code U}, capital {@code A}) regardless
 * of the Java field name. That explicit annotation is the contract guarantee.</p>
 *
 * <p><strong>Pure carrier.</strong> This class only stores and returns the
 * nested payload: it performs no validation, no zero-padding, no date or number
 * formatting, no {@code BigDecimal} scale normalisation, and applies no defaults
 * beyond the no-argument constructor Jackson needs. The Form&rarr;{@link
 * UpdaccJson}&rarr;{@code UpdateAccountJson} mapping is the responsibility of the
 * service/controller mapper, not of this DTO.</p>
 *
 * @see UpdaccJson
 * @see JacksonConfig.EnvelopeNamingStrategy
 */
@JsonNaming(JacksonConfig.EnvelopeNamingStrategy.class)
public class UpdateAccountJson
{

	/**
	 * The nested update-account commarea payload. The explicit
	 * {@code @JsonProperty("UpdAcc")} pins the contract-critical top-level
	 * envelope key verbatim, overriding the class-level naming strategy.
	 *
	 * <p><strong>Validation (F-021).</strong> {@link NotNull @NotNull} rejects
	 * both a missing nested payload (an empty {@code {}} body, where Jackson
	 * leaves this field {@code null} because the no-argument constructor performs
	 * no eager initialisation) and an explicit {@code {"UpdAcc": null}} body with
	 * HTTP&nbsp;{@code 400} (via {@code MethodArgumentNotValidException} &rarr;
	 * {@code GlobalExceptionHandler}) BEFORE the controller dereferences the
	 * payload, eliminating the {@code NullPointerException}&rarr;{@code 500} path
	 * on {@code request.getUpdAcc().getCommCustno()}. {@link Valid @Valid}
	 * cascades Bean Validation into the inner {@link UpdaccJson} so that
	 * over-width / over-scale structural violations also surface as {@code 400}.
	 * These are STRUCTURAL/FORMAT checks only; the {@code UPDACC} business
	 * outcome (account-not-found, restricted-field rules) remains a COBOL
	 * fail-code envelope at HTTP&nbsp;{@code 200}, never converted to
	 * {@code 400}. The no-argument constructor deliberately leaves the field
	 * {@code null} (no eager initialisation) so that an empty body is rejected
	 * rather than silently degrading.</p>
	 */
	@JsonProperty("UpdAcc")
	@NotNull
	@Valid
	private UpdaccJson updAcc;

	/**
	 * Default constructor required for Jackson deserialisation.
	 */
	public UpdateAccountJson()
	{
		// No initialisation required; Jackson populates the field on deserialise.
		// The field is intentionally left null so that an empty {} or explicit
		// {"UpdAcc":null} body fails the @NotNull guard with HTTP 400 (F-021)
		// rather than reaching the controller and risking a NullPointerException.
	}

	/**
	 * Constructs the envelope around an already-populated commarea payload.
	 *
	 * @param updAcc the nested update-account commarea payload to wrap
	 */
	public UpdateAccountJson(UpdaccJson updAcc)
	{
		this.updAcc = updAcc;
	}

	/**
	 * Returns the nested update-account commarea payload.
	 *
	 * @return the nested commarea payload, or {@code null} if unset
	 */
	public UpdaccJson getUpdAcc()
	{
		return updAcc;
	}

	/**
	 * Sets the nested update-account commarea payload.
	 *
	 * @param updAcc the nested commarea payload to wrap
	 */
	public void setUpdAcc(UpdaccJson updAcc)
	{
		this.updAcc = updAcc;
	}

	/**
	 * Returns a simple diagnostic representation of this envelope.
	 *
	 * @return a string of the form {@code UpdateAccountJson [UpdAcc=...]}
	 */
	@Override
	public String toString()
	{
		return "UpdateAccountJson [UpdAcc=" + updAcc + "]";
	}

}
