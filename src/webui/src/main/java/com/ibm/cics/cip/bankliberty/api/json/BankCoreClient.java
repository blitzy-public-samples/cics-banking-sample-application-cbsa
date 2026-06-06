/*
 *
 *    Copyright IBM Corp. 2023,2026
 *
 */
package com.ibm.cics.cip.bankliberty.api.json;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Thin HTTP delegation client used by the surviving {@code webui} JAX-RS
 * resources to forward <em>mutating</em> customer / account / payment / transfer
 * operations to the {@code bank-core} Spring Boot module, which owns the banking
 * business logic and writes the PROCTRAN audit row atomically with each mutation.
 *
 * <p>
 * <b>Architecture (F-CUST-1 / F-ACCT-1 / F-TXN-1 / U3 / R3).</b> The legacy CICS
 * banking logic was re-implemented in {@code bank-core}; {@code webui} is a thin
 * presentation/API adapter. Rather than re-implementing CREACC / UPDACC /
 * DBCRFUN / XFRFUN / DELACC / CRECUST / UPDCUST / DELCUS via direct JDBC (which
 * created a second behavioural source of truth and split the mutation and its
 * PROCTRAN audit append across two JDBC transactions), the resources now call
 * the corresponding bank-core endpoint over HTTP and translate the response back
 * into the frozen {@code /webui-1.0/banking/*} JSON envelopes. bank-core performs
 * the mutation and the audit append inside a single {@code @Transactional}
 * service method, reproducing the CICS SYNCPOINT/ROLLBACK boundary.
 * </p>
 *
 * <p>
 * <b>Configuration.</b> The bank-core base URL is resolved the same way the
 * z/OS Connect interface modules resolve theirs: the scheme is {@code http}, the
 * host comes from the {@code CBSA_ZOSCONN_HOST} system property / environment
 * variable (default {@code localhost}) and the port from
 * {@code CBSA_ZOSCONN_PORT} (default {@code 8080}). Nothing is hardcoded beyond
 * the documented local-development defaults.
 * </p>
 */
final class BankCoreClient
{

	/** Configuration key for the bank-core host. */
	private static final String KEY_HOST = "CBSA_ZOSCONN_HOST";

	/** Configuration key for the bank-core port. */
	private static final String KEY_PORT = "CBSA_ZOSCONN_PORT";

	/** Documented local-development default host. */
	private static final String DEFAULT_HOST = "localhost";

	/** Documented local-development default port. */
	private static final String DEFAULT_PORT = "8080";

	/** Connection-establishment timeout. */
	private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);

	/**
	 * Per-request timeout. Generous enough to absorb the create-customer
	 * credit-agency fan-out, which bank-core bounds at three seconds.
	 */
	private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);

	/**
	 * Shared, thread-safe Jackson mapper for parsing bank-core responses.
	 *
	 * <p>{@link DeserializationFeature#USE_BIG_DECIMAL_FOR_FLOATS} is enabled so
	 * that monetary values (balances, amounts, interest rate) parse into
	 * {@code DecimalNode}s that preserve the original scale (for example
	 * {@code 0.00} rather than {@code 0.0}). This lets the surviving resources
	 * re-emit balances byte-for-byte in the frozen {@code /webui-1.0/banking/*}
	 * envelopes without any binary floating-point type ever being involved.</p>
	 */
	private static final ObjectMapper MAPPER = new ObjectMapper()
			.enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);

	/** Number of characters in a bank-core {@code DDMMYYYY} encoded date. */
	private static final int DDMMYYYY_LENGTH = 8;

	/** The bank-core "absent date" integer sentinel. */
	private static final int ABSENT_DATE_INT = 0;

	/** Shared, thread-safe HTTP client. */
	private static final HttpClient CLIENT = HttpClient.newBuilder()
			.connectTimeout(CONNECT_TIMEOUT).build();

	private BankCoreClient()
	{
		throw new IllegalStateException("Static HTTP client holder only");
	}

	/**
	 * Resolves a configuration value, preferring a JVM system property, then the
	 * matching environment variable, then the supplied default. Blank values are
	 * treated as absent.
	 *
	 * @param key          the system-property / environment-variable name
	 * @param defaultValue the value to use when neither source supplies a value
	 * @return the resolved, trimmed configuration value
	 */
	private static String resolve(String key, String defaultValue)
	{
		String value = System.getProperty(key);
		if (value == null || value.trim().isEmpty())
		{
			value = System.getenv(key);
		}
		if (value == null || value.trim().isEmpty())
		{
			return defaultValue;
		}
		return value.trim();
	}

	/**
	 * Returns the bank-core base URL ({@code http://host:port}) from the
	 * externalised host/port configuration.
	 *
	 * @return the scheme://host:port base URL with no trailing slash
	 */
	static String getBaseUrl()
	{
		return "http://" + resolve(KEY_HOST, DEFAULT_HOST) + ":"
				+ resolve(KEY_PORT, DEFAULT_PORT);
	}

	/**
	 * URL-encodes a single path segment so dynamic identifiers are transmitted
	 * safely. Application identifiers are numeric, but encoding defensively keeps
	 * the request well-formed for any value.
	 *
	 * @param segment the raw path segment
	 * @return the percent-encoded segment (spaces as {@code %20}, not {@code +})
	 */
	static String encodeSegment(String segment)
	{
		return URLEncoder.encode(segment, StandardCharsets.UTF_8)
				.replace("+", "%20");
	}

	/**
	 * Sends a POST with a JSON body to the supplied bank-core path.
	 *
	 * @param path     the bank-core path (must start with {@code /})
	 * @param jsonBody the JSON request body
	 * @return the parsed bank-core response
	 * @throws IOException          if the call fails or the response is unusable
	 * @throws InterruptedException if the calling thread is interrupted
	 */
	static BankCoreResult post(String path, String jsonBody)
			throws IOException, InterruptedException
	{
		return send(HttpRequest.newBuilder()
				.uri(URI.create(getBaseUrl() + path))
				.timeout(REQUEST_TIMEOUT)
				.header("Content-Type", "application/json")
				.header("Accept", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(jsonBody,
						StandardCharsets.UTF_8))
				.build());
	}

	/**
	 * Sends a PUT with a JSON body to the supplied bank-core path.
	 *
	 * @param path     the bank-core path (must start with {@code /})
	 * @param jsonBody the JSON request body
	 * @return the parsed bank-core response
	 * @throws IOException          if the call fails or the response is unusable
	 * @throws InterruptedException if the calling thread is interrupted
	 */
	static BankCoreResult put(String path, String jsonBody)
			throws IOException, InterruptedException
	{
		return send(HttpRequest.newBuilder()
				.uri(URI.create(getBaseUrl() + path))
				.timeout(REQUEST_TIMEOUT)
				.header("Content-Type", "application/json")
				.header("Accept", "application/json")
				.PUT(HttpRequest.BodyPublishers.ofString(jsonBody,
						StandardCharsets.UTF_8))
				.build());
	}

	/**
	 * Sends a DELETE to the supplied bank-core path.
	 *
	 * @param path the bank-core path (must start with {@code /})
	 * @return the parsed bank-core response
	 * @throws IOException          if the call fails or the response is unusable
	 * @throws InterruptedException if the calling thread is interrupted
	 */
	static BankCoreResult delete(String path)
			throws IOException, InterruptedException
	{
		return send(HttpRequest.newBuilder()
				.uri(URI.create(getBaseUrl() + path))
				.timeout(REQUEST_TIMEOUT)
				.header("Accept", "application/json")
				.DELETE()
				.build());
	}

	/**
	 * Executes the request and parses the response body as JSON (when present).
	 *
	 * @param request the prepared request
	 * @return the parsed result (status + optional JSON body + raw text)
	 * @throws IOException          if the call fails
	 * @throws InterruptedException if the calling thread is interrupted
	 */
	private static BankCoreResult send(HttpRequest request)
			throws IOException, InterruptedException
	{
		HttpResponse<String> response = CLIENT.send(request,
				HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
		String raw = response.body();
		JsonNode body = null;
		if (raw != null && !raw.trim().isEmpty())
		{
			body = MAPPER.readTree(raw);
		}
		return new BankCoreResult(response.statusCode(), body, raw);
	}

	/**
	 * Translates a bank-core {@code DDMMYYYY} integer date into the
	 * {@code YYYY-MM-DD} (ISO) string that the frozen webui account/customer
	 * envelopes emit (reproducing the legacy {@code java.sql.Date.toString()}
	 * rendering of the stored date). Returns {@code null} for the bank-core
	 * absent-date sentinel ({@code 0}) so the JSON field is emitted as
	 * {@code null}, matching the legacy null-date behaviour.
	 *
	 * @param ddmmyyyy the bank-core {@code DDMMYYYY} integer (0 if absent)
	 * @return the ISO {@code YYYY-MM-DD} string, or {@code null} if absent
	 */
	static String toIsoDate(int ddmmyyyy)
	{
		if (ddmmyyyy == ABSENT_DATE_INT)
		{
			return null;
		}
		return toIsoDate(Integer.toString(ddmmyyyy));
	}

	/**
	 * Translates a bank-core {@code DDMMYYYY} string date (left-zero-padded to
	 * eight characters when shorter) into the {@code YYYY-MM-DD} (ISO) string the
	 * frozen webui envelopes emit. Returns {@code null} for a blank value or the
	 * bank-core absent-date sentinels ({@code "0"} / {@code "00000000"}).
	 *
	 * @param ddmmyyyy the bank-core {@code DDMMYYYY} string
	 * @return the ISO {@code YYYY-MM-DD} string, or {@code null} if absent
	 */
	static String toIsoDate(String ddmmyyyy)
	{
		if (ddmmyyyy == null || ddmmyyyy.trim().isEmpty())
		{
			return null;
		}
		String trimmed = ddmmyyyy.trim();
		// Normalise to eight digits; bank-core drops leading zeros when a date
		// is carried as an integer (for example "1011990" for 01/01/1990).
		String padded = String.format("%0" + DDMMYYYY_LENGTH + "d",
				Long.parseLong(trimmed));
		if (padded.equals("00000000"))
		{
			return null;
		}
		String day = padded.substring(0, 2);
		String month = padded.substring(2, 4);
		String year = padded.substring(4, DDMMYYYY_LENGTH);
		return year + "-" + month + "-" + day;
	}

	/**
	 * Parses a JSON document with the BigDecimal-preserving mapper so that every
	 * numeric value is materialised as a {@link java.math.BigDecimal} node rather
	 * than a {@code double}. This is used when an adapter method re-reads a value
	 * (such as a money or interest-rate field) from another local response and
	 * must preserve the COBOL fixed-point scale exactly (no floating point).
	 *
	 * @param json the JSON text to parse
	 * @return the parsed tree
	 * @throws IOException if the text is not valid JSON
	 */
	static JsonNode parse(String json) throws IOException
	{
		return MAPPER.readTree(json);
	}

	/**
	 * Immutable carrier for a bank-core HTTP response: the HTTP status, the
	 * parsed JSON body (may be {@code null} for an empty body) and the raw text.
	 */
	static final class BankCoreResult
	{

		private final int status;

		private final transient JsonNode body;

		private final String raw;

		BankCoreResult(int status, JsonNode body, String raw)
		{
			this.status = status;
			this.body = body;
			this.raw = raw;
		}

		/**
		 * @return the HTTP status code returned by bank-core
		 */
		int getStatus()
		{
			return status;
		}

		/**
		 * @return the parsed JSON body, or {@code null} if the body was empty
		 */
		JsonNode getBody()
		{
			return body;
		}

		/**
		 * @return the raw response body text
		 */
		String getRaw()
		{
			return raw;
		}

		/**
		 * @return {@code true} if the HTTP status is in the 2xx range
		 */
		boolean isHttpSuccess()
		{
			return status >= 200 && status < 300;
		}
	}

}
