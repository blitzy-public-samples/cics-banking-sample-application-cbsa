/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.core.dto.listaccounts;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.ibm.cics.cip.bank.core.config.JacksonConfig;

import jakarta.validation.constraints.Size;

/**
 * Inner commarea payload for the frozen z/OS Connect <em>list-customer-accounts</em>
 * ({@code inqacccz}) contract. It is the nested object wrapped by the sibling
 * {@code ListAccountsJson} envelope under the {@code InqAccZ} key, reproducing
 * the legacy {@code INQACCCZ}/{@code INQACCCU} commarea (copybooks
 * {@code INQACCCZ.cpy} / {@code INQACCCU.cpy}) so the serialised JSON matches the
 * frozen z/OS Connect schema verbatim (feature F-019).
 *
 * <p><strong>Single class, request and response.</strong> Exactly as the legacy
 * interface-module class did, this single DTO serves both directions of the
 * contract. On the request the customer number travels as the
 * {@code /inqacccz/list/{custno}} path parameter, while the response echoes the
 * customer number alongside the customer's accounts. All six fields are
 * therefore declared so the class covers the response superset (the frozen
 * {@code getCScustacc_response_200} definition lists all six).</p>
 *
 * <p><strong>List of accounts (F-010).</strong> {@link #accountDetails} holds the
 * customer's account rows, modelling the COBOL
 * {@code ACCOUNT-DETAILS OCCURS 1 TO 20} group. The list is capped at twenty
 * entries (swagger {@code maxItems: 20}); the cap and the per-row population are
 * enforced by the service/mapper ({@code AccountService}, per
 * {@code INQACCCU.cbl} F-010), not by this carrier DTO. Each element is a
 * same-package {@link AccountDetails}.</p>
 *
 * <h2>Wire-name strategy</h2>
 * <p>The class is annotated {@code @JsonNaming(}{@link
 * JacksonConfig.EnvelopeNamingStrategy}{@code .class)} to carry the frozen
 * envelope naming behaviour forward into {@code bank-core}. Every field
 * additionally declares an explicit {@link JsonProperty}; an explicit
 * {@code @JsonProperty} always overrides the {@code substring(3)} naming
 * strategy, so each wire name is pinned verbatim ({@code CommFailCode},
 * {@code CustomerNumber}, {@code AccountDetails}, {@code CommPcbPointer},
 * {@code CustomerFound}, {@code CommSuccess}) regardless of the Java field
 * name.</p>
 *
 * <h2>Top-level customer number is a JSON integer (frozen schema, F-019)</h2>
 * <p>{@link #customerNumber} ({@code CustomerNumber}) is serialised as a JSON
 * {@code integer} to match the frozen z/OS Connect swagger verbatim (which
 * types the top-level {@code CustomerNumber} as {@code integer}) and to match
 * the consuming interface module. It is typed {@link Long} because a ten-digit
 * customer number ({@code 9(10)}) can exceed {@link Integer#MAX_VALUE}; this
 * mirrors the passing {@code InqaccJson.inqaccCustno} ({@code Long}) precedent.
 * The AAP &sect;0.6 fixed-width identifier rule applies to identifiers the
 * contract types as {@code string} (e.g. {@code CommCustno}/{@code CommScode}
 * inside {@link AccountDetails}), not to this integer-typed field.</p>
 *
 * <h2>Fail code as a single-character String</h2>
 * <p>{@link #commFailCode} ({@code CommFailCode}) carries the COBOL
 * single-character fail code as a {@code String} ({@code X(1)}), correcting the
 * legacy interface client which wrongly typed it {@code int}. The swagger types
 * it {@code string} ({@code maxLength 1}).</p>
 *
 * <h2>PCB pointer retained</h2>
 * <p>{@link #commPcbPointer} ({@code CommPcbPointer}) is retained as a
 * {@code String} placeholder because the frozen {@code inqacccz} swagger
 * includes it as {@code string} ({@code maxLength 4}) in both the request and
 * response definitions (verified). The AAP &sect;0.6 PCB-drop rule targets the
 * JPA <em>entities</em>, not this frozen wire contract.</p>
 *
 * <h2>Intentionally omitted field</h2>
 * <p>The COBOL {@code NUMBER-OF-ACCOUNTS} ({@code PIC S9(8) BINARY}) OCCURS
 * DEPENDING-ON control counter is intentionally <strong>omitted</strong>: it is
 * absent from the frozen swagger (neither request nor response) and from the
 * legacy interface Java client. The size of the {@link #accountDetails} list
 * implicitly conveys the count. There is likewise no per-row sort code
 * ({@code COMM-SCODE}) at this payload level (that field exists only inside
 * {@link AccountDetails}, where it too is dropped).</p>
 *
 * <h2>Carrier only</h2>
 * <p>This is a plain, mutable wire DTO with no business logic, no Spring
 * stereotype and no persistence. It carries no money or date fields at this
 * level &mdash; those live inside {@link AccountDetails}. Zero-padding of the
 * customer number and the twenty-account cap are performed by the
 * service/mapper that populates it, keeping the wire output byte-compatible
 * with the preserved front ends.</p>
 */
