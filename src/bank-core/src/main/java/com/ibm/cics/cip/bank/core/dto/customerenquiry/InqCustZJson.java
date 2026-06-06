/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.core.dto.customerenquiry;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.ibm.cics.cip.bank.core.config.JacksonConfig;

/**
 * Inner commarea payload of the frozen z/OS Connect {@code inqcustz}
 * (INQUIRE&nbsp;CUSTOMER) contract (feature F-019). This DTO is the object nested
 * under the outer envelope key {@code INQCUSTZ} inside the wrapper
 * {@code CustomerEnquiryJson}; it carries the result of a customer enquiry back
 * to the preserved front ends and interface modules.
 *
 * <p><strong>This class IS the frozen contract surface.</strong> Its serialised
 * shape must match the z/OS Connect wire form VERBATIM so that the preserved
 * Customer-Services interface module &mdash; whose own legacy {@code InqCustZJson}
 * wire class deserialises with a default {@code new ObjectMapper()}
 * ({@code FAIL_ON_UNKNOWN_PROPERTIES = true}, with no
 * {@code @JsonIgnoreProperties}) &mdash; reads it with ZERO client change.
 * Serialising a populated instance therefore yields EXACTLY these eleven keys and
 * no others: {@code InqCustEye}, {@code InqCustScode}, {@code InqCustCustno},
 * {@code InqCustName}, {@code InqCustAddr}, {@code InqCustDob},
 * {@code InqCustCreditScore}, {@code InqCustCsReviewDt}, {@code InqCustInqSuccess},
 * {@code InqCustInqFailCd}, {@code InqCustPcbPointer} (the two nested objects
 * carry their own component keys). The field set is fixed by {@code INQCUST.cpy}
 * /{@code INQCUSTZ.cpy}, the frozen swagger
 * ({@code inqcustz/api-docs/swagger.json}, object
 * {@code getCScustenq_response_200.InqCustZ}) and the frozen response schema
 * ({@code CScustenq/schemas/CScustenqResponse.json}); all three agree exactly.</p>
 *
 * <p><strong>Field&nbsp;#8 wire name is {@code InqCustCsReviewDt}, NOT
 * {@code InqCustCsReviewDate}.</strong> Both authoritative sources &mdash; the
 * frozen swagger ({@code getCScustenq_response_200.InqCustZ.InqCustCsReviewDt})
 * and the legacy consumer field ({@code @JsonProperty("InqCustCsReviewDt")})
 * &mdash; agree on the abbreviated form. The Java accessor pair is named
 * {@code getInqCustCsReviewDate}/{@code setInqCustCsReviewDate} for ergonomics,
 * but the on-the-wire key is pinned by the explicit
 * {@code @JsonProperty("InqCustCsReviewDt")} on the field. Using
 * {@code InqCustCsReviewDate} as the wire key would break the frozen contract and
 * the backward-compatible round-trip.</p>
 *
 * <p><strong>Identifiers are zero-padded {@link String}s (&sect;0.6).</strong>
 * {@code InqCustScode} and {@code InqCustCustno} are modelled as {@link String}
 * and emitted left-zero-padded to their declared COBOL display widths (sort code
 * width&nbsp;6, e.g. {@code "987654"}; customer number width&nbsp;10, e.g.
 * {@code "0000000123"}), preserving the leading-zero semantics of the COBOL
 * {@code PIC X(6)}/{@code PIC 9(10)} fields. The frozen response swagger types the
 * customer number as {@code integer}, but the preserved consumer declares its
 * matching field as a primitive {@code int} and Jackson's default scalar coercion
 * converts a numeric {@code String} (such as {@code "987654"}) cleanly to that
 * {@code int}, so backward compatibility holds for realistic in-range customer
 * numbers while honouring the &sect;0.6 zero-pad rule. The padding itself is
 * applied by the populating {@code CustomerService}; this DTO simply carries the
 * already-formatted {@code String}. (Fallback, documented but NOT implemented
 * here: should the required contract integration test ever reveal consumer
 * breakage, the customer number could instead be emitted as a JSON integer via
 * {@code Long} to match the swagger {@code integer} typing &mdash; but the
 * PRIMARY, per &sect;0.6 and the assigned-folder spec, is the zero-padded
 * {@code String}.)</p>
 *
 * <p><strong>Credit score is an {@link Integer} (JSON number).</strong>
 * {@code InqCustCreditScore} ({@code INQCUST-CREDIT-SCORE PIC 999}, swagger
 * {@code integer} range 0&ndash;999) is a boxed {@link Integer} so it serialises
 * as a JSON number and is read directly by the legacy consumer's {@code int}
 * field. No validation bound is added &mdash; this is a response DTO, not a
 * persisted entity, so it carries no persistence or bean-validation
 * annotations.</p>
 *
 * <p><strong>Fail code is a {@link String} (highest backward-compat risk).</strong>
 * {@code InqCustInqFailCd} ({@code INQCUST-INQ-FAIL-CD PIC X}, swagger
 * {@code string} maxLength&nbsp;1) is a single character &mdash; typically a
 * blank/space on success and a code on failure. The PRIMARY type, per the swagger
 * {@code string(1)} typing and the assigned-folder spec, is {@link String}. The
 * legacy consumer, however, declares its matching field as a primitive
 * {@code int}; if a non-numeric or empty fail code is emitted as a {@code String},
 * the legacy {@code int} field may fail Jackson coercion. This is the principal
 * risk that the REQUIRED contract integration test must exercise on BOTH a
 * successful enquiry ({@code InqCustInqSuccess = "Y"}) AND a failed enquiry
 * ({@code InqCustInqSuccess = "N"} with a fail code); if that test reveals a
 * coercion failure, the reconciliation (for example emitting a numeric-digit fail
 * code such as {@code "0"} for the no-failure case, or coordinating the consumer's
 * tolerance) is performed in the service/test layer &mdash; the wire field name is
 * never silently changed.</p>
 *
 * <p><strong>Eye-catcher and PCB pointer are retained as wire fields.</strong>
 * Although &sect;0.6 drops the {@code CUST}-style eye-catcher from the JPA
 * <em>entities</em>, this DTO keeps {@code InqCustEye} for contract fidelity.
 * {@code InqCustPcbPointer} is likewise retained as a {@link String}: although
 * {@code INQCUST.cpy} types it {@code POINTER} (a runtime-only construct),
 * {@code INQCUSTZ.cpy} and the frozen swagger/schema flatten it to
 * {@code PIC X(4)}/{@code string} (maxLength&nbsp;4) &mdash; the contract-facing
 * form &mdash; and the legacy consumer declares it, so omitting it would change
 * the contract surface. The populating service may leave it blank; emitting it
 * (even as {@code ""}) is contract-safe.</p>
 *
 * <p><strong>Wire names are pinned per field.</strong> The class is annotated
 * {@link JsonNaming @JsonNaming} with {@link JacksonConfig.EnvelopeNamingStrategy}
 * for consistency with the other {@code core/dto} envelopes, but every field also
 * declares an explicit {@link JsonProperty @JsonProperty} with its verbatim wire
 * name. Because an explicit {@code @JsonProperty} always wins over the class-level
 * {@code substring(3)} naming strategy, the emitted keys are exactly the eleven
 * names above &mdash; the strategy can never produce an unexpected key.</p>
 *
 * <p><strong>No business logic.</strong> This is a plain, mutable wire DTO. The
 * INQUIRE&nbsp;CUSTOMER behaviour (for example the {@code 0000000000} sentinel
 * that picks a random customer and the {@code 9999999999} sentinel that resolves
 * the highest customer via the control-row {@code LAST-CUSTOMER-NUMBER} rather
 * than a {@code MAX()} scan, F-008) lives in {@code CustomerService}, never in
 * this object. The two nested component types {@code InqCustDob} and
 * {@code InqCustReviewDate} are same-package helpers.</p>
 *
 * @see JacksonConfig.EnvelopeNamingStrategy
 * @see InqCustDob
 * @see InqCustReviewDate
 */
