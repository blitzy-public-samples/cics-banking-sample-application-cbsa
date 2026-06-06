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
 * connection helper are replaced, for the read-only transaction-history GET, by
 * a thin JDBC layer on the shared PostgreSQL store used by the {@code bank-core}
 * module (connection coordinates are externalised through {@link DatabaseConfig}
 * &mdash; F-CONFIG-SEC-1, never hardcoded). The {@code processed_transaction}
 * table is APPEND-ONLY with a logical {@code deleted} flag (bank-core ADR-006),
 * so the GET list reads only active rows ({@code deleted = false}).</li>
 * <li><b>All PROCTRAN audit writes are owned by {@code bank-core}</b> and occur
 * INSIDE the same transaction as the account/customer mutation that produced
 * them (CICS SYNCPOINT/ROLLBACK parity &mdash; F-TXN-1). The six POST audit
 * endpoints on this resource are retained only to preserve the frozen JAX-RS
 * syntactic contract; they are neutralised no-op acknowledgements and no longer
 * perform a separate-connection JDBC insert.</li>
 * <li>All monetary values remain {@link java.math.BigDecimal} at scale 2 with
 * {@link java.math.RoundingMode#HALF_UP}; no floating-point primitive is used
 * for money.</li>
 * </ul>
 *
 * <p>
 * <b>accountNumber semantics (F-PT-1).</b> In the legacy Db2 {@code PROCTRAN}
 * table the {@code PROCTRAN_NUMBER} column held the ACCOUNT NUMBER, and the
 * frozen JSON {@code accountNumber} field is populated from it. The bank-core
 * relational schema preserves this exactly: the unique per-row audit reference
 * is the {@code ref} column (the primary key is {@code (sort_code, ref)}), while
 * {@code transaction_number} carries the originating account number (or
 * {@code 00000000} for customer-level rows) &mdash; the structural successor of
 * {@code PROC-TRAN-NUMBER}. This GET therefore continues to populate
 * {@code accountNumber} from {@code transaction_number} and now reports the
 * caller's account number rather than a generated sequence; the field name,
 * width and types are unchanged.
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
		// Neutralised audit-write endpoint (F-PT-1 / F-TXN-1). The PROCTRAN
		// audit row for a debit/credit is now written atomically by bank-core
		// INSIDE THE SAME transaction as the balance mutation (see
		// AccountsResource -> PUT /makepayment/dbcr). This webui endpoint no
		// longer performs a separate-connection JDBC PROCTRAN insert, which was
		// what (a) broke CICS SYNCPOINT/ROLLBACK atomicity and (b) emitted a
		// generated MAX()+1 sequence into transaction_number. The JAX-RS
		// signature is preserved verbatim so the syntactic contract is
		// unchanged; the call is a no-op acknowledgement.
		return Response.ok().build();
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
		// Neutralised audit-write endpoint (F-PT-1 / F-TXN-1). The PROCTRAN
		// TFR audit row is now written atomically by bank-core inside the same
		// transaction as the transfer (see AccountsResource -> PUT /transfer).
		// Preserved as a no-op acknowledgement so the JAX-RS contract is
		// unchanged.
		return Response.ok().build();
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
		// Neutralised audit-write endpoint (F-PT-1 / F-TXN-1). The PROCTRAN
		// customer-delete audit row is now written atomically by bank-core
		// inside the same transaction as the customer delete (see
		// CustomerResource -> DELETE /delcus/remove/{customerNumber}, which
		// also cascades the customer's accounts). Preserved as a no-op
		// acknowledgement so the JAX-RS contract is unchanged.
		return Response.ok().build();
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
		// Neutralised audit-write endpoint (F-PT-1 / F-TXN-1). The PROCTRAN
		// customer-create audit row is now written atomically by bank-core
		// inside the same transaction as the customer create (see
		// CustomerResource -> POST /crecust/insert). Preserved as a no-op
		// acknowledgement so the JAX-RS contract is unchanged.
		return Response.ok().build();
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
		// Neutralised audit-write endpoint (F-PT-1 / F-TXN-1). The PROCTRAN
		// account-delete audit row (carrying the terminal balance) is now
		// written atomically by bank-core inside the same transaction as the
		// account delete (see AccountsResource -> DELETE
		// /delacc/remove/{accountNumber}). Preserved as a no-op acknowledgement
		// so the JAX-RS contract is unchanged.
		return Response.ok().build();
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
		// Neutralised audit-write endpoint (F-PT-1 / F-TXN-1). The PROCTRAN
		// account-create audit row is now written atomically by bank-core
		// inside the same transaction as the account create (see
		// AccountsResource -> POST /creacc/insert). Preserved as a no-op
		// acknowledgement so the JAX-RS contract is unchanged.
		return Response.ok().build();
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
		// F-CONFIG-SEC-1 (CWE-798): database coordinates are no longer hardcoded
		// as Java constants. They are resolved by the shared DatabaseConfig
		// helper from JVM system properties / environment variables, falling
		// back to the documented local-development defaults
		// (localhost:5432/cbsa, user/password cbsa). This connection now serves
		// ONLY the read-only GET transaction-history gap endpoint.
		return DatabaseConfig.getConnection();
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
