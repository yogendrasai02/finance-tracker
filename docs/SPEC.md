# FinanceTracker — Product Specification (Phase 0)

Status: Draft v0.4
Last updated: 2026-08-23

## 1. Purpose

A personal finance tracker for expenses and savings, built for the Indian financial context (INR, UPI, Indian banks and credit cards).

Primary goals, in order:

1. **Personal use** — tailored to the owner's real accounts, statements, and habits.
2. **Portfolio project** — public code on GitHub demonstrating strong engineering.
3. **Closed-circle sharing (later)** — friends/family/colleagues, invite-only.
   Not MVP, but the design must not paint us into a corner (multi-tenancy readiness, security from day 1).

The retention bar that decides every UX choice: **capturing a manual expense takes under 10 seconds; a monthly statement import takes under 5 minutes.**

## 2. Financial mental model

This section is the constitution of the app.
Code must not contradict it.

### 2.1 Accounts

An **account** is a place money sits or a debt that must be repaid.

| Account       | Type      | Notes                                                                                       |
| ------------- | --------- | ------------------------------------------------------------------------------------------- |
| HDFC Savings  | asset     | Salary account; primary UPI source                                                          |
| SBI Savings   | asset     | Secondary savings; occasional UPI                                                           |
| HDFC Millenia | liability | Credit card — balance is money owed                                                         |
| Investments   | virtual   | Black box: money that left for FD/RD/MF/PPF/NPS/stocks/ESPP. No holdings tracking until v2. |

Deferred account: **Payroll** (virtual, hidden) — contra-source for compensation that never reaches a bank account.
Designed in §2.8, ships in v1.1. Not seeded in MVP.

Deferred account: **ICICI Amazon Pay CC** — out of MVP because its statements are PDF-only (D-17).
Until it enters scope, its bill payments are expenses in "ICICI Card Spends" (see §4), not transfers.

Explicitly **not** accounts:

- **UPI apps (GPay, PhonePe, Paytm)** — they are payment _channels_ over bank accounts, not places money sits.
  No wallet balances are maintained by the owner.
  At most, channel may appear as a transaction attribute derived from narration.
- **Cash** — see §2.5. No cash account in MVP.
- **EPF/VPF/ESPP and anything deducted pre-salary-credit** — not bank accounts, and not importable.
  **Invisible in MVP** (D-09); recorded as off-statement transfers into Investments from v1.1 (§2.8, D-19).

### 2.2 Transactions

- A transaction belongs to exactly one account and has a signed amount.
- **Amounts are stored as integer paise.** Never floats, anywhere, ever.
- **Financial facts are immutable** for imported transactions: date, amount, narration, account, source reference.
  What is editable is the _interpretation layer_: category, needs/wants tag, notes, links.
- Manual entries are fully editable until matched/merged with an imported row; after merge, the imported facts win.
- Every classified transaction has exactly one nature:
  - **Expense** — money left the owner's world.
  - **Income** — money entered the owner's world.
  - **Transfer** — money moved between the owner's own accounts.
    Transfers are **neither income nor expense** and must never appear in spend/income reports.
- **Nature is assigned, not assumed.**
  On import the categorization rules (§6) set the nature together with the category.
  A row that no rule classifies is left **unclassified**: it counts as neither income nor expense and stays in the review inbox (FR-5) until the owner sets its nature.
  This keeps a dashboard number from ever being silently wrong — at most it is incomplete.
- **One-sided transfers are found from the inbox, not the matcher.**
  The transfer matcher (§2.4) only proposes two-sided pairs.
  A transfer with one bank side and a virtual counterparty — a SIP into Investments — has no second row to match, so the owner marks it from the review inbox.

### 2.3 Credit card model

- A card swipe is an **expense on the swipe date**, in its category.
  "August spending" means what was swiped in August.
- The monthly **bill payment is a transfer** (bank → card). It is never an expense — the expense already happened at swipe time.
  This is what prevents double counting.
- A card's derived balance at any moment = the upcoming bill (a free feature).
- Refunds/reversals (e.g., a returned Amazon order) are **negative expenses in the original category**, dated when the credit lands — refunds are not income.
  The negative reduces that category in the month the refund is credited, which may be later than the purchase month, so a month's category total can go negative.
- **Cashback and reward redemptions are income**, category "Cashback".
- EMIs and pay-later products: **out of scope** (owner does not use them).
  If one ever appears, each statement installment is simply an expense as it appears.

