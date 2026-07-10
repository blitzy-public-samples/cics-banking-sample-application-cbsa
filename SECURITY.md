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

### Implementation status

CBSA's security remediation is delivered incrementally, one vulnerability class at a time. The controls described in this section define the **target security model**; each is annotated below with its current status so that a given build's posture is not overstated:

- **In force now:** the Liberty container-managed authentication constraint on `/banking/*`; externalized credentials and the connection scheme; the CORS allowlist properties; property-level error-response sanitization (`server.error.include-stacktrace=never` and `server.error.include-message=never`); the Spring Boot deny-by-default `SecurityFilterChain` (HTTP Basic, CSRF cookie token, CORS allowlist, and CSP header), wired into each module through the main-class component scan of the `config` package; and the per-operation `@PreAuthorize("hasRole('TELLER')")` restrictions on the state-changing Spring Boot handlers, now backed by an externalized, provisioned `ROLE_TELLER` authority source (a `UserDetailsService` that reads `cbsa.security.teller.username` and `cbsa.security.teller.password`, and registers no user - denying access by default - when no teller password is supplied).
- **Planned:** the `@RolesAllowed` annotations and server-side IDOR ownership/entitlement checks on the Liberty JAX-RS resources; and the centralized `@ControllerAdvice` exception handler in each Spring Boot module.

When assessing a deployment's current posture, rely only on the controls listed under **In force now**; the items under **Planned** are not yet enforced.

**Spring Boot modules (Customer Services and Payment).** Both modules depend on Spring Security (`spring-boot-starter-security`, managed by the Spring Boot parent BOM) and define a `SecurityFilterChain` bean, wired into the running application through each module's main-class component scan of the `config` package, that authenticates every request under a deny-by-default posture, alongside method security (`@EnableMethodSecurity`) carrying the per-operation role checks. The `@PreAuthorize("hasRole('TELLER')")` restrictions that limit the state-changing money-movement and maintenance operations to the **Bank Teller** role are backed by an externalized, provisioned `ROLE_TELLER` authority source: a `UserDetailsService` bean provisions a teller principal from the `cbsa.security.teller.username` and `cbsa.security.teller.password` configuration (supplied via the `CBSA_TELLER_USERNAME` and `CBSA_TELLER_PASSWORD` environment variables), so an authorized teller is admitted while non-teller principals receive `403`. Secure-by-default: when no teller password is provisioned, no user is registered and access remains denied.

**Liberty z/OS Connect RESTful API (`webui`).** The Liberty tier uses container-managed authentication: a `security-constraint`/`login-config` in `src/webui/WebContent/WEB-INF/web.xml` (BASIC authentication, realm `zosConnect`, role `zosConnectAccess`) requires an authenticated principal for the `/banking/*` API, bound to a Liberty user registry. *Planned:* per-method `@RolesAllowed` authorization on the JAX-RS resources — so that create, update, and delete operations additionally require an authorized role — is being added as those resource files are finalized.

### Response semantics

| Condition | Response |
|-----------|----------|
| Request is not authenticated | `401 Unauthorized` |
| Request is authenticated but lacks the required role | `403 Forbidden` |
| Request is authenticated but omits or fails the CSRF check (state-changing verbs) | `403 Forbidden` |

> **Backward compatibility.** Adding `401`/`403` semantics is the *only* change to the REST contract. All paths, HTTP verbs, and request/response schemas are otherwise unchanged, so authorized callers observe behaviour identical to the pre-remediation baseline.

### Protection against Insecure Direct Object References (IDOR)

*Planned:* lookups keyed on a client-supplied identifier (for example an account or customer `GET` by id) will enforce ownership/entitlement checks wherever the role model restricts cross-principal access. Because CBSA is modelled from the point of view of a **Bank Teller**, that role legitimately has broad access to customer and account records; the entitlement checks will be applied where a narrower principal must not read another principal's data. These server-side checks build on the Liberty JAX-RS authorization above and are being added together with it.

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
| Spring Boot TELLER username | `CBSA_TELLER_USERNAME` environment variable (property `cbsa.security.teller.username`, default `teller`) | Username of the Bank Teller principal that satisfies the `@PreAuthorize("hasRole('TELLER')")` guards on the Spring Boot state-changing endpoints. |
| Spring Boot TELLER password | `CBSA_TELLER_PASSWORD` environment variable (property `cbsa.security.teller.password`) | Password for the teller principal; no default and never committed. When unset, no teller is registered and access stays denied by default. |

> **Error and log hygiene.** Error output is sanitized at the framework level: each Spring Boot module sets `server.error.include-stacktrace=never` and `server.error.include-message=never`, so stack traces, database SQLCODEs, internal class names, and personally identifiable information (PII) are not returned to clients. *Planned:* a centralized `@ControllerAdvice` exception handler in each module — standardizing a generic response for uncaught exceptions while preserving `401`/`403`/access-denied semantics — and the corresponding log sanitization are being added.

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
| **A01 Broken Access Control** | Deny-by-default authentication, CSRF protection, and role-based authorization are enforced across the Spring Boot tier, where the `@PreAuthorize("hasRole('TELLER')")` guards are backed by a provisioned `ROLE_TELLER` authority source; the Liberty JAX-RS `@RolesAllowed` checks and IDOR ownership/entitlement checks are planned (see the Implementation status note above). |
| **A02 Cryptographic Failures / A09 Security Logging & Monitoring Failures** | Error responses are sanitized at the framework property level — no stack traces or exception messages; centralized exception handling and log sanitization are planned. |
| **A03 Injection** | Parameterized (bind-variable) data access in the Liberty Db2 layer and static host-variable `EXEC SQL` in COBOL, reinforced with input-validation guards. |
| **A05 Security Misconfiguration** | Restrictive CORS allowlist, security response headers, and externalized credentials and configuration. |
| **A06 Vulnerable and Outdated Components** | Maven and Yarn dependency audits, with upgrades to patched, compatible versions. |
| **A07 Identification and Authentication Failures** | Authentication is enforced on both the Liberty interface and the Spring Boot modules; the Spring Boot `@PreAuthorize` role checks are backed by an externalized, provisioned `ROLE_TELLER` authority source. |

## References

- [README.md](README.md) — project overview and the short security summary.
- [CONTRIBUTING.md](CONTRIBUTING.md) — how to raise issues and request changes.
- [MAINTAINERS.md](MAINTAINERS.md) — current project maintainers.
- [LICENSE](LICENSE) — the Eclipse Public License v2.0 that governs this project.
- [OWASP Top 10 (2021)](https://owasp.org/Top10/2021/) — the taxonomy these controls follow.

---

This security policy is provided under the same [Eclipse Public License v2.0](LICENSE) as the rest of the project.