@JsonNaming(JacksonConfig.EnvelopeNamingStrategy.class)
public class InqCustZJson
{

	/**
	 * Eye-catcher field. {@code INQCUST-EYE PIC X(4)} &rarr; {@link String}
	 * (frozen schema {@code type=string}, {@code maxLength=4}). Retained as a wire
	 * field for contract fidelity even though the eye-catcher is dropped from the
	 * JPA entities (&sect;0.6). Serialised verbatim as {@code InqCustEye}.
	 */
	@JsonProperty("InqCustEye")
	private String inqCustEye;

	/**
	 * Bank sort code. {@code INQCUST-SCODE PIC X(6)} &rarr; {@link String} (frozen
	 * schema {@code type=string}, {@code maxLength=6}). Emitted left-zero-padded to
	 * width&nbsp;6 (for example {@code "987654"}) per &sect;0.6; the padding is
	 * applied by the populating service. Serialised verbatim as
	 * {@code InqCustScode}.
	 */
	@JsonProperty("InqCustScode")
	private String inqCustScode;

	/**
	 * Customer number. {@code INQCUST-CUSTNO PIC 9(10)} &rarr; {@link String}
	 * (frozen response swagger {@code type=integer}, range
	 * {@code 0..9999999999}). Modelled as a {@link String} and emitted
	 * left-zero-padded to width&nbsp;10 (for example {@code "0000000123"}) per
	 * &sect;0.6, preserving the COBOL display-numeric leading zeros. The legacy
	 * consumer field is a primitive {@code int}; Jackson coerces a numeric
	 * {@code String} to {@code int} for realistic in-range values, so backward
	 * compatibility holds (the value also exceeds {@code int} range only at its
	 * theoretical maximum). Serialised verbatim as {@code InqCustCustno}.
	 */
	@JsonProperty("InqCustCustno")
	private String inqCustCustno;