### 2.4 Transfer matching

- A real-world transfer produces two statement rows (debit in one account, credit in another).
  The app **suggests** matches (same amount, opposite signs, ±3 days) and the owner **confirms**; confirmed rows are linked as one logical transfer.
- **Confirm-first, never auto-link.** A wrong automatic link silently corrupts reports; a suggestion queue costs seconds.

### 2.5 Cash

Blunt model (accepted trade-off): an **ATM withdrawal is an expense**, category "Cash Spends", at withdrawal time.
Individual cash purchases are not tracked and there is no cash balance.
Consequence: manual quick-entry exists for bank/CC transactions (captured before their statement arrives), not for cash.

### 2.6 Income and savings

- Income sources in scope for MVP: **net salary credit** (HDFC), **bank interest**, **cashback**, **dividends and other credits that land in the bank**, rare freelance.
- Investment _instruments_ (FD, RD, MF, PPF, NPS, stocks, ESPP) are destinations, not income sources.
  Money moving to them is a **transfer to the Investments account**.
  Returns only count when they land back in a bank account as a credit.
- Two savings metrics, both shown, deliberately different:
  - **Savings capacity (month M)** = income in M − expenses in M.
    Statement-derived and fully verifiable.
    It answers "of the money that actually reached my bank, how much could I have saved?"
  - **Invested (month M)** = sum of transfers into the Investments account in M.
    In MVP this is real bank outflows only.

- **Known MVP understatement:** because EPF/VPF/ESPP never reach the bank, MVP's "Invested" is materially lower than the owner's true saving.
  Accepted for MVP, fixed in v1.1 by §2.8.
  Until then the dashboard should not present "Invested" as a complete savings picture.

### 2.7 Reimbursements (v1.1, not MVP)

When the owner pays a group bill and is partially repaid: incoming repayments get **linked** to the original expense, and reports show the **net** amount as the owner's expense.
Requires the transaction-link mechanism (shared with transfer matching), so it ships immediately after MVP.
Until then, group-bill months read slightly inflated — known and accepted.

### 2.8 Off-statement payroll contributions (v1.1, not MVP)

A material share of the owner's saving never passes through a bank account.
EPF, VPF and ESPP are deducted before the salary credit; employer EPF is never part of the owner's salary at all.
The statement shows only the net credit, so none of this is importable — but leaving it out understates the true savings rate by more than half.

**MVP does not track any of this.** The model below is recorded now so the reasoning is not lost and so the MVP data model does not foreclose it; it ships in v1.1.
Nothing in this section is built for MVP.

The design: these contributions are recorded as **transfers from a virtual Payroll account into the virtual Investments account**.

Rules (v1.1):

- **The salary credit is never grossed up.** Salary income is exactly the net amount imported from the statement.
- **No bank or card account is touched.** The counterparty is the virtual Payroll account, so derived balances, the running-balance chain, and the FR-7 reconciliation checkpoints are entirely unaffected.
- They are **transfers** (§2.2) and so are excluded from income and expense reports, exactly like any other transfer.
- Each carries a **component**: `EPF_EMPLOYEE`, `VPF`, `ESPP`, or `EPF_EMPLOYER`.
  These are different instruments with different tax treatment, liquidity, and (in v2) different holdings modelling — a single blended figure would have to be unpicked later.
- Each is tagged with provenance `OFF_STATEMENT`, distinct from both `IMPORTED` and ordinary manual entries.
  Any report that mixes them with statement-derived figures must be able to show the split.
- They are **never candidates for transfer matching (§2.4) or import merge (§4)**.
  There is no second side to find, and leaving them in the suggestion queue would fill it with proposals that can never be resolved.

Known limitations, accepted:

- **The amounts are self-declared and no arithmetic check can verify them.**
  Unlike every other transaction, a typo here is invisible to the balance chain.
  Mitigation is a variance warning: flag a component that differs from the previous month by more than a set threshold.
  A proper cross-check against the EPFO passbook is a v2 reconciliation-checkpoint concern.
- **ESPP is booked on the deduction date, not the purchase date.**
  The real purchase happens later, at a discount, in a quantity the payroll deduction does not state.
  This is harmless while Investments is a black box with no holdings; it must be revisited when v2 introduces cost basis, capital gains and Schedule FA reporting for foreign-listed shares.
