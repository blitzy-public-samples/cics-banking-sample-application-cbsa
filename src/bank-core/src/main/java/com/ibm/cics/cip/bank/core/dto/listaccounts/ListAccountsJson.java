/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.core.dto.listaccounts;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.ibm.cics.cip.bank.core.config.JacksonConfig;

/**
 * Outer transport envelope for the frozen z/OS Connect <em>list-customer-accounts</em>
 * ({@code inqacccz}) contract. This thin wrapper isolates the JSON envelope from
 * the inner business payload, nesting the sibling {@link InqAccczJson} under the
 * single, verbatim envelope key {@code "InqAccZ"} so the serialised shape matches
 * the frozen z/OS Connect schema byte-for-byte (feature F-019).
 *
 * <p><strong>Role.</strong> This is the response envelope returned by
 * {@code InquireCustomerAccountsController} (GET {@code /inqacccz/list/{custno}})
 * after {@code AccountService} performs the {@code INQACCCU}/{@code INQACCCZ}
 * "list customer accounts" operation (F-010). The nested {@link InqAccczJson}
 * carries the customer number and the customer's accounts (capped at twenty,
 * F-010); this class performs no logic of its own.</p>
 *
 * <h2>Wire-name strategy (why the explicit {@code @JsonProperty} is mandatory)</h2>
 * <p>The class is annotated {@code @JsonNaming(}{@link
 * JacksonConfig.EnvelopeNamingStrategy}{@code .class)} to carry the frozen
 * envelope naming behaviour forward into {@code bank-core}. That strategy's
 * {@code convert(name)} strips the conventional 3-character member-name prefix
 * ({@code name.substring(3)}); applied to the implicit property name
 * {@code inqAcccz} it would emit {@code Acccz} rather than the required
 * {@code InqAccZ}. The single field therefore declares an explicit
 * {@link JsonProperty}{@code ("InqAccZ")}, which always overrides the naming
 * strategy and pins the wire key verbatim. The internal Java field and accessor
 * names are consequently free and do not affect the contract.</p>
 *
 * <h2>Naming history</h2>
 * <p>Ported from the legacy z/OS Connect interface-module class
 * {@code ListAccJson} and renamed to {@code ListAccountsJson} to match the new
 * module's descriptive-outer-name convention (consistent with sibling
 * {@code dto/deleteaccount/DeleteAccountJson} and the controller
 * {@code InquireCustomerAccountsController}). The renaming is purely internal;
 * the wire key remains {@code InqAccZ} via the explicit {@code @JsonProperty}.</p>
 *
 * <h2>Carrier only</h2>
 * <p>A plain, mutable wire DTO: no Spring stereotype, no business logic and no
 * persistence. {@link JacksonConfig} is referenced solely as a class literal
 * inside {@code @JsonNaming}. It holds no money, date or identifier fields &mdash;
 * those all live inside {@link InqAccczJson} and its {@code AccountDetails} rows.</p>
 *
 * @see InqAccczJson
 * @see JacksonConfig.EnvelopeNamingStrategy
 */
@JsonNaming(JacksonConfig.EnvelopeNamingStrategy.class)
public class ListAccountsJson
{

	/**
	 * The inner commarea payload nested under the frozen envelope key
	 * {@code InqAccZ}. The explicit {@link JsonProperty}{@code ("InqAccZ")}
	 * overrides the class-level {@code substring(3)} naming strategy, pinning the
	 * single envelope field to its verbatim z/OS Connect wire name so the outer
	 * document serialises as <code>{"InqAccZ":{ &hellip; }}</code> (F-019).
	 */
	@JsonProperty("InqAccZ")
	private InqAccczJson inqAcccz;

	/**
	 * Default constructor required by Jackson for deserialisation.
	 */
	public ListAccountsJson()
	{
		super();
	}

	/**
	 * All-arguments constructor wrapping the inner payload in the envelope.
	 *
	 * @param inqAcccz the inner list-customer-accounts payload to nest under the
	 *                 {@code InqAccZ} envelope key
	 */
	public ListAccountsJson(InqAccczJson inqAcccz)
	{
		this.inqAcccz = inqAcccz;
	}

	/**
	 * Returns the inner list-customer-accounts payload carried by this envelope.
	 *
	 * @return the nested payload ({@code InqAccZ})
	 */
	public InqAccczJson getInqAcccz()
	{
		return inqAcccz;
	}

	/**
	 * Sets the inner list-customer-accounts payload carried by this envelope.
	 *
	 * @param inqAcccz the nested payload to wrap ({@code InqAccZ})
	 */
	public void setInqAcccz(InqAccczJson inqAcccz)
	{
		this.inqAcccz = inqAcccz;
	}

	/**
	 * Returns a diagnostic representation of this envelope. Not part of the wire
	 * contract.
	 *
	 * @return a string representation listing the nested payload
	 */
	@Override
	public String toString()
	{
		return "ListAccountsJson [InqAccZ=" + inqAcccz + "]";
	}
}
