# Security Review Report: SPEC.md + DATA_MODEL.md (Pre-Implementation Gate)

**Review Date**: 2026-08-23
**Target**: docs/SPEC.md (reviewed at Draft v0.3), docs/DATA_MODEL.md (reviewed at Draft v0.2), docs/STATEMENT_DATA_EXPLORATION.md
**Status**: Approved with Mitigations — all 8 proposed decisions approved by the owner on 2026-08-23 and applied to SPEC v0.4 and DATA_MODEL v0.3

This is a design review, run before any application code exists.
No exploits were run and no code was inspected, because there is none.
Every mitigation below is either a requirement now written in [SECURITY.md](../SECURITY.md) (referenced as SR-nn) or a proposed decision listed in §Proposed decisions for owner approval.
A second, code-level pass is required later (SECURITY.md §13): real log statements, real queries, real parser configuration, dependency CVEs.

## Executive Summary

The design's data-integrity posture is strong: immutable imported facts (DM-02), database-enforced dedup (D-20), tenant-carrying foreign keys (DM-23), and verbatim raw-row audit trails (DM-09) are all above the bar for a personal project.
The gaps are on the confidentiality side: nothing yet defines how tenant scoping is enforced in queries (DM-23 does not do this), the untrusted-XLSX import pipeline has no hardening requirements, the one-line logging rule in SPEC §8 is not testable, and the most sensitive store in the system — `raw_cells`, holding third parties' names, phones, and VPAs verbatim, forever — has no stated encryption, minimization, or erasure story.
None of these require reworking the frozen design; all are additive requirements, now written in SECURITY.md.
Eight new decisions are proposed for approval.

## Findings by Severity

No Critical/Blocker findings: the design contains no concrete vulnerability (no code exists to contain one), and every gap found can be closed additively before implementation.

### 🟠 High

- **H1 — Tenant scoping is a schema guarantee for references, but nothing for reads.**
  Fact: DM-23's composite foreign keys stop a child row from *pointing at* another user's parent.
  They do not stop a repository method that forgets `WHERE user_id = ?` from *returning* another user's rows — the exact bug class behind most real-world IDOR leaks.
  SPEC §8 defers "isolation testing" to the closed-circle phase, but every query written in MVP is inherited by that phase; retrofitting scoping means re-auditing all of them.
  - *Mitigation*: Structural scoping from the first line of code: mandatory tenant parameter or scoped repository base, enforced by an architecture test (SR-01, SR-02); Postgres RLS as the database backstop, matching the project's own "invariants live in the database" rule (SR-03, proposed DM-30); 404-for-foreign-rows (SR-04); multi-row ownership checks on links, merges, dismissals, replaces (SR-05); a two-user IDOR test suite that enumerates every endpoint, written in MVP (SR-07).

- **H2 — The import pipeline has no untrusted-input requirements.**
  FR-2 accepts XLSX uploads on an internet-facing app.
  XLSX is a zip of XML, so the parser is exposed to zip bombs, XML entity expansion (XXE), macro-bearing variants (`.xlsm`), and memory/CPU exhaustion — and neither SPEC §4 nor the data model says anything about file size, caps, timeouts, format validation, or where the file lands.
  A single crafted file could take the service down or, with an XXE-vulnerable configuration, read server files.
  - *Mitigation*: The full limit set in SECURITY.md §4 (SR-10…SR-20): 5 MB upload cap, inflate-ratio and entry caps, DTD/XXE hardening verified by test, sheet/row/column caps, 30 s timeout off the request thread, content-based `.xlsx`-only acceptance rejecting `vbaProject.bin`, sanitized filenames, temp files outside any web root, authenticated and rate-limited endpoint, CVE scanning for the parser library (POI) in CI.
    These feed the parser design note directly (proposed D-28).

- **H3 — Statement header blocks carry the worst identifiers, and nothing excludes them from storage.**
  The exploration doc shows the header blocks contain the owner's name, account numbers, CIF/customer IDs, IFSC/MICR, and credit limits.
  DATA_MODEL §6.2 stores "the raw source rows, verbatim" — it does not say *which* rows.
  If the parser persists the whole sheet into `raw_cells`, the database permanently holds full account numbers and customer IDs it has no use for, and every future leak (backup, log, bug) is worse for it.
  - *Mitigation*: Persist transaction-table rows only; header/footer blocks are read transiently for validation (period, opening balance, Account Summary) and never stored; a test asserts the fixture's account number appears nowhere in the database (SR-20, proposed DM-27).

