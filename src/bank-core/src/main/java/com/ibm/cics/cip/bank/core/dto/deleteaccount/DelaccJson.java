/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.core.dto.deleteaccount;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ibm.cics.cip.bank.core.domain.AccountType;

/**
 * Frozen z/OS Connect <em>delete-account</em> envelope ({@code DelaccJson}),
 * reproduced field-for-field from the interface module's class of the same name
 * (feature F-019). Nested inside {@link DeleteAccountJson} under the
 * {@code DelAcc} key.
 *
 * <p><strong>Fail-code semantics (critical for parity).</strong> The contract
 * carries <em>two</em> integer fail-code fields. The consumer
 * ({@code WebController.checkIfResponseValidDeleteAcc}) treats
 * {@code getDelaccDelFailCode() == 1} &mdash; the field bound to JSON
 * {@code DelAccFailCd} &mdash; as &quot;account not found&quot;. The second field
 * ({@code DelAccDelFailCd} &rarr; {@code delaccFailCode}) is a legacy PCB-style
 * slot that the consumer does not inspect. {@code bank-core} therefore sets
 * {@code DelAccFailCd = 1} on a not-found delete and {@code 0} on success.</p>
 *
 * <p>Monetary fields are typed {@link BigDecimal} (rule U1, replacing the legacy
 * {@code float}); statement/opened dates are {@code DDMMYYYY} strings, and the
 * account type is the {@link AccountType} domain enum (serialised by name, e.g.
 * {@code "CURRENT"}, which the consumer deserialises back into its own
 * equivalently-named enum).</p>
 */
public class DelaccJson
{

	/** Legacy success flag slot. */
	@JsonProperty("DelAccSuccess")
	private String delaccSuccess;

	/** Last-statement date, {@code DDMMYYYY} string. */
	@JsonProperty("DelAccLastStmtDt")
	private String delaccLastStatementDate;

	/** Interest rate. */
	@JsonProperty("DelAccIntRate")
	private BigDecimal delaccInterestRate;

	/**
	 * Primary fail code (JSON {@code DelAccFailCd}); {@code 1} signals
	 * &quot;account not found&quot; to the consumer, {@code 0} signals success.
	 */
	@JsonProperty("DelAccFailCd")
	private int delaccDelFailCode;

	/** Sort code. */
	@JsonProperty("DelAccScode")
	private String delaccSortcode;

	/** Legacy PCB slot 1. */
	@JsonProperty("DelAccDelPcb1")
	private String delaccDelPcb1;

	/** Date opened, {@code DDMMYYYY} string. */
	@JsonProperty("DelAccOpened")
	private String delaccOpened;

	/** Account type (serialised by enum name). */
	@JsonProperty("DelAccAccType")
	private AccountType delaccAccType;

	/** Next-statement date, {@code DDMMYYYY} string. */
	@JsonProperty("DelAccNextStmtDt")
	private String delaccNextStatementDate;

	/** Actual balance at deletion (terminal balance). */
	@JsonProperty("DelAccActualBal")
	private BigDecimal delaccActualBalance;

	/** Available balance at deletion. */
	@JsonProperty("DelAccAvailBal")
	private BigDecimal delaccAvailableBalance;

	/** Owning customer number. */
	@JsonProperty("DelAccCustno")
	private String delaccCustno;

	/** Legacy PCB slot 3. */
	@JsonProperty("DelAccDelPcb3")
	private String delaccDelPcb3;

	/** Legacy PCB slot 2. */
	@JsonProperty("DelAccDelPcb2")
	private String delaccDelPcb2;

	/** Account number. */
	@JsonProperty("DelAccAccno")
	private int delaccAccno;

	/** Overdraft limit (whole units). */
	@JsonProperty("DelAccOverdraft")
	private int delaccOverdraft;

	/** Secondary (PCB-style) fail code (JSON {@code DelAccDelFailCd}); unused by the consumer. */
	@JsonProperty("DelAccDelFailCd")
	private int delaccFailCode;

	/** Eye-catcher (preserved for wire parity). */
	@JsonProperty("DelAccEye")
	private String delaccEye;

	/** Legacy applid slot. */
	@JsonProperty("DelAccDelApplid")
	private String delaccDelApplid;

	/** Legacy delete-success slot. */
	@JsonProperty("DelAccDelSuccess")
	private String delaccDelSuccess;

	/**
	 * Default constructor for Jackson (de)serialisation.
	 */
	public DelaccJson()
	{
		super();
	}

	/**
	 * Returns the legacy success flag.
	 *
	 * @return the success flag
	 */
	public String getDelaccSuccess()
	{
		return delaccSuccess;
	}

	/**
	 * Sets the legacy success flag.
	 *
	 * @param delaccSuccessIn the success flag
	 */
	public void setDelaccSuccess(String delaccSuccessIn)
	{
		delaccSuccess = delaccSuccessIn;
	}

