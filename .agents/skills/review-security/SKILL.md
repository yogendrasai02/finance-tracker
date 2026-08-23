---
name: review-security
description: Review the project's design or code for security and privacy risks — tenant isolation/IDOR, financial PII and credentials, statement-import/parser safety, auth, injection, and secrets. Use when the user asks for a security review of the spec, data model, API design, or implementation.
---

# Review Security

Review for security and privacy risks as a security-minded senior engineer.
This app holds personal financial data, so treat confidentiality and integrity as high priority.
The goal is to find tenant boundary leaks, sensitive-data exposure, and unsafe ingestion vectors before code ships.

The target depends on what exists:
- If reviewing design docs, read `docs/SPEC.md` and `docs/DATA_MODEL.md`.
- If reviewing code, review the changed files or the paths the user names.
Ask the user which, only if it is unclear.

This is a review, not a pentest. Do not run exploits or destructive tests.

## Review process

1. **Read context.**
   - Read the target design doc, schema, API spec, or code module.
   - Map trust boundaries, entry points, data storage locations, and tenant boundaries.
2. **Threat-model and apply the checklist.**
   - Trace data flow from user input / statement upload through parsing, storage, and retrieval.
   - Evaluate every checklist item against that flow.
3. **Classify findings** by severity (see below).
4. **Produce the structured report** using the template.

## Review objectives

Judge the target against five security domains:

1. **Multi-tenant isolation and authorization** — is cross-tenant leakage prevented across queries, mutations, and bulk exports?
2. **Financial PII and data privacy** — is sensitive personal/banking data masked, encrypted, or kept out of persistence and logs?
3. **File upload and parser safety** — are statement pipelines protected against malicious payloads, memory exhaustion, and parser flaws?
4. **Authentication and session lifecycle** — are tokens, sessions, and access boundaries managed and verified?
5. **Auditability and log hygiene** — are security events audited without leaking financial records into logs?

## Checklist

### Multi-tenant isolation and IDOR
- Does every user-owned table include a non-nullable `user_id` column?
- Are all SELECT/UPDATE/DELETE operations explicitly scoped by `user_id`? No global queries that cross tenants.
- IDOR: do endpoints verify a referenced resource ID (`/accounts/{id}`, `/transactions/{id}`) belongs to the authenticated user?
- For cross-account links or transfers, are both source and target validated as owned by the same user?
- Is isolation enforced server-side (service or data-access layer), not just hidden in the UI or trusting client params?
- If using Row Level Security or JPA filters, are bypass scenarios considered?

### Sensitive data and financial PII
- What PII and financial data is stored (account numbers, card numbers, balances, statements)?
- Is anything sensitive stored that does not need to be? Prefer not storing it.
- Account/card masking: are full numbers masked in storage and UI (e.g. last 4 only)?
- Are CVV, card expiry, PIN, and netbanking credentials explicitly barred from storage?
- Narration sanitization: is accidental PII in narrations (names, phone numbers, UPI IDs) minimized where not needed?
- Is sensitive data kept out of logs, error messages, and URLs/query strings?

### Credentials and secrets
- Are any bank/login credentials stored? They should not be. Flag if they are.
- Are secrets (DB passwords, signing keys, API tokens) injected via env vars or a secret manager, never hardcoded or in config in the repo?
- Is a repo secret-scan (git-secrets, gitleaks) planned to prevent credential commits?

### File upload, parser, and ingestion security
- Statement imports are untrusted input. Are upload endpoints size-limited (e.g. max ~10MB)?
- Is file validation based on content inspection, not the client `Content-Type` or extension?
- Are parsers (PDF, CSV, XLS) protected against XXE, decompression/zip bombs, and memory exhaustion?
- Are heavy parser processes sandboxed or bounded in CPU and memory?
- Password-protected PDFs: is the statement password handled only in memory, never stored or logged?
- Are uploaded files ephemeral and deleted after parsing? If archived, are access controls and retention enforced?
- Is there protection against injection via filenames (path traversal)?
- Are financial calculations done server-side, never trusting client-supplied amounts or totals?