- **H4 — "No financial data or PII in logs" is one line, and one line is not a control.**
  SPEC §8 states the rule but nothing defines what is sensitive, what may be logged, or how the rule is enforced.
  Without concrete rules, narrations (third-party names, phones, VPAs), amounts, and balances reach logs through the ordinary paths: logged exceptions, logged entities, Postgres unique-violation `DETAIL` fields that print the conflicting values, and DEBUG logging of request bodies.
  - *Mitigation*: The data classification table (SECURITY.md §2) and the logging rules SR-25…SR-30: whitelist-only structured logging, safe entity `toString()`, SQLSTATE-and-constraint-name-only database errors with `DETAIL` dropped, no PII in URLs, and a log-capture test that asserts fixture narrations, amounts, balances, and account numbers never appear in captured output (proposed D-30 makes SECURITY.md normative from SPEC §8).

- **H5 — `raw_cells` keeps third-party PII forever, and its protection is only implied.**
  DM-09 keeps raw rows permanently, including for replaced imports.
  Those rows hold verbatim third-party names, phone numbers, and UPI VPAs.
  SPEC §8 says "encryption at rest for the DB" generically; nothing confirms it covers this store, and nothing anywhere defines deletion or erasure — the schema actively resists it (`RESTRICT` everywhere, delete-blocking triggers), which is correct for the ledger but currently absolute.
  Single-user MVP makes this acceptable today; the closed-circle phase makes it a legal-duty gap (DPDP-style erasure) that would be expensive to retrofit.
  - *Mitigation*: Verified at-rest encryption explicitly covering `raw_cells` and backups (SR-51, SR-52); permanence accepted for MVP with rationale (SR-55); a full-tenant erasure path — including a documented service-layer exception to the DM-02 triggers for whole-tenant purge — designed and built *before* any second user exists (SR-60, SR-61, proposed D-32).

### 🟡 Medium

- **M1 — The uploaded file lands on disk by framework default, and its fate is unspecified.**
  `statement_imports` stores `source_filename` and `file_sha256`, but no design statement says whether the original XLSX is kept, where, or for how long.
  This is not a question that can be answered by deciding not to write saving code.
  Spring Boot's `spring.servlet.multipart.file-size-threshold` defaults to `0B`, so every uploaded part is spooled to a temp file before the controller runs, and `MultipartFile.getInputStream()` reads it back from there.
  The result is a plaintext copy of a real bank statement on the filesystem, outside the database's encryption and access story, with container-default permissions — produced by the default configuration, not by any application decision.
  - *Mitigation*: Set the multipart threshold above the SR-10 size cap so an accepted file stays in memory and never reaches disk (real files are under 200 KB against a 5 MB cap); set the multipart `location` to a private `0700` directory outside any served root as the backstop; delete any transient copy in a `finally` block; do not retain the original at all, since `raw_cells` is the audit copy and `file_sha256` the identity (SR-17, proposed D-29).
    The effective configuration is verified at implementation time — the default is the unsafe one.

- **M2 — Auth requirements are absent, not just the mechanism.**
  Leaving the mechanism unchosen (FR-8, DM-14) is sound.
  But no constraints exist for whatever gets chosen, so decisions like "token in localStorage" or "auth disabled in the dev profile" could be made by default during implementation.
  - *Mitigation*: The constraint set SR-35…SR-41 (proposed D-31): no unauthenticated endpoint except login, TLS on both hops including app→DB certificate verification, session in `HttpOnly`/`Secure`/`SameSite` cookie (not browser storage), server-side expiry and logout, rate-limited uniform-failure login, hashed passwords with the seed credential bootstrapped from the environment — never in a Flyway migration, CSRF protection, strict CORS.

- **M3 — Error responses are undesigned, and the design makes leaks likely.**
  DM-02's trigger exceptions deliberately "name the rule directly", and D-21 failures involve balance-chain details.
  Without a defined boundary, Spring Boot's default error attributes ship stack traces and exception messages — including trigger text and constraint violations — to the client.
  - *Mitigation*: Generic client errors (code + safe message + correlation id) via a single global handler with Spring's default error attributes disabled; trigger and parser specifics go to logs only, in SR-28 format (SR-75…SR-78).