	/**
	 * Returns the last-statement date ({@code DDMMYYYY}).
	 *
	 * @return the last-statement date
	 */
	public String getDelaccLastStatementDate()
	{
		return delaccLastStatementDate;
	}

	/**
	 * Sets the last-statement date ({@code DDMMYYYY}).
	 *
	 * @param delaccLastStatementDateIn the last-statement date
	 */
	public void setDelaccLastStatementDate(String delaccLastStatementDateIn)
	{
		delaccLastStatementDate = delaccLastStatementDateIn;
	}

	/**
	 * Returns the interest rate.
	 *
	 * @return the interest rate
	 */
	public BigDecimal getDelaccInterestRate()
	{
		return delaccInterestRate;
	}

	/**
	 * Sets the interest rate.
	 *
	 * @param delaccInterestRateIn the interest rate
	 */
	public void setDelaccInterestRate(BigDecimal delaccInterestRateIn)
	{
		delaccInterestRate = delaccInterestRateIn;
	}

	/**
	 * Returns the primary fail code (JSON {@code DelAccFailCd}); {@code 1} means
	 * &quot;account not found&quot;.
	 *
	 * @return the primary fail code
	 */
	public int getDelaccDelFailCode()
	{
		return delaccDelFailCode;
	}

	/**
	 * Sets the primary fail code (JSON {@code DelAccFailCd}).
	 *
	 * @param delaccDelFailCodeIn the primary fail code
	 */
	public void setDelaccDelFailCode(int delaccDelFailCodeIn)
	{
		delaccDelFailCode = delaccDelFailCodeIn;
	}

	/**
	 * Returns the sort code.
	 *
	 * @return the sort code
	 */
	public String getDelaccSortcode()
	{
		return delaccSortcode;
	}

	/**
	 * Sets the sort code.
	 *
	 * @param delaccSortcodeIn the sort code
	 */
	public void setDelaccSortcode(String delaccSortcodeIn)
	{
		delaccSortcode = delaccSortcodeIn;
	}

	/**
	 * Returns legacy PCB slot 1.
	 *
	 * @return PCB slot 1
	 */
	public String getDelaccDelPcb1()
	{
		return delaccDelPcb1;
	}

	/**
	 * Sets legacy PCB slot 1.
	 *
	 * @param delaccDelPcb1In PCB slot 1
	 */
	public void setDelaccDelPcb1(String delaccDelPcb1In)
	{
		delaccDelPcb1 = delaccDelPcb1In;
	}

	/**
	 * Returns the date opened ({@code DDMMYYYY}).
	 *
	 * @return the date opened
	 */
	public String getDelaccOpened()
	{
		return delaccOpened;
	}

	/**
	 * Sets the date opened ({@code DDMMYYYY}).
	 *
	 * @param delaccOpenedIn the date opened
	 */
	public void setDelaccOpened(String delaccOpenedIn)
	{
		delaccOpened = delaccOpenedIn;
	}

	/**
	 * Returns the account type.
	 *
	 * @return the account type
	 */
	public AccountType getDelaccAccType()
	{
		return delaccAccType;
	}

	/**
	 * Sets the account type from a typed enum constant.
	 *
	 * @param delaccAccTypeIn the account type
	 */
	public void setDelaccAccType(AccountType delaccAccTypeIn)
	{
		delaccAccType = delaccAccTypeIn;
	}

	/**
	 * Sets the account type from a raw string, resolving it to the matching
	 * {@link AccountType} constant (case-sensitive, trimmed). A blank value
	 * resolves to {@code null}, matching the legacy contract's handling of a
	 * re-deleted account.
	 *
	 * @param delaccAccTypeIn the raw account-type string; may be blank
	 */
	public void setDelaccAccType(String delaccAccTypeIn)
	{
		if (delaccAccTypeIn == null || delaccAccTypeIn.trim().isEmpty())
		{
			delaccAccType = null;
		}
		else
		{
			delaccAccType = AccountType.fromValue(delaccAccTypeIn);
		}
	}

	/**
	 * Returns the next-statement date ({@code DDMMYYYY}).
	 *
	 * @return the next-statement date
	 */
	public String getDelaccNextStatementDate()
	{
		return delaccNextStatementDate;
	}

	/**
	 * Sets the next-statement date ({@code DDMMYYYY}).
	 *
	 * @param delaccNextStatementDateIn the next-statement date
	 */
	public void setDelaccNextStatementDate(String delaccNextStatementDateIn)
	{
		delaccNextStatementDate = delaccNextStatementDateIn;
	}

	/**
	 * Returns the actual balance at deletion.
	 *
	 * @return the actual balance
	 */
	public BigDecimal getDelaccActualBalance()
	{
		return delaccActualBalance;
	}

