/*
 *
 *    Copyright IBM Corp. 2023
 *
 */
/**
 * This class describes the methods of the SortCode Resource
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

@Path("/sortCode")
public class SortCodeResource
{


	private static Logger logger = Logger
			.getLogger("com.ibm.cics.cip.bankliberty.api.json");

	/**
	 * Shared, thread-safe Jackson mapper used to build and serialize the
	 * {@code /sortCode} JSON response envelope. Replaces the WebSphere Liberty
	 * JSON API used by the legacy implementation, which has been decommissioned
	 * for the standalone Java target.
	 */
	private static final ObjectMapper mapper = new ObjectMapper();

	/**
	 * The bank's sort code. Sourced from the COBOL GETSCODE routine and the
	 * SORTCODE copybook, and modeled as the constant {@code 987654} in the new
	 * bank-core {@code BankConstants}. The legacy implementation obtained this
	 * value through a mainframe program link to GETSCODE followed by a fixed
	 * record parse; both have been decommissioned for the standalone Java
	 * target, so the value is populated directly to preserve the
	 * {@code {"sortCode":"987654"}} response byte-for-byte.
	 */
	private static final String SORT_CODE = "987654";


	public SortCodeResource()
	{
		sortOutLogging();
	}

	static String sortCodeString = null;


	@GET
	@Produces("application/json")
	public Response getSortCode()
	{

		if (sortCodeString == null)
		{
			// Re-point to bank-core reference data: the sort code is the
			// well-known constant 987654 (bank-core BankConstants, originally
			// returned by the COBOL GETSCODE program). The static cache is
			// retained so the value is resolved at most once per JVM, exactly
			// as the original CICS-link implementation did.
			SortCodeResource.setSortCode(SORT_CODE);
		}

		// Build the frozen single-field response envelope {"sortCode":"987654"}
		// with Jackson (ObjectNode) in place of the decommissioned WebSphere
		// JSON API. The field name and value are unchanged.
		ObjectNode response = mapper.createObjectNode();
		response.put("sortCode", sortCodeString);

		String responseString;
		try
		{
			responseString = mapper.writeValueAsString(response);
		}
		catch (JsonProcessingException e)
		{
			// Preserve the original fall-through-on-error behavior: log and
			// continue rather than returning 500. ObjectNode.toString() yields
			// the same JSON text and never throws, so the response shape and
			// HTTP status code remain unchanged.
			logger.severe(e.toString());
			responseString = response.toString();
		}

		return Response.status(200).entity(responseString).build();
	}


	private static void setSortCode(String sortcode)
	{
		sortCodeString = sortcode;
	}


	private static void sortOutLogging()
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
}