- **Employer EPF is money the owner never chose to save.**
  It is genuinely theirs and belongs in Investments, but it inflates any "savings discipline" reading.
  The component tag exists so it can be excluded from such a metric.

**What MVP must not foreclose.**
Only two things, both cheap and both already implied by the MVP model:

1. A transaction's counterparty account may be virtual, so a transfer can have no bank side.
2. A transaction carries a **provenance** field distinguishing imported from manually created rows.

MVP needs both anyway — the Investments account is already virtual (§2.1), and FR-3 already creates manual rows alongside imported ones.
No extra MVP work is implied by this section.

## 3. Categories

- **Flat taxonomy** (no hierarchy), owner-defined, target ≤ 18 expense categories.
- Every transaction gets exactly one category in MVP (no splits — see §7).
- Independent **Needs/Wants tag** on expense transactions (owner thinks in these terms).

Initial expense categories (owner's draft, to be refined during first imports):
Groceries & Vegetables · Eating Out · Office Food · Transport · Car & Maintenance · Travel & Vacation · Home · Utilities · Subscriptions · Clothes & Accessories · Healthy Lifestyle · Family · Seva · Bank Charges & Fees · Cash Spends · ICICI Card Spends · Misc

A foreign transaction lands as several INR rows: the purchase, a markup fee, and GST rows (exploration §5.9).
The markup fee and GST rows are ordinary expenses in **Bank Charges & Fees**; the purchase keeps its normal category.
MVP does not join these rows back into one event.

Income categories: Salary · Interest · Cashback · Dividends · Freelance · Other Income

Merchant analytics are **not** user-facing in MVP; merchant/narration patterns exist internally only as inputs to categorization rules.

## 4. Statement import

- The single XLSX workbook with three sheets (SBI savings, HDFC savings, HDFC Millenia CC) is the **owner's manual collection convenience** — how the raw statements happen to be assembled today for understanding the data.
  It is **not** the app's ingestion contract.
  The app imports each bank's **native export directly** — SBI Savings, HDFC Savings and HDFC Millenia CC each as their own file, in the XLSX shape documented in [STATEMENT_DATA_EXPLORATION.md](STATEMENT_DATA_EXPLORATION.md) — not the combined workbook.
  One file per statement period per account; the CC's file is one statement cycle.
- **ICICI Amazon Pay CC is out of MVP scope** — its statements are PDF-only, and PDF parsing is a subproject we defer (D-17).
  Since the card is not a tracked account in MVP, its bill payment from HDFC/SBI cannot be a transfer; instead it is an **expense in category "ICICI Card Spends"** — the same blunt model as cash (§2.5): an opaque but honest bucket, visible in reports, until the card enters scope.
- Import is **idempotent**: re-importing an overlapping period must not create duplicates.
  Both bank exports are financial-year-to-date, so overlap occurs on the second import, not eventually.
  Strategy is §4.1.
- Every import creates an **import batch** that preserves the raw rows verbatim — the audit trail from any transaction back to its source line must never break.
- **Manual-entry collision**: on import, the app suggests merges between manual entries and imported rows (same account, same amount, ±3 days); owner confirms.
  Confirmed merge keeps imported facts + manual interpretation (category, notes).
- Initial backfill: the **current financial year's** statements — the FY-to-date exports already supply the whole year in one file per bank account — reviewed and actioned by the owner. More history later if desired.
- **An uploaded statement is untrusted input on an internet-facing endpoint.**
  Size, sheet, row and column caps, zip and XML hardening, content-based `.xlsx`-only acceptance and a parse timeout are product requirements, not parser implementation details — they are specified in [SECURITY.md](SECURITY.md) §4 and the parser design must implement them (D-28).
  The uploaded file itself is **not retained** after its rows are stored: `statement_import_rows.raw_cells` is the audit copy and `file_sha256` the identity (D-29).
  Only the transaction table is stored. The statement's header block — account holder name, account number, CIF/customer ID, IFSC/MICR, credit limits — is read for validation and never persisted (DM-27).

### 4.1 Deduplication and import integrity

No source provides a usable reference number: SBI's column is empty in every row and no export populates it, HDFC Savings' column is populated but not unique, and the credit card has no such column.
Row identity is therefore derived from row content, differently per source type.
Evidence for everything below is in [§6 of the exploration doc](STATEMENT_DATA_EXPLORATION.md).

**Bank accounts (SBI, HDFC Savings) — content key including the running balance.**

Identity is the tuple:

```
(user_id, account_id, txn_date, signed_amount_paise, narration_normalized, balance_after_paise)
```

All six fields are required.
`(date, amount, narration)` collides on SBI's twice-monthly identical SIP debits; `(date, amount, balance)` collides on HDFC.
Each real file breaks a different shorter key, and the full tuple is unique across every row of both.

`narration_normalized` is the raw narration with SBI's hard line-wrap removed (delete `\n` and the single space following it — never substitute a space, which corrupts wrapped tokens) and whitespace collapsed.

**Credit cards — statement-scoped identity.**

Statement cycles are disjoint, so the same transaction never appears in two statements.
The only possible overlap is re-uploading the same statement file, which is a **batch-level** concern, not a row-level one.
Identity is `(user_id, account_id, statement_date, ordinal_within_statement)`.
Re-uploading an already-imported `(account, statement_date)` offers to **replace** the batch, never to merge rows into it.
Ordinal fragility does not matter because batches are replaced whole.
Replacing a batch deletes its rows, so any matched transfer that used one of those rows is returned to the review inbox; the owner re-confirms it against the new batch (D-27).

**Storage and enforcement.**

- The normalized components are stored as ordinary columns, and a `source_row_fingerprint` (hash of their canonical concatenation) carries a `UNIQUE` constraint.
  Components stay visible for debugging and migration; the fingerprint keeps the index compact.
- A `fingerprint_version` column records which normalization produced the value.
  Changing the normalizer is then an explicit, testable backfill — never a silent divergence in which old and new rows stop matching each other.
  Fingerprints from different versions are never compared without recomputation.
- **Enforced in the database, not only in the application.** The unique constraint is the backstop that survives retries, double-clicked uploads, and concurrent imports.
  The application additionally pre-classifies rows to produce FR-2's new / duplicate / needs-attention counts, but a unique violation is treated as "duplicate", not as an error.

**Near-misses are never resolved automatically.**
A row matching an existing one on `(account, date, amount, balance)` but differing in normalized narration is neither inserted nor silently updated.
It goes to the needs-review inbox (FR-5) showing both versions.
It is either a bank restatement of the narration or a genuinely distinct transaction, and guessing wrong either loses a real transaction or corrupts an audit trail.
The owner resolves it by choosing **keep as a new transaction** or **treat as a duplicate** of the existing row.

**Batch-level integrity gate (bank accounts).**
A batch commits only if its balance chain reconciles:

- every internal link satisfies `balance[i] == balance[i-1] + amount[i]`, and
- the batch's first row chains onto the last stored row for that account, or is that account's first row ever.

A broken internal link means a parse error.
A broken join means a missing statement period.
Either way the batch is held for review rather than committed.
This was verified to hold across 480 of 480 links in the real files, and it is what makes an import provable rather than merely plausible.

For credit cards the equivalent gate is the statement's own Account Summary block, whose `Purchases/Debits` and `Payment/Credit` figures the parsed rows must reproduce — allowing for HDFC rounding `Total Amount Due` to whole rupees.

## 5. Reporting

- Reporting period: **calendar month**. No salary-cycle months, no financial-year views in MVP (FY Apr–Mar is v2).
- MVP dashboard, in priority order:
  1. **Category-wise expenses for a month** (the #1 requested view).
  2. Income vs expense per month.
  3. Savings capacity and Invested (§2.6) per month.
- **Reports count only classified rows.** An unclassified row (§2.2) is in neither the income nor the expense total. It stays in the review inbox until the owner sets its nature.
- **Each month shows a completeness state.** A month is complete when every in-scope account has imports covering the whole month and no held (§4.1) or near-miss rows affect it. Until then the dashboard marks the month incomplete.
- **Card spend for a month is provisional** until the statement covering the month end is imported, which lands about mid-next-month. Manual quick entry (FR-3) fills the gap before then.
- Balance model: **derived balances with reconciliation checkpoints** — balances are computed from transactions; the owner occasionally enters the real bank balance and the app surfaces any drift as a data-quality warning (missing imports).

## 6. Categorization automation

- MVP: **local rules engine only** — narration pattern → category (e.g., contains "SWIGGY" → Eating Out).
  Rules are user-editable data, not code.
- Editing a rule re-applies to the rows that rule set, never to categories the owner set by hand.
  Each row records whether its category came from a rule or from the owner, so a rule change never overwrites a manual choice.
- Phase 2: LLM-assisted categorization for narrations that no rule matches — only if the rule system proves insufficient.
  Sending narrations to an external LLM API is a privacy decision to revisit explicitly at that point, not a default.

## 7. Out of scope for MVP (explicit non-goals)

Splits (one transaction, many categories — data model may anticipate it, UI later) · reimbursement netting (v1.1, §2.7) · off-statement payroll contributions: EPF/VPF/ESPP/employer EPF (v1.1, §2.8 — designed, deliberately not built) · TDS and gross-salary reconstruction (v2, needs payslip import) · budgets and alerts · bill reminders · net worth · investment holdings tracking (v2) · tax computation and FY views (v2) · multi-currency · receipts/attachments · group/Splitwise features · EMI handling · multi-user (see §8).

## 8. Security & architecture posture (day 1)

- Cloud-hosted DB; the app is internet-facing even in single-user phase, therefore: real authentication from day 1 (no "localhost trust"), TLS everywhere, secrets in a secret store (never in the repo), encryption at rest for the DB, no financial data or PII in logs.
- **[SECURITY.md](SECURITY.md) is the normative security document (D-30).**
  It classifies what this app stores and turns each line above into numbered, testable requirements (`SR-nn`): tenant isolation, statement-upload and parser safety, logging and redaction, auth and session constraints, secrets, encryption, retention and deletion, error handling.
  Its §14 lists what a production deployment must have in place.
  Where this section and SECURITY.md appear to disagree, SECURITY.md governs.
- **The data is unusually sensitive.**
  Statements carry the owner's financial PII and, inside narrations, **third parties' names, phone numbers and UPI VPAs**.
  Both are first-class assets. This is why narrations never reach logs and why sending them to an external LLM (§6) is an explicit decision rather than a default.
- **Multi-tenancy readiness, not multi-tenancy**: every domain table carries `user_id` from day 1; MVP has exactly one user.
  Invites and roles wait for the closed-circle phase.
  **Isolation does not wait.**
  Tenant scoping is structural from the first query, Row-Level Security is on from the first migration (DM-30), and the two-user IDOR test suite is written during MVP.
  Every query written now is inherited by the closed-circle phase, so deferring this would mean re-auditing all of them later.
- **A full-tenant erasure path must exist before a second person's data is held (D-32).**
  The permanent retention of third-party PII in raw rows is re-examined at the same point.
  Neither is MVP work; both are gates on the closed-circle phase rather than open-ended deferrals.
- Stack (decided in earlier discussion): React + TypeScript frontend, Spring Boot backend, PostgreSQL, monorepo.
  Migrations via Flyway. Financial logic covered by tests before UI polish.

## 9. Decision log

| ID   | Decision                                                                                                | Rationale                                                              |
| ---- | -------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------- |
| D-01 | Web-first responsive app; no native mobile in MVP                                                      | Iteration speed, no store friction, portfolio visibility              |
| D-02 | React+TS / Spring Boot / Postgres monorepo                                                             | Owner's stack → better code review; portfolio depth                   |
| D-03 | Credit cards are liability accounts; swipe = expense, bill payment = transfer                          | Prevents double counting; enables per-month category accuracy         |
| D-04 | UPI apps are channels, not accounts                                                                    | No wallet balances held                                                |
| D-05 | Transfers are neither income nor expense; confirm-first matching                                       | Report correctness; no silent auto-links                              |
| D-06 | Refunds = negative expense in original category; cashback = income                                     | Matches owner's mental model                                          |
| D-07 | No cash account; ATM withdrawal = "Cash Spends" expense                                                | Honest about untracked cash; zero bookkeeping burden                  |
| D-08 | Amounts in integer paise; imported financial facts immutable; interpretation editable                  | Financial correctness, auditability                                   |
| D-09 | **Net salary only in MVP.** EPF/VPF/ESPP/TDS (pre-credit deductions) invisible in MVP. Revisited for saving — not income — by D-19 | Statements are the source of truth; keeps MVP small. The understatement it causes is documented in §2.6 |
| D-10 | Two savings metrics: capacity (income−expense) and invested (transfers to Investments)                | They answer different questions; owner wants both                     |
| D-11 | Calendar-month reporting                                                                                | Simpler, comparable periods                                           |
| D-12 | Flat categories (≤ ~18) + independent Needs/Wants tag                                                  | Owner thinks in these dimensions; hierarchy deferred                  |
| D-13 | Derived balances + manual reconciliation checkpoints                                                    | Savings picture + data-quality tripwire without perfection burden     |
| D-14 | Rules-based categorization first; LLM only if rules fall short (privacy revisit)                        | Local-first privacy posture                                           |
| D-15 | Backfill is the current FY, supplied by the FY-to-date exports and actioned by the owner                | The export already covers the year; no need to cap it at 3 months     |
| D-16 | `user_id` on all tables from day 1; single hard-coded user in MVP                                      | Multi-tenancy readiness without building auth features early          |
| D-17 | ICICI Amazon Pay CC out of MVP (PDF-only statements); its bill payments = "ICICI Card Spends" expense   | Defers PDF parsing; keeps reports honest via an opaque bucket         |
| D-18 | MVP functional scope frozen as §10 (FR-1 … FR-8)                                                        | Prevents scope creep during build                                     |
| D-19 | **Model decided, delivery deferred to v1.1.** EPF/VPF/ESPP/employer-EPF will be off-statement transfers Payroll → Investments (§2.8). Salary income stays the net credit; TDS stays out of scope. MVP builds none of it | Investments must eventually reflect true total invested, and a virtual counterparty leaves the statement ledger and its balance chain untouched — but MVP stays small. Recorded now so the reasoning is not re-derived later |
| D-20 | Dedupe identity: bank rows keyed on `(user, account, date, signed amount, normalized narration, balance after)`; cards keyed per statement with whole-batch replacement (§4.1). Enforced by a DB unique constraint on a versioned fingerprint | No source has a usable reference number. Measured: each real file breaks a different shorter key; the full tuple is unique across all 482 rows |
| D-21 | An import batch commits only if its balance chain reconciles internally and joins onto the stored history; near-misses go to review, never auto-resolve (§4.1) | Turns import from plausible into provable, and catches missing statement periods for free. Verified 480/480 links on real data |
| D-22 | Import ingests each bank's native XLSX export directly, one file per account per statement period — not the combined manually-assembled workbook (§4) | The combined workbook is a personal convenience with a different, lossier shape; §4.1's dedupe design is already built against the native columns |
| D-23 | Imported rows not classified by a rule are **unclassified**: excluded from income and expense, held in the review inbox until the owner sets their nature | A money number is never silently wrong, only incomplete. One-sided transfers to Investments have no matcher, so the inbox is where they are caught |
| D-24 | Each month carries a completeness state; card spend is provisional until the statement covering the month end is imported | Card cycles are not calendar months, so a recent month's card spend is not final. The owner must know when a number is safe to act on |
| D-25 | Add a **Bank Charges & Fees** expense category; forex markup and GST rows go there | These INR rows appear on the first import and had no home; Misc would hide them |
| D-26 | A near-miss (§4.1) is resolved by the owner as keep-as-new or treat-as-duplicate | Guessing either way loses a transaction or corrupts the audit trail |
| D-27 | Replacing a statement batch returns any matched transfer on the replaced rows to the review inbox for re-confirmation | The replaced card row is gone, so the old link is stale; the owner re-matches against the new batch rather than trusting a broken link |
| D-28 | Statement upload and parsing enforce the [SECURITY.md](SECURITY.md) §4 limits: 5 MB cap, zip and XML hardening (inflate ratio, entry count, DTD/XXE off), exactly 1 sheet, 10,000 rows, 50 columns, 30 s timeout off the request thread, content-based `.xlsx`-only acceptance rejecting macros. The parser design note implements these, adjusting numbers only with recorded rationale | The import endpoint is untrusted, internet-facing input into a zip-of-XML parser. Each missing limit is a concrete crash or exfiltration vector. The single-sheet cap also makes the security limit and the parser's contract one rule, so a two-sheet export fails loudly instead of silently reading the wrong sheet |
| D-29 | The uploaded file is not retained after its rows are stored; `raw_cells` is the audit copy and `file_sha256` the identity. The multipart threshold is set above the size cap so an accepted file never reaches disk, with a private `0700` spill directory as backstop | Avoids a second, unmanaged copy of the most sensitive data. Spring writes uploads to a temp file by default (`file-size-threshold` is `0B`), so this needs a configuration decision, not merely a decision to write no saving code. FR-2's traceability is already met by `statement_import_rows` |
| D-30 | [SECURITY.md](SECURITY.md) is normative; §8 references it. Its data classification and logging rules replace the one-line logging statement, and the log-capture test is part of definition-of-done. Postgres `DETAIL` logging is permitted in local development and refused from the first deployment onwards, with redaction as the default and a startup assertion under the production profile | One line is an intention; classification plus whitelist logging plus a test is a control. The development exception has real value while building the importer, and it holds only because forgetting to configure anything gives the safe result. Consequence: development logs contain real third-party PII and are themselves restricted |
| D-31 | The FR-8 auth mechanism, when chosen, must satisfy SECURITY.md §6 (SR-35 … SR-41) | Keeps the mechanism genuinely open while closing the defaults that are expensive to undo — a token in browser storage, an unauthenticated dev profile, or a seeded password inside a Flyway migration |
| D-32 | A full-tenant erasure path — every row for a `user_id`, including raw rows and replaced imports, with a documented trigger exception for whole-tenant purge — must be designed and built before any second user exists. Raw-row retention for third-party PII is re-examined at the same gate | Erasure duties attach the moment another person's data is held. Retrofitting deletion into a `RESTRICT`-everywhere, trigger-guarded schema is exactly the work that gets skipped under time pressure, so it is a gate rather than a backlog item |

## 10. MVP functional requirements

Definition of done for MVP: _the owner imports the three in-scope statements for the current FY to date, categorizes them with rules + manual touch-up, and trusts the monthly dashboard numbers enough to act on them._

**FR-1 — Accounts.** The four accounts of §2.1 exist as seed data (no account-management UI).
Each shows a derived balance (§2.2, §5).

**FR-2 — Statement import.** Upload each bank's native statement export for the three in-scope accounts (SBI savings, HDFC savings, HDFC Millenia CC), per §4.
Import is idempotent across overlapping re-imports; every import creates a batch preserving raw rows; each transaction is traceable to its source row.
A post-import summary shows counts: new / duplicate / needs-attention.
Uploads are authenticated, rate-limited, and subject to the [SECURITY.md](SECURITY.md) §4 limits; the file itself is not retained after its rows are stored (D-28, D-29).

**FR-3 — Manual quick entry.** Add a transaction (account, amount, date, category, needs/wants, note) in under 10 seconds.
Manual entries are editable; on later import, the app suggests merges with matching imported rows (same account + amount, ±3 days), confirm-first.

**FR-4 — Transfer matching.** A suggestion queue proposes transfer pairs (equal amount, opposite direction, ±3 days) across accounts — including CC bill payments.
Owner confirms or rejects; confirmed pairs are linked and excluded from income/expense reports.
Unmatched sides can also be manually marked as one-sided transfers (e.g., to Investments).

**FR-5 — Categorization.** Every expense gets exactly one flat category and an optional Needs/Wants tag.
A user-editable rules engine (narration pattern → category) applies on import and sets the nature (§2.2) with the category.
A "needs review" inbox lists uncategorized/unclassified rows, one-sided transfers still to be marked, and import near-misses (§4.1).
For a near-miss the owner picks **keep as a new transaction** or **treat as a duplicate** of the existing row.
MVP succeeds when this inbox reaches zero for a month within minutes, not hours.

**FR-6 — Dashboard.** Per calendar month: category-wise expenses (primary view), income vs expense, savings capacity, and invested amount (§2.6).
Each month shows a completeness state (§5), and card spend is marked provisional until its covering statement is imported.
Month navigation; no custom date ranges in MVP.

**FR-7 — Reconciliation checkpoints.** Owner enters an account's real balance as of a date; the app shows drift vs the derived balance as a data-quality warning.

**FR-8 — Authentication.** Real login from day 1 (app is internet-facing) for a single seeded user.
No registration, invites, or roles in MVP.
The mechanism is still unchosen, and choosing it is its own design step.
Whatever is chosen must satisfy the constraints in [SECURITY.md](SECURITY.md) §6 (D-31): TLS on both hops, the session in an `HttpOnly`/`Secure`/`SameSite` cookie rather than browser storage, server-side expiry and logout, rate-limited login with uniform failure messages, the seeded credential bootstrapped from the environment rather than written into a migration, CSRF protection and strict CORS.
