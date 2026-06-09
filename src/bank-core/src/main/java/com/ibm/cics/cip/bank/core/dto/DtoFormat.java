/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.core.dto;

import java.time.LocalDate;

/**
 * Stateless formatting helpers shared by the wire DTOs to convert between
 * {@link LocalDate} (the entity/service representation) and the two date
 * encodings the frozen z/OS Connect envelope uses:
 *
 * <ul>
 *   <li>an <strong>integer</strong> {@code DDMMYYYY} value (for example the
 *       {@code CommOpened}, {@code CommLastStmtDt} and {@code CommNextStmtDt}
 *       fields of the create-account envelope), and</li>
 *   <li>a <strong>string</strong> {@code DDMMYYYY} value (for example the
 *       {@code CommDateOfBirth} and {@code CommCsReviewDate} fields of the
 *       create-customer and update-account envelopes).</li>
 * </ul>
 *
 * <p>The {@code DDMMYYYY} layout matches the COBOL {@code 9(8)} account/customer
 * date fields and the interface module's {@code OutputFormatUtils.date(...)}
 * helper, which left-zero-pads to eight characters and renders {@code DD/MM/YYYY}
 * for display. Keeping these conversions in one place guarantees every DTO
 * encodes dates identically, preserving byte-for-byte wire compatibility
 * (feature F-019).</p>
 *
 * <p>The class is {@code final} with a private constructor; it holds no state
 * and is never instantiated.</p>
 */
public final class DtoFormat
{

	/** Number of characters in a {@code DDMMYYYY} encoded date. */
	private static final int DATE_WIDTH = 8;

	/** Sentinel integer used when a date is absent (matches envelope default). */
	private static final int ABSENT_DATE_INT = 0;

	/** Sentinel string used when a date is absent (matches envelope default). */
	private static final String ABSENT_DATE_STRING = "0";

	/**
	 * Private constructor; this is a non-instantiable utility holder.
	 */
	private DtoFormat()
	{
		throw new AssertionError(
				"DtoFormat is a utility class and must not be instantiated");
	}

	/**
	 * Encodes a date as an eight-character {@code DDMMYYYY} string.
	 *
	 * @param date the date to encode, or {@code null}
	 * @return the {@code DDMMYYYY} string, or {@value #ABSENT_DATE_STRING} if the
	 *         date is {@code null}
	 */
	public static String dateToString(LocalDate date)
	{
		if (date == null)
		{
			return ABSENT_DATE_STRING;
		}
		return String.format("%02d%02d%04d", date.getDayOfMonth(),
				date.getMonthValue(), date.getYear());
	}

	/**
	 * Encodes a date as an integer {@code DDMMYYYY} value. Note that, as an
	 * integer, a leading zero on the day is naturally dropped (for example the
	 * 6th of June 2026 encodes as {@code 6062026}); this matches the interface
	 * module's {@code int}-typed date fields, which it re-pads before display.
	 *
	 * @param date the date to encode, or {@code null}
	 * @return the {@code DDMMYYYY} integer, or {@value #ABSENT_DATE_INT} if the
	 *         date is {@code null}
	 */
	public static int dateToInt(LocalDate date)
	{
		if (date == null)
		{
			return ABSENT_DATE_INT;
		}
		return Integer.parseInt(dateToString(date));
	}

	/**
	 * Parses an eight-character (or shorter, left-zero-padded) {@code DDMMYYYY}
	 * string into a {@link LocalDate}.
	 *
	 * @param value the {@code DDMMYYYY} string; may be shorter than eight
	 *              characters (it is left-zero-padded) or blank
	 * @return the parsed date, or {@code null} if the value is blank, the absent
	 *         sentinel, or otherwise not a valid eight-digit date
	 */
	public static LocalDate parseDate(String value)
	{
		if (value == null)
		{
			return null;
		}
		String trimmed = value.trim();
		if (trimmed.isEmpty() || ABSENT_DATE_STRING.equals(trimmed))
		{
			return null;
		}
		String padded = String.format("%" + DATE_WIDTH + "s", trimmed)
				.replace(' ', '0');
		if (padded.length() != DATE_WIDTH || !padded.chars()
				.allMatch(Character::isDigit))
		{
			return null;
		}
		int day = Integer.parseInt(padded.substring(0, 2));
		int month = Integer.parseInt(padded.substring(2, 4));
		int year = Integer.parseInt(padded.substring(4, 8));
		return LocalDate.of(year, month, day);
	}

	/**
	 * Parses an integer {@code DDMMYYYY} value into a {@link LocalDate}.
	 *
	 * @param value the {@code DDMMYYYY} integer, or {@value #ABSENT_DATE_INT}
	 * @return the parsed date, or {@code null} if the value is the absent
	 *         sentinel or not a valid date
	 */
	public static LocalDate parseDate(int value)
	{
		if (value == ABSENT_DATE_INT)
		{
			return null;
		}
		return parseDate(Integer.toString(value));
	}

}
