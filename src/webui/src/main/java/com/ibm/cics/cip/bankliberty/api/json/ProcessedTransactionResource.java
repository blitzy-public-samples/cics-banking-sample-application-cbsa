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
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.DateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import jakarta.ws.rs.Consumes;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;

import jakarta.ws.rs.Path;

import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * This class describes the methods of the ProcessedTransaction Resource
 *
 * <p>
 * <b>Tech-stack migration note (CBSA COBOL/CICS &rarr; standalone Java).</b> The
 * external JAX-RS contract of this resource is FROZEN and reproduced verbatim
 * (paths, HTTP verbs, {@code @Produces}/{@code @Consumes}, query/path params,
 * JSON field names, HTTP status codes and the {@code errorMessage} envelope).
 * Only the implementation was re-pointed:
 * </p>
 * <ul>
 * <li>The removed IBM mainframe libraries (the JCICS server API, the WebSphere
 * JSON API and the JZOS record binding for the legacy PROCTRAN copybook) are
 * replaced by Jackson ({@link ObjectMapper}/{@link ObjectNode}/{@link ArrayNode})
 * for JSON construction.</li>
 * <li>The deleted Db2 processed-transaction access class and its CICS/JNDI
 * connection helper are replaced by a thin JDBC layer on the shared PostgreSQL
 * store used by the {@code bank-core}
 * module ({@code jdbc:postgresql://${DB_HOST:localhost}:5432/cbsa}). The
 * {@code processed_transaction} table is APPEND-ONLY with a logical
 * {@code deleted} flag (bank-core ADR-006), so the GET list reads only active
 * rows ({@code deleted = false}) and the write endpoints only insert rows.</li>
 * <li>All monetary values remain {@link java.math.BigDecimal} at scale 2 with
 * {@link java.math.RoundingMode#HALF_UP}; no floating-point primitive is used
 * for money.</li>
 * </ul>
 *
 * <p>
 * The legacy Db2 {@code PROCTRAN} table keyed audit rows in part by the account
 * number ({@code PROCTRAN_NUMBER}). The bank-core relational schema redesigns
 * {@code processed_transaction} with a composite primary key
 * {@code (sort_code, transaction_number)} and has no dedicated account-number
 * column, so {@code transaction_number} is allocated here as a gap-tolerant
 * per-sort-code sequence to keep the audit log append-able. The frozen JSON
 * {@code accountNumber} field continues to be populated from the
 * {@code transaction_number} column (the structural successor of
 * {@code PROC-TRAN-NUMBER}); the field name, width and types are unchanged.
 * </p>
 */

@Path("/processedTransaction")
public class ProcessedTransactionResource
{

	private static Logger logger = Logger
			.getLogger("com.ibm.cics.cip.bankliberty.api.json");

	private static final String JSON_NUMBER_OF_RECORDS = "numberOfProcessedTransactionRecords";

	private static final String JSON_PROCESSED_TRANSACTIONS = "processedTransactions";

	private static final String JSON_SORT_CODE = "sortCode";

	private static final String JSON_TARGET_SORT_CODE = "targetSortcode";

	private static final String JSON_TARGET_ACCOUNT = "targetAccount";

	private static final String JSON_ACCOUNT_NUMBER = "accountNumber";

	private static final String JSON_AMOUNT = "amount";

	private static final String JSON_TIMESTAMP = "timestamp";

	private static final String JSON_DESCRIPTION = "description";

	private static final String JSON_TYPE = "type";

	private static final String JSON_REFERENCE = "reference";

	private static final String JSON_ACCOUNT_TYPE = "accountType";

	private static final String JSON_LAST_STATEMENT = "lastStatement";

	private static final String JSON_NEXT_STATEMENT = "nextStatement";

	private static final String JSON_CUSTOMER_NAME = "customerName";

	private static final String JSON_DATE_OF_BIRTH = "dateOfBirth";

	private static final String JSON_ERROR_MSG = "errorMessage";

	private static final String JSON_SUCCESS = "success";

	private static final String JSON_CUSTOMER = "customer";

	private static final String LIMIT = "limit";

	private static final String OFFSET = "offset";

	/*
	 * PROCTRAN transaction-type codes (formerly supplied by the deleted JZOS
	 * PROCTRAN record binding). The
	 * values are the exact three-character COBOL 88-level codes from PROCTRAN.cpy
	 * and match bank-core's processed_transaction.type_code CHECK constraint.
	 */
	private static final String PROC_TY_CREDIT = "CRE";

	private static final String PROC_TY_DEBIT = "DEB";

	private static final String PROC_TY_WEB_CREATE_ACCOUNT = "ICA";

	private static final String PROC_TY_WEB_CREATE_CUSTOMER = "ICC";

	private static final String PROC_TY_WEB_DELETE_ACCOUNT = "IDA";

	private static final String PROC_TY_WEB_DELETE_CUSTOMER = "IDC";

	private static final String PROC_TY_BRANCH_CREATE_ACCOUNT = "OCA";

	private static final String PROC_TY_BRANCH_CREATE_CUSTOMER = "OCC";

	private static final String PROC_TY_BRANCH_DELETE_ACCOUNT = "ODA";

	private static final String PROC_TY_BRANCH_DELETE_CUSTOMER = "ODC";

	private static final String PROC_TY_TRANSFER = "TFR";

	/*
	 * Fixed description-area literals (formerly PROCTRAN 88-level flags). These
	 * preserve the exact 40-byte PROC-TRAN-DESC layouts so that rows written
	 * here are parsed back identically by the GET list below.
	 */
	private static final String PROC_TRAN_DESC_XFR_FLAG = "TRANSFER";

	private static final String PROC_DESC_DELACC_FLAG = "DELETE";

	private static final String PROC_DESC_CREACC_FLAG = "CREATE";

	/* Fixed widths of the display-numeric / character sub-fields. */
	private static final int CUSTOMER_NUMBER_LENGTH = 10;

	private static final int CUSTOMER_NAME_LENGTH = 14;

	private static final int ACCOUNT_TYPE_LENGTH = 8;

	/*
	 * Shared PostgreSQL connection coordinates for the bank-core data store. The
	 * database, user and password are all "cbsa" (per the setup constraint) and
	 * DB_HOST is overridable via the environment, defaulting to localhost.
	 */
	private static final String DB_NAME = "cbsa";

	private static final String DB_USER = "cbsa";

	private static final String DB_PASSWORD = "cbsa";

	private static final int DB_PORT = 5432;

	/*
	 * Bounded retry count for the allocate-then-insert of an audit row, guarding
	 * against a primary-key clash should two writers consume the same
	 * transaction_number concurrently.
	 */
	private static final int MAX_INSERT_ATTEMPTS = 5;

	/*
	 * The default error message emitted when the processed-transaction store is
	 * not reachable. Preserved verbatim so the frozen errorMessage envelope and
	 * its 500 status are byte-for-byte unchanged for existing consumers.
	 */
	private static final String PROCTRAN_NOT_ACCESSIBLE = "Proctran DB2 table not accessible. Please contact your system administrator.";

	/* Single shared Jackson mapper used to build the frozen JSON envelopes. */
	private static final ObjectMapper mapper = new ObjectMapper();


	public ProcessedTransactionResource()
	{
		sortOutLogging();
	}


	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public Response getProcessedTransactionExternal(
			@QueryParam(LIMIT) Integer limit,
			@QueryParam(OFFSET) Integer offset)
	{
		return getProcessedTransactionInternal(limit, offset);
	}


	public Response getProcessedTransactionInternal(
			@QueryParam(LIMIT) Integer limit,
			@QueryParam(OFFSET) Integer offset)
	{

		if (offset == null)
		{
			offset = 0;
		}
		if (limit == null)
		{
			limit = 250000;
		}
		ObjectNode response = mapper.createObjectNode();
		ArrayNode processedTransactionsJSON = mapper.createArrayNode();
		int numberOfProcessedTransactions = 0;
		Integer sortCode = this.getSortCode();
		String sortCodeString = String.format("%06d", sortCode);

		// Read only ACTIVE rows (logical-delete model) for this sort code,
		// ordered chronologically, applying the same offset/limit window the
		// legacy implementation used (rows offset+1 .. offset+limit).
		String sql = "SELECT sort_code, transaction_number, date, time, ref, type_code, description, amount "
				+ "FROM processed_transaction "
				+ "WHERE deleted = false AND sort_code = ? "
				+ "ORDER BY date ASC, time ASC OFFSET ? LIMIT ?";

		DateFormat myDateFormat = DateFormat.getDateInstance();
		DateFormat myDateTimeFormat = DateFormat.getDateTimeInstance();

		try (Connection conn = getConnection();
				PreparedStatement stmt = conn.prepareStatement(sql))
		{
			stmt.setString(1, sortCodeString);
			stmt.setInt(2, offset.intValue());
			stmt.setInt(3, limit.intValue());

			try (ResultSet rs = stmt.executeQuery())
			{
				while (rs.next())
				{
					ObjectNode proctran = mapper.createObjectNode();

					String description = rs.getString("description");
					String type = rs.getString("type_code");
					BigDecimal amount = rs.getBigDecimal("amount");
					Date transactionDate = combineDateTime(rs.getDate("date"),
							rs.getTime("time"));

					proctran.put(JSON_SORT_CODE, rs.getString("sort_code"));
					proctran.put(JSON_ACCOUNT_NUMBER,
							rs.getString("transaction_number"));
					proctran.put(JSON_AMOUNT, amount == null ? null
							: amount.setScale(2, RoundingMode.HALF_UP));
					proctran.put(JSON_TIMESTAMP,
							transactionDate == null ? null
									: myDateTimeFormat.format(transactionDate));
					proctran.put(JSON_DESCRIPTION,
							description == null ? null : description.trim());
					proctran.put(JSON_TYPE, type);
					proctran.put(JSON_REFERENCE, rs.getString("ref"));

					proctran = processDeleteCreateAccount(proctran, type,
							description, myDateFormat);
					proctran = processDeleteCreateCustomer(proctran, type,
							description, myDateFormat);
					proctran = processTransfer(proctran, type, description);

					processedTransactionsJSON.add(proctran);
				}
			}
		}
		catch (SQLException e)
		{
			logger.severe(e.getLocalizedMessage());
			ObjectNode error = mapper.createObjectNode();
			error.put(JSON_ERROR_MSG, PROCTRAN_NOT_ACCESSIBLE);
			return Response.status(500).entity(writeJson(error)).build();
		}

		numberOfProcessedTransactions = processedTransactionsJSON.size();

		/*
		 * Parse returned data and return to calling method
		 */

		response.put(JSON_NUMBER_OF_RECORDS, numberOfProcessedTransactions);
		response.set(JSON_PROCESSED_TRANSACTIONS, processedTransactionsJSON);
		response.put(JSON_SUCCESS, "Y");

		return Response.status(200).entity(writeJson(response)).build();
	}


	private ObjectNode processTransfer(ObjectNode proctran, String type,
			String description)
	{
		// Process bank to bank transfer records
		if (type != null && type.compareTo(PROC_TY_TRANSFER) == 0
				&& description != null && description.length() >= 40)
		{
			String targetSortcode = description.substring(26, 32);
			String targetAccount = description.substring(32, 40);
			proctran.put(JSON_TARGET_ACCOUNT, targetAccount);
			proctran.put(JSON_TARGET_SORT_CODE, targetSortcode);
		}
		return proctran;
	}


	private ObjectNode processDeleteCreateCustomer(ObjectNode proctran,
			String type, String description, DateFormat myDateFormat)
	{
		// Deal with create customer and delete customer
		if (type != null
				&& (type.compareTo(PROC_TY_BRANCH_DELETE_CUSTOMER) == 0
						|| type.compareTo(PROC_TY_WEB_DELETE_CUSTOMER) == 0
						|| type.compareTo(PROC_TY_BRANCH_CREATE_CUSTOMER) == 0
						|| type.compareTo(PROC_TY_WEB_CREATE_CUSTOMER) == 0)
				&& description != null && description.length() >= 40)
		{
			String customerNumber = description.substring(6, 16);
			String customerName = description.substring(16, 30);
			String dateOfBirthDD = description.substring(30, 32);
			String dateOfBirthMM = description.substring(33, 35);
			String dateOfBirthYYYY = description.substring(36, 40);
			Date dateOfBirth = makeDate(dateOfBirthYYYY, dateOfBirthMM,
					dateOfBirthDD);
			proctran.put(JSON_DATE_OF_BIRTH, myDateFormat.format(dateOfBirth));
			proctran.put(JSON_CUSTOMER_NAME, customerName);
			proctran.put(JSON_CUSTOMER, customerNumber);
		}
		return proctran;
	}


	private ObjectNode processDeleteCreateAccount(ObjectNode proctran,
			String type, String description, DateFormat myDateFormat)
	{
		// Deal with create account and delete account
		if (type != null
				&& (type.compareTo(PROC_TY_BRANCH_DELETE_ACCOUNT) == 0
						|| type.compareTo(PROC_TY_WEB_DELETE_ACCOUNT) == 0
						|| type.compareTo(PROC_TY_BRANCH_CREATE_ACCOUNT) == 0
						|| type.compareTo(PROC_TY_WEB_CREATE_ACCOUNT) == 0)
				&& description != null && description.length() >= 40)
		{
			String customer = description.substring(0, 10);
			String accountType = description.substring(10, 18);
			Date lastStatement = makeDate(description.substring(22, 26),
					description.substring(20, 22),
					description.substring(18, 20));
			Date nextStatement = makeDate(description.substring(30, 34),
					description.substring(28, 30),
					description.substring(26, 28));
			proctran.put(JSON_ACCOUNT_TYPE, accountType);
			proctran.put(JSON_LAST_STATEMENT,
					myDateFormat.format(lastStatement));
			proctran.put(JSON_NEXT_STATEMENT,
					myDateFormat.format(nextStatement));
			proctran.put(JSON_CUSTOMER, customer);
		}
		return proctran;
	}


	private Integer getSortCode()
	{
		SortCodeResource mySortCodeResource = new SortCodeResource();
		Response mySortCodeJSON = mySortCodeResource.getSortCode();
		String mySortCode = ((String) mySortCodeJSON.getEntity()).substring(13,
				19);
		return Integer.valueOf(mySortCode);
	}


	@POST
	@Produces("application/json")
	@Path("/debitCreditAccount")
	public Response writeExternal(
			ProcessedTransactionDebitCreditJSON proctranDbCr)
	{
		return writeInternal(proctranDbCr);
	}


	public Response writeInternal(
			ProcessedTransactionDebitCreditJSON proctranDbCr)
	{
		if (proctranDbCr.getAmount().compareTo(new BigDecimal(0)) < 0)
		{
			if (insertProctranRecord(proctranDbCr.getSortCode(), PROC_TY_DEBIT,
					"INTERNET WTHDRW", proctranDbCr.getAmount()))
			{
				return Response.ok().build();
			}
			else
			{
				logger.severe("PROCTRAN Insert debit didn't work");
				return Response.serverError().build();
			}
		}
		else
		{
			if (insertProctranRecord(proctranDbCr.getSortCode(), PROC_TY_CREDIT,
					"INTERNET RECVED", proctranDbCr.getAmount()))
			{
				return Response.ok().build();
			}
			else
			{
				logger.severe("PROCTRAN Insert credit didn't work");
				return Response.serverError().build();
			}
		}
	}


	@POST
	@Produces("application/json")
	@Consumes(MediaType.APPLICATION_JSON)
	@Path("/transferLocal")
	public Response writeTransferLocalExternal(
			ProcessedTransactionTransferLocalJSON proctranLocal)
	{
		return writeTransferLocalInternal(proctranLocal);
	}


	public Response writeTransferLocalInternal(
			ProcessedTransactionTransferLocalJSON proctranLocal)
	{
		String description = buildTransferDescription(
				proctranLocal.getSortCode(),
				proctranLocal.getTargetAccountNumber());

		if (insertProctranRecord(proctranLocal.getSortCode(), PROC_TY_TRANSFER,
				description, proctranLocal.getAmount()))
		{
			return Response.ok().build();
		}
		else
		{
			return Response.serverError().build();
		}
	}


	@POST
	@Produces("application/json")
	@Consumes(MediaType.APPLICATION_JSON)
	@Path("/deleteCustomer")
	public Response writeDeleteCustomerExternal(
			ProcessedTransactionDeleteCustomerJSON myDeletedCustomer)
	{
		return writeDeleteCustomerInternal(myDeletedCustomer);
	}


	public Response writeDeleteCustomerInternal(
			ProcessedTransactionDeleteCustomerJSON myDeletedCustomer)
	{
		String description = buildCustomerDescription(
				myDeletedCustomer.getSortCode(),
				myDeletedCustomer.getCustomerNumber(),
				myDeletedCustomer.getCustomerName(),
				myDeletedCustomer.getCustomerDOB());

		if (insertProctranRecord(myDeletedCustomer.getSortCode(),
				PROC_TY_WEB_DELETE_CUSTOMER, description, BigDecimal.ZERO))
		{
			return Response.ok().build();
		}
		else
		{
			return Response.serverError().build();
		}

	}


	@POST
	@Produces("application/json")
	@Consumes(MediaType.APPLICATION_JSON)
	@Path("/createCustomer")
	public Response writeCreateCustomerExternal(
			ProcessedTransactionCreateCustomerJSON myCreatedCustomer)
	{
		return writeCreateCustomerInternal(myCreatedCustomer);
	}


	public Response writeCreateCustomerInternal(
			ProcessedTransactionCreateCustomerJSON myCreatedCustomer)
	{
		String description = buildCustomerDescription(
				myCreatedCustomer.getSortCode(),
				myCreatedCustomer.getCustomerNumber(),
				myCreatedCustomer.getCustomerName(),
				myCreatedCustomer.getCustomerDOB());

		if (insertProctranRecord(myCreatedCustomer.getSortCode(),
				PROC_TY_WEB_CREATE_CUSTOMER, description, BigDecimal.ZERO))
		{
			return Response.ok().build();
		}
		else
		{
			return Response.serverError().build();
		}

	}


	@POST
	@Produces("application/json")
	@Consumes(MediaType.APPLICATION_JSON)
	@Path("/deleteAccount")
	public Response writeDeleteAccountExternal(
			ProcessedTransactionAccountJSON myDeletedAccount)
	{
		return writeDeleteAccountInternal(myDeletedAccount);
	}


	public Response writeDeleteAccountInternal(
			ProcessedTransactionAccountJSON myDeletedAccount)
	{
		String description = buildAccountDescription(
				myDeletedAccount.getCustomerNumber(),
				myDeletedAccount.getType(),
				myDeletedAccount.getLastStatement(),
				myDeletedAccount.getNextStatement(), PROC_DESC_DELACC_FLAG);

		if (insertProctranRecord(myDeletedAccount.getSortCode(),
				PROC_TY_WEB_DELETE_ACCOUNT, description,
				myDeletedAccount.getActualBalance()))
		{
			return Response.ok().build();
		}
		else
		{
			return Response.serverError().build();
		}

	}


	@POST
	@Produces("application/json")
	@Consumes(MediaType.APPLICATION_JSON)
	@Path("/createAccount")
	public Response writeCreateAccountExternal(
			ProcessedTransactionAccountJSON myCreatedAccount)
	{
		return writeCreateAccountInternal(myCreatedAccount);
	}


	public Response writeCreateAccountInternal(
			ProcessedTransactionAccountJSON myCreatedAccount)
	{
		String description = buildAccountDescription(
				myCreatedAccount.getCustomerNumber(),
				myCreatedAccount.getType(),
				myCreatedAccount.getLastStatement(),
				myCreatedAccount.getNextStatement(), PROC_DESC_CREACC_FLAG);

		if (insertProctranRecord(myCreatedAccount.getSortCode(),
				PROC_TY_WEB_CREATE_ACCOUNT, description,
				myCreatedAccount.getActualBalance()))
		{
			return Response.ok().build();
		}
		else
		{
			return Response.serverError().build();
		}

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


	/**
	 * Serialises a Jackson node to its compact JSON string. Mirrors the legacy
	 * JSON serialisation behaviour while routing through the shared mapper; on
	 * the (practically impossible) serialisation failure it falls back to
	 * {@link ObjectNode#toString()} so a response entity is always
	 * produced.
	 *
	 * @param node the node to serialise
	 * @return the JSON string for the supplied node
	 */
	private String writeJson(ObjectNode node)
	{
		try
		{
			return mapper.writeValueAsString(node);
		}
		catch (JsonProcessingException e)
		{
			logger.severe(e.getLocalizedMessage());
			return node.toString();
		}
	}


	/**
	 * Allocates the next {@code transaction_number} for the supplied sort code.
	 * Because the relational {@code processed_transaction} table has a composite
	 * primary key {@code (sort_code, transaction_number)} and no database
	 * identity/sequence generator (bank-core ADR-003), the value is derived by
	 * incrementing the current maximum for the sort code. The result is the
	 * eight-character, zero-padded display-numeric form expected by the
	 * {@code CHAR(8)} column.
	 *
	 * @param conn           an open connection
	 * @param paddedSortCode the six-character sort code
	 * @return the next eight-character transaction number
	 * @throws SQLException if the lookup fails
	 */
	private String allocateTransactionNumber(Connection conn,
			String paddedSortCode) throws SQLException
	{
		String sql = "SELECT COALESCE(MAX(CAST(TRIM(transaction_number) AS BIGINT)), 0) + 1 AS next_number "
				+ "FROM processed_transaction WHERE sort_code = ?";
		try (PreparedStatement stmt = conn.prepareStatement(sql))
		{
			stmt.setString(1, paddedSortCode);
			try (ResultSet rs = stmt.executeQuery())
			{
				long nextNumber = 1L;
				if (rs.next())
				{
					nextNumber = rs.getLong("next_number");
				}
				return String.format("%08d", nextNumber);
			}
		}
	}


	/**
	 * Appends a single audit row to the shared {@code processed_transaction}
	 * table. The table is append-only with a logical {@code deleted} flag
	 * (bank-core ADR-006); every row is inserted with {@code deleted = false}
	 * and rows are never physically removed. The {@code amount} is stored as a
	 * {@link BigDecimal} at scale 2 ({@link RoundingMode#HALF_UP}); the
	 * {@code date}/{@code time} columns capture the current instant, and
	 * {@code ref} is derived from the allocated transaction number.
	 *
	 * @param sortCode    the sort code of the originating account
	 * @param typeCode    the three-character PROCTRAN type code
	 * @param description the (up to) 40-character description area
	 * @param amount      the monetary amount (scaled to 2 decimal places)
	 * @return {@code true} if the row was inserted, {@code false} otherwise
	 */
	private boolean insertProctranRecord(String sortCode, String typeCode,
			String description, BigDecimal amount)
	{
		String paddedSortCode = String.format("%06d",
				Integer.parseInt(sortCode.trim()));
		BigDecimal scaledAmount = amount.setScale(2, RoundingMode.HALF_UP);
		String insertSql = "INSERT INTO processed_transaction "
				+ "(sort_code, transaction_number, date, time, ref, type_code, description, amount, deleted) "
				+ "VALUES (?, ?, ?, ?, ?, ?, ?, ?, false)";

		for (int attempt = 1; attempt <= MAX_INSERT_ATTEMPTS; attempt++)
		{
			try (Connection conn = getConnection())
			{
				String transactionNumber = allocateTransactionNumber(conn,
						paddedSortCode);
				String reference = String.format("%012d",
						Long.parseLong(transactionNumber));
				long nowMillis = System.currentTimeMillis();

				try (PreparedStatement stmt = conn
						.prepareStatement(insertSql))
				{
					stmt.setString(1, paddedSortCode);
					stmt.setString(2, transactionNumber);
					stmt.setDate(3, new java.sql.Date(nowMillis));
					stmt.setTime(4, new java.sql.Time(nowMillis));
					stmt.setString(5, reference);
					stmt.setString(6, typeCode);
					stmt.setString(7, description);
					stmt.setBigDecimal(8, scaledAmount);
					stmt.executeUpdate();
				}
				return true;
			}
			catch (SQLException e)
			{
				// A concurrent insert may have consumed the same
				// transaction_number (primary-key clash); log and retry a
				// bounded number of times before reporting failure.
				logger.severe(e.getLocalizedMessage());
			}
		}
		return false;
	}


	/**
	 * Builds the 40-character transfer description area (PROC-TRAN-DESC-XFR):
	 * the literal {@code TRANSFER} flag left-justified in a 26-column header,
	 * followed by the six-digit sort code and the eight-digit target account.
	 *
	 * @param sortCode            the (source) sort code; for a local transfer it
	 *                            is also the target sort code
	 * @param targetAccountNumber the destination account number
	 * @return the fixed-width 40-character description
	 */
	private String buildTransferDescription(String sortCode,
			String targetAccountNumber)
	{
		StringBuilder description = new StringBuilder();
		description.append(String.format("%-26s", PROC_TRAN_DESC_XFR_FLAG));
		description.append(
				String.format("%06d", Integer.parseInt(sortCode.trim())));
		description.append(String.format("%08d",
				Integer.parseInt(targetAccountNumber.trim())));
		return description.toString();
	}


	/**
	 * Builds the 40-character create/delete-customer description area
	 * (PROC-TRAN-DESC-CRECUS / DELCUS): six-digit sort code, ten-digit customer
	 * number, fourteen-character name and the {@code DD-MM-YYYY} date of birth.
	 *
	 * @param sortCode       the sort code
	 * @param customerNumber the customer number
	 * @param customerName   the customer name
	 * @param customerDOB    the customer date of birth
	 * @return the fixed-width 40-character description
	 */
	private String buildCustomerDescription(String sortCode,
			String customerNumber, String customerName, Date customerDOB)
	{
		StringBuilder description = new StringBuilder();
		description.append(
				String.format("%06d", Integer.parseInt(sortCode.trim())));
		description.append(padCustomerNumber(customerNumber));
		description.append(padCustomerName(customerName));
		description.append(formatCustomerDOB(customerDOB));
		return description.toString();
	}


	/**
	 * Builds the 40-character create/delete-account description area
	 * (PROC-TRAN-DESC-CREACC / DELACC): ten-digit customer number, eight-char
	 * account type, the {@code DDMMYYYY} last and next statement dates and the
	 * six-character footer flag ({@code CREATE} or {@code DELETE}).
	 *
	 * @param customerNumber the owning customer number
	 * @param accountType    the account type
	 * @param lastStatement  the last statement date
	 * @param nextStatement  the next statement date
	 * @param footer         the six-character footer flag
	 * @return the fixed-width 40-character description
	 */
	private String buildAccountDescription(String customerNumber,
			String accountType, Date lastStatement, Date nextStatement,
			String footer)
	{
		StringBuilder description = new StringBuilder();
		description.append(String.format("%010d",
				Long.parseLong(customerNumber.trim())));
		description.append(padAccountType(accountType));
		description.append(formatStatementDate(lastStatement));
		description.append(formatStatementDate(nextStatement));
		description.append(footer);
		return description.toString();
	}


	/**
	 * Left-zero-pads a customer number to its ten-character display-numeric
	 * width, preserving the legacy fixed-width representation.
	 *
	 * @param customerNumber the customer number
	 * @return the padded customer number
	 */
	private String padCustomerNumber(String customerNumber)
	{
		StringBuilder builder = new StringBuilder();
		for (int i = customerNumber.length(); i < CUSTOMER_NUMBER_LENGTH; i++)
		{
			builder.append('0');
		}
		builder.append(customerNumber);
		return builder.toString();
	}


	/**
	 * Renders the fourteen-character customer-name field exactly as the legacy
	 * writer did: shorter names are left-padded with zeros to width and the
	 * result is truncated to fourteen characters.
	 *
	 * @param customerName the customer name
	 * @return the fourteen-character name field
	 */
	private String padCustomerName(String customerName)
	{
		StringBuilder builder = new StringBuilder();
		for (int i = customerName.length(); i < CUSTOMER_NAME_LENGTH; i++)
		{
			builder.append('0');
		}
		builder.append(customerName);
		return builder.substring(0, CUSTOMER_NAME_LENGTH);
	}


	/**
	 * Renders the eight-character account-type field, right-padding with spaces
	 * (and truncating) to the fixed width, matching the legacy COBOL
	 * {@code PIC X(8)} representation.
	 *
	 * @param accountType the account type
	 * @return the eight-character account-type field
	 */
	private String padAccountType(String accountType)
	{
		String value = (accountType == null) ? "" : accountType;
		return String.format("%-" + ACCOUNT_TYPE_LENGTH + "s", value)
				.substring(0, ACCOUNT_TYPE_LENGTH);
	}


	/**
	 * Formats a date of birth as {@code DD-MM-YYYY}, applying the same
	 * time-zone-offset correction the legacy writer used so the stored value is
	 * unaffected by the JVM default zone.
	 *
	 * @param customerDOB the date of birth
	 * @return the {@code DD-MM-YYYY} string
	 */
	private String formatCustomerDOB(Date customerDOB)
	{
		Calendar myCalendar = Calendar.getInstance();
		myCalendar.setTime(customerDOB);
		myCalendar.setTimeInMillis(myCalendar.getTimeInMillis() - myCalendar
				.getTimeZone().getOffset(myCalendar.getTimeInMillis()));
		String dd = String.format("%02d", myCalendar.get(Calendar.DATE));
		String mm = String.format("%02d", myCalendar.get(Calendar.MONTH) + 1);
		String yyyy = String.format("%04d", myCalendar.get(Calendar.YEAR));
		return dd + "-" + mm + "-" + yyyy;
	}


	/**
	 * Formats a statement date as the eight-digit {@code DDMMYYYY} field used in
	 * the create/delete-account description area.
	 *
	 * @param date the statement date
	 * @return the {@code DDMMYYYY} string
	 */
	private String formatStatementDate(Date date)
	{
		Calendar myCalendar = Calendar.getInstance();
		myCalendar.setTime(date);
		String dd = String.format("%02d", myCalendar.get(Calendar.DATE));
		String mm = String.format("%02d", myCalendar.get(Calendar.MONTH) + 1);
		String yyyy = String.format("%04d", myCalendar.get(Calendar.YEAR));
		return dd + mm + yyyy;
	}


	/**
	 * Combines the {@code date} and {@code time} columns of a row into a single
	 * {@link Date} so the {@code timestamp} field can be rendered exactly as the
	 * legacy implementation rendered its combined transaction date/time.
	 *
	 * @param datePart the date column value (may be {@code null})
	 * @param timePart the time column value (may be {@code null})
	 * @return the combined instant, or {@code null} if no date was present
	 */
	private Date combineDateTime(Date datePart, Date timePart)
	{
		if (datePart == null)
		{
			return null;
		}
		Calendar calendar = Calendar.getInstance();
		calendar.setTimeInMillis(datePart.getTime());
		if (timePart != null)
		{
			Calendar timeCalendar = Calendar.getInstance();
			timeCalendar.setTimeInMillis(timePart.getTime());
			calendar.set(Calendar.HOUR_OF_DAY,
					timeCalendar.get(Calendar.HOUR_OF_DAY));
			calendar.set(Calendar.MINUTE, timeCalendar.get(Calendar.MINUTE));
			calendar.set(Calendar.SECOND, timeCalendar.get(Calendar.SECOND));
			calendar.set(Calendar.MILLISECOND, 0);
		}
		return calendar.getTime();
	}


	/**
	 * Builds a {@link Date} from the year/month/day string components parsed out
	 * of a fixed-width description area (months are 1-based on the wire).
	 *
	 * @param yyyy the four-digit year
	 * @param mm   the two-digit month (1-based)
	 * @param dd   the two-digit day
	 * @return the corresponding {@link Date}
	 */
	private Date makeDate(String yyyy, String mm, String dd)
	{
		Calendar myCalendar = Calendar.getInstance();
		myCalendar.set(Calendar.YEAR, Integer.parseInt(yyyy));
		myCalendar.set(Calendar.MONTH, Integer.parseInt(mm) - 1);
		myCalendar.set(Calendar.DAY_OF_MONTH, Integer.parseInt(dd));
		return new Date(myCalendar.getTimeInMillis());
	}


	protected void sortOutLogging()
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
