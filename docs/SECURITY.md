# FinanceTracker — Security & Privacy Requirements

Status: Draft v0.1 (from the Phase 0 design review)
Last updated: 2026-08-23

## 1. Purpose and status

This document is the security counterpart of [SPEC.md §2](SPEC.md).
SPEC §2 is the constitution for financial correctness; this document is the constitution for confidentiality and integrity.
Code must not contradict it.

Every requirement is numbered `SR-nn` and written to be testable.
Where a requirement implies a spec or schema change, the proposed decision is listed in [reviews/SECURITY_REVIEW.md](reviews/SECURITY_REVIEW.md) and cross-referenced here; those decisions need owner approval before SPEC.md or DATA_MODEL.md are edited.

Scope: this is a design-time document.
A second, code-level security pass is still required once the code exists (§13).

## 2. Data classification

Every rule in this document depends on knowing what data is sensitive.
This table classifies everything the MVP stores or will store.

| Class | Data | Where it lives | Handling |
| ----- | ---- | -------------- | -------- |
| **Restricted — third-party PII** | Names, phone numbers, UPI VPAs of people the owner transacted with, embedded in narrations | `transactions.narration`, `narration_normalized`, `statement_import_rows.raw_cells` | Never logged, never in URLs, never sent to external services (D-14), encrypted at rest, shown only to the owning user |
| **Restricted — owner financial identifiers** | Account numbers, CIF/customer IDs, IFSC/MICR, card limits, branch details (statement header blocks) | Source files only — must **not** be persisted (SR-20) | Never stored, never logged. The app has no need for them |
| **Restricted — financial facts** | Amounts, balances, salary credit, transaction dates, checkpoint balances | `transactions`, `balance_checkpoints`, `raw_cells` | Never logged, never in URLs, encrypted at rest, tenant-scoped on every access |
| **Confidential — owner identity** | Email, display name | `users` | Never logged except as opaque user id, encrypted at rest |
| **Confidential — interpretation data** | Categories, needs/wants, notes, rule patterns, link structure | `categories`, `category_rules`, `transactions` interpretation columns, `transaction_links` | Notes and rule patterns may contain anything the owner typed — treat like narrations. Never logged verbatim |
| **Confidential — upload metadata** | Source filename | `statement_imports.source_filename` | Banks put identifying details in filenames; never logged, sanitized on write (SR-33) |
| **Secret — credentials** | Future login credentials (FR-8), session identifiers, DB credentials, any API keys | Secret store / env (never the repo); credential columns when FR-8 is designed | Never logged, never in the repo, never in URLs, hashed (passwords) or encrypted, excluded from backups where possible |
| **Internal — operational data** | Numeric row ids, statuses, enum values, row counts, `fingerprint_version`, timestamps, SQLSTATE and constraint names | Everywhere | May be logged |

Two deliberate consequences:

- **`source_row_fingerprint` is Internal**, not Restricted: it is a SHA-256 over a tuple that includes a high-entropy narration, so it is not practically reversible, and it is needed in duplicate-handling paths. It may appear in logs.
- **The class attaches to the data, not the table.** A narration copied into an error message or a suggestion payload carries its class with it.

## 3. Tenant isolation and IDOR

Fact: DM-23's composite foreign keys stop a row from *referencing* another user's row.
They do nothing about a query that forgets `WHERE user_id = ?` and *returns* another user's row.
MVP has one user, so nothing would leak today — but the closed-circle phase inherits every query written now.
Isolation must therefore be structural from the first line of code, not retrofitted.

- **SR-01.** Every read and write of a user-owned table is scoped by the authenticated user's id, taken from the server-side session — never from a request parameter, header, or body.
- **SR-02.** Tenant scoping is enforced by structure, not by per-query habit:
  every repository method for a user-owned entity either takes `userId` as a mandatory parameter or inherits it from a tenant-scoped base.
  An architecture test (e.g. ArchUnit) fails the build if a repository for a user-owned entity exposes a finder without tenant scope.
- **SR-03.** PostgreSQL Row-Level Security is enabled on every domain table as the database-level backstop, with the app setting the tenant id per transaction (`SET LOCAL`).
  This is proposed as DM-30 (see the review) because it matches the project's existing rule that invariants live in the database (DM-02, D-20): an unscoped query then returns zero foreign rows even when the Java code is wrong.
