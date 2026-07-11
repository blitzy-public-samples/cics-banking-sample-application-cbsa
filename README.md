# cics-banking-sample-application-cbsa
The **C**ICS **B**ank **S**ample **A**pplication (CBSA) is an application which simulates the operation of a bank, from the point of view of the Bank Teller. CBSA has multiple uses
here are a few examples:
  - CBSA can be used as a teaching/learning aid, as all source code is provided. It demonstrates how various technologies can be integrated together; CICS, COBOL, BMS, Db2, SQL, Java, Liberty, Spring Boot etc.
  - CBSA provides an example of a traditionally written CICS application that has been extended over time, and is structured in a way that is recognisable to most CICS TS customers - so
    it can be used a conversation piece for discussions around the application development lifecycle.
  - CBSA can be used straight out of the box for testing purposes. For example: the testing of CICS interactions, or for testing verification/validation/interaction of IBM and vendor tool offerings.
  - CBSA can be used as the building block for application modernisation conversations.


## Table of Contents

- [About](#about)
- [Architecture](#architecture)
- [Requirements](#requirements)
- [Downloading](#downloading)
- [Installing](#installing)
- [Usage](#usage)
- [Security](#security)
- [Contributors](#contributors)

## About
There are multiple different interfaces exploiting a range of different underlying technologies.

The first interface is the **base COBOL (BMS) interface**:
>![Main Menu](./doc/images/Architecture/Baseinstall_CBSA_MAIN_MENU.jpg)
</br>

The second interface is the **Carbon React UI interface**:
> ![Carbon React Main Menu](./doc/images/Architecture/CarbonReactUI_MainMenu.png)
</br>

The third interface is the **Customer Services interface**:
> ![CS Landing Page](./doc/images/Architecture/Landing_Page_Small.jpg)
</br>

The fourth interface is the **Payment interface**:
> ![Payment Landing Page](./doc/images/Architecture/Payment_Landing_page_small.jpg)
</br>

There is also a **RESTful API** please refer to the [CBSA RESTful API Guide](https://github.com/cicsdev/cics-banking-sample-application-cbsa/tree/main/etc/usage/springBoot/doc/CBSA_Restful_API_guide.md) for more detailed information.
</br>

The interfaces are designed to exploit the underlying banking functionality, which includes functions to:
> - Pay money in
> - Take money out
> - Transfer funds
> - Open new accounts etc. etc.

## Architecture
![Payment and CS architecture diagram2](./doc/images/Architecture/Payment_and_Customer_Services_UI_CBSA_architecture_diagram2.jpg)

> Please refer to the [Architecture documentation](https://github.com/cicsdev/cics-banking-sample-application-cbsa/tree/main/doc/CBSA_Architecture_guide.md) for more detailed information.

## Requirements
- An existing CICS TS region running at V6.1 with APAR PH60795 applied, or later
- A Db2 subsystem (V12 or later)
- A Liberty JVM server in CICS (set up and configured during the installation process) for the Carbon React UI and Spring Boot interfaces
- Java 17
- Yarn to build the web front-end
- Various VSAM files (set up and configured during the installation process)
- A z/OS Connect server (if the RESTful API or the Customer Services or Payment interfaces are required)
- A Maven wrapper is used to build the Java components. This is included for your convenience.



## Downloading
- Clone the repository using your IDEs support, such as the Eclipse Git plugin
- **or**, download the sample as a [ZIP] from the release page and then follow the installation instructions (see Installing).

>*Tip: Eclipse Git provides an 'Import existing Projects' check-box when cloning a repository.*

These instructions will get you a copy of the project up and running on your local machine for development and testing purposes.

## Installing
Installation instructions:
</br>

Installation of CBSA is split into 3 parts :
  1. The base COBOL(BMS) installation, this is mandatory and should be installed first. See the [base COBOL installation documentation.](https://github.com/cicsdev/cics-banking-sample-application-cbsa/tree/main/etc/install/base/doc/README.md)
  2. The Carbon React UI installation, this is optional, requires a JVM server to be running in the CICS region (this gets set up as part of installation process). See [the Carbon React UI installation documentation.](https://github.com/cicsdev/cics-banking-sample-application-cbsa/tree/main/etc/install/carbonReactUI/doc/CBSA_Carbon_React_UI_installation_deployment_guide.md)
  3. The Customer Service and Payment interface installation (also optional). See [deploying the Payment and Customer Services documentation.](https://github.com/cicsdev/cics-banking-sample-application-cbsa/tree/main/etc/install/springBootUI/doc/CBSA_Deploying_the_Payment_Customer_Services_Springboot_apps.md)

## Usage
Various user guides are provided:

  1. For the base/COBOL(BMS) interface please refer to the [CBSA BMS User Guide.](https://github.com/cicsdev/cics-banking-sample-application-cbsa/tree/main/etc/usage/base/doc/CBSA_BMS_User_Guide.md)
  2. For the Carbon React UI please refer to the [CBSA Carbon React UI User Guide.](https://github.com/cicsdev/cics-banking-sample-application-cbsa/tree/main/etc/usage/libertyUI/doc/CBSA_Liberty_UI_User_Guide.md)
  3. For the Customer Services interface please refer to the [CBSA Customer Service User Interface User Guide.](https://github.com/cicsdev/cics-banking-sample-application-cbsa/tree/main/etc/usage/springBoot/doc/CBSA_Customer_Services_Interface_User_Guide.md)
  4. For the Payment interface please refer to the [CBSA Payment Interface User Guide.](https://github.com/cicsdev/cics-banking-sample-application-cbsa/tree/main/etc/usage/springBoot/doc/CBSA_Payment_Interface_User_Guide.md)
  5. For the RESTful API guide please refer to the [CBSA RESTful Api Guide.](https://github.com/cicsdev/cics-banking-sample-application-cbsa/tree/main/etc/usage/springBoot/doc/CBSA_Restful_API_guide.md)


## Security
Application-layer security is now enforced across the banking interfaces. Authentication is **required** and access is governed by role-based authorization. These controls were added as part of an OWASP-aligned security remediation (addressing Broken Access Control, Identification and Authentication Failures, and Security Misconfiguration).

> Please refer to [SECURITY.md](SECURITY.md) for the full security model, the vulnerability-reporting process, and the complete configuration-variable reference.

**Authentication and authorization (now required)**
> All state-changing banking endpoints across the Spring Boot Customer Services (`/customerservices-1.0/`) and Payment (`/paymentinterface-1.1/`) interfaces, together with the Liberty z/OS Connect RESTful API, now require authentication with role-based authorization (currently a single Bank Teller role, `ROLE_TELLER`).
> - Unauthenticated requests receive `401 Unauthorized`.
> - Authenticated requests that lack the required role, or that omit a valid CSRF token, receive `403 Forbidden`.
>
> This is the single deliberate behavioral change; the REST contracts (paths, verbs, and request/response schemas) are otherwise unchanged.

**CSRF, CORS, and security headers**
> - CSRF protection is enabled using the cookie-based token pattern for the single-page application (an `XSRF-TOKEN` cookie that is read back from the `X-XSRF-TOKEN` request header).
> - CORS is restricted to an explicit origin allowlist (no wildcard origin combined with credentials).
> - Standard security response headers are applied: `Content-Security-Policy`, `X-Content-Type-Options`, `X-Frame-Options`, and `Strict-Transport-Security` (HSTS).

**Coordinated rollout**
> Because authentication is now enforced, the Carbon React UI and any other API clients must authenticate and send the CSRF token on state-changing requests. The client and server changes must therefore be deployed together.

### Configuration and Credentials
Credentials and environment-specific connection settings have been externalized out of source control. **No credentials are committed to this repository**; deployers must supply the following values at deployment time through environment variables, JVM system properties, or Liberty variable configuration:

> - **Liberty keystore password** - previously hardcoded in `etc/install/springBootUI/zosconnectserver/server.xml`, this is now resolved from a Liberty variable / environment (for example, a `${keystore.password}` placeholder sourced from `bootstrap.properties` or the environment). The concrete value is provisioned at deployment.
> - **Liberty `basicRegistry` credentials** - the registry user and password previously embedded in `server.xml` are now resolved from Liberty variables / environment.
> - **z/OS Connect connection settings (Spring Boot modules)** - the connection host and port are supplied by the `CBSA_ZOSCONN_HOST` and `CBSA_ZOSCONN_PORT` JVM system properties. The connection scheme (previously hardcoded to `http`) is now externalized via the `CBSA_ZOSCONN_SCHEME` system property (default `http`), enabling HTTPS to be selected by configuration.
> - **Spring Boot TELLER role credentials (authentication)** - the state-changing Spring Boot endpoints are guarded by `@PreAuthorize("hasRole('TELLER')")` and admit a **Bank Teller** principal that is provisioned from configuration. Supply the username via the `CBSA_TELLER_USERNAME` environment variable (property `cbsa.security.teller.username`, default `teller`) and the password via the `CBSA_TELLER_PASSWORD` environment variable (property `cbsa.security.teller.password`). No credential is committed; when no password is supplied the teller is not registered and access remains denied by default.

## Contributors
 > Jon Collett - JonCollettIBM
 >
 > James O'Grady - JAMOGRAD
 >
 > Tom Slattery - Tom-Slattery
 >
 > Christopher Clash - ChristopherClash

## License
This project is licensed under [Eclipse Public License - v 2.0](LICENSE).

## Usage terms
By downloading, installing, and/or using this sample, you acknowledge that separate license terms may apply to any dependencies that might be required as part of the installation and/or execution and/or automated build of the sample, including the following IBM license terms for relevant IBM components:

- IBM CICS development components terms: https://www.ibm.com/support/customer/csol/terms/?id=L-ACRR-BBZLGX
