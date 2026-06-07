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

	/**
	 * Parses an inbound account-number path/identifier value into a
	 * {@code long}, enforcing the eight-digit ({@code PIC 9(8)}) contract width.
	 *
	 * <p>Used by the REST controllers to bound-check the {@code {accno}} path
	 * variable <em>before</em> it is used as a lookup key. Rejecting an
	 * over-width value here is what prevents the legacy integer-overflow echo
	 * (a twelve-digit value can never be represented faithfully in the frozen
	 * contract's eight-digit, integer-typed {@code Accno} field): a non-numeric
	 * or over-width value raises {@link NumberFormatException}, which
	 * {@code GlobalExceptionHandler} renders as HTTP&nbsp;400 rather than letting
	 * a raw parse error surface as HTTP&nbsp;500.</p>
	 *
	 * @param value the inbound account-number string (may be {@code null} or
	 *              contain surrounding whitespace)
	 * @return the parsed account number
	 * @throws NumberFormatException if the value is {@code null}, blank,
	 *                               non-numeric, or wider than eight digits
	 */
	public static long parseAccountNumber(String value)
	{
		return parseFixedWidth(value, ACCOUNT_NUMBER_LENGTH);
	}

	/**
	 * Parses an inbound customer-number path/identifier value into a
	 * {@code long}, enforcing the ten-digit ({@code PIC 9(10)}) contract width.
	 *
	 * <p>Used by the REST controllers to bound-check the {@code {custno}} path
	 * variable before it is used as a lookup key. A non-numeric or over-width
	 * value raises {@link NumberFormatException}, which
	 * {@code GlobalExceptionHandler} renders as HTTP&nbsp;400.</p>
	 *
	 * @param value the inbound customer-number string (may be {@code null} or
	 *              contain surrounding whitespace)
	 * @return the parsed customer number
	 * @throws NumberFormatException if the value is {@code null}, blank,
	 *                               non-numeric, or wider than ten digits
	 */
	public static long parseCustomerNumber(String value)
	{
		return parseFixedWidth(value, CUSTOMER_NUMBER_LENGTH);
	}

	/**
	 * Parses a fixed-width, display-numeric identifier, accepting only one to
	 * {@code width} decimal digits (after trimming surrounding whitespace). Any
	 * other shape &mdash; {@code null}, blank, a non-digit character, or more
	 * than {@code width} digits &mdash; raises {@link NumberFormatException} so
	 * the caller maps it to a single, consistent HTTP&nbsp;400 client-error
	 * response. Leading zeros are accepted (the value is later re-padded to its
	 * fixed width).
	 *
	 * @param value the identifier string (may be {@code null})
	 * @param width the maximum permitted number of digits
	 * @return the parsed numeric value
	 * @throws NumberFormatException if the value is not one-to-{@code width}
	 *                               decimal digits
	 */
	private static long parseFixedWidth(String value, int width)
	{
		String trimmed = (value == null) ? "" : value.trim();
		if (trimmed.isEmpty() || trimmed.length() > width
				|| !trimmed.chars().allMatch(Character::isDigit))
		{
			throw new NumberFormatException(
					"Identifier must be 1 to " + width + " digits");
		}
		return Long.parseLong(trimmed);
	}

}
