/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.core.dto.payment;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.ibm.cics.cip.bank.core.config.JacksonConfig;

/**
 * Top-level {@code {"PAYDBCR":{...}}} wire envelope for the frozen z/OS Connect
 * <em>make-payment</em> (debit/credit) endpoint (HTTP {@code PUT
 * /makepayment/dbcr}, feature&nbsp;F-019). This is the outermost wire object of
 * the single endpoint consumed by the Payment Interface UI, and it is a faithful
 * 1:1 port of the legacy Payment-Interface module's {@code PaymentInterfaceJson}
 * (the legacy "Interface" suffix is dropped here, matching the new
 * {@code payment} package and the {@code PaymentController} / {@code PaymentService}
 * naming).
 *
 * <h2>Why this wrapper exists</h2>
 * <p>The frozen contract is <strong>not flat</strong>: the {@code Pay} service's
 * {@code PayRequest.json} and {@code PayResponse.json} schemas are
 * <em>identical</em> and each declares a top-level object whose <em>only</em>
 * property is {@code PAYDBCR} (an object holding all the debit/credit fields).
 * This class materialises that envelope exactly &mdash; its single
 * {@link #payDbCr} field is bound to the literal wire key {@code "PAYDBCR"} so
 * that the serialised JSON is {@code {"PAYDBCR":{ ... }}}. Because request and
 * response share the same shape, <strong>one wrapper serves both</strong> the
 * {@code @RequestBody} and the {@code @ResponseBody} directions; no separate
 * response wrapper is needed.</p>
 *
 * <h2>Wire contract is frozen and reproduced verbatim</h2>
 * <p>The class is annotated {@link JsonNaming @JsonNaming} with
 * {@link JacksonConfig.EnvelopeNamingStrategy} (the {@code substring(3)} envelope
 * strategy) for consistency with every other {@code core/dto} envelope. The
 * explicit {@link JsonProperty @JsonProperty}{@code ("PAYDBCR")} on the field
 * always wins over the naming strategy and pins the verbatim wire key. The
 * accessors are deliberately named {@link #getPAYDBCR()} / {@link #setPAYDBCR(DbcrJson)}
 * (upper-case after {@code get}/{@code set}): this both honours the legacy
 * contract and keeps the strategy in agreement, since
 * {@code "getPAYDBCR".substring(3)} is itself {@code "PAYDBCR"}. Declaring the
 * same name on the field and on its standard getter/setter merges them into a
 * single Jackson property, avoiding any {@code substring(3)} phantom-property
 * issue.</p>
 *
 * <h2>Responsibility boundary</h2>
 * <p>This is a plain, mutable POJO with no business logic and no Spring
 * stereotype: it is never injected and is referenced by Jackson only. The
 * debit/credit posting, the facility-type-496 channel restrictions, the
 * overdraft / insufficient-funds checks, the signed-amount convention, and the
 * population of the success / fail-code fields are all the responsibility of the
 * {@code PaymentService} ({@code DBCRFUN} port); the substantive field set and
 * money conversion live in {@link DbcrJson}, and the calling-channel data in
 * {@code OriginJson}. This wrapper only carries the {@code PAYDBCR} payload
 * across the wire. Consistent with the binding money rule, no binary
 * fractional numeric type (indeed no numeric field at all) appears anywhere in
 * this class.</p>
 *
 * @see DbcrJson
 * @see TransferForm
 * @see JacksonConfig.EnvelopeNamingStrategy
 */
@JsonNaming(JacksonConfig.EnvelopeNamingStrategy.class)
public class PaymentJson
{

	/**
	 * The single nested debit/credit payload, bound to the frozen wire key
	 * {@code "PAYDBCR"}. The explicit {@link JsonProperty @JsonProperty} pins the
	 * verbatim contract key regardless of the class-level naming strategy. Left
	 * {@code null} by the no-arg constructor (Jackson populates it via
	 * {@link #setPAYDBCR(DbcrJson)} on deserialisation); built from the inbound
	 * form by the {@link #PaymentJson(TransferForm)} convenience constructor.
	 */
	@JsonProperty("PAYDBCR")
	private DbcrJson payDbCr;

	/**
	 * No-argument constructor required for Jackson deserialisation of the
	 * {@code @RequestBody}. Leaves {@link #payDbCr} {@code null} until it is
	 * populated through {@link #setPAYDBCR(DbcrJson)}.
	 */
	public PaymentJson()
	{
		// No initialisation required; Jackson populates payDbCr via setPAYDBCR.
	}

	/**
	 * Convenience constructor that builds the {@code PAYDBCR} payload from an
	 * inbound {@link TransferForm}, mirroring the legacy
	 * {@code PaymentInterfaceJson(TransferForm)}. Useful for building requests
	 * (and in tests). The account-number padding, the debit/credit sign
	 * convention, and the scale-2 money normalisation are all performed inside
	 * {@link DbcrJson#DbcrJson(TransferForm)}; this wrapper merely nests the
	 * resulting payload under the {@code PAYDBCR} envelope key.
	 *
	 * @param transferForm the inbound payment form supplying the account number,
	 *                      debit/credit flag, amount, and originating organisation
	 */
	public PaymentJson(TransferForm transferForm)
	{
		payDbCr = new DbcrJson(transferForm);
	}

	/**
	 * Returns the nested debit/credit payload carried under the {@code PAYDBCR}
	 * envelope key.
	 *
	 * @return the nested {@link DbcrJson} payload, or {@code null} if not set
	 */
	public DbcrJson getPAYDBCR()
	{
		return payDbCr;
	}

	/**
	 * Sets the nested debit/credit payload carried under the {@code PAYDBCR}
	 * envelope key.
	 *
	 * @param payDbCrIn the nested {@link DbcrJson} payload
	 */
	public void setPAYDBCR(DbcrJson payDbCrIn)
	{
		this.payDbCr = payDbCrIn;
	}

	/**
	 * Renders the envelope by delegating to the nested payload's
	 * {@code toString()}. A defensive {@code null} guard prints {@code "null"}
	 * for an unpopulated payload (the legacy implementation delegated
	 * unconditionally and would have thrown on a {@code null} payload).
	 *
	 * @return a human-readable representation of this envelope
	 */
	@Override
	public String toString()
	{
		return "PaymentJson [PAYDBCR=" + (payDbCr == null ? "null" : payDbCr.toString()) + "]";
	}
}