- **M4 — The immutability triggers bind whoever the app connects as — and that role is undefined.**
  DM-02 argues triggers "survive application bugs, bulk updates, and a manual `psql` session".
  True only if the connecting role cannot `ALTER TABLE ... DISABLE TRIGGER`.
  If the app (or the developer's habitual psql session) connects as the schema owner, the tamper-resistance argument is void.
  - *Mitigation*: Least-privilege app role — DML only, no DDL, no trigger control, no `BYPASSRLS`; Flyway runs under a separate privileged role (SR-48, proposed DM-28).
    The triggers' limits are stated explicitly so they are not over-trusted (SR-70, SR-71).

- **M5 — `narration_pattern` is user input evaluated against every row.**
  Substring-only matching (DATA_MODEL §8.2) already avoids regex denial-of-service — good.
  Two residual issues: if matching is implemented as SQL `ILIKE '%' || pattern || '%'`, unescaped `%`/`_` turn a "literal substring" into a wildcard expression; and an empty or 1-character pattern silently categorizes nearly everything.
  Rule count is also unbounded, and matching cost is rules × rows.
  - *Mitigation*: Literal matching with `%`/`_`/`\` escaped (or in-app `contains`), proven by test; pattern length `CHECK` (2–100, proposed DM-29); app-enforced cap on active rules (SR-65…SR-68).

- **M6 — No rate limiting anywhere.**
  Login (credential stuffing) and import (the most expensive endpoint) are the two that matter on an internet-facing single-user app.
  - *Mitigation*: SR-18 (uploads) and SR-39 (login). Broader per-user API limits can wait for the closed-circle phase.

### 🟢 Low / Informational

- **L1 — One `.gitignore` line guards the crown jewels.**
  `statements/` being gitignored is correct but fragile — a rename or a "quick fixture" copy defeats it.
  Add gitleaks/git-secrets in CI and pre-commit, plus a CI check for real-statement markers in tracked files; committed fixtures are synthetic or masked only (SR-46, SR-47).
- **L2 — No PII in URLs is currently only implicit.**
  Stated as a rule now (SR-30) so GET-with-query-string search endpoints are never built: ids in paths are fine, narration/amount/email search text travels in POST bodies.
- **L3 — Dependency scanning should cover more than POI.**
  Spring and the frontend toolchain carry their own advisories; one CI scanner covers all of it (SR-19).
- **L4 — Backups inherit the sensitivity of the data.**
  Same encryption and access control as the live DB, and restore actually tested (SR-52).
- **L5 — Column-level encryption of narrations/`raw_cells`: considered, deferred.**
  It would break substring rule-matching and near-miss comparison; provider disk encryption plus TLS is proportionate for MVP.
  Recorded (SR-53) so it is revisited deliberately at the closed-circle phase, not forgotten.
- **L6 — `users.email` enables account enumeration later.**
  Irrelevant with one seeded user and no registration; becomes real with invites.
  Covered by SR-39's uniform failures when FR-8 is designed.

## Security Checklist Summary

| Domain | Status | Notes |
| :--- | :--- | :--- |
| Multi-Tenant Isolation (IDOR) | Partial | DM-23 is a strong reference-level control; query-level scoping and IDOR testing were undefined → H1, SECURITY.md §3 |
| Financial PII & Log Hygiene | Partial | Intent stated in SPEC §8; classification, rules, and tests were missing → H4, H5, SECURITY.md §2, §5 |
| File Upload & Parser Safety | Fail | No requirements existed → H2, H3, SECURITY.md §4 |
| Auth & Access Control | Partial | Real-auth-from-day-1 is decided; constraints on the mechanism were missing → M2, SECURITY.md §6 |
| API Validation & Injection Safety | Partial | Substring-only rules and paise-integer parsing are good calls; ILIKE escaping, error boundary, rate limits added → M3, M5, M6 |
| Secrets Management | Partial | "Secret store, never in repo" decided in SPEC §8; scanning, DB roles, seed-credential handling added → M4, L1, SECURITY.md §7 |
| Data Retention & Erasure | Partial | Permanent raw retention is a deliberate audit feature; erasure story deferred with a hard gate before user #2 → H5, SECURITY.md §9 |
| Audit-Trail Integrity | Pass | DM-02 + D-20 + D-21 are genuine tamper-resistance controls; limits now stated (SR-70, SR-71) and made real by the role split (DM-28) |

## Proposed decisions — all approved and applied (2026-08-23)

The review itself was run under a propose-don't-edit boundary.
The owner approved all eight on 2026-08-23, and they are now folded into the two decision logs and the sections they touch: SPEC.md is Draft v0.4, DATA_MODEL.md is Draft v0.3.

Applied alongside them, found while folding in:

- **SPEC §8 contradicted SR-07.** It said isolation testing happens in the closed-circle phase, while the review requires structural scoping and the two-user IDOR suite during MVP. §8 now states that invites and roles wait, and isolation does not.
- **DATA_MODEL §10 said "Three rules" above a four-row table**, left over from the earlier data-model review. Reworded so the count cannot go stale again; the table now has six rows.

**SPEC.md:**

| ID | Proposed decision | Rationale |
| --- | ---------------- | --------- |
| D-28 | Statement upload and parsing enforce the SECURITY.md §4 limit set (size, zip/XML hardening, structural caps, timeout, content-based `.xlsx`-only acceptance, no macros). The parser design note must implement these limits, adjusting numbers only with recorded rationale | The import endpoint is untrusted, internet-facing input into a zip-of-XML parser; each missing limit is a concrete crash or exfiltration vector (H2) |
| D-29 | The uploaded file is not retained after its raw rows are stored; `raw_cells` is the audit copy and `file_sha256` the identity. The multipart threshold is set above the size cap so an accepted file never reaches disk, with a private `0700` spill directory as backstop | Avoids a second, unmanaged copy of the most sensitive data. The framework writes the file to disk by default, so this needs a configuration decision, not just a decision to write no saving code. FR-2's traceability is already met by `statement_import_rows` (M1) |
| D-30 | docs/SECURITY.md is normative. SPEC §8 references it; its data classification and logging rules (SR-25…SR-30) replace the one-line logging statement; the log-capture test (SR-29) is part of definition-of-done. Postgres `DETAIL` logging is permitted in local development and refused from the first deployment onwards, with redaction as the default behaviour and a startup assertion under the production profile | One line is an intention; classification plus whitelist logging plus a test is a control (H4). The development exception is real value while building the importer, and it holds only because forgetting to configure anything gives the safe result — owner decision, 2026-08-23 |
| D-31 | The FR-8 auth mechanism, when chosen, must satisfy SR-35…SR-41 (TLS both hops, `HttpOnly`/`Secure`/`SameSite` cookie session, no browser storage, server-side expiry, rate-limited uniform-failure login, environment-bootstrapped seed credential, CSRF, strict CORS) | Keeps the mechanism open (DM-14) while closing the defaults that are hard to undo later (M2) |
| D-32 | A full-tenant erasure path (all rows for a `user_id`, including raw rows and replaced imports, with a documented trigger exception for whole-tenant purge) must be designed and built before any second user exists. Raw-row retention for third-party PII is re-examined at the same gate | Erasure duties attach when someone else's data is held; retrofitting deletion into a `RESTRICT`-and-trigger schema is the work most likely to be skipped later (H5) |

**DATA_MODEL.md:**

| ID | Proposed decision | Rationale |
| --- | ---------------- | --------- |
| DM-27 | `raw_cells` stores transaction-table rows only. Statement header/footer blocks (name, account numbers, CIF, IFSC/MICR, limits) are parsed transiently for validation and never persisted. §6.2 states this explicitly | The app has no use for full account identifiers; not storing them is the cheapest control in this review (H3) |
| DM-28 | The application connects with a least-privilege role: DML on the app schema only, no DDL, no trigger control, no `BYPASSRLS`. Flyway runs under a separate migration role | DM-02's "survives a manual `psql` session" claim is only true for a role that cannot disable the triggers (M4) |
| DM-29 | `narration_pattern` gains `CHECK (char_length(...) BETWEEN 2 AND 100)`; matching escapes `%`, `_`, `\` if implemented via `LIKE`/`ILIKE`; active rules per user are capped in the service layer | Prevents wildcard injection and the match-everything pattern; bounds import-time matching cost (M5) |
| DM-30 | Row-Level Security is enabled on every domain table, with the app setting the tenant id per transaction (`SET LOCAL`). Service-layer scoping (SR-01, SR-02) remains the primary mechanism; RLS is the backstop | Same reasoning the model already uses for DM-02 and D-20: the database check survives application bugs. Cheapest to adopt now, while tables are empty and there is one user. Cost: per-transaction `SET LOCAL` discipline with the connection pool, which must be tested (H1) |

## What this review did not do

- No code, queries, log statements, or configuration were reviewed — none exist. The code-level pass (SECURITY.md §13) is mandatory before first internet exposure.
- No dependency CVE scan was run — there is no dependency tree yet. SR-19 puts it in CI from the first build.
- The auth mechanism was deliberately not designed (FR-8 boundary); only its constraints were set.
- Hosting-platform specifics (provider, network posture, WAF) are out of scope until a platform is chosen; SR-36, SR-51, SR-52 are the requirements any choice must meet.
