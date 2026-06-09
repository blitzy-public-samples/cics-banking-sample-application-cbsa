/*
 *
 *    Copyright IBM Corp. 2023,2026
 *
 */
package com.ibm.cics.cip.bankliberty.api.json;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Centralised, externalised JDBC configuration for the read-only gap endpoints
 * that the surviving {@code webui} JAX-RS resources serve directly against the
 * shared {@code bank-core} PostgreSQL store.
 *
 * <p>
 * <b>Security note (F-CONFIG-SEC-1 / CWE-798).</b> The database host, port,
 * name, user and password are NOT hardcoded as Java constants. Each value is
 * resolved at runtime from a JVM system property first and then from the
 * matching environment variable, falling back to the documented
 * <em>local-development</em> defaults ({@code localhost:5432/cbsa}, user/password
 * {@code cbsa}) only when neither is supplied. Those defaults are explicitly
 * sanctioned by the project setup constraint ("Local Postgres stands in for the
 * mainframe data store ... database {@code cbsa} with user/password {@code cbsa}
 * and {@code DB_HOST} defaulting to localhost") and must be overridden in any
 * non-local deployment by setting the corresponding properties / variables.
 * </p>
 *
 * <p>
 * Recognised keys (system property OR environment variable; the system property
 * wins when both are present):
 * </p>
 * <ul>
 * <li>{@code DB_HOST} &ndash; database host (default {@code localhost})</li>
 * <li>{@code DB_PORT} &ndash; database port (default {@code 5432})</li>
 * <li>{@code DB_NAME} &ndash; database / schema name (default {@code cbsa})</li>
 * <li>{@code DB_USER} &ndash; database user (default {@code cbsa})</li>
 * <li>{@code DB_PASSWORD} &ndash; database password (default {@code cbsa})</li>
 * </ul>
 */
final class DatabaseConfig
{

	/** Configuration key for the database host. */
	private static final String KEY_HOST = "DB_HOST";

	/** Configuration key for the database port. */
	private static final String KEY_PORT = "DB_PORT";

	/** Configuration key for the database name. */
	private static final String KEY_NAME = "DB_NAME";

	/** Configuration key for the database user. */
	private static final String KEY_USER = "DB_USER";

	/** Configuration key for the database password. */
	private static final String KEY_PASSWORD = "DB_PASSWORD";

	/** Documented local-development default host. */
	private static final String DEFAULT_HOST = "localhost";

	/** Documented local-development default port. */
	private static final String DEFAULT_PORT = "5432";

	/** Documented local-development default database name. */
	private static final String DEFAULT_NAME = "cbsa";

	/** Documented local-development default user. */
	private static final String DEFAULT_USER = "cbsa";

	/** Documented local-development default password. */
	private static final String DEFAULT_PASSWORD = "cbsa";

	private DatabaseConfig()
	{
		throw new IllegalStateException("Static configuration holder only");
	}

	/**
	 * Resolves a configuration value, preferring a JVM system property, then the
	 * matching environment variable, then the supplied default. Blank values are
	 * treated as absent so an empty override never wins over the default.
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
	 * Builds the JDBC URL for the shared bank-core PostgreSQL store from the
	 * externalised host, port and database-name configuration.
	 *
	 * @return the {@code jdbc:postgresql://host:port/name} URL
	 */
	static String getUrl()
	{
		return "jdbc:postgresql://" + resolve(KEY_HOST, DEFAULT_HOST) + ":"
				+ resolve(KEY_PORT, DEFAULT_PORT) + "/"
				+ resolve(KEY_NAME, DEFAULT_NAME);
	}

	/**
	 * Opens a JDBC connection to the shared bank-core PostgreSQL store using the
	 * externalised connection coordinates. The caller is responsible for closing
	 * the returned connection.
	 *
	 * @return an open {@link Connection}
	 * @throws SQLException if the connection cannot be established
	 */
	static Connection getConnection() throws SQLException
	{
		return DriverManager.getConnection(getUrl(),
				resolve(KEY_USER, DEFAULT_USER),
				resolve(KEY_PASSWORD, DEFAULT_PASSWORD));
	}

}