@JsonNaming(JacksonConfig.EnvelopeNamingStrategy.class)
public class InqAccczJson
{

	/**
	 * Single-character fail code, {@code X(1)}; carries the COBOL
	 * {@code COMM-FAIL-CODE}. Bound verbatim to the wire name
	 * {@code CommFailCode}. Typed {@code String} (the legacy interface client
	 * wrongly used {@code int}).
	 */
	@JsonProperty("CommFailCode")
	@Size(max = 1)
	private String commFailCode;

	/**
	 * Owning customer number, COBOL {@code 9(10)}; serialised as a JSON integer
	 * to match the frozen {@code inqacccz} schema ({@code CustomerNumber:
	 * integer}, F-019) and the consuming interface module. Typed {@link Long}
	 * because a ten-digit value can exceed {@link Integer#MAX_VALUE} (mirrors
	 * the {@code InqaccJson.inqaccCustno} precedent).
	 */
	@JsonProperty("CustomerNumber")
	private Long customerNumber;

	/**
	 * The customer's account rows, modelling the COBOL
	 * {@code ACCOUNT-DETAILS OCCURS 1 TO 20}; capped at twenty entries (F-010 /
	 * swagger {@code maxItems: 20}) by the populating service/mapper. Each
	 * element is a same-package {@link AccountDetails}.
	 */
	@JsonProperty("AccountDetails")
	@Size(max = 20)
	private List<AccountDetails> accountDetails;

	/**
	 * PCB pointer placeholder, {@code X(4)}; retained because the frozen
	 * {@code inqacccz} swagger includes it ({@code string}, {@code maxLength 4}).
	 * Bound verbatim to the wire name {@code CommPcbPointer}.
	 */
	@JsonProperty("CommPcbPointer")
	@Size(max = 4)
	private String commPcbPointer;

	/**
	 * Customer-found flag, {@code X(1)} (e.g. {@code "Y"} / {@code "N"}); carries
	 * the COBOL {@code CUSTOMER-FOUND}. Bound verbatim to the wire name
	 * {@code CustomerFound}.
	 */
	@JsonProperty("CustomerFound")
	@Size(max = 1)
	private String customerFound;

	/**
	 * Success flag, {@code X(1)}; carries the COBOL {@code COMM-SUCCESS}. Bound
	 * verbatim to the wire name {@code CommSuccess}.
	 */
	@JsonProperty("CommSuccess")
	@Size(max = 1)
	private String commSuccess;

	/**
	 * Default constructor for Jackson (de)serialisation.
	 */
	public InqAccczJson()
	{
		super();
	}