	/**
	 * Customer name. {@code INQCUST-NAME PIC X(60)} &rarr; {@link String} (frozen
	 * schema {@code type=string}, {@code maxLength=60}). Serialised verbatim as
	 * {@code InqCustName}.
	 */
	@JsonProperty("InqCustName")
	private String inqCustName;

	/**
	 * Customer address. {@code INQCUST-ADDR PIC X(160)} &rarr; {@link String}
	 * (frozen schema {@code type=string}, {@code maxLength=160}). Note the wire key
	 * is the abbreviated {@code InqCustAddr} (matching the copybook field name and
	 * the swagger), while the Java accessor pair uses the fuller
	 * {@code getInqCustAddress}/{@code setInqCustAddress} for ergonomics; the
	 * explicit {@code @JsonProperty} pins the wire key. Serialised verbatim as
	 * {@code InqCustAddr}.
	 */
	@JsonProperty("InqCustAddr")
	private String inqCustAddress;

	/**
	 * Date-of-birth component object ({@code INQCUST-DOB} group: day, month,
	 * four-digit year). Modelled by the same-package {@link InqCustDob} helper,
	 * which carries the three integer components {@code InqCustDobDd},
	 * {@code InqCustDobMm} and {@code InqCustDobYyyy}. Serialised verbatim as the
	 * nested object {@code InqCustDob}.
	 */
	@JsonProperty("InqCustDob")
	private InqCustDob inqCustDob;

	/**
	 * Credit score. {@code INQCUST-CREDIT-SCORE PIC 999} &rarr; {@link Integer}
	 * (frozen schema {@code type=integer}, range 0&ndash;999). A boxed
	 * {@link Integer} serialises as a JSON number and is read directly by the
	 * legacy consumer's {@code int} field. Serialised verbatim as
	 * {@code InqCustCreditScore}.
	 */
	@JsonProperty("InqCustCreditScore")
	private Integer inqCustCreditScore;

	/**
	 * Credit-score review-date component object ({@code INQCUST-CS-REVIEW-DT}
	 * group: day, month, four-digit year). Modelled by the same-package
	 * {@link InqCustReviewDate} helper, which carries the three integer components
	 * {@code InqCustCsReviewDd}, {@code InqCustCsReviewMm} and
	 * {@code InqCustCsReviewYyyy}. <strong>The wire key is
	 * {@code InqCustCsReviewDt}</strong> (the abbreviated form agreed by both the
	 * swagger and the legacy consumer), NOT {@code InqCustCsReviewDate}; the Java
	 * accessor pair uses the fuller {@code getInqCustCsReviewDate}/
	 * {@code setInqCustCsReviewDate} for ergonomics and the explicit
	 * {@code @JsonProperty} pins the abbreviated wire key. Serialised verbatim as
	 * the nested object {@code InqCustCsReviewDt}.
	 */
	@JsonProperty("InqCustCsReviewDt")
	private InqCustReviewDate inqCustCsReviewDate;

	/**
	 * Single-character enquiry-success flag. {@code INQCUST-INQ-SUCCESS PIC X}
	 * &rarr; {@link String} (frozen schema {@code type=string},
	 * {@code maxLength=1}). Carries {@code 'Y'} or {@code 'N'}; the preserved
	 * consumer calls {@code getInqcustInqSuccess().equals("N")}, so this MUST be a
	 * non-{@code null} {@code String} on the wire when populated by the service.
	 * Serialised verbatim as {@code InqCustInqSuccess}.
	 */
	@JsonProperty("InqCustInqSuccess")
	private String inqCustInqSuccess;