- **SR-04.** Resource-id endpoints (`/transactions/{id}`, `/imports/{id}`, `/rules/{id}`, …) return **404** for a row that exists but belongs to another user — the same response as for a row that does not exist.
  403 confirms the id exists and enables enumeration.
- **SR-05.** Multi-row operations validate every referenced id:
  confirming a transfer link checks both transactions belong to the caller;
  a merge checks both rows; a dismissal checks both rows; batch replace checks the import; a rule checks its category and optional account.
  (DM-23 backstops this at the schema level; the service must still check so the failure is a clean 404, not a constraint error.)
- **SR-06.** Derived and aggregate queries (dashboard sums, suggestion queries, inbox, balance views, FR-2 counts) carry the tenant filter exactly like row reads.
  The Investments balance view (DM-11) must expose `user_id` and be filtered by it.
- **SR-07. IDOR test suite.** Integration tests seed two users with data and, for **every** endpoint, call it as user B with user A's resource ids, asserting 404/empty.
  The test enumerates endpoints from the route table so a new endpoint cannot ship untested.
  This suite is written in MVP, while there is still only one real user.

## 4. Upload and parser safety (FR-2)

A statement file is untrusted input from an internet-facing endpoint.
XLSX is a zip of XML, so the parser is exposed to zip and XML attacks, not just bad data.
These limits are design input for the upcoming parser note; the concrete numbers below are defaults, adjustable there with rationale — the *existence* of each limit is not optional.

- **SR-10.** Maximum upload size: **5 MB**, enforced before the body is buffered (real statements are under 200 KB).
- **SR-11.** Zip-bomb protection: maximum total uncompressed size **50 MB**, minimum inflate ratio **1%** (POI `ZipSecureFile.setMinInflateRatio` / `setMaxEntrySize`), maximum **100** archive entries.
- **SR-12.** XML hardening: DTDs disallowed, external entity resolution disabled (XXE), secure processing enabled on every XML reader the parser touches.
  POI's defaults must be verified by a test that feeds an XXE sample and asserts rejection, not assumed.
- **SR-13.** Structural caps: **exactly 1** sheet, **10,000** rows, **50** columns read.
  A file over any cap is rejected whole, with a generic error.
  All three real sources are single-sheet (`sheet`, `Sheet 1`, `Statement`), so the security cap and the parser's contract are the same rule.
  Consequence, accepted deliberately: if a bank ever ships a two-sheet export, the import fails loudly instead of silently reading the wrong sheet — which is the behaviour the exploration doc already chose for layout changes (§7, item 2).
  Raising the cap is then a one-line change made after looking at the new file, not a decision taken in advance for a file nobody has seen.
- **SR-14.** Parse timeout **30 seconds** and bounded memory: parsing runs on a bounded worker pool, never on the request thread, and one poisoned file must not take down the JVM.
- **SR-15.** Accepted format: `.xlsx` only.
  `.xlsm`, `.xlsb`, `.xls` are rejected by content, not extension: the file must be a zip whose `[Content_Types].xml` declares the xlsx workbook type, and any archive containing `vbaProject.bin` is rejected.
  The client-supplied `Content-Type` is ignored.
- **SR-16.** The uploaded filename is data, not a path: stored after sanitization (strip path separators and control characters, cap at 255 chars), never used to build a filesystem path, never logged (§2).
- **SR-17. The uploaded file reaches the disk by default, and that default must be turned off.**
  This is not a hypothetical about code that might save the file.
  Spring Boot's multipart handling spools every uploaded part to a temp file before the controller runs: `spring.servlet.multipart.file-size-threshold` defaults to `0B`, so the file is written to disk regardless of size, and `MultipartFile.getInputStream()` reads it back from there.
  Application code that never saves the file therefore still produces a plaintext copy of a real bank statement outside the database's encryption.

  Required configuration:
  - `spring.servlet.multipart.file-size-threshold` set **above** the SR-10 cap (e.g. `6MB`), so any accepted file stays in memory and never reaches disk.
    Real statements are under 200 KB against a 5 MB cap, so this costs nothing.
  - `spring.servlet.multipart.location` set to an app-private directory (mode `0700`, outside any served root) as the backstop for anything that does spill.
  - Any transient copy deleted in a `finally` block.

  Verified by inspecting the effective configuration at implementation time, not assumed — the framework default is the unsafe one.
  The original file is **not retained** after its raw rows are stored — `statement_import_rows.raw_cells` is the audit copy, `file_sha256` the identity (proposed D-29).