	/**
	 * All-arguments constructor taking the six fields in declaration order.
	 *
	 * @param commFailCode   single-character fail code ({@code CommFailCode})
	 * @param customerNumber owning customer number as a JSON integer
	 *                       ({@code CustomerNumber})
	 * @param accountDetails the customer's account rows, capped at twenty
	 *                       ({@code AccountDetails})
	 * @param commPcbPointer PCB pointer placeholder ({@code CommPcbPointer})
	 * @param customerFound  customer-found flag ({@code CustomerFound})
	 * @param commSuccess    success flag ({@code CommSuccess})
	 */
	public InqAccczJson(String commFailCode, Long customerNumber,
			List<AccountDetails> accountDetails, String commPcbPointer,
			String customerFound, String commSuccess)
	{
		this.commFailCode = commFailCode;
		this.customerNumber = customerNumber;
		this.accountDetails = accountDetails;
		this.commPcbPointer = commPcbPointer;
		this.customerFound = customerFound;
		this.commSuccess = commSuccess;
	}

	/**
	 * Returns the single-character fail code.
	 *
	 * @return the fail code ({@code CommFailCode})
	 */
	public String getCommFailCode()
	{
		return commFailCode;
	}

	/**
	 * Sets the single-character fail code.
	 *
	 * @param commFailCode the fail code ({@code CommFailCode})
	 */
	public void setCommFailCode(String commFailCode)
	{
		this.commFailCode = commFailCode;
	}

	/**
	 * Returns the owning customer number.
	 *
	 * @return the customer number ({@code CustomerNumber}; JSON integer)
	 */
	public Long getCustomerNumber()
	{
		return customerNumber;
	}

	/**
	 * Sets the owning customer number (serialised as a JSON integer).
	 *
	 * @param customerNumber the customer number ({@code CustomerNumber})
	 */
	public void setCustomerNumber(Long customerNumber)
	{
		this.customerNumber = customerNumber;
	}

	/**
	 * Returns the customer's account rows.
	 *
	 * @return the account rows ({@code AccountDetails}; up to twenty)
	 */
	public List<AccountDetails> getAccountDetails()
	{
		return accountDetails;
	}

	/**
	 * Sets the customer's account rows (expected capped at twenty by the
	 * populating service/mapper).
	 *
	 * @param accountDetails the account rows ({@code AccountDetails})
	 */
	public void setAccountDetails(List<AccountDetails> accountDetails)
	{
		this.accountDetails = accountDetails;
	}

	/**
	 * Returns the PCB pointer placeholder.
	 *
	 * @return the PCB pointer ({@code CommPcbPointer})
	 */
	public String getCommPcbPointer()
	{
		return commPcbPointer;
	}

	/**
	 * Sets the PCB pointer placeholder.
	 *
	 * @param commPcbPointer the PCB pointer ({@code CommPcbPointer})
	 */
	public void setCommPcbPointer(String commPcbPointer)
	{
		this.commPcbPointer = commPcbPointer;
	}

	/**
	 * Returns the customer-found flag.
	 *
	 * @return the customer-found flag ({@code CustomerFound})
	 */
	public String getCustomerFound()
	{
		return customerFound;
	}

	/**
	 * Sets the customer-found flag.
	 *
	 * @param customerFound the customer-found flag ({@code CustomerFound})
	 */
	public void setCustomerFound(String customerFound)
	{
		this.customerFound = customerFound;
	}

	/**
	 * Returns the success flag.
	 *
	 * @return the success flag ({@code CommSuccess})
	 */
	public String getCommSuccess()
	{
		return commSuccess;
	}

	/**
	 * Sets the success flag.
	 *
	 * @param commSuccess the success flag ({@code CommSuccess})
	 */
	public void setCommSuccess(String commSuccess)
	{
		this.commSuccess = commSuccess;
	}

	/**
	 * Returns a diagnostic representation listing all six fields. Not part of
	 * the wire contract.
	 *
	 * @return a string representation of this payload
	 */
	@Override
	public String toString()
	{
		return "InqAccczJson [CommFailCode=" + commFailCode
				+ ", CustomerNumber=" + customerNumber + ", AccountDetails="
				+ accountDetails + ", CommPcbPointer=" + commPcbPointer
				+ ", CustomerFound=" + customerFound + ", CommSuccess="
				+ commSuccess + "]";
	}
}