	/**
	 * Sets the actual balance at deletion.
	 *
	 * @param delaccActualBalanceIn the actual balance
	 */
	public void setDelaccActualBalance(BigDecimal delaccActualBalanceIn)
	{
		delaccActualBalance = delaccActualBalanceIn;
	}

	/**
	 * Returns the available balance at deletion.
	 *
	 * @return the available balance
	 */
	public BigDecimal getDelaccAvailableBalance()
	{
		return delaccAvailableBalance;
	}

	/**
	 * Sets the available balance at deletion.
	 *
	 * @param delaccAvailableBalanceIn the available balance
	 */
	public void setDelaccAvailableBalance(BigDecimal delaccAvailableBalanceIn)
	{
		delaccAvailableBalance = delaccAvailableBalanceIn;
	}

	/**
	 * Returns the owning customer number.
	 *
	 * @return the customer number
	 */
	public String getDelaccCustno()
	{
		return delaccCustno;
	}

	/**
	 * Sets the owning customer number.
	 *
	 * @param delAccCustnoIn the customer number
	 */
	public void setDelaccCustno(String delAccCustnoIn)
	{
		delaccCustno = delAccCustnoIn;
	}

	/**
	 * Returns legacy PCB slot 3.
	 *
	 * @return PCB slot 3
	 */
	public String getDelaccDelPcb3()
	{
		return delaccDelPcb3;
	}

	/**
	 * Sets legacy PCB slot 3.
	 *
	 * @param delaccDelPcb3In PCB slot 3
	 */
	public void setDelaccDelPcb3(String delaccDelPcb3In)
	{
		delaccDelPcb3 = delaccDelPcb3In;
	}

	/**
	 * Returns legacy PCB slot 2.
	 *
	 * @return PCB slot 2
	 */
	public String getDelaccDelPcb2()
	{
		return delaccDelPcb2;
	}

	/**
	 * Sets legacy PCB slot 2.
	 *
	 * @param delaccDelPcb2In PCB slot 2
	 */
	public void setDelaccDelPcb2(String delaccDelPcb2In)
	{
		delaccDelPcb2 = delaccDelPcb2In;
	}

	/**
	 * Returns the account number.
	 *
	 * @return the account number
	 */
	public int getDelaccAccno()
	{
		return delaccAccno;
	}

	/**
	 * Sets the account number.
	 *
	 * @param delaccAccnoIn the account number
	 */
	public void setDelaccAccno(int delaccAccnoIn)
	{
		delaccAccno = delaccAccnoIn;
	}

	/**
	 * Returns the overdraft limit.
	 *
	 * @return the overdraft limit
	 */
	public int getDelaccOverdraft()
	{
		return delaccOverdraft;
	}

	/**
	 * Sets the overdraft limit.
	 *
	 * @param delaccOverdraftIn the overdraft limit
	 */
	public void setDelaccOverdraft(int delaccOverdraftIn)
	{
		delaccOverdraft = delaccOverdraftIn;
	}

	/**
	 * Returns the secondary (PCB-style) fail code.
	 *
	 * @return the secondary fail code
	 */
	public int getDelaccFailCode()
	{
		return delaccFailCode;
	}

	/**
	 * Sets the secondary (PCB-style) fail code.
	 *
	 * @param delaccFailCodeIn the secondary fail code
	 */
	public void setDelaccFailCode(int delaccFailCodeIn)
	{
		delaccFailCode = delaccFailCodeIn;
	}

	/**
	 * Returns the eye-catcher.
	 *
	 * @return the eye-catcher
	 */
	public String getDelaccEye()
	{
		return delaccEye;
	}

	/**
	 * Sets the eye-catcher.
	 *
	 * @param delaccEyeIn the eye-catcher
	 */
	public void setDelaccEye(String delaccEyeIn)
	{
		delaccEye = delaccEyeIn;
	}

	/**
	 * Returns the legacy applid slot.
	 *
	 * @return the applid slot
	 */
	public String getDelaccDelApplid()
	{
		return delaccDelApplid;
	}

	/**
	 * Sets the legacy applid slot.
	 *
	 * @param delaccDelApplidIn the applid slot
	 */
	public void setDelaccDelApplid(String delaccDelApplidIn)
	{
		delaccDelApplid = delaccDelApplidIn;
	}

	/**
	 * Returns the legacy delete-success slot.
	 *
	 * @return the delete-success slot
	 */
	public String getDelaccDelSuccess()
	{
		return delaccDelSuccess;
	}

	/**
	 * Sets the legacy delete-success slot.
	 *
	 * @param delaccDelSuccessIn the delete-success slot
	 */
	public void setDelaccDelSuccess(String delaccDelSuccessIn)
	{
		delaccDelSuccess = delaccDelSuccessIn;
	}

}