- **SR-18.** Upload endpoints require authentication and are rate-limited: **5 uploads per minute per user**.
- **SR-19.** The parser library (Apache POI or equivalent) is a standing CVE surface:
  dependency scanning (OWASP Dependency-Check or GitHub Dependabot) runs in CI, fails the build on known-critical CVEs in the parse path, and POI upgrades are applied promptly.
- **SR-20.** The parser extracts **transaction-table rows only**.
  Header and footer blocks — which carry account numbers, CIF/customer ids, IFSC/MICR, name, limits — are read transiently for validation (period, opening balance, Account Summary) and are **never persisted**, not in `raw_cells`, not anywhere (proposed DM-27).
  A test imports a real-shaped fixture and asserts the account number appears nowhere in the database.

## 5. Logging and redaction

SPEC §8 says "no financial data or PII in logs" in one line.
These are the rules that make that testable.

- **SR-25.** Data classed Restricted, Confidential, or Secret (§2) never reaches any log, at any level, including DEBUG, in any environment.
  Concretely banned: narrations, `raw_cells`, amounts, balances, account numbers, emails, notes, rule patterns, filenames, session ids, passwords.
- **SR-26.** Allowed in logs: the Internal class only — entity ids, `user_id`, statuses, enum values, counts, `fingerprint_version`, fingerprints, durations, SQLSTATE, constraint names, correlation ids.
  "Import 42 for user 1: 361 rows, 340 new, 21 duplicate, committed in 1.8s" is the model log line.
- **SR-27.** Logging is structured (key–value), and only explicitly whitelisted fields are passed.
  Never log a whole entity, DTO, or request body; `toString()` on domain entities must not include any non-Internal field, so an accidental log of an entity leaks nothing.
- **SR-28.** Database exceptions are logged as exception class + SQLSTATE + constraint name only.
  The Postgres `DETAIL` field is dropped before logging: a unique-violation DETAIL prints the conflicting values, which for domain constraints can include Restricted data.

  **Development exception, allowed.** Local development may log `DETAIL`.
  It is genuinely useful while building the importer — a fingerprint collision tells you which row collided and why, and reconstructing that from a constraint name alone wastes time.
  It is banned from the first deployment onwards.
  Three conditions make that ban hold, because a flag someone must remember to flip is not a control:
  - **The safe behaviour is the default.** Redaction is on unless a `dev` profile explicitly turns it off. Forgetting to configure anything gives the safe result. The opposite arrangement — verbose by default, disabled for production — leaks the first time a profile is misapplied.
  - **The app refuses to start** if `DETAIL` logging is enabled while the production profile is active. This is a startup assertion, not a warning that scrolls past.
  - **SR-29's log-capture test runs under the production profile**, so it tests the configuration that actually ships.

  One consequence to be explicit about: development runs against the owner's **real** statements, so development logs contain real narrations, real third-party names, phone numbers and VPAs.
  Dev log files are Restricted data (§2).
  They are gitignored, never pasted into an issue, a pull request, or a chat with any external tool, and deleted when no longer needed.
- **SR-29.** A log-capture test exercises the sensitive flows (import with a real-shaped masked fixture, failed import, rule edit, checkpoint entry) and asserts the captured output contains none of the fixture's narrations, amounts, balances, or the account number.
  This test is the enforcement mechanism for SR-25; without it the rule is an intention.
- **SR-30.** No PII in URLs or query strings — server routes, redirects, and frontend routes alike.
  Ids are allowed; narrations, amounts, emails, and search text are not (search text travels in a POST body).
  URLs land in access logs, browser history, and proxies, which is why this is a logging rule.

## 6. Authentication and session (constraints on FR-8)

The mechanism is deliberately unchosen (FR-8, DM-14).
These are the constraints any chosen mechanism must satisfy — proposed as D-31.

- **SR-35.** Every endpoint except login and static assets requires authentication.
  There is no "localhost trust", no unauthenticated dev profile reachable in a deployed build, and no default credentials.
