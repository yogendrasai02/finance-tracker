---
name: review-spec
description: Review the project specification (docs/SPEC.md) for clarity, completeness, edge cases, Indian financial domain correctness, multi-tenancy readiness, and engineering feasibility. Use when the user asks to review, critique, or check the spec, or before starting design or implementation based on it.
---

# Review Spec

Review the project specification as a senior/principal engineer with strong Indian personal-finance domain knowledge.
The goal is to catch requirement gaps, wrong domain assumptions, ambiguous boundaries, and unhandled edge cases before design or implementation begins.

The default target is `docs/SPEC.md` unless the user names another file.

This is a review, not a rewrite. Point to specific sections or lines and suggest. Do not edit the spec unless the user asks.

## Review process

1. **Read context.**
   - Read the target spec in full.
   - Read upstream docs where they exist: `docs/STATEMENT_DATA_EXPLORATION.md`, `docs/DATA_MODEL.md`, and `AGENTS.md` for project goals.
2. **Apply the checklist** systematically. Find gaps, ambiguities, contradictions, and unrealistic assumptions.
3. **Classify findings** by severity (see below).
4. **Produce the structured report** using the template.

## Review objectives

Judge the spec against four standards:

1. **Domain correctness** — does it reflect Indian banking, credit card, tax, and personal-finance reality?
2. **Engineering soundness** — are requirements unambiguous, deterministic, testable, and feasible?
3. **Data lifecycle and integrity** — are mutability rules, state transitions, and edge cases clearly specified?
4. **Scope discipline** — are phase boundaries (MVP / Phase 1 / Future) explicit and protected from scope creep?

## Checklist

### Goals and scope
- Is the problem being solved stated clearly and up front?
- Are goals ordered by priority (personal use first, portfolio project, closed-circle sharing later)?
- Is scope bounded? Are non-goals stated explicitly?
- Does every feature trace back to a stated goal? Flag features with no clear reason.
- Are deferred features listed with concrete phase tags, not vague backlog promises?

### Clarity and ambiguity
- Any requirement two engineers could read differently?
- Vague words with no definition ("fast", "secure", "flexible", "later")?
- Terms used before they are defined?

### Financial mental model and core concepts
- Are accounts classified by nature (asset, liability, virtual contra-accounts)?
- Are non-accounts separated out — payment channels (GPay/PhonePe/UPI apps) and pre-tax deductions (EPF/ESPP) are not holding accounts?
- Does the spec require money stored as integer smallest units (paise)? No floating-point money.
- Are transactions split into mutually exclusive natures (expense, income, transfer)?
- Are transfers defined as non-income, non-expense events that do not affect net cash flow?
- Credit card flow:
  - Card swipes are expenses on the swipe date.
  - Card bill payments are transfers (bank to card), not a second expense — avoids double counting.
  - Refunds are negative expenses in the original category, not income.
  - Cashback and rewards are income.
- Is the cash / ATM model defined (ATM withdrawal as immediate expense vs. a tracked physical-cash account)?
- Are "savings capacity" (income − expenses) and "invested amount" (transfers to investment accounts) defined separately, not conflated?

### Indian financial and banking realities
- Statement ingestion:
  - Does it account for narration noise (UPI refs, merchant VPA handles, IMPS/NEFT reference numbers)?
  - Are multiple formats (CSV, XLS, password-protected PDF) scoped with clear parsing boundaries?
- Dates:
  - Are Indian date formats (`DD/MM/YYYY`) and posting-date vs. value-date handled?
  - Is the financial year (April 1 – March 31) considered for tax and annual reporting?
- Are reversals handled — merchant auto-refunds, failed UPI debits with a later credit, chargebacks?
- Transfer matching:
  - Is it a suggest-and-confirm workflow, not automatic blind merging?
  - Are matching tolerances stated (e.g. ±N days, exact amount match)?