### Authentication, authorization, and sessions
- Is there an auth model, even if single-user today, with server-side checks on every sensitive action?
- Are tokens stored safely on the client (`HttpOnly`, `Secure`, `SameSite` cookies)?
- Are sessions/tokens validated for expiry, tampering, and revocation on every authenticated request?
- For future closed-circle sharing: are roles (owner, viewer, editor) planned with least-privilege defaults?
- Are auth endpoints (login, password reset, invite) rate-limited? Are heavy ingestion/export endpoints rate-limited against DoS?

### API security and input validation
- Are incoming payloads validated against strict schemas (whitelisted fields, types, length)?
- Is mass-assignment prevented on entity updates?
- Are all DB queries parameterized (PreparedStatements / ORM binding)? No raw SQL string concatenation.
- Is CORS restricted to trusted frontend origins? Are state-changing requests protected against CSRF?

### Data at rest and in transit
- Is transport encrypted (HTTPS/TLS) for all client-server and inter-service traffic?
- Is encryption at rest considered for the statement store and database?
- How are raw statement files stored and access-controlled?

### Auditability and data lifecycle
- Are security-relevant actions (login, data export, deletion) logged?
- Can a data change be traced to who made it and when?
- Are statement contents, raw rows, and auth tokens excluded from application logs? Is prod log level safe (no dumping request payloads)?
- Is there a way to fully delete a user's data when needed?
- Are backups considered, and do they carry the same protections?
- For closed-circle sharing later: is shared vs. private data separated by design?

## How to report

Group findings by severity. Skip a heading with nothing under it.
For each finding: describe the risk (attack vector), the impact, and a concrete mitigation.
Rank by real risk to the user's financial data, not by how easy the fix is.

- **Critical / Blocker** — direct IDOR exposing another tenant's data, SQL injection, RCE via a file parser, or hardcoded production secrets.
- **High** — plaintext PII logging, missing upload size/type controls, unauthenticated endpoints, or insecure statement-password handling.
- **Medium** — missing rate limiting, permissive CORS, or missing defense-in-depth authorization checks.
- **Low / Informational** — header hardening, minor defense-in-depth, or documentation.

Use this report format:

```markdown
# Security Review Report: [Document / Module Name]

**Review Date**: [YYYY-MM-DD]
**Status**: [Approved | Approved with Mitigations | Changes Required]

## Executive Summary
[2-4 sentences on security posture, tenant safety, PII handling, and critical vulnerabilities.]

## Findings by Severity

### 🔴 Critical / Blocker
- **[Title]**: [Vulnerability, attack vector, and impact.]
  - *Mitigation*: [Concrete remediation.]

### 🟠 High
- **[Title]**: [Vulnerability and impact.]
  - *Mitigation*: [Concrete remediation.]

### 🟡 Medium
- **[Title]**: [Issue and risk.]
  - *Mitigation*: [Concrete remediation.]

### 🟢 Low / Informational
- **[Title]**: [Hardening recommendation.]

## Security Checklist Summary
| Domain | Status | Notes |
| :--- | :--- | :--- |
| Multi-Tenant Isolation (IDOR) | [Pass/Fail/Partial] | [Note] |
| Financial PII & Log Hygiene | [Pass/Fail/Partial] | [Note] |
| File Upload & Parser Safety | [Pass/Fail/Partial] | [Note] |
| Auth & Access Control | [Pass/Fail/Partial] | [Note] |
| API Validation & Injection Safety | [Pass/Fail/Partial] | [Note] |
| Secrets Management | [Pass/Fail/Partial] | [Note] |
```

## Style
Follow AGENTS.md §6: simple, direct English. Short sentences. No metaphors.
Rank findings by real risk to the user's financial data, not by how easy they are to fix.
