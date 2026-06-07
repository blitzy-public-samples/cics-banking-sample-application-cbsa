/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.core.dto.createaccount;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.ibm.cics.cip.bank.core.config.JacksonConfig;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * Outer JSON <em>envelope</em> wire DTO for the frozen z/OS Connect
 * <em>create-account</em> ({@code creacc}, CREATE&nbsp;ACCOUNT) REST contract
 * (feature F-019).
 *
 * <p><strong>Role.</strong> This is a thin transport wrapper that isolates the
 * outer JSON envelope from the inner business payload. It holds a single field
 * of type {@link CreaccJson} (the sibling inner DTO in this same package) mapped
 * to the frozen envelope key {@code "CreAcc"}, so the full serialised document
 * is {@code {"CreAcc": { &hellip; }}}. It is both the request envelope consumed
 * by {@code CreateAccountController} (HTTP {@code POST /creacc/insert}) and the
 * response envelope it produces, mapping onto {@code AccountService}, whose
 * behaviour is the authoritative {@code CREACC.cbl} program (its ordered
 * five-step create with fail codes {@code '1'} customer-not-found, {@code '8'}
 * max-ten-accounts and {@code 'A'} invalid-account-type). The inner payload
 * carries the create-account commarea in both directions; this envelope simply
 * nests it and performs no logic of its own (behavioural parity over
 * enhancement).</p>
 *
 * <h2>The outer key is {@code "CreAcc"} &mdash; pinned verbatim</h2>
 * <p>Both authoritative wire sources agree: the frozen request schema
 * {@code src/zosconnect_artefacts/apis/creacc/services/CSacccre/schemas/CSacccreRequest.json}
 * and the byte-for-byte-identical response schema {@code CSacccreResponse.json}
 * each define a single top-level property {@code CreAcc}, and the preserved
 * Customer-Services interface-module consumer (its legacy {@code CreateAccountJson})
 * hard-codes the same {@code CreAcc} envelope key. The wire key is therefore pinned
 * verbatim to {@code CreAcc} (capital&nbsp;{@code C}, capital&nbsp;{@code A}) via
 * the explicit {@link JsonProperty} on the single field, and is never uppercased
 * or renamed.</p>
 *
 * <h2>Wire-name strategy</h2>
 * <p>The class is annotated
 * {@code @JsonNaming(}{@link JacksonConfig.EnvelopeNamingStrategy}{@code .class)}
 * to carry the frozen z/OS Connect envelope naming behaviour forward into the
 * pure-Java {@code bank-core} module, mirroring how the legacy source class
 * declared its own per-class envelope naming strategy and matching the
 * {@code accountenquiry} sibling. The strategy strips the conventional
 * 3-character member-name prefix via {@code substring(3)}. The single field
 * additionally declares an explicit {@link JsonProperty}; an explicit
 * {@code @JsonProperty} always overrides the naming strategy, so the wire key is
 * pinned verbatim to {@code CreAcc} regardless of the Java field name or the
 * {@code substring(3)} strategy. That explicit annotation is the contract
 * guarantee; the {@code @JsonNaming} is present only to mirror the source class
 * shape exactly.</p>
 *
 * <h2>Backward-compatibility constraint</h2>
 * <p>The preserved consumer deserialises with a default {@code new ObjectMapper()}
 * ({@code FAIL_ON_UNKNOWN_PROPERTIES = true}; neither the legacy envelope nor its
 * inner payload declares {@code @JsonIgnoreProperties(ignoreUnknown=true)}) &mdash;
 * proven in {@code WebController.processCreateAcc}, which round-trips the response
 * via {@code new ObjectMapper().readValue(responseBody, CreateAccountJson.class)}.
 * Consequently this envelope MUST serialise to EXACTLY ONE top-level key
 * ({@code CreAcc}) and never emit any additional top-level field (no metadata,
 * status, or helper field at this level); the inner payload obeys the same rule,
 * enforced in {@link CreaccJson}. Serialising a populated instance therefore
 * yields exactly {@code {"CreAcc":{...}}}, which the re-pointed interface module
 * reads with zero client change.</p>
 *
 * <p><strong>Carrier only.</strong> This is a plain, mutable data holder: no
 * Spring stereotype, no persistence mapping, and no business logic. The legacy
 * client-side display helper ({@code toPrettyString()}) that formatted fields for
 * the Customer-Services UI is deliberately dropped &mdash; it depended on a
 * legacy presentation-formatting utility that is not ported into
 * {@code bank-core}, and display formatting is a presentation concern owned by
 * the preserved front end, not by this server-side wire DTO. Likewise the legacy
 * numeric display-format constant is dropped because money is modelled as
 * {@link java.math.BigDecimal} on the inner payload, never as a formatted
 * inexact primitive. All money, date, and identifier concerns live inside the
 * nested {@link CreaccJson}.</p>
 *
 * @see CreaccJson
 * @see CreateAccountForm
 * @see JacksonConfig.EnvelopeNamingStrategy
 */
@JsonNaming(JacksonConfig.EnvelopeNamingStrategy.class)
public class CreateAccountJson
{