	/**
	 * Single-character fail code. {@code INQCUST-INQ-FAIL-CD PIC X} &rarr;
	 * {@link String} (frozen schema {@code type=string}, {@code maxLength=1}).
	 * Typically blank/space on success and a code on failure. <strong>Highest
	 * backward-compatibility risk:</strong> the legacy consumer declares its
	 * matching field as a primitive {@code int}, so a non-numeric or empty fail
	 * code emitted as a {@code String} may fail Jackson coercion at the consumer.
	 * The REQUIRED contract integration test exercises this on both a successful
	 * and a failed enquiry; any reconciliation happens in the service/test layer,
	 * never by changing this wire field name. Serialised verbatim as
	 * {@code InqCustInqFailCd}.
	 */
	@JsonProperty("InqCustInqFailCd")
	private String inqCustInqFailCd;

	/**
	 * PCB pointer, retained for contract fidelity. {@code INQCUST-PCB-POINTER} is
	 * {@code POINTER} in {@code INQCUST.cpy} (runtime only) but {@code PIC X(4)} in
	 * {@code INQCUSTZ.cpy} and {@code string} ({@code maxLength=4}) in the frozen
	 * swagger/schema &mdash; the contract-facing form &mdash; so it is modelled as
	 * {@link String}. The populating service may leave it blank; emitting it (even
	 * as {@code ""}) is contract-safe, whereas omitting it would change the
	 * contract surface. Serialised verbatim as {@code InqCustPcbPointer}.
	 */
	@JsonProperty("InqCustPcbPointer")
	private String inqCustPcbPointer;

	/**
	 * No-argument constructor required by Jackson for deserialisation. Leaves all
	 * eleven fields {@code null} until populated by the setters or by
	 * deserialisation; the populating {@code CustomerService} is responsible for
	 * setting every field (including the zero-padded identifiers).
	 */
	public InqCustZJson()
	{
		super();
	}

	/**
	 * Returns the eye-catcher field.
	 *
	 * @return the eye-catcher (serialised as {@code InqCustEye})
	 */
	public String getInqCustEye()
	{
		return inqCustEye;
	}

	/**
	 * Sets the eye-catcher field.
	 *
	 * @param inqCustEyeIn the eye-catcher (serialised as {@code InqCustEye})
	 */
	public void setInqCustEye(String inqCustEyeIn)
	{
		inqCustEye = inqCustEyeIn;
	}

	/**
	 * Returns the bank sort code (left-zero-padded to width&nbsp;6).
	 *
	 * @return the sort code (serialised as {@code InqCustScode})
	 */
	public String getInqCustScode()
	{
		return inqCustScode;
	}

	/**
	 * Sets the bank sort code. The value is expected already left-zero-padded to
	 * width&nbsp;6 by the populating service (&sect;0.6).
	 *
	 * @param inqCustScodeIn the sort code (serialised as {@code InqCustScode})
	 */
	public void setInqCustScode(String inqCustScodeIn)
	{
		inqCustScode = inqCustScodeIn;
	}

	/**
	 * Returns the customer number (left-zero-padded to width&nbsp;10).
	 *
	 * @return the customer number (serialised as {@code InqCustCustno})
	 */
	public String getInqCustCustno()
	{
		return inqCustCustno;
	}

	/**
	 * Sets the customer number. The value is expected already left-zero-padded to
	 * width&nbsp;10 by the populating service (&sect;0.6).
	 *
	 * @param inqCustCustnoIn the customer number (serialised as
	 *                        {@code InqCustCustno})
	 */
	public void setInqCustCustno(String inqCustCustnoIn)
	{
		inqCustCustno = inqCustCustnoIn;
	}

	/**
	 * Returns the customer name.
	 *
	 * @return the name (serialised as {@code InqCustName})
	 */
	public String getInqCustName()
	{
		return inqCustName;
	}

	/**
	 * Sets the customer name.
	 *
	 * @param inqCustNameIn the name (serialised as {@code InqCustName})
	 */
	public void setInqCustName(String inqCustNameIn)
	{
		inqCustName = inqCustNameIn;
	}

	/**
	 * Returns the customer address.
	 *
	 * @return the address (serialised as {@code InqCustAddr})
	 */
	public String getInqCustAddress()
	{
		return inqCustAddress;
	}

	/**
	 * Sets the customer address.
	 *
	 * @param inqCustAddressIn the address (serialised as {@code InqCustAddr})
	 */
	public void setInqCustAddress(String inqCustAddressIn)
	{
		inqCustAddress = inqCustAddressIn;
	}

	/**
	 * Returns the date-of-birth component object.
	 *
	 * @return the date of birth (serialised as the nested object
	 *         {@code InqCustDob})
	 */
	public InqCustDob getInqCustDob()
	{
		return inqCustDob;
	}

