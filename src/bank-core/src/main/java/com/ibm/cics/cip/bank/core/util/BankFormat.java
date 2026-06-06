/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.core.util;

/**
 * Stateless formatting helpers for the fixed-width, left-zero-padded
 * display-numeric identifiers used throughout CBSA.
 *
 * <p>The legacy records store sort code, account number, customer number and
 * transaction reference as fixed-width {@code PIC 9(n)} display-numeric fields,
 * and the relational schema preserves that as {@code CHAR(n)} (AAP &sect;0.6,
 * "Fixed-width character identifiers"). These helpers reproduce the COBOL
 * left-zero-padding so identifiers round-trip verbatim between the wire, the
 * entities and the database.</p>
 */
public final class BankFormat
{

	/** Width of a sort code ({@code PIC 9(6)}). */
	public static final int SORT_CODE_LENGTH = 6;

	/** Width of an account number ({@code PIC 9(8)}). */
	public static final int ACCOUNT_NUMBER_LENGTH = 8;

	/** Width of a customer number ({@code PIC 9(10)}). */
	public static final int CUSTOMER_NUMBER_LENGTH = 10;

	/** Width of a transaction reference ({@code PIC 9(12)}). */
	public static final int REFERENCE_LENGTH = 12;

	private BankFormat()
	{
		// Utility class; not instantiable.
	}

	/**
	 * Left-zero-pads a numeric value to the given width.
	 *
	 * @param value the non-negative value
	 * @param width the target width
	 * @return the value as a fixed-width, left-zero-padded string
	 */
	public static String pad(long value, int width)
	{
		return String.format("%0" + width + "d", value);
	}

	/**
	 * Left-zero-pads an arbitrary string to the given width (digits assumed).
	 * Strings already at or beyond the width are returned trimmed of leading and
	 * trailing whitespace then re-padded, preserving the numeric content.
	 *
	 * @param value the value (may contain surrounding whitespace)
	 * @param width the target width
	 * @return the value as a fixed-width, left-zero-padded string
	 */
	public static String pad(String value, int width)
	{
		String trimmed = value == null ? "" : value.trim();
		if (trimmed.length() >= width)
		{
			return trimmed;
		}
		StringBuilder sb = new StringBuilder();
		for (int i = trimmed.length(); i < width; i++)
		{
			sb.append('0');
		}
		sb.append(trimmed);
		return sb.toString();
	}

	/**
	 * Formats a sort code as a six-digit, zero-padded string.
	 *
	 * @param sortCode the sort code value
	 * @return the six-character sort code
	 */
	public static String sortCode(long sortCode)
	{
		return pad(sortCode, SORT_CODE_LENGTH);
	}

	/**
	 * Formats an account number as an eight-digit, zero-padded string.
	 *
	 * @param accountNumber the account number value
	 * @return the eight-character account number
	 */
	public static String accountNumber(long accountNumber)
	{
		return pad(accountNumber, ACCOUNT_NUMBER_LENGTH);
	}

	/**
	 * Formats a customer number as a ten-digit, zero-padded string.
	 *
	 * @param customerNumber the customer number value
	 * @return the ten-character customer number
	 */
	public static String customerNumber(long customerNumber)
	{
		return pad(customerNumber, CUSTOMER_NUMBER_LENGTH);
	}

	/**
	 * Formats a transaction reference as a twelve-digit, zero-padded string.
	 *
	 * @param reference the reference value
	 * @return the twelve-character reference
	 */
	public static String reference(long reference)
	{
		return pad(reference, REFERENCE_LENGTH);
	}

}
