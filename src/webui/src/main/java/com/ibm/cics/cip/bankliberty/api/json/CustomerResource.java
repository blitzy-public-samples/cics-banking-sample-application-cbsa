/*
 *
 *    Copyright IBM Corp. 2023,2026
 *
 */

package com.ibm.cics.cip.bankliberty.api.json;

import java.io.IOException;
import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.DateFormat;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import jakarta.ws.rs.Consumes;

import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * This class describes the methods of the Customer Resource.
 *
 * <p>
 * <b>Tech-stack migration note (CBSA mainframe &rarr; standalone Java).</b> The
 * external JAX-RS contract of this resource is FROZEN and reproduced verbatim:
 * the class-level {@code @Path("customer")} (no leading slash), every endpoint
 * path/HTTP verb/{@code @Produces}/{@code @Consumes}, every {@code @PathParam}
 * /{@code @QueryParam}, every JSON field name, every HTTP status code and the
 * {@code errorMessage} envelope are unchanged. Only the implementation was
 * re-pointed off the decommissioned mainframe libraries:
 * </p>
 * <ul>
 * <li>The legacy WebSphere JSON API is replaced by Jackson
 * ({@link ObjectMapper}/{@link ObjectNode}/{@link ArrayNode}); the JSON field
 * names are emitted byte-for-byte identically.</li>
 * <li>The legacy JCICS data path (the deleted shared data-access base class, the
 * JCICS task rollback hooks and the deleted VSAM customer access class,
 * including its now-removed local credit-score helper) is replaced by a thin
 * JDBC layer on the shared PostgreSQL store used by the {@code bank-core} module
 * ({@code jdbc:postgresql://${DB_HOST:localhost}:5432/cbsa}). Connection and
 * transaction lifecycle is provided by JDBC under explicit transaction
 * boundaries, reproducing the SYNCPOINT/ROLLBACK semantics that previously
 * lived in the JCICS task.</li>
 * <li>Credit scoring is now owned by the bank-core services; webui no longer
 * computes a score locally. On create the base customer record is inserted with
 * a zero score; read paths simply surface whatever score bank-core has
 * stored.</li>
 * </ul>
 *
 * <p>
 * This resource handles no monetary values, and uses only integral and
 * fixed-precision types (no binary floating-point types).
 * </p>
 */

@Path("customer")
public class CustomerResource
{


	static String sortcode = null;

	private static final String CREATE_CUSTOMER_INTERNAL = "createCustomerInternal(CustomerJSON customer) for customer ";

	private static final String CREATE_CUSTOMER_INTERNAL_EXIT = "createCustomerInternal() exiting";

	private static final String CREATE_CUSTOMER_EXTERNAL = "createCustomerExternal(CustomerJSON customer) for customer ";

	private static final String CREATE_CUSTOMER_EXTERNAL_EXIT = "createCustomerExternal(CustomerJSON customer) exiting";

	private static final String GET_CUSTOMER_INTERNAL_EXIT = "getCustomerInternal() exiting";

	private static final String GET_CUSTOMER_EXTERNAL = "getCustomerExternal for customerNumber ";

	private static final String GET_CUSTOMER_EXTERNAL_EXIT = "getCustomerExternal exiting";

	private static final String DELETE_CUSTOMER_INTERNAL = "deleteCustomerInternal()";

	private static final String DELETE_CUSTOMER_INTERNAL_EXIT = "deleteCustomerInternal() exiting";

	private static final String UPDATE_CUSTOMER_INTERNAL = "updateCustomerInternal for customerNumber ";

	private static final String UPDATE_CUSTOMER_INTERNAL_EXIT = "updateCustomerInternal() exiting ";

	private static final String UPDATE_CUSTOMER_EXTERNAL = "updateCustomerExternal for customerNumber ";

	private static Logger logger = Logger
			.getLogger("com.ibm.cics.cip.bankliberty.api.json");

	private static final String JSON_NUMBER_OF_CUSTOMERS = "numberOfCustomers";

	private static final String JSON_CUSTOMERS = "customers";

	private static final String JSON_SORT_CODE = "sortCode";

	private static final String JSON_ID = "id";

	private static final String JSON_CUSTOMER_NAME = "customerName";

	private static final String JSON_CUSTOMER_ADDRESS = "customerAddress";

	private static final String JSON_CUSTOMER_CREDIT_SCORE = "customerCreditScore";

	private static final String JSON_CUSTOMER_REVIEW_DATE = "customerCreditScoreReviewDate";

	private static final String JSON_DATE_OF_BIRTH = "dateOfBirth";

	private static final String JSON_ERROR_MSG = "errorMessage";

	private static final String CUSTOMER_PREFIX = "Customer ";

	private static final String NOT_FOUND_MSG = " not found";

	/*
	 * Jackson mapper used to build the JSON envelopes that replace the removed
	 * legacy WebSphere JSON object/array types. Field names and insertion order
	 * are preserved so the wire format is byte-identical.
	 */
	private static final ObjectMapper mapper = new ObjectMapper();

	/*
	 * Shared PostgreSQL connection coordinates for the bank-core data store. The
	 * database, user and password are all "cbsa" (per the setup constraint) and
	 * DB_HOST is overridable via the environment, defaulting to localhost. This
	 * replaces the deleted JCICS/VSAM customer data path.
	 */
	private static final String DB_NAME = "cbsa";

	private static final String DB_USER = "cbsa";

	private static final String DB_PASSWORD = "cbsa";

	private static final int DB_PORT = 5432;

	/*
	 * The fixed customer-number display width (zero-padded), matching the legacy
	 * 10-digit CUSTOMER-NUMBER and the customer_number CHAR(10) relational key.
	 */
	private static final int CUSTOMER_NUMBER_LENGTH = 10;


	public CustomerResource()
	{
		sortOutLogging();
	}


	@POST
	@Produces(MediaType.APPLICATION_JSON)
	public Response createCustomerExternal(CustomerJSON customer)
	{
		logger.entering(this.getClass().getName(),
				CREATE_CUSTOMER_EXTERNAL + customer.toString());
		Response myResponse = createCustomerInternal(customer);
		logger.exiting(this.getClass().getName(), CREATE_CUSTOMER_EXTERNAL_EXIT,
				myResponse);
		return myResponse;
	}


	public Response createCustomerInternal(CustomerJSON customer)
	{
		logger.entering(this.getClass().getName(),
				CREATE_CUSTOMER_INTERNAL + customer.toString());
		ObjectNode response = mapper.createObjectNode();


		if(customer.getCustomerName() == null)
		{
			ObjectNode error = mapper.createObjectNode();
			error.put(JSON_ERROR_MSG,
					"Customer name is null");
			Response myResponse = Response.status(400).entity(error.toString())
					.build();
			logger.log(Level.WARNING,
					() -> "Customer name is null in CustomerResource.createCustomerInternal(), "
							+ customer.toString());
			logger.exiting(this.getClass().getName(),
					CREATE_CUSTOMER_INTERNAL_EXIT, myResponse);
			return myResponse;
		}

		String[] name = customer.getCustomerName().split(" ");

		if (!customer.validateTitle(name[0].trim()))
		{
			ObjectNode error = mapper.createObjectNode();
			error.put(JSON_ERROR_MSG,
					"Customer title " + name[0] + " is not valid");
			Response myResponse = Response.status(400).entity(error.toString())
					.build();
			logger.log(Level.WARNING,
					() -> "Invalid title in CustomerResource.createCustomerInternal(), "
							+ name[0].trim());
			logger.exiting(this.getClass().getName(),
					CREATE_CUSTOMER_INTERNAL_EXIT, myResponse);
			return myResponse;
		}

		if(customer.getSortCode() == null)
		{
			ObjectNode error = mapper.createObjectNode();
			error.put(JSON_ERROR_MSG,
					"Sort Code is null");
			Response myResponse = Response.status(400).entity(error.toString())
					.build();
			logger.log(Level.WARNING,
					() -> "Sort Code is null in CustomerResource.createCustomerInternal(), "
							+ customer.toString());
			logger.exiting(this.getClass().getName(),
					CREATE_CUSTOMER_INTERNAL_EXIT, myResponse);
			return myResponse;
		}

		Integer inputSortCode = Integer.parseInt(customer.getSortCode());

		if (!inputSortCode.equals(this.getSortCode()))
		{
			ObjectNode error = mapper.createObjectNode();
			error.put(JSON_ERROR_MSG, "Sortcode " + inputSortCode
					+ " not valid for this bank (" + this.getSortCode() + ")");
			Response myResponse = Response.status(400).entity(error.toString())
					.build();
			logger.log(Level.WARNING,
					() -> "Invalid sortcode CustomerResource.createCustomerInternal(), "
							+ inputSortCode.intValue());
			logger.exiting(this.getClass().getName(),
					CREATE_CUSTOMER_INTERNAL_EXIT, myResponse);
			return myResponse;
		}

		if(customer.getCustomerAddress() == null)
		{
			ObjectNode error = mapper.createObjectNode();
			error.put(JSON_ERROR_MSG,
					"Customer address is null");
			Response myResponse = Response.status(400).entity(error.toString())
					.build();
			logger.log(Level.WARNING,
					() -> "Customer address is null in CustomerResource.createCustomerInternal(), "
							+ customer.toString());
			logger.exiting(this.getClass().getName(),
					CREATE_CUSTOMER_INTERNAL_EXIT, myResponse);
			return myResponse;
		}

		if(customer.getDateOfBirth() == null)
		{
			ObjectNode error = mapper.createObjectNode();
			error.put(JSON_ERROR_MSG,
					"Date of Birth is null");
			Response myResponse = Response.status(400).entity(error.toString())
					.build();
			logger.log(Level.WARNING,
					() -> "Date of Birth is null in CustomerResource.createCustomerInternal(), "
							+ customer.toString());
			logger.exiting(this.getClass().getName(),
					CREATE_CUSTOMER_INTERNAL_EXIT, myResponse);
			return myResponse;
		}

		Date nowDate = new Date(Calendar.getInstance().getTimeInMillis());

		if(nowDate.before(customer.getDateOfBirth()))
		{
			ObjectNode error = mapper.createObjectNode();
			error.put(JSON_ERROR_MSG,
					"Date of Birth is in the future");
			Response myResponse = Response.status(400).entity(error.toString())
					.build();
			logger.log(Level.WARNING,
					() -> "Date of Birth is in the future in CustomerResource.createCustomerInternal(), "
							+ customer.toString());
			logger.exiting(this.getClass().getName(),
					CREATE_CUSTOMER_INTERNAL_EXIT, myResponse);
			return myResponse;
		}

// 150 years as milliseconds
		nowDate.setTime(nowDate.getTime() - (4733640000000L));

		if(nowDate.after(customer.getDateOfBirth()))
		{
			ObjectNode error = mapper.createObjectNode();
			error.put(JSON_ERROR_MSG,
					"Customer is over 150 years old");
			Response myResponse = Response.status(400).entity(error.toString())
					.build();
			logger.log(Level.WARNING,
					() -> "Customer is over 150 years old in CustomerResource.createCustomerInternal(), "
							+ customer.toString());
			logger.exiting(this.getClass().getName(),
					CREATE_CUSTOMER_INTERNAL_EXIT, myResponse);
			return myResponse;
		}

		customer.setSortCode(this.getSortCode().toString());

		String sortCodeString = padSortCode(this.getSortCode());
		String customerNameTrimmed = customer.getCustomerName().trim();
		String customerAddressTrimmed = customer.getCustomerAddress().trim();

		// Reproduce the original date-of-birth normalisation used for the JSON
		// response and the PROCTRAN audit record (no functional change).
		Calendar myCalendar = Calendar.getInstance();
		myCalendar.setTime(customer.getDateOfBirth());
		myCalendar.setTimeInMillis(myCalendar.getTimeInMillis() - myCalendar.getTimeZone().getOffset(myCalendar.getTimeInMillis()));

		java.sql.Date mySqlDate = new java.sql.Date(myCalendar.getTimeInMillis());
		mySqlDate.setTime(mySqlDate.getTime() - myCalendar.getTimeZone().getOffset(myCalendar.getTimeInMillis()));

		// Allocate a gap-free customer number from the customer_control counter
		// row and insert the customer, all in one transaction so that a failure
		// to append the PROCTRAN audit record rolls back BOTH the counter
		// increment and the insert (reproducing the CICS SYNCPOINT/ROLLBACK
		// boundary that the deleted JCICS task previously provided).
		try (Connection conn = getConnection())
		{
			conn.setAutoCommit(false);

			long newCustomerNumber;
			try (PreparedStatement allocate = conn.prepareStatement(
					"UPDATE customer_control SET last_customer_number = last_customer_number + 1, "
							+ "number_of_customers = number_of_customers + 1 "
							+ "WHERE sort_code = ? RETURNING last_customer_number"))
			{
				allocate.setString(1, sortCodeString);
				try (ResultSet rs = allocate.executeQuery())
				{
					if (!rs.next())
					{
						conn.rollback();
						ObjectNode error = mapper.createObjectNode();
						error.put(JSON_ERROR_MSG, "Failed to create customer");
						logger.severe("Failed to create customer");
						Response myResponse = Response.status(500)
								.entity(error.toString()).build();
						logger.exiting(this.getClass().getName(),
								CREATE_CUSTOMER_INTERNAL_EXIT, myResponse);
						return myResponse;
					}
					newCustomerNumber = rs.getLong(1);
				}
			}

			String paddedCustomerNumber = padCustomerNumber(
					Long.toString(newCustomerNumber));

			try (PreparedStatement insert = conn.prepareStatement(
					"INSERT INTO customer (sort_code, customer_number, name, address, "
							+ "date_of_birth, credit_score, cs_review_date) "
							+ "VALUES (?, ?, ?, ?, ?, ?, ?)"))
			{
				insert.setString(1, sortCodeString);
				insert.setString(2, paddedCustomerNumber);
				insert.setString(3, customerNameTrimmed);
				insert.setString(4, customerAddressTrimmed);
				insert.setDate(5, mySqlDate);
				// Credit scoring now belongs to bank-core; webui inserts a zero
				// baseline score and a current review date.
				insert.setInt(6, 0);
				insert.setDate(7, new java.sql.Date(
						Calendar.getInstance().getTimeInMillis()));
				insert.executeUpdate();
			}

			response.put(JSON_ID, paddedCustomerNumber);
			response.put(JSON_SORT_CODE, sortcode);
			response.put(JSON_CUSTOMER_NAME, customer.getCustomerName());
			response.put(JSON_CUSTOMER_ADDRESS, customer.getCustomerAddress());

			DateFormat myDateFormat = DateFormat.getDateInstance();
			Calendar newCalendar = Calendar.getInstance();
			newCalendar.setTime(myCalendar.getTime());
			response.put(JSON_DATE_OF_BIRTH,
					myDateFormat.format(newCalendar.getTime()));

			ProcessedTransactionResource myProcessedTransactionResource = new ProcessedTransactionResource();

			ProcessedTransactionCreateCustomerJSON myCreatedCustomer = new ProcessedTransactionCreateCustomerJSON();
			myCreatedCustomer.setAccountNumber("0");
			myCreatedCustomer.setCustomerDOB(mySqlDate);
			myCreatedCustomer.setCustomerName(customerNameTrimmed);
			myCreatedCustomer.setSortCode(sortCodeString);
			myCreatedCustomer.setCustomerNumber(paddedCustomerNumber);

			Response writeCreateCustomerResponse = myProcessedTransactionResource
					.writeCreateCustomerInternal(myCreatedCustomer);
			if (writeCreateCustomerResponse == null
					|| writeCreateCustomerResponse.getStatus() != 200)
			{
				ObjectNode error = mapper.createObjectNode();
				error.put(JSON_ERROR_MSG,
						"Failed to write to PROCTRAN data store");
				logger.severe(
						"Customer: createCustomer: Failed to write to PROCTRAN");
				conn.rollback();
				Response myResponse = Response.status(500)
						.entity(error.toString()).build();
				logger.exiting(this.getClass().getName(),
						CREATE_CUSTOMER_INTERNAL_EXIT, myResponse);
				return myResponse;
			}

			conn.commit();
		}
		catch (SQLException e)
		{
			ObjectNode error = mapper.createObjectNode();
			error.put(JSON_ERROR_MSG, "Failed to create customer");
			logger.severe("Failed to create customer " + e.getMessage());
			Response myResponse = Response.status(500).entity(error.toString())
					.build();
			logger.exiting(this.getClass().getName(),
					CREATE_CUSTOMER_INTERNAL_EXIT, myResponse);
			return myResponse;
		}

		Response myResponse = Response.status(201).entity(response.toString())
				.build();
		logger.exiting(this.getClass().getName(), CREATE_CUSTOMER_INTERNAL_EXIT,
				myResponse);
		return myResponse;
	}


	@PUT
	@Path("/{id}")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response updateCustomerExternal(@PathParam(JSON_ID) Long id,
			CustomerJSON customer)
	{
		logger.entering(this.getClass().getName(),
				UPDATE_CUSTOMER_EXTERNAL + id);
		Response myResponse = updateCustomerInternal(id, customer);
		logger.exiting(this.getClass().getName(), UPDATE_CUSTOMER_EXTERNAL + id,
				myResponse);
		return myResponse;

	}


	public Response updateCustomerInternal(@PathParam(JSON_ID) Long id,
			CustomerJSON customer)
	{
		logger.entering(this.getClass().getName(),
				UPDATE_CUSTOMER_INTERNAL + id);

		if(customer.getCustomerName() == null)
		{
			ObjectNode error = mapper.createObjectNode();
			error.put(JSON_ERROR_MSG,
					"Customer name is null");
			Response myResponse = Response.status(400).entity(error.toString())
					.build();
			logger.log(Level.WARNING,
					() -> "Customer name is null in CustomerResource.updateCustomerInternal(), "
							+ customer.toString());
			logger.exiting(this.getClass().getName(),
					UPDATE_CUSTOMER_INTERNAL_EXIT, myResponse);
			return myResponse;
		}

		String[] name = customer.getCustomerName().split(" ");

		if (!customer.validateTitle(name[0].trim()))
		// Customer title invalid
		{
			ObjectNode error = mapper.createObjectNode();
			error.put(JSON_ERROR_MSG,
					"Customer title " + name[0] + " is not valid");
			Response myResponse = Response.status(400).entity(error.toString())
					.build();
			logger.log(Level.WARNING,
					() -> "Invalid title in CustomerResource.updateCustomerInternal(), "
							+ name[0].trim());
			logger.exiting(this.getClass().getName(),
					UPDATE_CUSTOMER_INTERNAL_EXIT, myResponse);
			return myResponse;
		}


		if(customer.getSortCode() == null)
		{
			ObjectNode error = mapper.createObjectNode();
			error.put(JSON_ERROR_MSG,
					"Sort Code is null");
			Response myResponse = Response.status(400).entity(error.toString())
					.build();
			logger.log(Level.WARNING,
					() -> "Sort Code is null in CustomerResource.updateCustomerInternal(), "
							+ customer.toString());
			logger.exiting(this.getClass().getName(),
					UPDATE_CUSTOMER_INTERNAL_EXIT, myResponse);
			return myResponse;
		}
		Integer inputSortCode = Integer.parseInt(customer.getSortCode());

		if (!inputSortCode.equals(this.getSortCode()))
		// Sortcode invalid
		{
			ObjectNode error = mapper.createObjectNode();
			error.put(JSON_ERROR_MSG, "Sortcode " + inputSortCode
					+ " not valid for this bank (" + this.getSortCode() + ")");
			logger.log(Level.WARNING,
					() -> "Invalid sortcode in CustomerResource.updateCustomerInternal(), "
							+ inputSortCode);
			return Response.status(400).entity(error.toString()).build();
		}

		if(customer.getCustomerAddress() == null)
		{
			ObjectNode error = mapper.createObjectNode();
			error.put(JSON_ERROR_MSG,
					"Customer address is null");
			Response myResponse = Response.status(400).entity(error.toString())
					.build();
			logger.log(Level.WARNING,
					() -> "Customer address is null in CustomerResource.updateCustomerInternal(), "
							+ customer.toString());
			logger.exiting(this.getClass().getName(),
					UPDATE_CUSTOMER_INTERNAL_EXIT, myResponse);
			return myResponse;
		}

		ObjectNode response = mapper.createObjectNode();

		customer.setId(id.toString());
		customer.setSortCode(this.getSortCode().toString());

		String sortCodeString = padSortCode(this.getSortCode());
		String paddedCustomerNumber = padCustomerNumber(id.toString());

		// UPDCUST changes name and address only; balances, credit score, date of
		// birth and review date are never modified, and no PROCTRAN record is
		// written for an update (behavioural parity with the COBOL).
		try (Connection conn = getConnection())
		{
			int rowsUpdated;
			try (PreparedStatement update = conn.prepareStatement(
					"UPDATE customer SET name = ?, address = ? "
							+ "WHERE sort_code = ? AND customer_number = ?"))
			{
				update.setString(1, customer.getCustomerName());
				update.setString(2, customer.getCustomerAddress());
				update.setString(3, sortCodeString);
				update.setString(4, paddedCustomerNumber);
				rowsUpdated = update.executeUpdate();
			}

			if (rowsUpdated == 0)
			{
				ObjectNode error = mapper.createObjectNode();
				error.put(JSON_ERROR_MSG,
						CUSTOMER_PREFIX + id.toString() + " not found.");
				Response myResponse = Response.status(404)
						.entity(error.toString()).build();
				logger.log(Level.WARNING,
						() -> "Failed to find customer to update");
				logger.exiting(this.getClass().getName(),
						"updateCustomerInternal() exiting", myResponse);
				return myResponse;
			}

			try (PreparedStatement select = conn.prepareStatement(
					"SELECT sort_code, customer_number, name, address, date_of_birth "
							+ "FROM customer WHERE sort_code = ? AND customer_number = ?"))
			{
				select.setString(1, sortCodeString);
				select.setString(2, paddedCustomerNumber);
				try (ResultSet rs = select.executeQuery())
				{
					if (rs.next())
					{
						response.put(JSON_ID, rs.getString("customer_number"));
						response.put(JSON_SORT_CODE,
								rs.getString("sort_code").trim());
						response.put(JSON_CUSTOMER_NAME,
								rs.getString("name").trim());
						response.put(JSON_CUSTOMER_ADDRESS,
								rs.getString("address").trim());
						response.put(JSON_DATE_OF_BIRTH,
								rs.getDate("date_of_birth").toString().trim());
					}
				}
			}
		}
		catch (SQLException e)
		{
			ObjectNode error = mapper.createObjectNode();
			error.put(JSON_ERROR_MSG, "Failed to update customer");
			Response myResponse = Response.status(500).entity(error.toString())
					.build();
			logger.log(Level.WARNING,
					() -> "Failed to update customer " + e.getMessage());
			logger.exiting(this.getClass().getName(),
					"updateCustomerInternal() exiting", myResponse);
			return myResponse;
		}

		logger.exiting(this.getClass().getName(), UPDATE_CUSTOMER_INTERNAL + id,
				Response.status(200).entity(response.toString()).build());
		return Response.status(200).entity(response.toString()).build();
	}


	@GET
	@Path("/{id}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getCustomerExternal(@PathParam(JSON_ID) Long id)
	{
		logger.entering(this.getClass().getName(), GET_CUSTOMER_EXTERNAL + id);

		try
		{
			Response myResponse = getCustomerInternal(id);
			logger.exiting(this.getClass().getName(), "getCustomerExternal",
					myResponse);
			return myResponse;

		}
		catch (Exception ex)
		{
			// Log the exception
			logger.log(Level.WARNING,
					() -> "Exception in getCustomerExternal " + ex);
		}
		logger.exiting(this.getClass().getName(), GET_CUSTOMER_EXTERNAL_EXIT,
				null);

		return null;

	}


	public Response getCustomerInternal(@PathParam(JSON_ID) Long id)
	{
		logger.entering(this.getClass().getName(),
				"getCustomerInternal for customerNumber " + id);
		Integer sortCode = this.getSortCode();

		ObjectNode response = mapper.createObjectNode();

		if (id.longValue() < 0)
		{
			// Customer number cannot be negative
			response.put(JSON_ERROR_MSG, "Customer number cannot be negative");
			Response myResponse = Response.status(404)
					.entity(response.toString()).build();
			logger.log(Level.WARNING,
					() -> "Customer number supplied was negative in CustomerResource.getCustomerInternal");
			logger.exiting(this.getClass().getName(),
					GET_CUSTOMER_INTERNAL_EXIT, myResponse);
			return myResponse;
		}

		boolean found = false;
		try (Connection conn = getConnection();
				PreparedStatement select = conn.prepareStatement(
						"SELECT sort_code, customer_number, name, address, date_of_birth, "
								+ "credit_score, cs_review_date FROM customer "
								+ "WHERE sort_code = ? AND customer_number = ?"))
		{
			select.setString(1, padSortCode(sortCode));
			select.setString(2, padCustomerNumber(id.toString()));
			try (ResultSet rs = select.executeQuery())
			{
				if (rs.next())
				{
					found = true;
					response.put(JSON_SORT_CODE,
							rs.getString("sort_code").trim());
					response.put(JSON_ID,
							rs.getString("customer_number").trim());
					response.put(JSON_CUSTOMER_NAME,
							rs.getString("name").trim());
					response.put(JSON_CUSTOMER_ADDRESS,
							rs.getString("address").trim());
					response.put(JSON_DATE_OF_BIRTH,
							rs.getDate("date_of_birth").toString());
					response.put(JSON_CUSTOMER_CREDIT_SCORE,
							Integer.toString(rs.getInt("credit_score")).trim());
					response.put(JSON_CUSTOMER_REVIEW_DATE,
							rs.getDate("cs_review_date").toString());
				}
			}
		}
		catch (SQLException e)
		{
			logger.log(Level.WARNING,
					() -> "Exception reading customer " + id + " "
							+ e.getMessage());
		}

		if (!found)
		{
			response.put(JSON_ERROR_MSG, CUSTOMER_PREFIX + id + NOT_FOUND_MSG);
			Response myResponse = Response.status(404)
					.entity(response.toString()).build();
			logger.log(Level.INFO,
					() -> "Customer not found");
			logger.exiting(this.getClass().getName(),
					GET_CUSTOMER_INTERNAL_EXIT, myResponse);
			return myResponse;
		}

		logger.exiting(this.getClass().getName(),
				"getCustomerInternal(Long id)",
				Response.status(200).entity(response.toString()).build());
		return Response.status(200).entity(response.toString()).build();
	}


	@DELETE
	@Path("/{id}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response deleteCustomerExternal(@PathParam(JSON_ID) Long id)
	{
		logger.entering(this.getClass().getName(),
				"deleteCustomerExtnernal(Long id) for customerNumber " + id);
		Response myResponse = deleteCustomerInternal(id);
		logger.exiting(this.getClass().getName(),
				"deleteCustomerExternal(Long id)", myResponse);
		return myResponse;
	}


	public Response deleteCustomerInternal(Long id)
	{
		logger.entering(this.getClass().getName(),
				"deleteCustomerInternal(Long id) for customerNumber " + id);

		Integer sortCode = this.getSortCode();

		ObjectNode response = mapper.createObjectNode();

		if (id.longValue() < 0)
		{
			// Customer number cannot be negative
			response.put(JSON_ERROR_MSG, "Customer number cannot be negative");
			Response myResponse = Response.status(404)
					.entity(response.toString()).build();
			logger.log(Level.WARNING,
					() -> "Customer number supplied was negative in deleteCustomerInternal()");
			logger.exiting(this.getClass().getName(),
					DELETE_CUSTOMER_INTERNAL_EXIT, myResponse);
			return myResponse;
		}

		// First we need to delete all the accounts

		AccountsResource myAccountsResource = new AccountsResource();

		JsonNode myAccountsJSON;
		try
		{
			myAccountsJSON = mapper.readTree(myAccountsResource
					.getAccountsByCustomerInternal(id).getEntity().toString());

			//
			JsonNode accountsToDelete = myAccountsJSON.get("accounts");
			for (int i = 0; i < accountsToDelete.size(); i++)
			{

				JsonNode accountToDelete = accountsToDelete.get(i);
				Long accountToDeleteLong = Long
						.parseLong(accountToDelete.get(JSON_ID).asText());
				Response deleteAccountResponse = myAccountsResource
						.deleteAccountInternal(accountToDeleteLong);

				if (deleteAccountResponse.getStatus() == 404)
				{

					response.put(JSON_ERROR_MSG,
							"Error deleting account " + accountToDeleteLong
									+ " for customer " + id
									+ ",account not found");
					logger.log(Level.SEVERE,
							() -> "Customer: deleteAccount: Failed to delete account, not found");
					Response myResponse = Response.status(404)
							.entity(response.toString()).build();
					logger.log(Level.WARNING,
							() -> "Customer: deleteAccount: Failed to delete account, not found for customer "
									+ id + " in deleteCustomerInternal()");
					logger.exiting(this.getClass().getName(),
							DELETE_CUSTOMER_INTERNAL_EXIT, myResponse);
					return myResponse;
				}

				if (deleteAccountResponse.getStatus() != 200)
				{
					response.put(JSON_ERROR_MSG, "Error deleting account "
							+ accountToDeleteLong + " for customer " + id);
					logger.log(Level.SEVERE,
							() -> "Customer: deleteAccount: Failed to delete account, error");
					Response myResponse = Response
							.status(deleteAccountResponse.getStatus())
							.entity(response.toString()).build();
					logger.exiting(this.getClass().getName(),
							DELETE_CUSTOMER_INTERNAL_EXIT, myResponse);
					return myResponse;
				}
			}
		}
		catch (IOException e)
		{

			response.put(JSON_ERROR_MSG,
					"Error obtaining accounts to delete for customer " + id);
			Response myResponse = Response.status(500)
					.entity(response.toString()).build();
			logger.log(Level.WARNING,
					() -> "Error obtaining accounts to delete for customer "
							+ id + " in deleteCustomerInternal()");
			logger.exiting(this.getClass().getName(),
					GET_CUSTOMER_INTERNAL_EXIT, myResponse);
			return myResponse;
		}

		// If we are still here then we can try to delete the customer

		String sortCodeString = padSortCode(sortCode);
		String paddedCustomerNumber = padCustomerNumber(id.toString());

		String deletedSortCode;
		String deletedCustomerNumber;
		String deletedName;
		String deletedAddress;
		java.sql.Date deletedDob;
		int deletedCreditScore;
		java.sql.Date deletedReviewDate;

		try (Connection conn = getConnection())
		{
			conn.setAutoCommit(false);

			// Read the customer first so its terminal state can be returned and
			// recorded in PROCTRAN, then physically delete it and decrement the
			// customer_control counter, all in the same transaction.
			try (PreparedStatement select = conn.prepareStatement(
					"SELECT sort_code, customer_number, name, address, date_of_birth, "
							+ "credit_score, cs_review_date FROM customer "
							+ "WHERE sort_code = ? AND customer_number = ?"))
			{
				select.setString(1, sortCodeString);
				select.setString(2, paddedCustomerNumber);
				try (ResultSet rs = select.executeQuery())
				{
					if (!rs.next())
					{
						conn.rollback();
						response.put(JSON_ERROR_MSG,
								CUSTOMER_PREFIX + id + NOT_FOUND_MSG);
						Response myResponse = Response.status(404)
								.entity(response.toString()).build();
						logger.log(Level.WARNING,
								() -> "CustomerResource.deleteCustomerInternal() customer "
										+ id + NOT_FOUND_MSG);
						logger.exiting(this.getClass().getName(),
								DELETE_CUSTOMER_INTERNAL, myResponse);
						return myResponse;
					}
					deletedSortCode = rs.getString("sort_code");
					deletedCustomerNumber = rs.getString("customer_number");
					deletedName = rs.getString("name");
					deletedAddress = rs.getString("address");
					deletedDob = rs.getDate("date_of_birth");
					deletedCreditScore = rs.getInt("credit_score");
					deletedReviewDate = rs.getDate("cs_review_date");
				}
			}

			try (PreparedStatement delete = conn.prepareStatement(
					"DELETE FROM customer WHERE sort_code = ? AND customer_number = ?"))
			{
				delete.setString(1, sortCodeString);
				delete.setString(2, paddedCustomerNumber);
				delete.executeUpdate();
			}

			try (PreparedStatement decrement = conn.prepareStatement(
					"UPDATE customer_control SET number_of_customers = number_of_customers - 1 "
							+ "WHERE sort_code = ?"))
			{
				decrement.setString(1, sortCodeString);
				decrement.executeUpdate();
			}

			response.put(JSON_SORT_CODE, deletedSortCode.trim());
			response.put(JSON_ID, deletedCustomerNumber.trim());
			response.put(JSON_CUSTOMER_NAME, deletedName.trim());
			response.put(JSON_CUSTOMER_ADDRESS, deletedAddress.trim());
			response.put(JSON_DATE_OF_BIRTH, deletedDob.toString());
			response.put(JSON_CUSTOMER_CREDIT_SCORE,
					Integer.toString(deletedCreditScore));
			response.put(JSON_CUSTOMER_REVIEW_DATE,
					deletedReviewDate.toString());

			ProcessedTransactionResource myProcessedTransactionResource = new ProcessedTransactionResource();

			ProcessedTransactionDeleteCustomerJSON myDeletedCustomer = new ProcessedTransactionDeleteCustomerJSON();
			myDeletedCustomer.setAccountNumber("0");
			myDeletedCustomer.setCustomerDOB(deletedDob);
			myDeletedCustomer.setCustomerName(deletedName);


			myDeletedCustomer.setSortCode(deletedSortCode);
			myDeletedCustomer.setCustomerNumber(deletedCustomerNumber);


			Response writeDeleteCustomerResponse = myProcessedTransactionResource
					.writeDeleteCustomerInternal(myDeletedCustomer);
			if (writeDeleteCustomerResponse.getStatus() != 200)
			{
				ObjectNode error = mapper.createObjectNode();
				error.put(JSON_ERROR_MSG,
						"Failed to write to PROCTRAN data store");
				logger.log(Level.SEVERE,
						() -> "Customer: deleteCustomer: Failed to write to proctran");
				conn.rollback();
				Response myResponse = Response.status(500)
						.entity(error.toString()).build();
				logger.log(Level.WARNING,
						() -> "CustomerResource.deleteCustomerInternal() failed to write to proctran");
				logger.exiting(this.getClass().getName(),
						DELETE_CUSTOMER_INTERNAL, myResponse);
				return myResponse;
			}

			conn.commit();
		}
		catch (SQLException e)
		{
			ObjectNode error = mapper.createObjectNode();
			error.put(JSON_ERROR_MSG,
					"Error obtaining accounts to delete for customer " + id);
			Response myResponse = Response.status(500)
					.entity(error.toString()).build();
			logger.log(Level.WARNING,
					() -> "Error deleting customer " + id + " " + e.getMessage());
			logger.exiting(this.getClass().getName(), DELETE_CUSTOMER_INTERNAL,
					myResponse);
			return myResponse;
		}

		logger.exiting(this.getClass().getName(),
				"deleteCustomerInternal(Long id)",
				Response.status(200).entity(response.toString()).build());
		return Response.status(200).entity(response.toString()).build();
	}


	@GET
	@Path("/all/town/{town}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getCustomersTownExternal(@PathParam("town") String town)
	{

		logger.entering(this.getClass().getName(),
				"getCustomersTownExternal(String town) for town " + town);
		Response myResponse = getCustomersTownInternal(town);
		logger.exiting(this.getClass().getName(),
				"getCustomersTownExternal(String town)", myResponse);
		return myResponse;
	}


	public Response getCustomersTownInternal(String town)
	{

		logger.entering(this.getClass().getName(),
				"getCustomersTownInternal(String town) for town " + town);

		ArrayNode allCustomers = mapper.createArrayNode();

		try (Connection conn = getConnection();
				PreparedStatement select = conn.prepareStatement(
						"SELECT customer_number, name, address, date_of_birth "
								+ "FROM customer WHERE sort_code = ? "
								+ "AND strpos(address, ?) > 0 "
								+ "ORDER BY customer_number"))
		{
			select.setString(1, padSortCode(this.getSortCode()));
			select.setString(2, town);
			try (ResultSet rs = select.executeQuery())
			{
				while (rs.next())
				{
					ObjectNode response = mapper.createObjectNode();
					response.put(JSON_ID,
							rs.getString("customer_number").trim());
					response.put(JSON_CUSTOMER_NAME,
							rs.getString("name").trim());
					response.put(JSON_CUSTOMER_ADDRESS,
							rs.getString("address").trim());
					response.put(JSON_DATE_OF_BIRTH,
							formatDayMonthYear(rs.getDate("date_of_birth")));
					allCustomers.add(response);
				}
			}
		}
		catch (SQLException e)
		{
			logger.log(Level.WARNING,
					() -> "Exception listing customers by town " + town + " "
							+ e.getMessage());
		}

		logger.exiting(this.getClass().getName(),
				"getCustomersTownInternal(String town)",
				Response.status(200).entity(allCustomers.toString()).build());
		return Response.status(200).entity(allCustomers.toString()).build();
	}


	@GET
	@Path("/all/surname/{surname}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getCustomersSurnameExternal(
			@PathParam("surname") String surname)
	{
		logger.entering(this.getClass().getName(),
				"getCustomersSurnameExternal(String surname) for surname "
						+ surname);
		Response myResponse = getCustomersSurnameInternal(surname);
		logger.exiting(this.getClass().getName(),
				"getCustomersSurnameExternal(String surname)", myResponse);
		return myResponse;
	}


	public Response getCustomersSurnameInternal(String surname)
	{

		logger.entering(this.getClass().getName(),
				"getCustomersSurnameInternal(String surname) for surname "
						+ surname);

		ArrayNode allCustomers = mapper.createArrayNode();

		try (Connection conn = getConnection();
				PreparedStatement select = conn.prepareStatement(
						"SELECT customer_number, name, address, date_of_birth "
								+ "FROM customer WHERE sort_code = ? "
								+ "AND strpos(name, ?) > 0 "
								+ "ORDER BY customer_number"))
		{
			select.setString(1, padSortCode(this.getSortCode()));
			select.setString(2, surname);
			try (ResultSet rs = select.executeQuery())
			{
				while (rs.next())
				{
					ObjectNode response = mapper.createObjectNode();
					response.put(JSON_ID,
							rs.getString("customer_number").trim());
					response.put(JSON_CUSTOMER_NAME,
							rs.getString("name").trim());
					response.put(JSON_CUSTOMER_ADDRESS,
							rs.getString("address").trim());
					response.put(JSON_DATE_OF_BIRTH,
							formatDayMonthYear(rs.getDate("date_of_birth")));
					allCustomers.add(response);
				}
			}
		}
		catch (SQLException e)
		{
			logger.log(Level.WARNING,
					() -> "Exception listing customers by surname " + surname
							+ " " + e.getMessage());
		}

		logger.exiting(this.getClass().getName(),
				"getCustomersSurnameInternal(String surname)",
				Response.status(200).entity(allCustomers.toString()).build());
		return Response.status(200).entity(allCustomers.toString()).build();
	}


	@GET
	@Path("/all/age/{age}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getCustomersAgeExternal(@PathParam("age") String age)
	{
		logger.entering(this.getClass().getName(),
				"getCustomersAgeExternal(String age) for age " + age);
		Response myResponse = getCustomersAgeInternal(age);
		logger.entering(this.getClass().getName(),
				"getCustomersAgeExternal(String age)", myResponse);
		return myResponse;
	}


	public Response getCustomersAgeInternal(String age)
	{

		logger.entering(this.getClass().getName(),
				"getCustomersAgeInternalInternal(String age) for age " + age);

		ArrayNode allCustomers = mapper.createArrayNode();
		ObjectNode response = mapper.createObjectNode();

		int requestedAge = Integer.parseInt(age);

		try (Connection conn = getConnection();
				PreparedStatement select = conn.prepareStatement(
						"SELECT customer_number, name, address, date_of_birth "
								+ "FROM customer WHERE sort_code = ? "
								+ "ORDER BY customer_number"))
		{
			select.setString(1, padSortCode(this.getSortCode()));
			try (ResultSet rs = select.executeQuery())
			{
				while (rs.next())
				{
					java.sql.Date dob = rs.getDate("date_of_birth");
					if (customerAgeInYears(dob) == requestedAge)
					{
						ObjectNode customer = mapper.createObjectNode();
						customer.put(JSON_ID,
								rs.getString("customer_number").trim());
						customer.put(JSON_CUSTOMER_NAME,
								rs.getString("name").trim());
						customer.put(JSON_CUSTOMER_ADDRESS,
								rs.getString("address").trim());
						customer.put(JSON_DATE_OF_BIRTH,
								formatDayMonthYear(dob));
						allCustomers.add(customer);
					}
				}
			}
		}
		catch (SQLException e)
		{
			logger.log(Level.WARNING,
					() -> "Exception listing customers by age " + age + " "
							+ e.getMessage());
		}

		logger.exiting(this.getClass().getName(),
				"getCustomersAgeInternal(String age)",
				Response.status(200).entity(allCustomers.toString()).build());
		response.set(JSON_CUSTOMERS, allCustomers);
		response.put(JSON_NUMBER_OF_CUSTOMERS, allCustomers.size());
		return Response.status(200).entity(response.toString()).build();
	}


	private Integer getSortCode()
	{
		logger.entering(this.getClass().getName(), "getSortCode()");
		if (sortcode == null)
		{
			SortCodeResource mySortCodeResource = new SortCodeResource();
			Response mySortCodeJSON = mySortCodeResource.getSortCode();
			CustomerResource.setSortcode(
					((String) mySortCodeJSON.getEntity()).substring(13, 19));
		}
		logger.exiting(this.getClass().getName(), "getSortCode()", sortcode);
		return Integer.parseInt(sortcode);
	}


	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public Response getCustomersExternal(@QueryParam("limit") Integer limit,
			@QueryParam("offset") Integer offset,
			@QueryParam("countOnly") Boolean countOnly)
	{
		logger.entering(this.getClass().getName(),
				"getCustomersExternal(Integer limit, Integer offset, Boolean countOnly) "
						+ limit + " " + offset + " " + countOnly);
		boolean countOnlyReal = false;
		if (countOnly != null)
		{
			countOnlyReal = countOnly.booleanValue();
		}
		Response myResponse = getCustomersInternal(limit, offset,
				countOnlyReal);
		logger.exiting(this.getClass().getName(),
				"getCustomersExternal(Integer limit, Integer offset, Boolean countOnly)",
				myResponse);
		return myResponse;
	}


	public Response getCustomersInternal(@QueryParam("limit") Integer limit,
			@QueryParam("offset") Integer offset, boolean countOnly)
	{
		logger.entering(this.getClass().getName(),
				"getCustomersInternal(Integer limit, Integer offset, Boolean countOnly) "
						+ limit + " " + offset + " " + countOnly);
		Integer sortCode = this.getSortCode();

		ObjectNode response = mapper.createObjectNode();
		ArrayNode customers = null;

		if (offset == null)
		{
			offset = 0;
		}

		if (limit == null)
		{
			limit = 250000;
		}

		if (limit.intValue() == 0)
		{
			limit = 250000;
		}

		if (countOnly)
		{
			long customerCount = -1L;
			try (Connection conn = getConnection();
					PreparedStatement select = conn.prepareStatement(
							"SELECT number_of_customers FROM customer_control "
									+ "WHERE sort_code = ?"))
			{
				select.setString(1, padSortCode(sortCode));
				try (ResultSet rs = select.executeQuery())
				{
					if (rs.next())
					{
						customerCount = rs.getLong("number_of_customers");
					}
				}
			}
			catch (SQLException e)
			{
				logger.severe("Error reading control record for customer file "
						+ e.getMessage());
			}
			response.put(JSON_NUMBER_OF_CUSTOMERS, customerCount);
		}
		else
		{
			boolean dataAccessFailed = false;
			customers = mapper.createArrayNode();
			try (Connection conn = getConnection();
					PreparedStatement select = conn.prepareStatement(
							"SELECT sort_code, customer_number, name, address, date_of_birth, "
									+ "credit_score, cs_review_date FROM customer "
									+ "WHERE sort_code = ? ORDER BY customer_number "
									+ "LIMIT ? OFFSET ?"))
			{
				select.setString(1, padSortCode(sortCode));
				select.setInt(2, limit.intValue());
				select.setInt(3, offset.intValue());
				try (ResultSet rs = select.executeQuery())
				{
					while (rs.next())
					{
						ObjectNode customer = mapper.createObjectNode();
						customer.put(JSON_SORT_CODE,
								rs.getString("sort_code").trim());
						customer.put(JSON_CUSTOMER_NAME,
								rs.getString("name").trim());
						customer.put(JSON_ID,
								rs.getString("customer_number").trim());
						customer.put(JSON_CUSTOMER_ADDRESS,
								rs.getString("address").trim());
						customer.put(JSON_DATE_OF_BIRTH,
								rs.getDate("date_of_birth").toString());
						customer.put(JSON_CUSTOMER_CREDIT_SCORE,
								Integer.toString(rs.getInt("credit_score"))
										.trim());
						customer.put(JSON_CUSTOMER_REVIEW_DATE,
								rs.getDate("cs_review_date").toString());
						customers.add(customer);
					}
				}
			}
			catch (SQLException e)
			{
				dataAccessFailed = true;
				logger.severe("Error listing customers " + e.getMessage());
			}

			if (!dataAccessFailed)
			{
				response.set(JSON_CUSTOMERS, customers);
				response.put(JSON_NUMBER_OF_CUSTOMERS, customers.size());
			}
			else
			{

				response.put(JSON_ERROR_MSG,
						"Customers cannot be listed");
				logger.log(Level.WARNING, () -> this.getClass().getName()
						+ ".getCustomersInternal() "
						+ " Customers cannot be listed");
				Response myResponse = Response.status(404)
						.entity(response.toString()).build();
				logger.exiting(this.getClass().getName(),
						"getCustomersInternal()", myResponse);
				return myResponse;
			}
		}

		logger.exiting(this.getClass().getName(),
				"getCustomersInternal(Integer limit, Integer offset, Boolean countOnly)",
				Response.status(200).entity(response.toString()).build());
		return Response.status(200).entity(response.toString()).build();

	}


	@GET
	@Path("/name")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getCustomersByNameExternal(@QueryParam("name") String name,
			@QueryParam("limit") Integer limit,
			@QueryParam("offset") Integer offset,
			@QueryParam("countOnly") Boolean countOnly)
	{
		logger.entering(this.getClass().getName(),
				"getCustomersByNameExternal(String name, Integer limit, Integer offset, Boolean countOnly) "
						+ name + " " + limit + " " + offset + " " + countOnly);

		boolean countOnlyReal = false;
		if (countOnly != null)
		{
			countOnlyReal = countOnly.booleanValue();
		}
		if (offset == null)
		{
			offset = 0;
		}

		if (limit == null)
		{
			limit = 250000;
		}

		if (limit.intValue() == 0)
		{
			limit = 250000;
		}
		Response myResponse = getCustomersByNameInternal(name, limit, offset,
				countOnlyReal);
		logger.exiting(this.getClass().getName(),
				"getCustomersByNameExternal(String name, Integer limit, Integer offset, Boolean countOnly)",
				myResponse);
		return myResponse;
	}


	public Response getCustomersByNameInternal(@QueryParam("name") String name,
			@QueryParam("limit") int limit, @QueryParam("offset") int offset,
			boolean countOnly)
	{
		logger.entering(this.getClass().getName(),
				"getCustomersByNameInternal(String name, Integer limit, Integer offset, Boolean countOnly) "
						+ name + " " + limit + " " + offset + " " + countOnly);
		Integer sortCode = this.getSortCode();

		ObjectNode response = mapper.createObjectNode();
		ArrayNode customers = null;

		if (countOnly)
		{
			long numberOfCustomers = -1L;
			try (Connection conn = getConnection();
					PreparedStatement select = conn.prepareStatement(
							"SELECT COUNT(*) FROM customer WHERE sort_code = ? "
									+ "AND strpos(name, ?) > 0"))
			{
				select.setString(1, padSortCode(sortCode));
				select.setString(2, name);
				try (ResultSet rs = select.executeQuery())
				{
					if (rs.next())
					{
						numberOfCustomers = rs.getLong(1);
					}
				}
			}
			catch (SQLException e)
			{
				logger.log(Level.FINE, e::getMessage);
			}
			response.put(JSON_NUMBER_OF_CUSTOMERS, numberOfCustomers);
		}
		else
		{
			boolean dataAccessFailed = false;
			customers = mapper.createArrayNode();
			try (Connection conn = getConnection();
					PreparedStatement select = conn.prepareStatement(
							"SELECT sort_code, customer_number, name, address, date_of_birth, "
									+ "credit_score, cs_review_date FROM customer "
									+ "WHERE sort_code = ? AND strpos(name, ?) > 0 "
									+ "ORDER BY customer_number LIMIT ?"))
			{
				select.setString(1, padSortCode(sortCode));
				select.setString(2, name);
				select.setInt(3, limit);
				try (ResultSet rs = select.executeQuery())
				{
					while (rs.next())
					{
						ObjectNode customer = mapper.createObjectNode();
						customer.put(JSON_SORT_CODE,
								rs.getString("sort_code").trim());
						customer.put(JSON_CUSTOMER_NAME,
								rs.getString("name").trim());
						customer.put(JSON_ID,
								rs.getString("customer_number").trim());
						customer.put(JSON_CUSTOMER_ADDRESS,
								rs.getString("address").trim());
						customer.put(JSON_DATE_OF_BIRTH,
								rs.getDate("date_of_birth").toString());
						customer.put(JSON_CUSTOMER_CREDIT_SCORE,
								Integer.toString(rs.getInt("credit_score"))
										.trim());
						customer.put(JSON_CUSTOMER_REVIEW_DATE,
								rs.getDate("cs_review_date").toString());
						customers.add(customer);
					}
				}
			}
			catch (SQLException e)
			{
				dataAccessFailed = true;
				logger.severe("Error listing customers by name "
						+ e.getMessage());
			}

			if (!dataAccessFailed)
			{
				response.set(JSON_CUSTOMERS, customers);
				response.put(JSON_NUMBER_OF_CUSTOMERS, customers.size());
			}
			else
			{
				response.put(JSON_ERROR_MSG,
						"Customers cannot be listed");
				logger.log(Level.WARNING, () -> this.getClass().getName()
						+ ".getCustomersByNameInternal() "
						+ " Customers cannot be listed");
				Response myResponse = Response.status(404)
						.entity(response.toString()).build();
				logger.exiting(this.getClass().getName(),
						"getCustomersByNameInternal()", myResponse);
				return myResponse;
			}
		}
		logger.exiting(this.getClass().getName(),
				"getCustomersByNameInternal(String name, Integer limit, Integer offset, Boolean countOnly)",
				Response.status(200).entity(response.toString()).build());
		return Response.status(200).entity(response.toString()).build();

	}


	/**
	 * Computes a customer's age in completed years from the date of birth,
	 * reproducing the legacy age calculation used by the customer search.
	 *
	 * @param dob the date of birth
	 * @return the age in completed years
	 */
	private int customerAgeInYears(java.util.Date dob)
	{
		Calendar nowCalendar = Calendar.getInstance();
		Calendar birthCalendar = Calendar.getInstance();
		birthCalendar.setTime(dob);
		int age = nowCalendar.get(Calendar.YEAR)
				- birthCalendar.get(Calendar.YEAR);
		if (birthCalendar.get(Calendar.MONTH) > nowCalendar.get(Calendar.MONTH))
		{
			return age - 1;
		}
		if (birthCalendar.get(Calendar.MONTH) == nowCalendar.get(Calendar.MONTH)
				&& birthCalendar.get(Calendar.DAY_OF_MONTH) > nowCalendar
						.get(Calendar.DAY_OF_MONTH))
		{
			return age - 1;
		}
		return age;
	}


	/**
	 * Formats a date as {@code D-M-YYYY} (day-month-year, no leading zeros),
	 * matching the date string the town/surname/age search endpoints have always
	 * emitted for the {@code dateOfBirth} field.
	 *
	 * @param date the date to format
	 * @return the {@code D-M-YYYY} string
	 */
	private String formatDayMonthYear(java.util.Date date)
	{
		Calendar dobCalendar = Calendar.getInstance();
		dobCalendar.setTime(date);
		Integer dobDD = dobCalendar.get(Calendar.DAY_OF_MONTH);
		String dateOfBirth = dobDD.toString();
		dateOfBirth = dateOfBirth.concat("-");

		Integer dobMM = dobCalendar.get(Calendar.MONTH) + 1;
		dateOfBirth = dateOfBirth.concat(dobMM.toString());
		dateOfBirth = dateOfBirth.concat("-");

		Integer dobYYYY = dobCalendar.get(Calendar.YEAR);
		dateOfBirth = dateOfBirth.concat(dobYYYY.toString());

		return dateOfBirth;
	}


	/**
	 * Left-zero-pads a customer number to the fixed display width of
	 * {@value #CUSTOMER_NUMBER_LENGTH} digits, reproducing the legacy
	 * fixed-width CUSTOMER-NUMBER / customer_number CHAR(10) representation.
	 *
	 * @param customerNumber the customer number as a string
	 * @return the zero-padded 10-character customer number
	 */
	private static String padCustomerNumber(String customerNumber)
	{
		StringBuilder myStringBuilder = new StringBuilder();
		for (int z = customerNumber.length(); z < CUSTOMER_NUMBER_LENGTH; z++)
		{
			myStringBuilder.append('0');
		}
		myStringBuilder.append(customerNumber);
		return myStringBuilder.toString();
	}


	/**
	 * Renders the bank sort code as the fixed 6-character string used by the
	 * relational {@code sort_code CHAR(6)} key.
	 *
	 * @param sortCode the numeric sort code
	 * @return the 6-character zero-padded sort code
	 */
	private static String padSortCode(Integer sortCode)
	{
		return String.format("%06d", sortCode);
	}


	/**
	 * Opens a JDBC connection to the shared PostgreSQL bank-core store. The host
	 * is read from the {@code DB_HOST} environment variable (defaulting to
	 * {@code localhost}); the database, user and password are all {@code cbsa}.
	 *
	 * @return an open {@link Connection} that the caller must close
	 * @throws SQLException if the connection cannot be established
	 */
	private Connection getConnection() throws SQLException
	{
		String host = System.getenv("DB_HOST");
		if (host == null || host.trim().isEmpty())
		{
			host = "localhost";
		}
		String url = "jdbc:postgresql://" + host + ":" + DB_PORT + "/"
				+ DB_NAME;
		return DriverManager.getConnection(url, DB_USER, DB_PASSWORD);
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


	private static void setSortcode(String sortcodeIn)
	{
		sortcode = sortcodeIn;
	}
}
