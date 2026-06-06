/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.core.service;

import org.springframework.stereotype.Service;

import com.ibm.cics.cip.bank.core.constants.BankConstants;

/**
 * Reference-data service &mdash; the Java rendering of the COBOL {@code GETCOMPY}
 * and {@code GETSCODE} programs (feature F-001).
 *
 * <p>Both legacy programs simply return a hard-coded constant: {@code GETCOMPY}
 * returns the company name and {@code GETSCODE} returns the bank sort code.
 * There is no data access; the values live in {@link BankConstants}. This
 * service exposes them through a small typed API so the rest of the module never
 * hard-codes the literals.</p>
 */
@Service
public class ReferenceDataService
{

	/**
	 * Returns the bank's company name &mdash; the COBOL {@code GETCOMPY} result.
	 *
	 * @return the company name
	 */
	public String getCompanyName()
	{
		return BankConstants.COMPANY_NAME;
	}

	/**
	 * Returns the bank's sort code as a six-digit, zero-padded string &mdash; the
	 * COBOL {@code GETSCODE} result.
	 *
	 * @return the six-character sort code
	 */
	public String getSortCode()
	{
		return BankConstants.SORT_CODE;
	}

}
