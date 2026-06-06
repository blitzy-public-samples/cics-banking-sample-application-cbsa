/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.core.dto.payment;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Frozen z/OS Connect payment <em>origin</em> envelope ({@code OriginJson}),
 * reproduced field-for-field from the Payment-Interface module's class of the
 * same name (feature F-019). Nested inside {@link DbcrJson} under the
 * {@code CommOrigin} key.
 *
 * <p>This structure conveys the calling channel identity. On a request the
 * {@code bank-core} {@code PaymentController} derives two pieces of information
 * from it:</p>
 * <ul>
 *   <li>the <strong>facility type</strong> from {@code CommFaciltype} (the legacy
 *       value {@code "0496"} parses to the integer {@code 496}, which
 *       {@code PaymentService} uses to apply the payment-channel debit/credit
 *       restrictions reproduced from {@code DBCRFUN});</li>
 *   <li>the <strong>origin string</strong> from {@code CommApplid} concatenated
 *       with {@code CommUserid} (16 characters), of which the leading 14 form the
 *       PROCTRAN description for payment-channel movements (types {@code PDR} /
 *       {@code PCR}).</li>
 * </ul>
 *
 * <p>The remaining fields ({@code CommFacilityName}, {@code CommNetwrkId},
 * {@code Fill0}) are preserved verbatim with their legacy default values so the
 * envelope round-trips unchanged.</p>
 */
public class OriginJson
{

	/** Application id (first 8 characters of the origin). */
	@JsonProperty("CommApplid")
	private String commApplid;

	/** User id (second 8 characters of the origin). */
	@JsonProperty("CommUserid")
	private String commUserid;

	/** Facility name (preserved default). */
	@JsonProperty("CommFacilityName")
	private String commFacilityName = "        ";

	/** Network id (preserved default). */
	@JsonProperty("CommNetwrkId")
	private String commNetwrkId = "        ";

	/** Facility type; the legacy default {@code "0496"} denotes the payment channel. */
	@JsonProperty("CommFaciltype")
	private String commFaciltype = "0496";

	/** Filler (preserved default). */
	@JsonProperty("Fill0")
	private String fill0 = "    ";

	/**
	 * Default constructor for Jackson (de)serialisation.
	 */
	public OriginJson()
	{
		// Field defaults preserve the legacy envelope's initial values.
	}

	/**
	 * Returns the application id.
	 *
	 * @return the application id
	 */
	public String getCommApplid()
	{
		return commApplid;
	}

	/**
	 * Sets the application id.
	 *
	 * @param commApplidIn the application id
	 */
	public void setCommApplid(String commApplidIn)
	{
		commApplid = commApplidIn;
	}

	/**
	 * Returns the user id.
	 *
	 * @return the user id
	 */
	public String getCommUserid()
	{
		return commUserid;
	}

	/**
	 * Sets the user id.
	 *
	 * @param commUseridIn the user id
	 */
	public void setCommUserid(String commUseridIn)
	{
		commUserid = commUseridIn;
	}

	/**
	 * Returns the facility name.
	 *
	 * @return the facility name
	 */
	public String getCommFacilityName()
	{
		return commFacilityName;
	}

	/**
	 * Sets the facility name.
	 *
	 * @param commFacilityNameIn the facility name
	 */
	public void setCommFacilityName(String commFacilityNameIn)
	{
		commFacilityName = commFacilityNameIn;
	}

	/**
	 * Returns the network id.
	 *
	 * @return the network id
	 */
	public String getCommNetwrkId()
	{
		return commNetwrkId;
	}

	/**
	 * Sets the network id.
	 *
	 * @param commNetwrkIdIn the network id
	 */
	public void setCommNetwrkId(String commNetwrkIdIn)
	{
		commNetwrkId = commNetwrkIdIn;
	}

	/**
	 * Returns the facility type (e.g. {@code "0496"}).
	 *
	 * <p>The {@code @JsonProperty("CommFaciltype")} here is required: the getter
	 * name ({@code getCommFacilType}, capital {@code T}) does not follow from the
	 * field name ({@code commFaciltype}, lower-case {@code t}) by JavaBean
	 * convention, so without the explicit annotation Jackson would treat the
	 * field and the getter as two separate properties and emit <em>two</em> keys.
	 * Annotating the field, getter, and setter with the same name merges them
	 * into the single {@code CommFaciltype} key.</p>
	 *
	 * @return the facility type
	 */
	@JsonProperty("CommFaciltype")
	public String getCommFacilType()
	{
		return commFaciltype;
	}

	/**
	 * Sets the facility type.
	 *
	 * @param commFacilType the facility type
	 */
	@JsonProperty("CommFaciltype")
	public void setCommFacilType(String commFacilType)
	{
		commFaciltype = commFacilType;
	}

	/**
	 * Returns the filler.
	 *
	 * @return the filler
	 */
	public String getFill0()
	{
		return fill0;
	}

	/**
	 * Sets the filler.
	 *
	 * @param fill0In the filler
	 */
	public void setFill0(String fill0In)
	{
		fill0 = fill0In;
	}

}