- **SR-36.** TLS everywhere: browser→app is HTTPS only (HSTS on), app→DB uses TLS with certificate verification (`sslmode=verify-full` or the JDBC equivalent).
- **SR-37.** The session lives in an `HttpOnly`, `Secure`, `SameSite` cookie — never in `localStorage` or `sessionStorage`.
  Reason: a single XSS bug can read browser storage; it cannot read an `HttpOnly` cookie.
  Consequence: state-changing endpoints need CSRF protection (token or `SameSite=Strict` plus origin checks — decided with the mechanism).
- **SR-38.** Sessions expire: an idle timeout and an absolute lifetime, both server-enforced; logout invalidates server-side.
- **SR-39.** Login is rate-limited and failures are uniform ("invalid credentials" — never "no such user").
- **SR-40.** If passwords are the chosen factor: hashed with Argon2id or bcrypt (per current OWASP guidance at implementation time), never logged, never in the repo — including the seeded user's hash.
  The seed user's credential is bootstrapped from the environment or set on first run, not written into a Flyway migration.
- **SR-41.** CORS allows exactly the app's own origin(s); no wildcard with credentials.

## 7. Secrets management

- **SR-45.** No secret of any kind in the repo: DB credentials, session signing keys, API tokens, TLS keys, seed passwords.
  Secrets are injected from the environment or a secret store (SPEC §8).
- **SR-46.** A secret scanner (gitleaks or git-secrets) runs in CI on every push, and as a pre-commit hook locally.
- **SR-47.** The `statements/` directory stays gitignored, and CI additionally fails if any tracked file matches real-statement patterns (sheet names, bank header markers).
  All committed test fixtures are synthetic or masked per the exploration doc's Privacy note — never lightly-edited real files.
- **SR-48.** The application's DB role is least-privilege: DML on the app schema only — no DDL, no `ALTER TABLE`, no ability to drop or disable triggers, no `BYPASSRLS`.
  Flyway migrations run under a separate, privileged role.
  This is what makes DM-02's triggers a real control (§11); proposed as DM-28.

## 8. Encryption

- **SR-50.** In transit: §6's SR-36 covers both hops.
- **SR-51.** At rest: the managed Postgres instance has storage encryption enabled, verified against the provider's console/API, and this **includes `statement_import_rows.raw_cells`** — the single most sensitive store in the system (verbatim third-party PII, kept permanently, including for replaced imports).
- **SR-52.** Backups and snapshots carry the same encryption and access control as the live database, and backup restore is tested (a backup nobody can restore is not a backup; a backup anyone can restore is a leak).
- **SR-53.** Column-level (application-side) encryption of narrations and `raw_cells` was considered and is **deferred**: it breaks substring rule-matching and near-miss comparison, and MVP's threat model (single user, provider-encrypted disk, TLS) does not justify it.
  Revisit at the closed-circle phase alongside SR-60.

## 9. Retention, deletion, erasure

- **SR-55.** DM-09's "raw rows kept permanently" is accepted for MVP: the data is the owner's own audit trail, and the third-party PII in it was lawfully received by the owner as their own statement.
- **SR-56.** Interpretation data the owner deletes (a note, a rule) is actually deleted, not soft-kept.
- **SR-60.** Before any second user exists, a **full-tenant erasure path** must be designed and built: delete every row for a `user_id` across all eleven tables, including `HELD`/`REPLACED` imports and raw rows, in an order compatible with the `RESTRICT` rules, with a documented, service-layer exception to the DM-02 immutability triggers for whole-tenant purge.
  Recorded now (proposed D-32) because DPDP-Act-style erasure duties attach the moment someone else's data is held, and retrofitting deletion into a `RESTRICT`-everywhere, trigger-guarded schema is exactly the kind of work that gets skipped under time pressure.
- **SR-61.** At the same phase, DM-09 must be re-examined for third-party PII *of the circle members' counterparties* (retention limits, or purge of raw rows for replaced imports).
  Nothing is built now; the decision is on record.

## 10. Rules engine input (FR-5, DM §8.2)

`narration_pattern` is user-editable data matched against every imported row.

