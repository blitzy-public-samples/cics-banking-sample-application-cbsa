/*
 *
 *    Copyright IBM Corp. 2023
 *
 */

package com.ibm.cics.cip.bankliberty.api.json;

import java.io.IOException;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import jakarta.ws.rs.GET;

import jakarta.ws.rs.Path;

import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Response;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * This class is used to get the Company Name
 *
 */

@Path("/companyName")
public class CompanyNameResource
{

	static String companyNameString = null;

	private static Logger logger = Logger.getLogger(
			"com.ibm.cics.cip.bankliberty.api.json.CompanyNameResource");
	// </copyright>

	private static final String GET_COMPANY_NAME = "getCompanyName()";

	private static final String ERROR_MSG_PREFIX = "CompanyNameResource.getCompanyName() has experienced error ";

	private static final String ERROR_MSG_SUFFIX = " linking to program GETCOMPY";

	/**
	 * Shared, thread-safe Jackson mapper used to build and serialize the
	 * {@code /companyName} JSON response envelope. Replaces the WebSphere
	 * Liberty JSON API used by the legacy implementation, which has been
	 * decommissioned for the standalone Java target; Jackson
	 * ({@code jackson-databind}) is the JSON binding going forward.
	 */
	private static final ObjectMapper mapper = new ObjectMapper();

	/**
	 * The bank's company name returned by the reference-data lookup.
	 *
	 * <p>COBOL source: {@code GETCOMPY.cbl} L38 &mdash;
	 * {@code move 'CICS Bank Sample Application' to COMPANY-NAME}. The same
	 * literal is modeled as {@code COMPANY_NAME} in the new bank-core
	 * {@code BankConstants}. The legacy implementation obtained this value
	 * through a jCICS program link to {@code GETCOMPY} followed by a JZOS
	 * fixed-record parse (which {@code .trim()}-ed the 40-character
	 * {@code PIC X(40)} field); both the CICS link and the JZOS record class
	 * have been decommissioned for the standalone Java target, so the exact,
	 * already-trimmed value is populated directly. This preserves the
	 * {@code {"companyName":"CICS Bank Sample Application"}} response
	 * byte-for-byte with zero network calls.</p>
	 */
	private static final String COMPANY_NAME = "CICS Bank Sample Application";


	public CompanyNameResource()
	{
		sortOutLogging();
	}


	@GET
	@Produces("application/json")
	public Response getCompanyName()
	{
		logger.entering(this.getClass().getName(), GET_COMPANY_NAME);
		// We cache the company name as a static variable. If not set, we
		// populate it from bank-core's reference data. The legacy
		// implementation obtained this value through a jCICS LINK to the COBOL
		// GETCOMPY program followed by a JZOS fixed-record parse; both the CICS
		// program link and the JZOS-backed record class have been
		// decommissioned for the standalone Java target, so the value is taken
		// directly from the well-known reference-data constant (bank-core
		// BankConstants.COMPANY_NAME), which is the exact, already-trimmed
		// string COBOL GETCOMPY returned. This is a pure in-process lookup, so
		// the CICS link exceptions the legacy code caught can no longer occur.
		if (companyNameString == null)
		{
			CompanyNameResource.setCompanyName(COMPANY_NAME);
		}

		// Preserve the original failure semantics: the legacy code returned
		// HTTP 500 when it could not obtain the company name from GETCOMPY. If
		// the reference-data value is unavailable for any reason, surface the
		// same status code and Response shape rather than emitting an empty
		// envelope.
		if (companyNameString == null)
		{
			String errorMessage = ERROR_MSG_PREFIX + "company name unavailable"
					+ ERROR_MSG_SUFFIX;
			logger.severe(errorMessage);
			Response myResponse = Response.status(500).entity(errorMessage)
					.build();
			logger.exiting(this.getClass().getName(), GET_COMPANY_NAME,
					myResponse);
			return myResponse;
		}

		// Build the frozen single-field response envelope
		// {"companyName":"..."} with Jackson (ObjectNode) in place of the
		// decommissioned WebSphere JSON API. The field name and value are
		// unchanged.
		ObjectNode response = mapper.createObjectNode();
		response.put("companyName", companyNameString);

		String responseString;
		try
		{
			responseString = mapper.writeValueAsString(response);
		}
		catch (JsonProcessingException e)
		{
			// Serialization is the only checked failure that can now occur on
			// this code path; handle it with the preserved 500-style error
			// path so the HTTP status code and Response shape are unchanged.
			String errorMessage = ERROR_MSG_PREFIX + e.toString()
					+ ERROR_MSG_SUFFIX;
			logger.warning(errorMessage);
			Response myResponse = Response.status(500).entity(errorMessage)
					.build();
			logger.exiting(this.getClass().getName(), GET_COMPANY_NAME,
					myResponse);
			return myResponse;
		}

		Response myResponse = Response.status(200).entity(responseString)
				.build();
		logger.exiting(this.getClass().getName(), GET_COMPANY_NAME, myResponse);

		return myResponse;
	}


	private void sortOutLogging()
	{
		try
		{
			LogManager.getLogManager().readConfiguration();
		}
		catch (SecurityException | IOException e)
		{
			logger.severe(e.toString());
		}
	}


	private static void setCompanyName(String companyName)
	{
		companyNameString = companyName;
	}

}
