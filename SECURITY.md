# Security Policy

The **C**ICS **B**anking **S**ample **A**pplication (CBSA) is a hybrid banking application that spans a COBOL/CICS/BMS core with Db2 and VSAM data, a Liberty JVM server exposing a z/OS Connect RESTful API, two Spring Boot interfaces (Customer Services and Payment), and a Carbon React single-page application (SPA). This document describes the **application-layer security controls** that protect those interfaces and explains how to configure, operate, and report issues against them.

These controls were introduced as a minimal, non-invasive security remediation that follows [OWASP Top 10 (2021)](https://owasp.org/Top10/2021/) guidance and secure-by-default principles. Existing banking behaviour is unchanged for authenticated, authorized users; the only additive change to the REST contract is the introduction of standard `401 Unauthorized` and `403 Forbidden` responses once authentication is enforced.

> This policy complements the project [README](README.md). The README carries a short security summary; this file is the authoritative, complete reference.

## Table of Contents

- [Supported Scope](#supported-scope)
- [Security Model: Authentication and Authorization](#security-model-authentication-and-authorization)
- [HTTP Security Controls](#http-security-controls)
- [Configuration and Credentials](#configuration-and-credentials)
- [Reporting a Vulnerability](#reporting-a-vulnerability)
- [Preserved Application Business Rules](#preserved-application-business-rules)
- [Reversibility and Rollback](#reversibility-and-rollback)
- [OWASP Top 10 (2021) Mapping](#owasp-top-10-2021-mapping)
- [References](#references)

## Supported Scope

This policy covers the application-layer controls added to:

- the two **Spring Boot** modules — **Customer Services** (`/customerservices-1.0/`) and **Payment** (`/paymentinterface-1.1/`);
- the **Liberty z/OS Connect RESTful API** served by the `webui` module (`/webui-1.0/banking/...`); and
- the **Carbon React** single-page application that consumes those APIs.

> The underlying COBOL/CICS transaction programs, Db2/VSAM data stores, BMS 3270 maps, and JCL install jobs are reached only through the tiers above. Deeper platform hardening for those layers is noted under [Reversibility and Rollback](#reversibility-and-rollback) as deferred, separately tracked work.

## Security Model: Authentication and Authorization

**Authentication is required on all state-changing endpoints.** Access is governed by role-based authorization following a deny-by-default posture — a request must be both authenticated *and* carry the role required by the operation.

**Spring Boot modules (Customer Services and Payment).** Both modules use Spring Security (`spring-boot-starter-security`, managed by the Spring Boot parent BOM). A `SecurityFilterChain` bean authenticates every request by default, and method-level authorization (`@EnableMethodSecurity` with `@PreAuthorize`) restricts money-movement and administrative operations to the appropriate role — for example a **Bank Teller** role for customer/account maintenance and a **payment-channel** role for the payment endpoints.

**Liberty z/OS Connect RESTful API (`webui`).** The Liberty tier uses container-managed authentication: a `security-constraint`/`login-config` in `src/webui/WebContent/WEB-INF/web.xml` is bound to a Liberty user registry, and JAX-RS resource methods are annotated with `@RolesAllowed` so that create, update, and delete operations require an authenticated principal in an authorized role.

### Response semantics

| Condition | Response |
|-----------|----------|
| Request is not authenticated | `401 Unauthorized` |
| Request is authenticated but lacks the required role | `403 Forbidden` |
| Request is authenticated but omits or fails the CSRF check (state-changing verbs) | `403 Forbidden` |

> **Backward compatibility.** Adding `401`/`403` semantics is the *only* change to the REST contract. All paths, HTTP verbs, and request/response schemas are otherwise unchanged, so authorized callers observe behaviour identical to the pre-remediation baseline.

### Protection against Insecure Direct Object References (IDOR)

Lookups keyed on a client-supplied identifier (for example an account or customer `GET` by id) enforce ownership/entitlement checks wherever the role model restricts cross-principal access. Because CBSA is modelled from the point of view of a **Bank Teller**, that role legitimately has broad access to customer and account records; the entitlement checks are applied where a narrower principal must not read another principal's data.

## HTTP Security Controls

**CSRF protection.** Cross-Site Request Forgery protection is enabled (it is on by default under Spring Security). For the single-page application a cookie-based token repository is used: the server issues an `XSRF-TOKEN` cookie that the browser returns in the `X-XSRF-TOKEN` request header — the convention the Axios HTTP client follows automatically. On the Liberty tier, XSRF protection in `src/webui/WebContent/WEB-INF/web.xml` is re-enabled (the previous `disable-xsrf-protection` setting is turned off).

**CORS.** Cross-Origin Resource Sharing is restricted to an **explicit origin allowlist**; the insecure combination of a wildcard origin (`*`) with credentials is not used. In the Spring Boot modules CORS is configured through a `CorsConfigurationSource` bean wired into the Spring Security filter chain; in the Liberty tier the `cors` element of the z/OS Connect `server.xml` is tightened to an allowlisted origin.

**Security response headers.** Spring Security supplies a baseline set of response headers by default:

- `X-Content-Type-Options: nosniff`
- `X-Frame-Options: DENY`
- `Strict-Transport-Security` (HSTS, on secure requests)

`Content-Security-Policy` is **not** a Spring Security default, so it is declared explicitly (for example `default-src 'self'`) to constrain the sources from which content may be loaded.

## Configuration and Credentials

**No credentials are committed to this repository.** Credentials and environment-specific connection settings are externalized and must be supplied at deployment time through environment variables, JVM system properties, or Liberty variable configuration. The placeholders below are illustrative — the concrete secret values are provisioned by the deployer and are never stored in source control.

| Setting | How it is supplied | Notes |
|---------|--------------------|-------|
| Liberty keystore password | Liberty variable placeholder, e.g. `${keystore.password}`, resolved from `bootstrap.properties` or the environment | Previously hardcoded in `etc/install/springBootUI/zosconnectserver/server.xml`; the literal has been removed. |
| Liberty `basicRegistry` user and password | Liberty variables, e.g. `${zosconnect.registry.user}` / `${zosconnect.registry.password}`, resolved from the environment | Previously embedded in `server.xml`; the literals have been removed. |
| CORS allowlist (Liberty) | Liberty variable, e.g. `${cors.allowed.origins}` | Replaces the former wildcard origin. |
| z/OS Connect host (Spring Boot) | `CBSA_ZOSCONN_HOST` JVM system property | Already externalized. |
| z/OS Connect port (Spring Boot) | `CBSA_ZOSCONN_PORT` JVM system property | Already externalized. |
| z/OS Connect scheme (Spring Boot) | `CBSA_ZOSCONN_SCHEME` JVM system property (default `http`) | Newly externalized; set to `https` to select TLS by configuration. |
| Spring Boot CORS allowlist and error handling | Each module's `src/main/resources/application.properties` | Restrictive CORS origins and sanitized error output. |

> **Error and log hygiene.** Error responses and logs are sanitized: stack traces, database SQLCODEs, internal class names, and personally identifiable information (PII) are not returned to clients or written to logs. A centralized exception handler in each Spring Boot module returns a generic message.

## Reporting a Vulnerability

We welcome responsible disclosure of security issues in CBSA.

- **Where to report.** Open an issue using the repository **Issues** tab, as described in [CONTRIBUTING.md](CONTRIBUTING.md). For anything you would prefer not to file publicly at first, contact a project maintainer listed in [MAINTAINERS.md](MAINTAINERS.md).
- **What to include.** A clear description of the issue, the affected interface or endpoint, and step-by-step reproduction instructions (including any request payloads, expected versus actual behaviour, and relevant logs with secrets and PII removed).
- **Responsible disclosure.** Please avoid publicly disclosing exploit details until a fix is available, so that deployers have time to upgrade. Reports will be acknowledged and a remediation timeline coordinated.

> CBSA is an open sample project rather than a hosted service, so each deployer operates their own instance. There is no dedicated security mailbox; the issue tracker and maintainer contact above are the supported reporting channels.

## Preserved Application Business Rules

The security additions layer on top of — and must never weaken or bypass — the mandatory application-layer rules already embedded in the domain logic:

- **Payment-channel facility binding.** The payment channel's facility type is bound from the route binding, never from the request body; the debit/credit program rejects any transaction that would take a balance below zero and rejects mortgage and loan products.
- **Account-update field allowlist.** Account updates accept only an allowlisted set of fields (account type, interest rate, and overdraft limit) and never mutate the balance.
- **Processed-transaction integrity.** The processed-transaction store is insert-only at the data-access layer; physical deletion is forbidden, and removal is expressed as a soft-delete flag.
- **Seeded-data guardrail.** The bulk data-seeding path remains unreachable in production.
- **Counter-row access.** Account and customer control (counter) rows are accessible only through the counter service.

## Reversibility and Rollback

- **Independently revertible.** Each vulnerability class is addressed by a separable change, so it can be reverted on its own without disturbing the others.
- **Config-toggleable.** Each control is driven by configuration (the security filter chain, the CORS allowlist, and externalized variables), so it can be adjusted or disabled without code changes if an operational issue arises.
- **Auditable trail.** All changes land via pull request on a dedicated branch; there are no direct commits to `main`.
- **Deferred, separately tracked hardening.** Deeper hardening items are recorded for follow-up rather than performed here, including additional z/OS Connect server hardening, BMS map and JCL security review, and secrets-scanning pipeline enhancements (a `.secrets.baseline` already exists, and a `detect-secrets` pre-commit hook may be added to `.pre-commit-config.yaml`).

## OWASP Top 10 (2021) Mapping

| OWASP 2021 category | How it is addressed in CBSA |
|---------------------|-----------------------------|
| **A01 Broken Access Control** | Deny-by-default authentication with role-based authorization, CSRF protection, and ownership/entitlement checks that close IDOR. |
| **A02 Cryptographic Failures / A09 Security Logging & Monitoring Failures** | Sanitized error responses and logs — no stack traces, SQLCODEs, internal details, or PII. |
| **A03 Injection** | Parameterized (bind-variable) data access in the Liberty Db2 layer and static host-variable `EXEC SQL` in COBOL, reinforced with input-validation guards. |
| **A05 Security Misconfiguration** | Restrictive CORS allowlist, security response headers, and externalized credentials and configuration. |
| **A06 Vulnerable and Outdated Components** | Maven and Yarn dependency audits, with upgrades to patched, compatible versions. |
| **A07 Identification and Authentication Failures** | Enforced authentication across the Spring Boot and Liberty interfaces. |

## References

- [README.md](README.md) — project overview and the short security summary.
- [CONTRIBUTING.md](CONTRIBUTING.md) — how to raise issues and request changes.
- [MAINTAINERS.md](MAINTAINERS.md) — current project maintainers.
- [LICENSE](LICENSE) — the Eclipse Public License v2.0 that governs this project.
- [OWASP Top 10 (2021)](https://owasp.org/Top10/2021/) — the taxonomy these controls follow.

---

This security policy is provided under the same [Eclipse Public License v2.0](LICENSE) as the rest of the project.