	/**
	 * Nested create-account commarea payload, serialised under the verbatim
	 * envelope key {@code "CreAcc"}. The explicit {@link JsonProperty} annotation
	 * pins the contract-critical top-level envelope key, overriding the
	 * class-level {@code substring(3)} naming strategy.
	 *
	 * <p><strong>Validation (F-021).</strong> {@link NotNull @NotNull} rejects an
	 * explicit {@code {"CreAcc": null}} request body with HTTP&nbsp;{@code 400}
	 * (via {@code MethodArgumentNotValidException} &rarr;
	 * {@code GlobalExceptionHandler}) BEFORE the controller dereferences the
	 * payload, eliminating the {@code NullPointerException}&rarr;{@code 500} path.
	 * {@link Valid @Valid} cascades Bean Validation into the inner
	 * {@link CreaccJson} so that over-width / over-scale structural violations
	 * also surface as {@code 400}. These are STRUCTURAL/FORMAT checks only;
	 * business rules (customer-not-found {@code '1'}, max-ten-accounts {@code '8'},
	 * invalid-account-type {@code 'A'}) remain COBOL fail-code envelopes at
	 * HTTP&nbsp;{@code 200}, never converted to {@code 400}.</p>
	 */
	@JsonProperty("CreAcc")
	@NotNull
	@Valid
	private CreaccJson creAcc;

	/**
	 * No-argument constructor required by Jackson for deserialisation. Eagerly
	 * instantiates the nested {@link CreaccJson} payload so that
	 * {@link #getCreAcc()} is never {@code null} on a freshly-constructed
	 * envelope, matching the {@code accountenquiry} sibling and the legacy source
	 * class shape.
	 */
	public CreateAccountJson()
	{
		this.creAcc = new CreaccJson();
	}

	/**
	 * Convenience constructor that builds a request-shaped envelope from a
	 * validated {@link CreateAccountForm}, mirroring the legacy customer-services
	 * analog (retyped to the AAP-mandated wire types). It populates the nested
	 * {@link CreaccJson} from the four client-supplied inputs through the inner
	 * payload's four-argument constructor, which applies the COBOL fixed-width
	 * formatting rules (account type space-padded to width&nbsp;8, customer number
	 * left-zero-padded to width&nbsp;10).
	 *
	 * <p>The account type is read as {@code getAccountType().name()}; because
	 * {@code AccountType} is a plain enum, {@code name()} yields the canonical
	 * constant text ({@code "ISA"}, {@code "CURRENT"}, &hellip;). The form's
	 * {@code accountType} is {@code @NotNull}, so a direct call is safe (the
	 * legacy analog likewise assumed a non-{@code null} value). The overdraft
	 * limit is a boxed {@link Integer} and the interest rate a
	 * {@link java.math.BigDecimal} &mdash; never a floating-point type &mdash; in
	 * accordance with the binding money rule.</p>
	 *
	 * @param createAccForm the validated create-account input form; must not be
	 *                       {@code null} and must carry a non-{@code null}
	 *                       account type
	 */
	public CreateAccountJson(CreateAccountForm createAccForm)
	{
		this.creAcc = new CreaccJson(createAccForm.getAccountType().name(),
				createAccForm.getCustNumber(),
				createAccForm.getOverdraftLimit(),
				createAccForm.getInterestRate());
	}

	/**
	 * Convenience constructor that wraps an already-populated inner payload in the
	 * outer envelope. This is the response-path entry point used by
	 * {@code CreateAccountController}, which builds a {@link CreaccJson} response
	 * commarea (echoing the persisted account or the COBOL fail code) and nests it
	 * for serialisation. Performs no copying or transformation &mdash; the
	 * supplied payload becomes the envelope's nested object directly.
	 *
	 * @param creAcc the populated inner create-account commarea to wrap; may be
	 *               {@code null}, in which case the envelope serialises
	 *               {@code "CreAcc":null}
	 */
	public CreateAccountJson(CreaccJson creAcc)
	{
		this.creAcc = creAcc;
	}

	/**
	 * Returns the nested create-account commarea payload.
	 *
	 * @return the nested {@link CreaccJson} payload (wire key {@code CreAcc});
	 *         never {@code null} on an envelope built with the no-argument
	 *         constructor
	 */
	public CreaccJson getCreAcc()
	{
		return creAcc;
	}

	/**
	 * Sets the nested create-account commarea payload.
	 *
	 * @param creAccIn the nested {@link CreaccJson} payload to wrap
	 */
	public void setCreAcc(CreaccJson creAccIn)
	{
		this.creAcc = creAccIn;
	}

	/**
	 * Returns a diagnostic representation of this envelope showing the nested
	 * payload. Delegates to the inner {@link CreaccJson#toString()} and pulls in
	 * no external dependencies.
	 *
	 * @return a string of the form {@code CreateAccountJson [creAcc=...]}
	 */
	@Override
	public String toString()
	{
		return "CreateAccountJson [creAcc=" + creAcc + "]";
	}
}
