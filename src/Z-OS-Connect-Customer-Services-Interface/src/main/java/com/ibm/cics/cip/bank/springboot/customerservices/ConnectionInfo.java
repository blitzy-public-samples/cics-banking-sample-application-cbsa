/*                                                                        */
/* Copyright IBM Corp. 2023, 2025                                         */
/*                                                                        */
package com.ibm.cics.cip.bank.springboot.customerservices;

import com.beust.jcommander.Parameter;

public class ConnectionInfo
{



	@Parameter(names =
	{ "--scheme", "-s" }, description = "Scheme/protocol to connect with")
	private static String scheme = "http";

	@Parameter(names =
	{ "--port", "-p" }, description = "Port to connect with")
	private static int port = 38417;

	@Parameter(names =
	{ "--address", "--url", "-a", "-u" }, description = "Address to use")
	private static String address = "localhost";


	private ConnectionInfo()
	{
		throw new IllegalStateException("Static only");
	}


public static String getAddressAndPort() {
        // Security fix (V7 CWE-798 Hardcoded Credentials/Configuration - OWASP A05): call getScheme()
        // so the externalized scheme (CBSA_ZOSCONN_SCHEME system property) is honored instead of the
        // raw hardcoded field.
        return getScheme() + "://" + getAddress() + ":" + getPort();
    }

    public static int getPort() {
        port = Integer.parseInt(System.getProperty("CBSA_ZOSCONN_PORT"));
        return port;
    }

    public static String getPortString() {
        return Integer.toString(getPort());
    }

    public static void setPort(int port) {
        ConnectionInfo.port = port;
    }

    public static String getAddress() {
        address = System.getProperty("CBSA_ZOSCONN_HOST");
        return address;
    }

    public static void setAddress(String address) {
        ConnectionInfo.address = address;
    }

	public static String getScheme()
	{
		// Security fix (V7 CWE-798 Hardcoded Credentials/Configuration - OWASP A05 Security
		// Misconfiguration): resolve the connection scheme from a system property (default "http")
		// instead of a hardcoded literal, mirroring the externalized host/port and enabling HTTPS
		// purely by configuration.
		scheme = System.getProperty("CBSA_ZOSCONN_SCHEME", "http");
		return scheme;
	}


	public static void setScheme(String scheme)
	{
		ConnectionInfo.scheme = scheme;
	}

}