	/**
	 * Sets the date-of-birth component object.
	 *
	 * @param inqCustDobIn the date of birth (serialised as the nested object
	 *                     {@code InqCustDob})
	 */
	public void setInqCustDob(InqCustDob inqCustDobIn)
	{
		inqCustDob = inqCustDobIn;
	}

	/**
	 * Returns the credit score.
	 *
	 * @return the credit score (serialised as the JSON number
	 *         {@code InqCustCreditScore})
	 */
	public Integer getInqCustCreditScore()
	{
		return inqCustCreditScore;
	}

	/**
	 * Sets the credit score.
	 *
	 * @param inqCustCreditScoreIn the credit score (serialised as the JSON number
	 *                             {@code InqCustCreditScore})
	 */
	public void setInqCustCreditScore(Integer inqCustCreditScoreIn)
	{
		inqCustCreditScore = inqCustCreditScoreIn;
	}

	/**
	 * Returns the credit-score review-date component object. Note the wire key is
	 * {@code InqCustCsReviewDt} (abbreviated), pinned by the field's
	 * {@code @JsonProperty}; this accessor uses the fuller {@code ReviewDate}
	 * spelling for ergonomics only.
	 *
	 * @return the review date (serialised as the nested object
	 *         {@code InqCustCsReviewDt})
	 */
	public InqCustReviewDate getInqCustCsReviewDate()
	{
		return inqCustCsReviewDate;
	}

	/**
	 * Sets the credit-score review-date component object.
	 *
	 * @param inqCustCsReviewDateIn the review date (serialised as the nested
	 *                              object {@code InqCustCsReviewDt})
	 */
	public void setInqCustCsReviewDate(InqCustReviewDate inqCustCsReviewDateIn)
	{
		inqCustCsReviewDate = inqCustCsReviewDateIn;
	}

	/**
	 * Returns the single-character enquiry-success flag ({@code 'Y'}/{@code 'N'}).
	 *
	 * @return the success flag (serialised as {@code InqCustInqSuccess})
	 */
	public String getInqCustInqSuccess()
	{
		return inqCustInqSuccess;
	}

	/**
	 * Sets the single-character enquiry-success flag ({@code 'Y'}/{@code 'N'}).
	 *
	 * @param inqCustInqSuccessIn the success flag (serialised as
	 *                            {@code InqCustInqSuccess})
	 */
	public void setInqCustInqSuccess(String inqCustInqSuccessIn)
	{
		inqCustInqSuccess = inqCustInqSuccessIn;
	}

	/**
	 * Returns the single-character fail code.
	 *
	 * @return the fail code (serialised as {@code InqCustInqFailCd})
	 */
	public String getInqCustInqFailCd()
	{
		return inqCustInqFailCd;
	}

	/**
	 * Sets the single-character fail code.
	 *
	 * @param inqCustInqFailCdIn the fail code (serialised as
	 *                           {@code InqCustInqFailCd})
	 */
	public void setInqCustInqFailCd(String inqCustInqFailCdIn)
	{
		inqCustInqFailCd = inqCustInqFailCdIn;
	}

	/**
	 * Returns the PCB pointer.
	 *
	 * @return the PCB pointer (serialised as {@code InqCustPcbPointer})
	 */
	public String getInqCustPcbPointer()
	{
		return inqCustPcbPointer;
	}

	/**
	 * Sets the PCB pointer.
	 *
	 * @param inqCustPcbPointerIn the PCB pointer (serialised as
	 *                            {@code InqCustPcbPointer})
	 */
	public void setInqCustPcbPointer(String inqCustPcbPointerIn)
	{
		inqCustPcbPointer = inqCustPcbPointerIn;
	}

	/**
	 * Renders all eleven fields for diagnostics and logging, labelled with their
	 * verbatim wire names. This is not a Jackson getter and therefore does not
	 * affect the serialised wire form.
	 *
	 * @return a diagnostic string containing every field
	 */
	@Override
	public String toString()
	{
		return "InqCustZJson [InqCustEye=" + inqCustEye + ", InqCustScode="
				+ inqCustScode + ", InqCustCustno=" + inqCustCustno
				+ ", InqCustName=" + inqCustName + ", InqCustAddr="
				+ inqCustAddress + ", InqCustDob=" + inqCustDob
				+ ", InqCustCreditScore=" + inqCustCreditScore
				+ ", InqCustCsReviewDt=" + inqCustCsReviewDate
				+ ", InqCustInqSuccess=" + inqCustInqSuccess
				+ ", InqCustInqFailCd=" + inqCustInqFailCd
				+ ", InqCustPcbPointer=" + inqCustPcbPointer + "]";
	}

}