- **SR-65.** Patterns are matched as **literals**.
  If matching is pushed to SQL as `ILIKE '%' || pattern || '%'`, the characters `%`, `_`, `\` in the pattern are escaped first; otherwise a user pattern becomes a wildcard expression.
  If matching runs in Java (`contains` on a case-folded string), no escaping is needed.
  Either way, a test proves a pattern containing `%` matches only a literal `%`.
- **SR-66.** Pattern length is constrained: minimum 2 characters, maximum 100 (`CHECK`, proposed DM-29).
  An empty or 1-character pattern silently categorizes almost everything — a correctness trap more than an attack, but the same constraint kills both.
- **SR-67.** Active rules per user are capped (default 500, app-enforced).
  Matching cost is rules × rows per import; the cap keeps a future tenant from turning import into a CPU sink.
- **SR-68.** No regex in MVP (DATA_MODEL §8.2 already decides this).
  If a `match_type` is ever added, regex evaluation needs its own review: timeout, engine without catastrophic backtracking (e.g. RE2), length caps.

## 11. Audit-trail integrity

- **SR-70.** The DM-02 immutability triggers and D-20's database-enforced dedup are **tamper-resistance controls**, not just correctness features: they ensure imported financial facts cannot be silently edited through any application bug or ad-hoc DML.
  Their tests are security tests.
- **SR-71.** Their limits are stated so nobody over-trusts them:
  they bind only sessions without DDL rights — which is why SR-48 (least-privilege app role) is required for them to mean anything;
  a DBA or the migration role can disable them, so schema-touching migrations get reviewed with that in mind;
  and they are not cryptographic — they prove nothing to an outside auditor.
  Hash-chaining or append-only audit tables are out of scope for MVP and not currently justified.

## 12. Error handling

- **SR-75.** Clients receive generic errors: an error code, a safe human message, and a correlation id.
  Never a stack trace, an SQL fragment, a constraint name, a trigger exception text, or a file path.
  A global exception handler (`@ControllerAdvice`) is the single choke point; Spring Boot's default error attributes (`message`, `trace`, `exception`) are disabled.
- **SR-76.** The DM-02 trigger exceptions "name the rule directly" — for developers.
  They are mapped at the API boundary to a generic 409/422; the named rule goes to the log (SR-28 format), not to the client.
- **SR-77.** Parser rejections (SR-10…SR-16) return one uniform "file could not be processed" error without echoing file content, sheet names, or cell values; the specifics are logged as codes.
- **SR-78.** 404-for-foreign-rows (SR-04) is part of error handling: the not-found path and the not-yours path are byte-identical responses.

## 13. What this document does not cover — the code-level pass

This is a design-time gate.
After implementation, a second security review must check the things that only exist as code:

- actual log statements and `toString()` implementations against §5;
- every repository/query against §3, plus running the SR-07 suite;
- real parser configuration against §4 (POI settings are verified, not assumed);
- dependency CVE scan results (POI, Spring, frontend packages);
- Spring Security configuration, CSRF, CORS, headers, cookie flags against §6;
- Flyway migrations for seeded credentials or secrets;
- the deployed platform: TLS config, DB encryption flag, backup settings, DB roles (SR-48).

That review should be scheduled as its own task before the app is first exposed to the internet.

## 14. Production-grade checklist

Everything the requirements above imply must exist as a real tool, test, or configuration.
This table is the shopping list.
"Example" names a concrete option, not a mandate — any tool doing the same job is fine.

### CI and repository

| Required | Example | Purpose | Requirement |
| -------- | ------- | ------- | ----------- |
| Secret scanner | gitleaks, git-secrets | Stops DB credentials, a signing key, or the seeded password from being committed. Runs in CI and as a pre-commit hook | SR-46 |
| Dependency CVE scanner | OWASP Dependency-Check, Dependabot | POI parses untrusted files, so its advisories matter most; also covers Spring and frontend packages. Fails the build on known-critical CVEs in the parse path | SR-19 |
| Real-statement guard | CI check for bank header markers in tracked files | Backstop for the single `.gitignore` line protecting `statements/`. A rename or a "quick fixture" copy defeats the line, not the check | SR-47 |

### Test suites

| Required | Example | Purpose | Requirement |
| -------- | ------- | ------- | ----------- |
| IDOR suite | Spring Boot integration tests, two seeded users | Calls every endpoint as user B with user A's ids and asserts 404. Enumerated from the route table so a new endpoint cannot ship untested | SR-07 |
| Architecture test | ArchUnit | Fails the build if a repository for a user-owned entity exposes a finder without tenant scope. This is what makes scoping structural instead of a habit | SR-02 |
| Log-capture test | Logback list appender, `OutputCaptureExtension` | Runs the sensitive flows and asserts no fixture narration, amount, balance, or account number appears in captured output. Runs under the production profile | SR-29 |
| Parser hostile-input tests | Crafted XXE, zip bomb, `.xlsm`, oversize and over-cap fixtures | Proves each limit rejects. POI's defaults are verified, never assumed | SR-11…SR-15 |
| Header-block exclusion test | Real-shaped masked fixture | Asserts the account number and customer ID appear nowhere in the database after an import | SR-20 |
| Immutability trigger tests | Migration + JDBC tests | The DM-02 triggers are tamper-resistance controls, so their tests are security tests. Covered by `TriggerImmutabilityTest` (step 3) | SR-70 |
| Pattern-escaping test | A rule whose pattern contains a literal `%` | Proves user patterns match literally and cannot become wildcard expressions | SR-65 |
| Multipart disk-spill check | Effective-configuration test or inspection | Confirms an accepted upload never reaches the filesystem | SR-17 |

### Application configuration

| Required | Example | Purpose | Requirement |
| -------- | ------- | ------- | ----------- |
| Global error handler | `@ControllerAdvice`, Spring error attributes disabled | No stack trace, SQL fragment, constraint name, or trigger text reaches a client | SR-75 |
| Structured logging with a field whitelist | Logback JSON encoder, safe entity `toString()` | Only Internal-class fields are loggable; an accidental entity log leaks nothing | SR-26, SR-27 |
| Profile-bound redaction, safe by default | Spring profiles + startup assertion | Postgres `DETAIL` allowed in development, refused in production, with the safe behaviour as the default | SR-28 |
| Rate limiter | Bucket4j, or gateway rules | Login (credential stuffing) and import (the most expensive endpoint) | SR-18, SR-39 |
| Session cookie configuration | Spring Security | `HttpOnly`, `Secure`, `SameSite`; never browser storage | SR-37 |
| CSRF protection and strict CORS | Spring Security | State-changing requests protected; exactly the app's own origin allowed | SR-37, SR-41 |
| Multipart limits | `max-file-size`, `file-size-threshold` above the cap, private `location` | Enforces SR-10 and keeps the statement out of the filesystem | SR-10, SR-17 |

### Platform and database

| Required | Example | Purpose | Requirement |
| -------- | ------- | ------- | ----------- |
| TLS on both hops | HTTPS with HSTS; JDBC `sslmode=verify-full` | Restricted data in transit, including app→DB | SR-36 |
| Verified at-rest encryption | Provider console or API check | Must demonstrably cover `raw_cells`, the most sensitive store in the system | SR-51 |
| Encrypted backups with a tested restore | Provider snapshots | Backups carry the same data and the same duty. An untested restore is not a backup | SR-52 |
| Least-privilege database roles | App role: DML only, no DDL, no trigger control, no `BYPASSRLS`. Separate Flyway role | Without this, DM-02's triggers and RLS can be switched off by the app's own connection. The database half — what `ft_app` cannot do — is covered by `RolePrivilegeTest` (step 3); the application's own connection is not built yet | SR-48 |
| Row-Level Security | Postgres RLS + per-transaction `SET LOCAL` | Database backstop for tenant scoping: an unscoped query returns zero foreign rows. The database half is covered by `RowLevelSecurityTest` and `RlsCoverageTest` (step 3); wiring `SET LOCAL app.user_id` into the application's real transactions is step 4 | SR-03 |
| Secret store | Environment injection or a cloud secret manager | No secret in the repo, an image, or a migration | SR-45 |

### Before the closed-circle phase (not MVP)

| Required | Example | Purpose | Requirement |
| -------- | ------- | ------- | ----------- |
| Full-tenant erasure path | Service-layer purge across all eleven tables | Erasure duties attach the moment a second person's data is held. Retrofitting deletion into a `RESTRICT`-and-trigger schema is the work most likely to be skipped under pressure | SR-60 |
| Raw-row retention review | Decision, not code | Re-examines permanent retention of other people's counterparty PII | SR-61 |
| Column-level encryption decision | Decision, not code | Deferred deliberately in MVP because it breaks substring matching; revisited here rather than forgotten | SR-53 |