### Data lifecycle, immutability, and mutability
- Are imported facts (date, amount, narration, account) strictly immutable?
- Is user interpretation (category, tags, notes) separated out and mutable?
- Is the manual quick-entry lifecycle specified (draft → matched with a statement row → merged or discarded)?
- Are categories and accounts retired via an active flag, not hard-deleted, to keep historical ledger integrity?

### Completeness
- Are user flows described end to end, not just the happy path?
- Are error cases, empty states, and failure modes covered (parse errors, duplicate-row warnings, unmatched transfers)?
- Are data sources named (bank statements, credit card statements, formats)?
- Are assumptions listed and separated from decided requirements?

### Multi-tenancy and security readiness
- The design must support multi-tenancy from day 1. Does the spec reflect that (tenant/`user_id` on every user-owned entity, even if single-user in MVP)?
- Is data ownership and isolation between users addressed, even if sharing comes later?
- Does the spec avoid storing sensitive unencrypted PII (full account numbers, card CVV/PIN, netbanking passwords)?
- Is the auth boundary defined without prematurely locking to one identity provider?

### Indian financial context (currency and reporting)
- Is INR the default? Is currency handling specified, even if INR is the only one today?
- Are Indian conventions covered where relevant (UPI, Indian banks, credit cards, date/number formats)?
- Are tax or financial-year concerns mentioned where they apply?

### Correctness and data integrity (financial app)
- Does the spec treat financial data as needing strong correctness and auditability?
- Is traceability of every transaction back to its source statement required?
- Is precision/rounding of money specified?

### UX latency and success criteria
- Are interaction targets specified (e.g. quick expense entry under ~10s, monthly import under ~5 min)?
- How will "done" be judged per feature? Are success criteria measurable and turnable into test cases?

### Decision log and trade-offs
- Does every major decision record the problem, options considered, the accepted compromise, and the rationale?

### Consistency
- Does the spec contradict itself anywhere?
- Does it match the goals in `AGENTS.md` and the current data model (`docs/DATA_MODEL.md`)?

## How to report

Group findings by severity. Skip a heading with nothing under it.
For each finding: state the problem, why it matters, and a concrete suggestion. Point to specific sections or lines.

- **Blocker** — contradiction, flawed financial math, missing multi-tenancy foundation, or an unworkable constraint. Must resolve before design/implementation.
- **High** — an underspecified edge case that will cause implementation bugs or silent data corruption.
- **Medium** — an ambiguous requirement or a missing UX/error state that needs clarification.
- **Low / Suggestion** — wording, cleanup, or future-proofing.

Use this report format:

```markdown
# Specification Review Report: [Document Name]

**Review Date**: [YYYY-MM-DD]
**Status**: [Approved | Approved with Comments | Changes Requested]

## Executive Summary
[2-4 sentences on document quality, completeness, and key risks.]

## Findings by Severity

### 🔴 Blockers
- **[Title]**: [Issue and exact risk.]
  - *Recommendation*: [Concrete action.]

### 🟠 High
- **[Title]**: [Issue and risk.]
  - *Recommendation*: [Concrete action.]

### 🟡 Medium
- **[Title]**: [Issue and risk.]
  - *Recommendation*: [Concrete action.]

### 🟢 Low / Suggestions
- **[Title]**: [Suggestion.]

## Checklist Verification Summary
| Area | Status | Notes |
| :--- | :--- | :--- |
| Financial Mental Model | [Pass/Fail/Partial] | [Note] |
| Indian Financial Realities | [Pass/Fail/Partial] | [Note] |
| Data Lifecycle & Mutability | [Pass/Fail/Partial] | [Note] |
| Multi-Tenancy Readiness | [Pass/Fail/Partial] | [Note] |
| Decision Log & Scope | [Pass/Fail/Partial] | [Note] |
```

## Style
Follow AGENTS.md §6: simple, direct English. Short sentences. No metaphors.
Distinguish facts, assumptions, and recommendations clearly.
