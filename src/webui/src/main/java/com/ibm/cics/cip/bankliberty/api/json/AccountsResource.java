/*
 *
 *    Copyright IBM Corp. 2023,2026
 *
 */

package com.ibm.cics.cip.bankliberty.api.json;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Calendar;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * This class describes the methods of the AccountsResource.
 *
 * <p>
 * <b>Tech-stack migration note (CBSA mainframe &rarr; standalone Java).</b> The
 * external JAX-RS contract of this resource is FROZEN and reproduced verbatim:
 * the class-level {@code @Path("/account")}, every endpoint path / HTTP verb /
 * {@code @Produces} / {@code @Consumes}, every {@code @PathParam} /
 * {@code @QueryParam}, every JSON field name, every HTTP status code and the
 * {@code errorMessage} envelope are unchanged. Only the implementation was
 * re-pointed off the decommissioned mainframe libraries:
 * </p>
 * <ul>
 * <li>The legacy WebSphere Liberty JSON API is replaced by Jackson
 * ({@link ObjectMapper}/{@link ObjectNode}/{@link ArrayNode}); the JSON field
 * names are emitted byte-for-byte identically.</li>
 * <li>The legacy JCICS data path (the deleted shared data-access base class, the
 * JCICS transaction rollback hooks and the deleted Db2/VSAM access class) is
 * replaced by a thin JDBC
 * client against the shared bank-core PostgreSQL store
 * ({@code jdbc:postgresql://${DB_HOST:localhost}:5432/cbsa}). Connection and
 * transaction lifecycle is managed locally with {@code @Transactional}-style
 * commit / rollback boundaries that reproduce the CICS SYNCPOINT / ROLLBACK
 * semantics the deleted JCICS task previously provided.</li>
 * <li>All monetary values remain {@link java.math.BigDecimal}; no
 * {@code double} / {@code float} is used anywhere in financial logic.</li>
 * </ul>
 *
 */

@Path("/account")
public class AccountsResource
{

	private static Logger logger = Logger
			.getLogger("com.ibm.cics.cip.bankliberty.api.json");

	private static final String NOT_SUPPORTED = " is not supported.";

	private static final String ACC_TYPE_STRING = "Account type ";

	private static final String INTEREST_RATE = "Interest rate ";

	private static final String OVERDRAFT_LIMIT = "Overdraft limit ";

	private static final String CUSTOMER_NUMBER = "Customer Number ";

	private static final String IS_NULL = "is null";

	private static final String CREATE_ACCOUNT_INTERNAL = "createAccountInternal(AccountJSON account)";

	private static final String CREATE_ACCOUNT_EXTERNAL = "createAccountExternal(AccountJSON account)";

	private static final String GET_ACCOUNT_INTERNAL = "getAccountInternal(Long accountNumber)";

	private static final String GET_ACCOUNTS_BY_CUSTOMER_INTERNAL = "getAccountsByCustomerInternal(Long customerNumber, boolean countOnly)";

	private static final String UPDATE_ACCOUNT_INTERNAL = "updateAccountInternal(Long id, AccountJSON account)";

	private static final String DEBIT_ACCOUNT_INTERNAL = "debitAccountInternal(String accountNumber, DebitCreditAccountJSON dbcr)";

	private static final String CREDIT_ACCOUNT_INTERNAL = "creditAccountInternal(String accountNumber, DebitCreditAccountJSON dbcr)";

	private static final String TRANSFER_LOCAL_INTERNAL = "transferLocalInternal(String accountNumber, TransferLocalJSON transferLocal)";

	private static final String DEBIT_CREDIT_ACCOUNT = "debitCreditAccount(Long sortCode, String accountNumber, BigDecimal apiAmount, boolean debitAccount)";

	private static final String DELETE_ACCOUNT = "deleteAccountInternal(Long accountNumber)";

	private static final String GET_ACCOUNTS_BY_BALANCE_WITH_OFFSET_AND_LIMIT_INTERNAL = "getAccountsByBalanceWithOffsetAndLimitInternal(BigDecimal balance, String operator, Integer offset, Integer limit, Boolean countOnly";

	private static final String CLASS_NAME_MSG = " in the account data store";

	private static final String INTEREST_RATE_LESS_THAN_ZERO = "Interest rate cannot be greater than 9999.99%.";

	private static final String INTEREST_RATE_TOO_HIGH = "Interest rate cannot be greater than 9999.99%.";

	private static final String GET_ACCOUNTS_EXTERNAL = "getAccountsExternal(Boolean countOnly)";

	private static final String GET_ACCOUNTS_INTERNAL = "getAccountsInternal(Boolean countOnly)";

	private static final String NOT_VALID_FOR_THIS_BANK = "not valid for this bank (";

	private static final String SORT_CODE_LITERAL = "Sortcode ";

	private static final String ACCOUNT_LITERAL = "Account ";

	private static final String CANNOT_BE_FOUND = " cannot be found.";

	private static final String CANNOT_BE_ACCESSED = " cannot be accessed.";

	private static final String CUSTOMER_NUMBER_LITERAL = "Customer number ";

	private static final String FAILED_TO_READ = "Failed to read account ";

	private static final String PROCTRAN_WRITE_FAILURE = "Failed to write to PROCTRAN data store";

	private static final String DB2_READ_FAILURE = "Unable to access account store";

	private static final String ACCOUNT_CREATE_FAILURE = "Failed to create account in the account data store";

	private static final String IN_DEBIT_ACCOUNT = " in debitAccount ";

	private static final String IN_CREDIT_ACCOUNT = " in creditAccount ";

	private static final String NEED_DIFFERENT_ACCOUNTS = "Source and target accounts must be different";

	private static final String SOURCE_ACCOUNT_NUMBER = "Source account number ";

	private static final String TARGET_ACCOUNT_NUMBER = "Target account number ";

	private static final String JSON_NUMBER_OF_ACCOUNTS = "numberOfAccounts";

	private static final String JSON_SORT_CODE = "sortCode";

	private static final String JSON_CUSTOMER_NUMBER = "customerNumber";

	private static final String JSON_ACCOUNT_TYPE = "accountType";

	private static final String JSON_AVAILABLE_BALANCE = "availableBalance";

	private static final String JSON_ACTUAL_BALANCE = "actualBalance";

	private static final String JSON_INTEREST_RATE = "interestRate";

	private static final String JSON_OVERDRAFT = "overdraft";

	private static final String JSON_LAST_STATEMENT_DATE = "lastStatementDate";

	private static final String JSON_NEXT_STATEMENT_DATE = "nextStatementDate";

	private static final String JSON_DATE_OPENED = "dateOpened";

	private static final String JSON_ACCOUNTS = "accounts";

	private static final String JSON_ERROR_MSG = "errorMessage";

	private static final int MAXIMUM_ACCOUNTS_PER_CUSTOMER = 10;

	private static final int CUSTOMER_NUMBER_LENGTH = 10;

	private static final int ACCOUNT_NUMBER_LENGTH = 8;

	private static final int SORT_CODE_LENGTH = 6;

	/**
	 * Shared, thread-safe Jackson mapper used to build and serialize every JSON
	 * response envelope for this resource. Replaces the decommissioned WebSphere
	 * Liberty JSON API; the field names and structure are reproduced byte-for-byte.
	 */
	private static final ObjectMapper mapper = new ObjectMapper();

	/**
	 * Shared bank-core PostgreSQL store coordinates. The database, user and
	 * password are all {@code cbsa} (per the setup constraint); {@code DB_HOST}
	 * is overridable via the environment, defaulting to {@code localhost}. This
	 * replaces the deleted JCICS / Db2 / VSAM data path.
	 */
	private static final String DB_NAME = "cbsa";

	private static final String DB_USER = "cbsa";

	private static final String DB_PASSWORD = "cbsa";

	private static final int DB_PORT = 5432;


	public AccountsResource()
	{
		/**
		 * Constructor
		 */
		sortOutLogging();
	}


	@POST
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)

	public Response createAccountExternal(AccountJSON account)
	{
		/**
		 * This method is the externally-invoked entry point. Connection and
		 * transaction lifecycle is now handled inside createAccountInternal
		 * against the bank-core PostgreSQL store, so there is no longer any
		 * JCICS / Db2 connection bookkeeping to perform here.
		 */
		logger.entering(this.getClass().getName(),
				CREATE_ACCOUNT_EXTERNAL + " for account " + account.toString());

		Response myResponse = createAccountInternal(account);

		logger.exiting(this.getClass().getName(), CREATE_ACCOUNT_EXTERNAL,
				myResponse);
		return myResponse;

	}


	public Response createAccountInternal(
			/**
			 * Internal methods can be called by either the external methods, or
			 * another part of the application
			 */
			AccountJSON account)
	{
		logger.entering(this.getClass().getName(),
				CREATE_ACCOUNT_INTERNAL + " for account " + account.toString());
		Response myResponse = null;

		ObjectNode error = validateNewAccount(account);
		if (error != null)
		{
			myResponse = Response.status(400).entity(error.toString()).build();
			logger.exiting(this.getClass().getName(), CREATE_ACCOUNT_INTERNAL,
					myResponse);
			return myResponse;

		}

		ObjectNode response = mapper.createObjectNode();
		AccountsResource thisAccountsResource = new AccountsResource();

		Long customerNumberLong = Long.parseLong(account.getCustomerNumber());
		JsonNode myAccountsJSON = null;
		try
		{
			Response accountsOfThisCustomer = thisAccountsResource
					.getAccountsByCustomerInternal(customerNumberLong);
			if (accountsOfThisCustomer.getStatus() != 200)
			{
				// If accountsOfThisCustomer returns status 404, create new
				// error node containing the error message
				if (accountsOfThisCustomer.getStatus() == 404)
				{
					error = mapper.createObjectNode();
					error.put(JSON_ERROR_MSG, CUSTOMER_NUMBER_LITERAL
							+ customerNumberLong.longValue() + CANNOT_BE_FOUND);
					logger.log(Level.WARNING, () -> CUSTOMER_NUMBER_LITERAL
							+ customerNumberLong.longValue() + CANNOT_BE_FOUND);
					myResponse = Response.status(404).entity(error.toString())
							.build();
					logger.exiting(this.getClass().getName(),
							CREATE_ACCOUNT_INTERNAL, myResponse);
					return myResponse;
				}
				error = mapper.createObjectNode();
				error.put(JSON_ERROR_MSG, CUSTOMER_NUMBER_LITERAL
						+ customerNumberLong.longValue() + CANNOT_BE_ACCESSED);
				logger.log(Level.SEVERE, () -> CUSTOMER_NUMBER_LITERAL
						+ customerNumberLong.longValue() + CANNOT_BE_ACCESSED);
				myResponse = Response.status(accountsOfThisCustomer.getStatus())
						.entity(error.toString()).build();
				logger.exiting(this.getClass().getName(),
						CREATE_ACCOUNT_INTERNAL, myResponse);
				return myResponse;

			}
			String accountsOfThisCustomerString = accountsOfThisCustomer
					.getEntity().toString();
			myAccountsJSON = mapper.readTree(accountsOfThisCustomerString);
		}
		catch (IOException e)
		{
			error = mapper.createObjectNode();
			error.put(JSON_ERROR_MSG, "Failed to retrieve customer number "
					+ customerNumberLong + " " + e.getLocalizedMessage());
			logger.log(Level.SEVERE, () -> "Failed to retrieve customer number "
					+ customerNumberLong + " " + e.getLocalizedMessage());
			myResponse = Response.status(500).entity(error.toString()).build();
			logger.exiting(this.getClass().getName(), CREATE_ACCOUNT_INTERNAL,
					myResponse);
			return myResponse;
		}
		long accountCount = myAccountsJSON.get(JSON_NUMBER_OF_ACCOUNTS)
				.asLong();

		// Does the customer have ten or more accounts?

		if (accountCount >= MAXIMUM_ACCOUNTS_PER_CUSTOMER)
		{
			error = mapper.createObjectNode();
			error.put(JSON_ERROR_MSG,
					CUSTOMER_NUMBER_LITERAL + customerNumberLong.longValue()
							+ " cannot have more than ten accounts.");
			logger.log(Level.WARNING,
					() -> (CUSTOMER_NUMBER_LITERAL
							+ customerNumberLong.longValue()
							+ " cannot have more than ten accounts."));
			myResponse = Response.status(400).entity(error.toString()).build();
			logger.exiting(this.getClass().getName(), CREATE_ACCOUNT_INTERNAL,
					myResponse);
			return myResponse;
		}

		// Allocate a gap-free account number from the account_control counter
		// row and insert the account, all in one transaction so that a failure
		// to append the PROCTRAN audit record rolls back BOTH the counter
		// increment and the insert (reproducing the CICS SYNCPOINT / ROLLBACK
		// boundary that the deleted JCICS task previously provided).
		String sortCodeString = padSortCode(this.getSortCode());
		String customerNumberString = padCustomerNumber(
				account.getCustomerNumber());
		try (Connection conn = getConnection())
		{
			conn.setAutoCommit(false);

			long newAccountNumber;
			try (PreparedStatement allocate = conn.prepareStatement(
					"UPDATE account_control SET last_account_number = last_account_number + 1, "
							+ "number_of_accounts = number_of_accounts + 1 "
							+ "WHERE sort_code = ? RETURNING last_account_number"))
			{
				allocate.setString(1, sortCodeString);
				try (ResultSet rs = allocate.executeQuery())
				{
					if (!rs.next())
					{
						conn.rollback();
						error = mapper.createObjectNode();
						error.put(JSON_ERROR_MSG, ACCOUNT_CREATE_FAILURE);
						logger.log(Level.SEVERE, () -> ACCOUNT_CREATE_FAILURE);
						myResponse = Response.status(500)
								.entity(error.toString()).build();
						logger.exiting(this.getClass().getName(),
								CREATE_ACCOUNT_INTERNAL, myResponse);
						return myResponse;
					}
					newAccountNumber = rs.getLong(1);
				}
			}

			String accountNumberString = padAccountNumber(
					(int) newAccountNumber);

			// Store today's date as the ACCOUNT-OPENED date and calculate the
			// LAST-STMT-DATE (today) and the NEXT-STMT-DATE (today + ~1 month),
			// reproducing the COBOL CREACC date handling.
			Calendar myCalendar = Calendar.getInstance();
			Date today = new Date(myCalendar.getTimeInMillis());
			Date dateOpened = today;
			Date lastStatement = today;
			long nextStatementLong = myCalendar.getTimeInMillis()
					+ getNextMonth(today);
			Date nextStatement = new Date(nextStatementLong);

			try (PreparedStatement insert = conn.prepareStatement(
					"INSERT INTO account (sort_code, account_number, customer_number, "
							+ "account_type, interest_rate, opened, overdraft_limit, "
							+ "last_statement_date, next_statement_date, available_balance, "
							+ "actual_balance) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0.00, 0.00)"))
			{
				insert.setString(1, sortCodeString);
				insert.setString(2, accountNumberString);
				insert.setString(3, customerNumberString);
				insert.setString(4, account.getAccountType());
				insert.setBigDecimal(5, account.getInterestRate());
				insert.setDate(6, dateOpened);
				insert.setInt(7, account.getOverdraft());
				insert.setDate(8, lastStatement);
				insert.setDate(9, nextStatement);
				insert.executeUpdate();
			}

			// Re-read the freshly inserted row so the response is built from the
			// canonical stored values, identical in shape to the read endpoints.
			try (PreparedStatement select = conn.prepareStatement(
					"SELECT * FROM account WHERE account_number = ? AND sort_code = ?"))
			{
				select.setString(1, accountNumberString);
				select.setString(2, sortCodeString);
				try (ResultSet rs = select.executeQuery())
				{
					if (rs.next())
					{
						populateAccountFull(response, rs);
					}
				}
			}

			// Append the PROCTRAN create-account audit record (sibling resource,
			// its own connection). On failure roll back the account insert and
			// the counter increment together.
			ProcessedTransactionResource myProcessedTransactionResource = new ProcessedTransactionResource();
			ProcessedTransactionAccountJSON myProctranAccount = new ProcessedTransactionAccountJSON();
			myProctranAccount.setSortCode(sortCodeString);
			myProctranAccount.setAccountNumber(accountNumberString);
			myProctranAccount.setCustomerNumber(customerNumberString);
			myProctranAccount.setLastStatement(lastStatement);
			myProctranAccount.setNextStatement(nextStatement);
			myProctranAccount.setType(account.getAccountType());
			myProctranAccount.setActualBalance(new BigDecimal("0.00"));

			Response writeCreateAccountResponse = myProcessedTransactionResource
					.writeCreateAccountInternal(myProctranAccount);
			if (writeCreateAccountResponse == null
					|| writeCreateAccountResponse.getStatus() != 200)
			{
				error = mapper.createObjectNode();
				error.put(JSON_ERROR_MSG, PROCTRAN_WRITE_FAILURE);
				logger.log(Level.SEVERE, () -> "Accounts: createAccount: "
						+ PROCTRAN_WRITE_FAILURE);
				conn.rollback();
				logger.log(Level.SEVERE, () -> ACCOUNT_CREATE_FAILURE);
				myResponse = Response.status(500).entity(error.toString())
						.build();
				logger.exiting(this.getClass().getName(),
						CREATE_ACCOUNT_INTERNAL, myResponse);
				return myResponse;
			}

			conn.commit();
		}
		catch (SQLException e)
		{
			error = mapper.createObjectNode();
			error.put(JSON_ERROR_MSG, ACCOUNT_CREATE_FAILURE);
			logger.log(Level.SEVERE,
					() -> ACCOUNT_CREATE_FAILURE + " " + e.getMessage());
			myResponse = Response.status(500).entity(error.toString()).build();
			logger.exiting(this.getClass().getName(), CREATE_ACCOUNT_INTERNAL,
					myResponse);
			return myResponse;
		}

		myResponse = Response.status(201).entity(response.toString()).build();
		logger.exiting(this.getClass().getName(), CREATE_ACCOUNT_INTERNAL,
				myResponse);
		return myResponse;

	}


	@GET
	@Path("/{accountNumber}")
	@Produces("application/json")
	public Response getAccountExternal(
			@PathParam("accountNumber") Long accountNumber)
	{
		/** This will list one single account of the specified number. */
		logger.entering(this.getClass().getName(),
				"getAccountExternal(Long accountNumber)");
		Response myResponse = getAccountInternal(accountNumber);
		logger.exiting(this.getClass().getName(),
				"getAccountExternal(Long accountNumber)", myResponse);
		return myResponse;
	}


	public Response getAccountInternal(Long accountNumber)
	{
		/** This will list one single account of the specified number. */
		logger.entering(this.getClass().getName(), GET_ACCOUNT_INTERNAL);
		Response myResponse = null;
		ObjectNode response = mapper.createObjectNode();

		Integer sortCode = this.getSortCode();
		boolean found = false;

		try (Connection conn = getConnection())
		{
			ObjectNode account = readSingleAccount(conn,
					accountNumber.longValue(), sortCode.intValue());
			if (account != null)
			{
				response = account;
				found = true;
			}
		}
		catch (SQLException e)
		{
			// A genuine data-store error leaves "found" false and falls through
			// to the 404 path, preserving the legacy behaviour where a failed
			// read surfaced as "account not found".
			logger.log(Level.WARNING, () -> "Exception reading account "
					+ accountNumber + " " + e.getMessage());
		}

		if (!found)
		{
			response.put(JSON_ERROR_MSG,
					ACCOUNT_LITERAL + accountNumber + " not found"
							+ CLASS_NAME_MSG);
			logger.log(Level.INFO, () -> ACCOUNT_LITERAL + accountNumber
					+ " not found" + CLASS_NAME_MSG);
			myResponse = Response.status(404).entity(response.toString())
					.build();
			logger.exiting(this.getClass().getName(), GET_ACCOUNT_INTERNAL,
					myResponse);
			return myResponse;
		}

		myResponse = Response.status(200).entity(response.toString()).build();
		logger.exiting(this.getClass().getName(), GET_ACCOUNT_INTERNAL,
				myResponse);

		return myResponse;
	}


	@GET
	@Path("/retrieveByCustomerNumber/{customerNumber}")
	@Produces("application/json")
	public Response getAccountsByCustomerExternal(
			@PathParam(JSON_CUSTOMER_NUMBER) Long customerNumber,
			@QueryParam("countOnly") Boolean countOnly)
	{
		/** This will list accounts owned by a specified customer */
		logger.entering(this.getClass().getName(),
				"getAccountsByCustomerExternal(Long customerNumber, Boolean countOnly)");

		Response myResponse = getAccountsByCustomerInternal(customerNumber);
		logger.exiting(this.getClass().getName(),
				"getAccountsByCustomerExternal(Long customerNumber, Boolean countOnly)",
				myResponse);
		return myResponse;
	}


	public Response getAccountsByCustomerInternal(
			@PathParam(JSON_CUSTOMER_NUMBER) Long customerNumber)
	{
		logger.entering(this.getClass().getName(),
				GET_ACCOUNTS_BY_CUSTOMER_INTERNAL);

		ArrayNode accounts = mapper.createArrayNode();
		Response myResponse = null;

		ObjectNode response = mapper.createObjectNode();
		Integer sortCode = this.getSortCode();
		int numberOfAccounts = 0;

		CustomerResource myCustomer = new CustomerResource();
		Response customerResponse = myCustomer
				.getCustomerInternal(customerNumber);

		if (customerResponse.getStatus() != 200)
		{
			if (customerResponse.getStatus() == 404)
			{
				// If cannot find response "CustomerResponse" then error 404
				// returned
				ObjectNode error = mapper.createObjectNode();
				error.put(JSON_ERROR_MSG, CUSTOMER_NUMBER_LITERAL
						+ customerNumber.longValue() + CANNOT_BE_FOUND);
				logger.log(Level.SEVERE, () -> CUSTOMER_NUMBER_LITERAL
						+ customerNumber.longValue() + CANNOT_BE_FOUND);
				myResponse = Response.status(404).entity(error.toString())
						.build();
				logger.exiting(this.getClass().getName(),
						GET_ACCOUNTS_BY_CUSTOMER_INTERNAL, myResponse);
				return myResponse;
			}
			else
			{
				ObjectNode error = mapper.createObjectNode();
				error.put(JSON_ERROR_MSG,
						CUSTOMER_NUMBER_LITERAL + customerNumber.longValue()
								+ " cannot be accessed. "
								+ customerResponse.toString());
				logger.log(Level.SEVERE, () -> CUSTOMER_NUMBER_LITERAL
						+ customerNumber.longValue() + " cannot be accessed. "
						+ customerResponse.toString());
				myResponse = Response.status(customerResponse.getStatus())
						.entity(error.toString()).build();
				logger.exiting(this.getClass().getName(),
						GET_ACCOUNTS_BY_CUSTOMER_INTERNAL, myResponse);
				return myResponse;
			}
		}

		try (Connection conn = getConnection();
				PreparedStatement select = conn.prepareStatement(
						"SELECT * FROM account WHERE customer_number = ? AND sort_code = ? "
								+ "ORDER BY account_number"))
		{
			select.setString(1, padCustomerNumber(customerNumber.toString()));
			select.setString(2, padSortCode(sortCode));
			try (ResultSet rs = select.executeQuery())
			{
				while (rs.next())
				{
					ObjectNode account = mapper.createObjectNode();
					populateAccountFull(account, rs);
					accounts.add(account);
					numberOfAccounts++;
				}
			}
		}
		catch (SQLException e)
		{
			ObjectNode error = mapper.createObjectNode();
			error.put(JSON_ERROR_MSG,
					"Accounts cannot be accessed for customer "
							+ customerNumber.longValue() + CLASS_NAME_MSG);
			logger.log(Level.SEVERE,
					() -> "Accounts cannot be accessed for customer "
							+ customerNumber.longValue() + CLASS_NAME_MSG);
			myResponse = Response.status(500).entity(error.toString()).build();
			logger.exiting(this.getClass().getName(),
					GET_ACCOUNTS_BY_CUSTOMER_INTERNAL, myResponse);
			return myResponse;
		}

		StringBuilder myStringBuilder = new StringBuilder();

		for (int i = customerNumber.toString()
				.length(); i < CUSTOMER_NUMBER_LENGTH; i++)
		{
			myStringBuilder.append('0');
		}
		myStringBuilder.append(customerNumber.toString());

		response.put(JSON_CUSTOMER_NUMBER, myStringBuilder.toString());
		response.put(JSON_NUMBER_OF_ACCOUNTS, numberOfAccounts);
		response.set(JSON_ACCOUNTS, accounts);

		myResponse = Response.status(200).entity(response.toString()).build();
		logger.exiting(this.getClass().getName(),
				GET_ACCOUNTS_BY_CUSTOMER_INTERNAL, myResponse);
		return myResponse;

	}


	private Integer getSortCode()
	{
		/** This will get the Sort Code from the SortCode Resource */
		SortCodeResource mySortCodeResource = new SortCodeResource();
		Response mySortCodeJSON = mySortCodeResource.getSortCode();
		String mySortCode = ((String) mySortCodeJSON.getEntity()).substring(13,
				19);
		return Integer.parseInt(mySortCode);
	}


	@PUT
	@Path("/{id}")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response updateAccountExternal(@PathParam("id") Long id,
			AccountJSON account)
	{
		/**
		 * Update the account specified by "id" with the JSON in AccountJSON.
		 * This is for interest rates, types and overdraft limits, not balances
		 */
		logger.entering(this.getClass().getName(),
				"updateAccountExternal(Long id, AccountJSON account)");
		Response myResponse = updateAccountInternal(id, account);
		logger.exiting(this.getClass().getName(),
				"updateAccountExternal(Long id, AccountJSON account)",
				myResponse);
		return myResponse;
	}


	public Response updateAccountInternal(Long id, AccountJSON account)
	{
		/**
		 * Update the account specified by "id" with the JSON in AccountJSON.
		 * This is for interest rates, types and overdraft limits, not balances.
		 * Per the migration rules this never writes a PROCTRAN record and never
		 * touches the balances.
		 */
		logger.entering(this.getClass().getName(), UPDATE_ACCOUNT_INTERNAL);
		ObjectNode response = mapper.createObjectNode();
		Response myResponse = null;

		if (!(account.validateType(account.getAccountType().trim())))
		// If account type invalid
		{
			ObjectNode error = mapper.createObjectNode();
			error.put(JSON_ERROR_MSG,
					ACC_TYPE_STRING + account.getAccountType() + NOT_SUPPORTED);
			logger.log(Level.WARNING, () -> (ACC_TYPE_STRING
					+ account.getAccountType() + NOT_SUPPORTED));
			myResponse = Response.status(400).entity(error.toString()).build();
			logger.exiting(this.getClass().getName(), UPDATE_ACCOUNT_INTERNAL,
					myResponse);
			return myResponse;
		}

		if (account.getInterestRate().signum() < 0)
		{
			// If interest rate < 0
			ObjectNode error = mapper.createObjectNode();
			error.put(JSON_ERROR_MSG, INTEREST_RATE_LESS_THAN_ZERO);
			logger.log(Level.WARNING, () -> (INTEREST_RATE_LESS_THAN_ZERO));
			myResponse = Response.status(400).entity(error.toString()).build();
			logger.exiting(this.getClass().getName(), UPDATE_ACCOUNT_INTERNAL,
					myResponse);
			return myResponse;
		}

		if (account.getInterestRate().compareTo(new BigDecimal("9999.99")) > 0)
		{
			// If interest rate > 9999.99
			ObjectNode error = mapper.createObjectNode();
			error.put(JSON_ERROR_MSG, INTEREST_RATE_TOO_HIGH);
			logger.log(Level.WARNING, () -> (INTEREST_RATE_TOO_HIGH));
			myResponse = Response.status(400).entity(error.toString()).build();
			logger.exiting(this.getClass().getName(), UPDATE_ACCOUNT_INTERNAL,
					myResponse);
			return myResponse;

		}

		BigDecimal myInterestRate = account.getInterestRate();
		if (myInterestRate.scale() > 2)
		// Interest rate more than 2dp
		{
			ObjectNode error = mapper.createObjectNode();
			error.put(JSON_ERROR_MSG,
					"Interest rate cannot have more than 2 decimal places. "
							+ myInterestRate.toPlainString());
			logger.log(Level.WARNING,
					() -> ("Interest rate cannot have more than 2 decimal places."
							+ myInterestRate.toPlainString()));
			myResponse = Response.status(400).entity(error.toString()).build();
			logger.exiting(this.getClass().getName(), UPDATE_ACCOUNT_INTERNAL,
					myResponse);
			return myResponse;
		}

		Integer inputSortCode = Integer.parseInt(account.getSortCode());
		Integer thisSortCode = this.getSortCode();

		if (inputSortCode.intValue() != thisSortCode.intValue())
		// Invalid sortcode
		{
			ObjectNode error = mapper.createObjectNode();
			error.put(JSON_ERROR_MSG, SORT_CODE_LITERAL + inputSortCode
					+ NOT_VALID_FOR_THIS_BANK + thisSortCode + ")");
			logger.log(Level.WARNING, () -> SORT_CODE_LITERAL + inputSortCode
					+ NOT_VALID_FOR_THIS_BANK + thisSortCode + ")");
			myResponse = Response.status(400).entity(error.toString()).build();
			logger.exiting(this.getClass().getName(), UPDATE_ACCOUNT_INTERNAL,
					myResponse);
			return myResponse;
		}

		account.setId(id.toString());
		String sortCodeString = padSortCode(thisSortCode);
		String accountNumberString = padAccountNumber(
				Integer.parseInt(account.getId()));

		try (Connection conn = getConnection())
		{
			boolean found = false;
			try (PreparedStatement check = conn.prepareStatement(
					"SELECT account_number FROM account WHERE account_number = ? AND sort_code = ?"))
			{
				check.setString(1, accountNumberString);
				check.setString(2, sortCodeString);
				try (ResultSet rs = check.executeQuery())
				{
					found = rs.next();
				}
			}

			if (!found)
			{
				ObjectNode error = mapper.createObjectNode();
				error.put(JSON_ERROR_MSG, FAILED_TO_READ + account.getId()
						+ " in " + this.getClass().toString());
				logger.log(Level.WARNING, () -> (FAILED_TO_READ
						+ account.getId() + CLASS_NAME_MSG));
				myResponse = Response.status(404).entity(error.toString())
						.build();
				logger.exiting(this.getClass().getName(),
						UPDATE_ACCOUNT_INTERNAL, myResponse);
				return myResponse;
			}

			// Update only the type, interest rate and overdraft limit (never
			// the balances) and write no PROCTRAN record, matching UPDACC.
			try (PreparedStatement update = conn.prepareStatement(
					"UPDATE account SET account_type = ?, interest_rate = ?, "
							+ "overdraft_limit = ? WHERE account_number = ? AND sort_code = ?"))
			{
				update.setString(1, account.getAccountType());
				update.setBigDecimal(2, account.getInterestRate());
				update.setInt(3, account.getOverdraft());
				update.setString(4, accountNumberString);
				update.setString(5, sortCodeString);
				update.executeUpdate();
			}

			try (PreparedStatement select = conn.prepareStatement(
					"SELECT * FROM account WHERE account_number = ? AND sort_code = ?"))
			{
				select.setString(1, accountNumberString);
				select.setString(2, sortCodeString);
				try (ResultSet rs = select.executeQuery())
				{
					if (rs.next())
					{
						populateAccountFull(response, rs);
					}
				}
			}
		}
		catch (SQLException e)
		{
			ObjectNode error = mapper.createObjectNode();
			error.put(JSON_ERROR_MSG,
					"Failed to update account" + CLASS_NAME_MSG);
			logger.log(Level.SEVERE,
					() -> "Failed to update account" + CLASS_NAME_MSG);
			myResponse = Response.status(500).entity(error.toString()).build();
			logger.exiting(this.getClass().getName(), UPDATE_ACCOUNT_INTERNAL,
					myResponse);
			return myResponse;
		}

		myResponse = Response.status(200).entity(response.toString()).build();
		logger.exiting(this.getClass().getName(), UPDATE_ACCOUNT_INTERNAL,
				myResponse);

		return myResponse;
	}


	@PUT
	@Path("/debit/{id}")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response debitAccountExternal(@PathParam("id") String accountNumber,
			DebitCreditAccountJSON dbcr)
	{
		// we use this to subtract money from an account
		logger.entering(this.getClass().getName(),
				"debitAccountExternal(String accountNumber, DebitCreditAccountJSON dbcr)");
		Response myResponse = debitAccountInternal(accountNumber, dbcr);
		logger.exiting(this.getClass().getName(),
				"debitAccountExternal(String accountNumber, DebitCreditAccountJSON dbcr)",
				myResponse);
		return myResponse;
	}


	public Response debitAccountInternal(String accountNumber,
			DebitCreditAccountJSON dbcr)
	{
		// we use this to subtract money from an account
		logger.entering(this.getClass().getName(), DEBIT_ACCOUNT_INTERNAL);
		Response myResponse = null;

		AccountsResource checkAccount = new AccountsResource();
		Response checkAccountResponse = checkAccount
				.getAccountInternal(Long.parseLong(accountNumber));

		if (checkAccountResponse.getStatus() != 200)
		{
			if (checkAccountResponse.getStatus() == 404)
			{
				ObjectNode error = mapper.createObjectNode();
				error.put(JSON_ERROR_MSG, FAILED_TO_READ + accountNumber
						+ IN_DEBIT_ACCOUNT + this.getClass().toString());
				logger.log(Level.WARNING, () -> (FAILED_TO_READ + accountNumber
						+ IN_DEBIT_ACCOUNT + this.getClass().toString()));
				myResponse = Response.status(404).entity(error.toString())
						.build();
				logger.exiting(this.getClass().getName(),
						DEBIT_ACCOUNT_INTERNAL, myResponse);
				return myResponse;
			}
			ObjectNode error = mapper.createObjectNode();
			error.put(JSON_ERROR_MSG, FAILED_TO_READ + accountNumber
					+ IN_DEBIT_ACCOUNT + this.getClass().toString());
			logger.log(Level.SEVERE, () -> FAILED_TO_READ + accountNumber
					+ IN_DEBIT_ACCOUNT + this.getClass().toString());
			myResponse = Response.status(checkAccountResponse.getStatus())
					.entity(error.toString()).build();
			logger.exiting(this.getClass().getName(), DEBIT_ACCOUNT_INTERNAL,
					myResponse);
			return myResponse;
		}
		Long sortcode = Long.parseLong(this.getSortCode().toString());
		myResponse = debitCreditAccount(sortcode, accountNumber,
				dbcr.getAmount(), true);
		logger.exiting(this.getClass().getName(), DEBIT_ACCOUNT_INTERNAL,
				myResponse);
		return myResponse;
	}


	@PUT
	@Path("/credit/{id}")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response creditAccountExternal(@PathParam("id") String accountNumber,
			DebitCreditAccountJSON dbcr)
	{
		// we use this to add money to an account
		logger.entering(this.getClass().getName(),
				"creditAccountExternal(String accountNumber, DebitCreditAccountJSON dbcr)");
		Response myResponse = creditAccountInternal(accountNumber, dbcr);
		logger.exiting(this.getClass().getName(),
				"creditAccountExternal(String accountNumber, DebitCreditAccountJSON dbcr)",
				myResponse);
		return myResponse;
	}


	public Response creditAccountInternal(@PathParam("id") String accountNumber,
			DebitCreditAccountJSON dbcr)
	{
		// we use this to add money to an account
		logger.entering(this.getClass().getName(), CREDIT_ACCOUNT_INTERNAL);
		Response myResponse = null;

		AccountsResource checkAccount = new AccountsResource();
		Response checkAccountResponse = checkAccount
				.getAccountInternal(Long.parseLong(accountNumber));

		if (checkAccountResponse.getStatus() != 200)
		{
			if (checkAccountResponse.getStatus() == 404)
			{
				ObjectNode error = mapper.createObjectNode();
				error.put(JSON_ERROR_MSG, FAILED_TO_READ + accountNumber
						+ IN_CREDIT_ACCOUNT + this.getClass().toString());
				logger.log(Level.WARNING, () -> (FAILED_TO_READ + accountNumber
						+ IN_CREDIT_ACCOUNT + this.getClass().toString()));
				myResponse = Response.status(404).entity(error.toString())
						.build();
				logger.exiting(this.getClass().getName(),
						CREDIT_ACCOUNT_INTERNAL, myResponse);
				return myResponse;
			}
			ObjectNode error = mapper.createObjectNode();
			error.put(JSON_ERROR_MSG, FAILED_TO_READ + accountNumber
					+ IN_CREDIT_ACCOUNT + this.getClass().toString());
			logger.log(Level.SEVERE, () -> FAILED_TO_READ + accountNumber
					+ IN_CREDIT_ACCOUNT + this.getClass().toString());
			myResponse = Response.status(checkAccountResponse.getStatus())
					.entity(error.toString()).build();
			logger.exiting(this.getClass().getName(), CREDIT_ACCOUNT_INTERNAL,
					myResponse);
			return myResponse;
		}

		Long sortcode = Long.parseLong(this.getSortCode().toString());
		myResponse = debitCreditAccount(sortcode, accountNumber,
				dbcr.getAmount(), false);
		logger.exiting(this.getClass().getName(), CREDIT_ACCOUNT_INTERNAL,
				myResponse);
		return myResponse;
	}


	@PUT
	@Path("/transfer/{id}")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response transferLocalExternal(@PathParam("id") String accountNumber,
			TransferLocalJSON transferLocal)
	{
		// we use this to move money between two accounts at the same bank
		logger.entering(this.getClass().getName(),
				"transferLocalExternal(String accountNumber, TransferLocalJSON transferLocal)");
		Integer accountNumberInteger;
		try
		{
			accountNumberInteger = Integer.parseInt(accountNumber);
			if (accountNumberInteger.intValue() < 1
					|| accountNumberInteger.intValue() == 99999999)
			{
				return null;
			}
		}
		catch (NumberFormatException e)
		{
			return null;
		}
		TransferLocalJSON transferLocalValid = new TransferLocalJSON();
		if (transferLocal.getAmount().signum() < 0)
		{
			return null;
		}
		else
		{
			transferLocalValid.setAmount(transferLocal.getAmount());
		}
		if (transferLocal.getTargetAccount() < 1
				|| transferLocal.getTargetAccount() == 99999999)
		{
			return null;
		}
		else
		{
			transferLocalValid
					.setTargetAccount(transferLocal.getTargetAccount());
		}

		Response myResponse = transferLocalInternal(
				accountNumberInteger.toString(), transferLocalValid);
		logger.exiting(this.getClass().getName(),
				"transferLocalExternal(String accountNumber, TransferLocalJSON transferLocal)",
				myResponse);
		return myResponse;
	}


	public Response transferLocalInternal(@PathParam("id") String accountNumber,
			TransferLocalJSON transferLocal)
	{
		// we use this to move money between two accounts at the same bank
		logger.entering(this.getClass().getName(), TRANSFER_LOCAL_INTERNAL);
		Response myResponse = null;

		// * We are transferring money from account "id" at this bank, to
		// another account at this bank
		// * The amount MUST be positive
		ObjectNode response = mapper.createObjectNode();

		if (Integer.parseInt(accountNumber) == transferLocal.getTargetAccount())
		{
			ObjectNode error = mapper.createObjectNode();
			error.put(JSON_ERROR_MSG, NEED_DIFFERENT_ACCOUNTS);
			logger.log(Level.WARNING, () -> (NEED_DIFFERENT_ACCOUNTS));
			myResponse = Response.status(400).entity(error.toString()).build();
			logger.exiting(this.getClass().getName(), TRANSFER_LOCAL_INTERNAL,
					myResponse);
			return myResponse;
		}

		if (transferLocal.getAmount().signum() <= 0)
		{
			ObjectNode error = mapper.createObjectNode();
			error.put(JSON_ERROR_MSG, "Amount to transfer must be positive");
			logger.log(Level.WARNING, () -> (NEED_DIFFERENT_ACCOUNTS));
			myResponse = Response.status(400).entity(error.toString()).build();
			logger.exiting(this.getClass().getName(), TRANSFER_LOCAL_INTERNAL,
					myResponse);
			return myResponse;
		}

		BigDecimal amount = transferLocal.getAmount();
		amount = amount.setScale(2, RoundingMode.HALF_UP);
		BigDecimal negativeAmount = amount;
		negativeAmount = negativeAmount.negate();
		negativeAmount = negativeAmount.setScale(2, RoundingMode.HALF_UP);

		Long sortCode = Long.parseLong(this.getSortCode().toString());

		// Let's make sure that from account and to account exist
		AccountsResource checkAccount = new AccountsResource();
		Response checkAccountResponse = checkAccount
				.getAccountInternal(Long.parseLong(accountNumber));

		if (checkAccountResponse.getStatus() == 404)
		{
			ObjectNode error = mapper.createObjectNode();
			error.put(JSON_ERROR_MSG,
					SOURCE_ACCOUNT_NUMBER + accountNumber + CANNOT_BE_FOUND);
			logger.log(Level.WARNING, () -> (SOURCE_ACCOUNT_NUMBER
					+ accountNumber + CANNOT_BE_FOUND));
			myResponse = Response.status(404).entity(error.toString()).build();
			logger.exiting(this.getClass().getName(), TRANSFER_LOCAL_INTERNAL,
					myResponse);
			return myResponse;
		}

		if (checkAccountResponse.getStatus() != 200)
		{
			ObjectNode error = mapper.createObjectNode();
			error.put(JSON_ERROR_MSG,
					SOURCE_ACCOUNT_NUMBER + accountNumber + CANNOT_BE_ACCESSED);
			logger.log(Level.WARNING, () -> (SOURCE_ACCOUNT_NUMBER
					+ accountNumber + CANNOT_BE_ACCESSED));
			myResponse = Response.status(checkAccountResponse.getStatus())
					.entity(error.toString()).build();
			logger.exiting(this.getClass().getName(), TRANSFER_LOCAL_INTERNAL,
					myResponse);
			return myResponse;
		}
		checkAccountResponse = checkAccount.getAccountInternal(
				Long.parseLong(transferLocal.getTargetAccount().toString()));

		if (checkAccountResponse.getStatus() == 404)
		{
			ObjectNode error = mapper.createObjectNode();
			error.put(JSON_ERROR_MSG, TARGET_ACCOUNT_NUMBER
					+ transferLocal.getTargetAccount() + CANNOT_BE_FOUND);
			logger.log(Level.WARNING, () -> (TARGET_ACCOUNT_NUMBER
					+ accountNumber + CANNOT_BE_FOUND));
			myResponse = Response.status(404).entity(error.toString()).build();
			logger.exiting(this.getClass().getName(), TRANSFER_LOCAL_INTERNAL,
					myResponse);
			return myResponse;
		}
		if (checkAccountResponse.getStatus() != 200)
		{
			ObjectNode error = mapper.createObjectNode();
			error.put(JSON_ERROR_MSG, TARGET_ACCOUNT_NUMBER
					+ transferLocal.getTargetAccount() + CANNOT_BE_ACCESSED);
			logger.log(Level.SEVERE, () -> TARGET_ACCOUNT_NUMBER + accountNumber
					+ CANNOT_BE_ACCESSED);
			myResponse = Response.status(404).entity(error.toString()).build();
			logger.exiting(this.getClass().getName(), TRANSFER_LOCAL_INTERNAL,
					myResponse);
			return myResponse;
		}

		// Move the money: debit the source account and credit the target
		// account within a single transaction so the pair is atomic (reproducing
		// the CICS SYNCPOINT boundary of XFRFUN). Both balances are updated on
		// both accounts.
		String sortCodeString = padSortCode(this.getSortCode());
		String sourceAccountString = padAccountNumber(
				Integer.parseInt(accountNumber));
		String targetAccountString = padAccountNumber(
				transferLocal.getTargetAccount());
		BigDecimal targetNewActual = null;
		BigDecimal targetNewAvailable = null;
		BigDecimal targetInterestRate = null;
		try (Connection conn = getConnection())
		{
			conn.setAutoCommit(false);

			// Debit the source account
			try (PreparedStatement select = conn.prepareStatement(
					"SELECT actual_balance, available_balance FROM account "
							+ "WHERE account_number = ? AND sort_code = ?"))
			{
				select.setString(1, sourceAccountString);
				select.setString(2, sortCodeString);
				try (ResultSet rs = select.executeQuery())
				{
					if (rs.next())
					{
						BigDecimal srcActual = rs
								.getBigDecimal("actual_balance")
								.add(negativeAmount)
								.setScale(2, RoundingMode.HALF_UP);
						BigDecimal srcAvailable = rs
								.getBigDecimal("available_balance")
								.add(negativeAmount)
								.setScale(2, RoundingMode.HALF_UP);
						try (PreparedStatement update = conn.prepareStatement(
								"UPDATE account SET actual_balance = ?, available_balance = ? "
										+ "WHERE account_number = ? AND sort_code = ?"))
						{
							update.setBigDecimal(1, srcActual);
							update.setBigDecimal(2, srcAvailable);
							update.setString(3, sourceAccountString);
							update.setString(4, sortCodeString);
							update.executeUpdate();
						}
					}
				}
			}

			// Credit the target account
			try (PreparedStatement select = conn.prepareStatement(
					"SELECT actual_balance, available_balance, interest_rate FROM account "
							+ "WHERE account_number = ? AND sort_code = ?"))
			{
				select.setString(1, targetAccountString);
				select.setString(2, sortCodeString);
				try (ResultSet rs = select.executeQuery())
				{
					if (rs.next())
					{
						targetNewActual = rs.getBigDecimal("actual_balance")
								.add(amount).setScale(2, RoundingMode.HALF_UP);
						targetNewAvailable = rs
								.getBigDecimal("available_balance").add(amount)
								.setScale(2, RoundingMode.HALF_UP);
						targetInterestRate = rs.getBigDecimal("interest_rate");
						try (PreparedStatement update = conn.prepareStatement(
								"UPDATE account SET actual_balance = ?, available_balance = ? "
										+ "WHERE account_number = ? AND sort_code = ?"))
						{
							update.setBigDecimal(1, targetNewActual);
							update.setBigDecimal(2, targetNewAvailable);
							update.setString(3, targetAccountString);
							update.setString(4, sortCodeString);
							update.executeUpdate();
						}
					}
				}
			}

			ProcessedTransactionResource myProcessedTransactionResource = new ProcessedTransactionResource();

			ProcessedTransactionTransferLocalJSON myProctranTransferLocal = new ProcessedTransactionTransferLocalJSON();
			myProctranTransferLocal.setSortCode(sortCode.toString());
			myProctranTransferLocal.setAccountNumber(
					transferLocal.getTargetAccount().toString());
			myProctranTransferLocal.setAmount(amount);
			myProctranTransferLocal.setTargetAccountNumber(
					transferLocal.getTargetAccount().toString());

			Response writeTransferResponse = myProcessedTransactionResource
					.writeTransferLocalInternal(myProctranTransferLocal);
			if (writeTransferResponse == null
					|| writeTransferResponse.getStatus() != 200)
			{
				ObjectNode error = mapper.createObjectNode();
				error.put(JSON_ERROR_MSG, PROCTRAN_WRITE_FAILURE);
				logger.log(Level.SEVERE, () -> "Accounts: transferLocal: "
						+ PROCTRAN_WRITE_FAILURE);
				conn.rollback();
				myResponse = Response.status(500).entity(error.toString())
						.build();
				logger.exiting(this.getClass().getName(),
						TRANSFER_LOCAL_INTERNAL, myResponse);
				return myResponse;
			}

			conn.commit();
		}
		catch (SQLException e)
		{
			ObjectNode error = mapper.createObjectNode();
			error.put(JSON_ERROR_MSG, PROCTRAN_WRITE_FAILURE);
			logger.log(Level.SEVERE, () -> "Accounts: transferLocal: "
					+ PROCTRAN_WRITE_FAILURE);
			myResponse = Response.status(500).entity(error.toString()).build();
			logger.exiting(this.getClass().getName(), TRANSFER_LOCAL_INTERNAL,
					myResponse);
			return myResponse;
		}

		response.put(JSON_SORT_CODE, sortCode.toString().trim());
		response.put("id", transferLocal.getTargetAccount().toString());
		response.put(JSON_AVAILABLE_BALANCE, targetNewAvailable);
		response.put(JSON_ACTUAL_BALANCE, targetNewActual);
		response.put(JSON_INTEREST_RATE, targetInterestRate);

		myResponse = Response.status(200).entity(response.toString()).build();
		logger.exiting(this.getClass().getName(), TRANSFER_LOCAL_INTERNAL,
				myResponse);
		return myResponse;
	}


	private Response debitCreditAccount(Long sortCode, String accountNumber,
			BigDecimal apiAmount, boolean debitAccount)
	{
		// This method does both debit AND credit, controlled by the boolean
		logger.entering(this.getClass().getName(), DEBIT_CREDIT_ACCOUNT);
		Response myResponse = null;
		ObjectNode response = mapper.createObjectNode();

		if (debitAccount)
		{
			apiAmount = apiAmount.negate();
		}
		final BigDecimal amount = apiAmount;

		String sortCodeString = padSortCode(sortCode.intValue());
		String accountNumberString = padAccountNumber(
				Integer.parseInt(accountNumber));

		try (Connection conn = getConnection())
		{
			conn.setAutoCommit(false);

			BigDecimal newActualBalance = null;
			BigDecimal newAvailableBalance = null;
			BigDecimal interestRate = null;

			try (PreparedStatement select = conn.prepareStatement(
					"SELECT actual_balance, available_balance, interest_rate FROM account "
							+ "WHERE account_number = ? AND sort_code = ?"))
			{
				select.setString(1, accountNumberString);
				select.setString(2, sortCodeString);
				try (ResultSet rs = select.executeQuery())
				{
					if (!rs.next())
					{
						conn.rollback();
						ObjectNode error = mapper.createObjectNode();
						if (amount.signum() < 0)
						{
							error.put(JSON_ERROR_MSG, "Failed to debit account "
									+ accountNumber + CLASS_NAME_MSG);
							logger.log(Level.SEVERE,
									() -> "Failed to debit account "
											+ accountNumber + CLASS_NAME_MSG);
						}
						else
						{
							error.put(JSON_ERROR_MSG, "Failed to credit account "
									+ accountNumber + CLASS_NAME_MSG);
							logger.log(Level.SEVERE,
									() -> "Failed to credit account "
											+ accountNumber + CLASS_NAME_MSG);
						}
						myResponse = Response.status(500)
								.entity(error.toString()).build();
						logger.exiting(this.getClass().getName(),
								DEBIT_CREDIT_ACCOUNT, myResponse);
						return myResponse;
					}
					BigDecimal actualBalance = rs
							.getBigDecimal("actual_balance");
					BigDecimal availableBalance = rs
							.getBigDecimal("available_balance");
					interestRate = rs.getBigDecimal("interest_rate");
					newActualBalance = actualBalance.add(amount).setScale(2,
							RoundingMode.HALF_UP);
					newAvailableBalance = availableBalance.add(amount)
							.setScale(2, RoundingMode.HALF_UP);
				}
			}

			try (PreparedStatement update = conn.prepareStatement(
					"UPDATE account SET actual_balance = ?, available_balance = ? "
							+ "WHERE account_number = ? AND sort_code = ?"))
			{
				update.setBigDecimal(1, newActualBalance);
				update.setBigDecimal(2, newAvailableBalance);
				update.setString(3, accountNumberString);
				update.setString(4, sortCodeString);
				update.executeUpdate();
			}

			ProcessedTransactionResource myProcessedTransactionResource = new ProcessedTransactionResource();
			ProcessedTransactionDebitCreditJSON myProctranDbCr = new ProcessedTransactionDebitCreditJSON();
			myProctranDbCr.setSortCode(sortCode.toString());
			myProctranDbCr.setAccountNumber(accountNumber);
			myProctranDbCr.setAmount(amount);

			Response debitCreditResponse = myProcessedTransactionResource
					.writeInternal(myProctranDbCr);

			if (debitCreditResponse == null
					|| debitCreditResponse.getStatus() != 200)
			{
				ObjectNode error = mapper.createObjectNode();
				error.put(JSON_ERROR_MSG, PROCTRAN_WRITE_FAILURE);
				logger.log(Level.SEVERE, () -> PROCTRAN_WRITE_FAILURE);
				conn.rollback();
				myResponse = Response.status(500).entity(error.toString())
						.build();
				logger.exiting(this.getClass().getName(), DEBIT_CREDIT_ACCOUNT,
						myResponse);
				return myResponse;
			}

			conn.commit();

			response.put(JSON_SORT_CODE, sortCode.toString().trim());
			response.put("id", accountNumber);
			response.put(JSON_AVAILABLE_BALANCE, newAvailableBalance);
			response.put(JSON_ACTUAL_BALANCE, newActualBalance);
			response.put(JSON_INTEREST_RATE, interestRate);
		}
		catch (SQLException e)
		{
			ObjectNode error = mapper.createObjectNode();
			if (amount.signum() < 0)
			{
				error.put(JSON_ERROR_MSG, "Failed to debit account "
						+ accountNumber + CLASS_NAME_MSG);
				logger.log(Level.SEVERE, () -> "Failed to debit account "
						+ accountNumber + CLASS_NAME_MSG);
			}
			else
			{
				error.put(JSON_ERROR_MSG, "Failed to credit account "
						+ accountNumber + CLASS_NAME_MSG);
				logger.log(Level.SEVERE, () -> "Failed to credit account "
						+ accountNumber + CLASS_NAME_MSG);
			}
			myResponse = Response.status(500).entity(error.toString()).build();
			logger.exiting(this.getClass().getName(), DEBIT_CREDIT_ACCOUNT,
					myResponse);
			return myResponse;
		}

		myResponse = Response.status(200).entity(response.toString()).build();
		logger.exiting(this.getClass().getName(), DEBIT_CREDIT_ACCOUNT,
				myResponse);
		return myResponse;
	}


	@DELETE
	@Path("/{accountNumber}")
	@Produces("application/json")
	public Response deleteAccountExternal(
			@PathParam("accountNumber") Long accountNumber)
	{
		logger.entering(this.getClass().getName(),
				"deleteAccountExternal(Long accountNumber)");
		Response myResponse = deleteAccountInternal(accountNumber);
		logger.exiting(this.getClass().getName(),
				"deleteAccountExternal(Long accountNumber)", myResponse);
		return myResponse;
	}


	public Response deleteAccountInternal(Long accountNumber)
	{
		logger.entering(this.getClass().getName(), DELETE_ACCOUNT);
		Response myResponse = null;

		ObjectNode response = mapper.createObjectNode();

		Integer sortCode = this.getSortCode();
		String sortCodeString = padSortCode(sortCode);
		String accountNumberString = padAccountNumber(accountNumber.intValue());

		try (Connection conn = getConnection())
		{
			conn.setAutoCommit(false);

			boolean found = false;
			String delCustomerNumber = null;
			String delType = null;
			Date delLastStatement = null;
			Date delNextStatement = null;
			BigDecimal delActualBalance = null;

			try (PreparedStatement select = conn.prepareStatement(
					"SELECT * FROM account WHERE account_number = ? AND sort_code = ?"))
			{
				select.setString(1, accountNumberString);
				select.setString(2, sortCodeString);
				try (ResultSet rs = select.executeQuery())
				{
					if (rs.next())
					{
						found = true;
						populateAccountFull(response, rs);
						// Capture the values required for the PROCTRAN
						// account-close record (which records the terminal
						// balance) before the row is removed.
						delCustomerNumber = rs.getString("customer_number");
						delType = rs.getString("account_type");
						delLastStatement = rs.getDate("last_statement_date");
						delNextStatement = rs.getDate("next_statement_date");
						delActualBalance = rs.getBigDecimal("actual_balance");
					}
				}
			}

			if (!found)
			{
				conn.rollback();
				logger.log(Level.INFO,
						() -> ("Accounts: deleteAccount: Failed to find account "
								+ accountNumber));
				response.put(JSON_ERROR_MSG,
						ACCOUNT_LITERAL + accountNumber + " not found");
				myResponse = Response.status(404).entity(response.toString())
						.build();
				logger.exiting(this.getClass().getName(), DELETE_ACCOUNT,
						myResponse);
				return myResponse;
			}

			// Physically remove the account row (DELACC physically deletes the
			// account but appends a PROCTRAN audit record) and decrement the
			// account_control counter, mirroring the create-side increment.
			try (PreparedStatement delete = conn.prepareStatement(
					"DELETE FROM account WHERE account_number = ? AND sort_code = ?"))
			{
				delete.setString(1, accountNumberString);
				delete.setString(2, sortCodeString);
				delete.executeUpdate();
			}

			try (PreparedStatement decrement = conn.prepareStatement(
					"UPDATE account_control SET number_of_accounts = number_of_accounts - 1 "
							+ "WHERE sort_code = ?"))
			{
				decrement.setString(1, sortCodeString);
				decrement.executeUpdate();
			}

			ProcessedTransactionResource myProcessedTransactionResource = new ProcessedTransactionResource();

			ProcessedTransactionAccountJSON myDeletedAccount = new ProcessedTransactionAccountJSON();
			myDeletedAccount.setAccountNumber(accountNumberString);
			myDeletedAccount.setType(delType);
			myDeletedAccount.setCustomerNumber(delCustomerNumber);
			myDeletedAccount.setSortCode(sortCodeString);
			myDeletedAccount.setNextStatement(delNextStatement);
			myDeletedAccount.setLastStatement(delLastStatement);
			myDeletedAccount.setActualBalance(delActualBalance);

			Response deletedAccountResponse = myProcessedTransactionResource
					.writeDeleteAccountInternal(myDeletedAccount);
			if (deletedAccountResponse == null
					|| deletedAccountResponse.getStatus() != 200)
			{
				ObjectNode error = mapper.createObjectNode();
				error.put(JSON_ERROR_MSG, PROCTRAN_WRITE_FAILURE);
				logger.log(Level.SEVERE, () -> PROCTRAN_WRITE_FAILURE);
				conn.rollback();
				myResponse = Response.status(500).entity(error.toString())
						.build();
				logger.exiting(this.getClass().getName(), DELETE_ACCOUNT,
						myResponse);
				return myResponse;
			}

			conn.commit();
		}
		catch (SQLException e)
		{
			ObjectNode error = mapper.createObjectNode();
			error.put(JSON_ERROR_MSG, "Failed to delete account "
					+ accountNumber + CLASS_NAME_MSG);
			logger.log(Level.SEVERE, () -> "Failed to delete account "
					+ accountNumber + CLASS_NAME_MSG);
			myResponse = Response.status(500).entity(error.toString()).build();
			logger.exiting(this.getClass().getName(), DELETE_ACCOUNT,
					myResponse);
			return myResponse;
		}
		/*
		 * Parse returned data and return to calling method
		 */

		myResponse = Response.status(200).entity(response.toString()).build();
		logger.exiting(this.getClass().getName(), DELETE_ACCOUNT, myResponse);
		return myResponse;

	}


	@GET
	@Produces("application/json")
	public Response getAccountsExternal(@QueryParam("limit") Integer limit,
			@QueryParam("offset") Integer offset,
			@QueryParam("countOnly") Boolean countOnly)
	{
		// This method returns a fixed number of accounts, up to limit "limit",
		// starting at offset "offset"
		logger.entering(this.getClass().getName(),
				"getAccountsExternal(Integer limit, Integer offset,Boolean countOnly)");
		boolean countOnlyReal = false;
		if (countOnly != null)
		{
			countOnlyReal = countOnly.booleanValue();
		}
		Response myResponse = getAccountsInternal(limit, offset, countOnlyReal);
		logger.exiting(this.getClass().getName(),
				"getAccountsExternal(Integer limit, Integer offset,Boolean countOnly)",
				myResponse);
		return myResponse;
	}


	public Response getAccountsInternal(Integer limit, Integer offset,
			boolean countOnly)
	{
		logger.entering(this.getClass().getName(),
				"getAccountsInternal(Integer limit, Integer offset,boolean countOnly)");
		Response myResponse = null;

		ObjectNode response = mapper.createObjectNode();
		ArrayNode accounts = null;
		int numberOfAccounts = 0;
		Integer sortCode = this.getSortCode();
		// We want to set a limit to try to avoid OutOfMemory Exceptions.
		// 250,000 seems a bit large
		if (limit == null)
		{
			limit = 250000;
		}
		if (limit == 0)
		{
			limit = 250000;
		}
		if (offset == null)
		{
			offset = 0;
		}

		String sortCodeString = padSortCode(sortCode);

		if (countOnly)
		{
			try (Connection conn = getConnection();
					PreparedStatement stmt = conn.prepareStatement(
							"SELECT COUNT(*) AS account_count FROM account WHERE sort_code = ?"))
			{
				stmt.setString(1, sortCodeString);
				try (ResultSet rs = stmt.executeQuery())
				{
					if (rs.next())
					{
						numberOfAccounts = rs.getInt("account_count");
					}
				}
			}
			catch (SQLException e)
			{
				response.put(JSON_ERROR_MSG, "Accounts cannot be accessed");
				logger.log(Level.SEVERE, () -> "Accounts cannot be accessed");
				myResponse = Response.status(500).entity(response.toString())
						.build();
				logger.exiting(this.getClass().getName(),
						"getAccountsInternal(Integer limit, Integer offset,boolean countOnly)",
						myResponse);
				return myResponse;
			}
		}
		else
		{
			accounts = mapper.createArrayNode();
			try (Connection conn = getConnection();
					PreparedStatement stmt = conn.prepareStatement(
							"SELECT * FROM account WHERE sort_code = ? "
									+ "ORDER BY account_number LIMIT ? OFFSET ?"))
			{
				stmt.setString(1, sortCodeString);
				stmt.setInt(2, limit);
				stmt.setInt(3, offset);
				try (ResultSet rs = stmt.executeQuery())
				{
					while (rs.next())
					{
						ObjectNode account = mapper.createObjectNode();
						populateAccountFull(account, rs);
						accounts.add(account);
					}
				}
			}
			catch (SQLException e)
			{
				response.put(JSON_ERROR_MSG, "Accounts cannot be accessed");
				logger.log(Level.SEVERE, () -> "Accounts cannot be accessed");
				myResponse = Response.status(500).entity(response.toString())
						.build();
				logger.exiting(this.getClass().getName(),
						"getAccountsInternal(Integer limit, Integer offset,boolean countOnly)",
						myResponse);
				return myResponse;
			}
			numberOfAccounts = accounts.size();
		}
		/*
		 * Parse returned data and return to calling method
		 */

		response.put(JSON_NUMBER_OF_ACCOUNTS, numberOfAccounts);
		if (accounts != null)
		{
			response.set(JSON_ACCOUNTS, accounts);
		}
		myResponse = Response.status(200).entity(response.toString()).build();
		logger.exiting(this.getClass().getName(), DELETE_ACCOUNT, myResponse);
		return myResponse;
	}


	@GET
	@Path("/balance")
	@Produces("application/json")
	public Response getAccountsByBalanceWithOffsetAndLimitExternal(
			@QueryParam("balance") BigDecimal balance,
			@QueryParam("operator") String operator,
			@QueryParam("offset") Integer offset,
			@QueryParam("limit") Integer limit,
			@QueryParam("countOnly") Boolean countOnly)
	{
		// return only accounts with a certain balance
		logger.entering(this.getClass().getName(),
				"getAccountsByBalanceWithOffsetAndLimitExternal(BigDecimal balance, String operator, Integer offset, Integer limit, Boolean countOnly");
		boolean countOnlyReal = false;
		if (countOnly != null)
		{
			countOnlyReal = countOnly.booleanValue();
		}

		Response myResponse = getAccountsByBalanceWithOffsetAndLimitInternal(
				balance, operator, offset, limit, countOnlyReal);
		logger.exiting(this.getClass().getName(),
				"getAccountsByBalanceWithOffsetAndLimitExternal(BigDecimal balance, String operator, Integer offset, Integer limit, Boolean countOnly",
				myResponse);
		return myResponse;
	}


	public Response getAccountsByBalanceWithOffsetAndLimitInternal(
			@QueryParam("balance") BigDecimal balance,
			@QueryParam("operator") String operator,
			@QueryParam("offset") Integer offset,
			@QueryParam("limit") Integer limit, boolean countOnly)
	{
		// return only accounts with a certain balance
		logger.entering(this.getClass().getName(),
				GET_ACCOUNTS_BY_BALANCE_WITH_OFFSET_AND_LIMIT_INTERNAL);
		Response myResponse = null;
		boolean lessThan;
		if (!operator.startsWith("<") && !(operator.startsWith(">")))
		{
			ObjectNode error = mapper.createObjectNode();
			error.put(JSON_ERROR_MSG, "Invalid operator, '" + operator
					+ "' only <= or >= allowed");
			logger.log(Level.WARNING, () -> "Invalid operator, '" + operator
					+ "' only <= or >= allowed");
			logger.exiting(this.getClass().getName(),
					GET_ACCOUNTS_BY_BALANCE_WITH_OFFSET_AND_LIMIT_INTERNAL,
					myResponse);
			myResponse = Response.status(400).entity(error.toString()).build();
			return myResponse;

		}
		lessThan = false;
		if (operator.startsWith("<"))
		{
			lessThan = true;
		}

		if (offset == null)
		{
			offset = 0;
		}
		// We want to set a limit to try to avoid OutOfMemory Exceptions.
		// 250,000 seems a bit large

		if (limit == null || limit.intValue() == 0)
		{
			limit = 250000;
		}

		ObjectNode response = mapper.createObjectNode();
		ArrayNode accounts = null;
		int numberOfAccounts = 0;
		Integer sortCode = this.getSortCode();
		String sortCodeString = padSortCode(sortCode);
		String balanceClause = lessThan ? " AND actual_balance <= ?"
				: " AND actual_balance >= ?";

		if (countOnly)
		{
			try (Connection conn = getConnection();
					PreparedStatement stmt = conn.prepareStatement(
							"SELECT COUNT(*) AS account_count FROM account WHERE sort_code = ?"
									+ balanceClause))
			{
				stmt.setString(1, sortCodeString);
				stmt.setBigDecimal(2, balance);
				try (ResultSet rs = stmt.executeQuery())
				{
					if (rs.next())
					{
						numberOfAccounts = rs.getInt("account_count");
					}
					else
					{
						numberOfAccounts = -1;
					}
				}
			}
			catch (SQLException e)
			{
				ObjectNode error = mapper.createObjectNode();
				error.put(JSON_ERROR_MSG, DB2_READ_FAILURE);
				logger.log(Level.SEVERE, () -> DB2_READ_FAILURE);
				logger.exiting(this.getClass().getName(),
						GET_ACCOUNTS_BY_BALANCE_WITH_OFFSET_AND_LIMIT_INTERNAL,
						myResponse);
				myResponse = Response.status(500).entity(error.toString())
						.build();
				return myResponse;
			}
			if (numberOfAccounts == -1)
			{
				ObjectNode error = mapper.createObjectNode();
				error.put(JSON_ERROR_MSG, DB2_READ_FAILURE);
				logger.log(Level.SEVERE, () -> DB2_READ_FAILURE);
				logger.exiting(this.getClass().getName(),
						GET_ACCOUNTS_BY_BALANCE_WITH_OFFSET_AND_LIMIT_INTERNAL,
						myResponse);
				myResponse = Response.status(500).entity(error.toString())
						.build();
				return myResponse;
			}
		}
		else
		{
			accounts = mapper.createArrayNode();
			try (Connection conn = getConnection();
					PreparedStatement stmt = conn.prepareStatement(
							"SELECT * FROM account WHERE sort_code = ?"
									+ balanceClause
									+ " ORDER BY account_number, actual_balance DESC "
									+ "LIMIT ? OFFSET ?"))
			{
				stmt.setString(1, sortCodeString);
				stmt.setBigDecimal(2, balance);
				stmt.setInt(3, limit);
				stmt.setInt(4, offset);
				try (ResultSet rs = stmt.executeQuery())
				{
					while (rs.next())
					{
						ObjectNode account = mapper.createObjectNode();
						populateAccountFull(account, rs);
						accounts.add(account);
					}
				}
			}
			catch (SQLException e)
			{
				ObjectNode error = mapper.createObjectNode();
				error.put(JSON_ERROR_MSG, DB2_READ_FAILURE);
				logger.log(Level.SEVERE, () -> DB2_READ_FAILURE);
				logger.exiting(this.getClass().getName(),
						GET_ACCOUNTS_BY_BALANCE_WITH_OFFSET_AND_LIMIT_INTERNAL,
						myResponse);
				myResponse = Response.status(500).entity(error.toString())
						.build();
				return myResponse;
			}
			numberOfAccounts = accounts.size();
		}

		/*
		 * Parse returned data and return to calling method
		 */

		response.put(JSON_NUMBER_OF_ACCOUNTS, numberOfAccounts);
		response.set(JSON_ACCOUNTS, accounts);
		myResponse = Response.status(200).entity(response.toString()).build();
		logger.exiting(this.getClass().getName(),
				GET_ACCOUNTS_BY_BALANCE_WITH_OFFSET_AND_LIMIT_INTERNAL,
				myResponse);
		return myResponse;

	}


	private ObjectNode validateNewAccount(AccountJSON newAccount)
	{
		ObjectNode error = mapper.createObjectNode();

		if (newAccount == null)
		{
			error.put(JSON_ERROR_MSG, "Account " + IS_NULL);
			logger.log(Level.WARNING, () -> "Account " + IS_NULL);
			return error;
		}

		if (newAccount.getAccountType() == null)
		{
			error.put(JSON_ERROR_MSG, ACC_TYPE_STRING + IS_NULL);
			logger.log(Level.WARNING, () -> ACC_TYPE_STRING + IS_NULL);
			return error;
		}

		if (!newAccount.validateType(newAccount.getAccountType().trim()))
		{
			error.put(JSON_ERROR_MSG, ACC_TYPE_STRING
					+ newAccount.getAccountType() + NOT_SUPPORTED);
			logger.log(Level.WARNING, () -> ACC_TYPE_STRING
					+ newAccount.getAccountType() + NOT_SUPPORTED);
			return error;
		}

		if (newAccount.getInterestRate() == null)
		{
			error.put(JSON_ERROR_MSG, INTEREST_RATE + IS_NULL);
			logger.log(Level.WARNING, () -> INTEREST_RATE + IS_NULL);
			return error;
		}
		// Interest rate cannot be < 0
		if (newAccount.getInterestRate().signum() < 0)
		{
			error.put(JSON_ERROR_MSG, INTEREST_RATE_LESS_THAN_ZERO);
			logger.log(Level.WARNING, () -> (INTEREST_RATE_LESS_THAN_ZERO));
			return error;
		}

		// Interest rate cannot be > 9999.99%
		if (newAccount.getInterestRate()
				.compareTo(new BigDecimal("9999.99")) > 0)
		{
			error.put(JSON_ERROR_MSG, INTEREST_RATE_TOO_HIGH);
			logger.log(Level.WARNING, () -> (INTEREST_RATE_TOO_HIGH));
			return error;
		}

		// Interest rate cannot have more than 2dp
		BigDecimal myInterestRate = newAccount.getInterestRate();
		if (myInterestRate.scale() > 2)
		{
			error.put(JSON_ERROR_MSG,
					"Interest rate cannot have more than 2 decimal places. "
							+ myInterestRate.toPlainString());
			logger.log(Level.WARNING,
					() -> ("Interest rate cannot have more than 2 decimal places."
							+ myInterestRate.toPlainString()));
			return error;
		}

		if (newAccount.getOverdraft() == null)
		{
			error.put(JSON_ERROR_MSG, OVERDRAFT_LIMIT + IS_NULL);
			logger.log(Level.WARNING, () -> OVERDRAFT_LIMIT + IS_NULL);
			return error;
		}

		// Overdraft limit cannot be < 0
		if (newAccount.getOverdraft().intValue() < 0)
		{
			error.put(JSON_ERROR_MSG,
					"Overdraft limit cannot be less than zero.");
			logger.log(Level.WARNING,
					() -> ("Overdraft limit cannot be less than zero."));
			return error;
		}

		if (newAccount.getCustomerNumber() == null)
		{
			error.put(JSON_ERROR_MSG, CUSTOMER_NUMBER + IS_NULL);
			logger.log(Level.WARNING, () -> CUSTOMER_NUMBER + IS_NULL);
			return error;
		}


		// Customer number cannot be < 1
		Long customerNumberLong = Long
				.parseLong(newAccount.getCustomerNumber());
		if (customerNumberLong.longValue() < 1)
		{
			error.put(JSON_ERROR_MSG,
					"Customer number cannot be less than one.");
			logger.log(Level.WARNING,
					() -> ("Customer number cannot be less than one."));
			return error;
		}

		// Customer number cannot be 9999999999
		if (customerNumberLong.longValue() == 9999999999L)
		{
			error.put(JSON_ERROR_MSG,
					"Customer number cannot be 9,999,999,999.");
			logger.log(Level.WARNING,
					() -> ("Customer number cannot be 9,999,999,999."));
			return error;
		}

		// Sortcode is not valid for this bank
		Integer inputSortCode = Integer.parseInt(newAccount.getSortCode());
		Integer thisSortCode = this.getSortCode();

		if (inputSortCode.intValue() != thisSortCode.intValue())
		{
			error.put(JSON_ERROR_MSG, SORT_CODE_LITERAL + inputSortCode
					+ NOT_VALID_FOR_THIS_BANK + thisSortCode + ")");
			logger.log(Level.WARNING, () -> (SORT_CODE_LITERAL + inputSortCode
					+ NOT_VALID_FOR_THIS_BANK + thisSortCode + ")"));
			return error;
		}

		CustomerResource myCustomer = new CustomerResource();
		Response customerResponse = myCustomer
				.getCustomerInternal(customerNumberLong);
		// Customer number cannot be found
		if (customerResponse.getStatus() != 200)
		{
			error.put(JSON_ERROR_MSG, CUSTOMER_NUMBER_LITERAL
					+ customerNumberLong.longValue() + CANNOT_BE_FOUND);
			logger.log(Level.WARNING, () -> CUSTOMER_NUMBER_LITERAL
					+ customerNumberLong.longValue() + CANNOT_BE_FOUND);
			return error;

		}

		return null;
	}


	/**
	 * Obtain a JDBC connection to the shared bank-core PostgreSQL store. This
	 * replaces the deleted JCICS / Db2 connection lifecycle that the former
	 * shared data-access base class provided.
	 *
	 * @return an open {@link Connection}; the caller owns closing it
	 * @throws SQLException if the connection cannot be established
	 */
	private Connection getConnection() throws SQLException
	{
		String host = System.getenv("DB_HOST");
		if (host == null || host.trim().isEmpty())
		{
			host = "localhost";
		}
		String url = "jdbc:postgresql://" + host + ":" + DB_PORT + "/" + DB_NAME;
		return DriverManager.getConnection(url, DB_USER, DB_PASSWORD);
	}


	/**
	 * Re-read the java.util.logging configuration. Previously inherited from the
	 * deleted shared data-access base class; retained locally so the constructor
	 * behaviour is unchanged.
	 */
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


	/**
	 * Populate a JSON account object from the current row of a result set,
	 * emitting exactly the frozen field names. Money columns are read as
	 * {@link BigDecimal} (scale 2) so no floating-point type is ever involved.
	 *
	 * @param account the JSON object to populate
	 * @param rs      a result set positioned on an account row
	 * @throws SQLException on any data-access error
	 */
	private void populateAccountFull(ObjectNode account, ResultSet rs)
			throws SQLException
	{
		account.put(JSON_SORT_CODE, rs.getString("sort_code").trim());
		account.put("id", rs.getString("account_number"));
		account.put(JSON_CUSTOMER_NUMBER, rs.getString("customer_number"));
		account.put(JSON_ACCOUNT_TYPE, rs.getString("account_type").trim());
		account.put(JSON_AVAILABLE_BALANCE,
				rs.getBigDecimal("available_balance"));
		account.put(JSON_ACTUAL_BALANCE, rs.getBigDecimal("actual_balance"));
		account.put(JSON_INTEREST_RATE, rs.getBigDecimal("interest_rate"));
		account.put(JSON_OVERDRAFT, rs.getInt("overdraft_limit"));
		Date lastStatement = rs.getDate("last_statement_date");
		account.put(JSON_LAST_STATEMENT_DATE,
				lastStatement == null ? null : lastStatement.toString().trim());
		Date nextStatement = rs.getDate("next_statement_date");
		account.put(JSON_NEXT_STATEMENT_DATE,
				nextStatement == null ? null : nextStatement.toString().trim());
		Date opened = rs.getDate("opened");
		account.put(JSON_DATE_OPENED,
				opened == null ? null : opened.toString().trim());
	}


	/**
	 * Read a single account, reproducing the legacy "highest account number"
	 * sentinel: an account number of 99999999 returns the account with the
	 * greatest number for this sort code rather than an exact match.
	 *
	 * @param conn          an open connection
	 * @param accountNumber the account number, or 99999999 for the highest
	 * @param sortCode      the bank sort code
	 * @return a populated JSON account object, or {@code null} if none found
	 * @throws SQLException on any data-access error
	 */
	private ObjectNode readSingleAccount(Connection conn, long accountNumber,
			int sortCode) throws SQLException
	{
		String sortCodeString = padSortCode(sortCode);
		boolean highest = (accountNumber == 99999999L);
		String sql;
		if (highest)
		{
			sql = "SELECT * FROM account WHERE sort_code = ? "
					+ "ORDER BY account_number DESC LIMIT 1";
		}
		else
		{
			sql = "SELECT * FROM account WHERE account_number = ? AND sort_code = ?";
		}
		try (PreparedStatement stmt = conn.prepareStatement(sql))
		{
			if (highest)
			{
				stmt.setString(1, sortCodeString);
			}
			else
			{
				stmt.setString(1, padAccountNumber((int) accountNumber));
				stmt.setString(2, sortCodeString);
			}
			try (ResultSet rs = stmt.executeQuery())
			{
				if (rs.next())
				{
					ObjectNode account = mapper.createObjectNode();
					populateAccountFull(account, rs);
					return account;
				}
			}
		}
		return null;
	}


	private String padCustomerNumber(String customerNumber2)
	{
		// Customer Numbers are 10 digit numbers, prefixed with zeroes as
		// required
		StringBuilder myStringBuilder = new StringBuilder();
		for (int z = customerNumber2.length(); z < CUSTOMER_NUMBER_LENGTH; z++)
		{
			myStringBuilder.append("0");
		}
		myStringBuilder.append(customerNumber2);
		return myStringBuilder.toString();
	}


	private String padAccountNumber(Integer accountNumber2)
	{
		// Account Numbers are 8 digit numbers, prefixed with zeroes as required
		StringBuilder myStringBuilder = new StringBuilder();
		for (int z = accountNumber2.toString()
				.length(); z < ACCOUNT_NUMBER_LENGTH; z++)
		{
			myStringBuilder.append("0");
		}
		myStringBuilder.append(accountNumber2.toString());
		return myStringBuilder.toString();
	}


	private String padSortCode(Integer sortcode2)
	{
		// Sort codes are 6 digit numbers, prefixed with zeroes as required
		StringBuilder myStringBuilder = new StringBuilder();

		for (int z = sortcode2.toString().length(); z < SORT_CODE_LENGTH; z++)
		{
			myStringBuilder.append("0");
		}
		myStringBuilder.append(sortcode2.toString());
		return myStringBuilder.toString();

	}


	private long getNextMonth(Date today)
	{
		// What is next month?
		long nextMonthInMs;
		Calendar myCalendar = Calendar.getInstance();
		myCalendar.setTime(today);
		switch (myCalendar.get(Calendar.MONTH))
		{
		case 8:
		case 3:
		case 5:
		case 10:
			nextMonthInMs = 1000L * 60L * 60L * 24L * 30L;
			break;
		case 1:
			if ((myCalendar.get(Calendar.YEAR)) % 4 > 0)
			{
				nextMonthInMs = 1000L * 60L * 60L * 24L * 28L;
			}
			else
			{
				if (myCalendar.get(Calendar.YEAR) % 100 > 0)
				{
					nextMonthInMs = 1000L * 60L * 60L * 24L * 29L;
				}
				else
				{
					if (myCalendar.get(Calendar.YEAR) % 400 == 0)
					{
						nextMonthInMs = 1000L * 60L * 60L * 24L * 29L;
					}
					else
					{
						nextMonthInMs = 1000L * 60L * 60L * 24L * 28L;
					}
				}
			}
			break;
		default:
			nextMonthInMs = 1000L * 60L * 60L * 24L * 31L;
			break;
		}
		return nextMonthInMs;

	}

}
