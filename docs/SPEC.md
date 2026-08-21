# FinanceTracker — Product Specification (Phase 0)

Status: Draft v0.2
Last updated: 2026-08-21

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
- Every transaction has one of three natures:
  - **Expense** — money left the owner's world.
  - **Income** — money entered the owner's world.
  - **Transfer** — money moved between the owner's own accounts.
    Transfers are **neither income nor expense** and must never appear in spend/income reports.

### 2.3 Credit card model

- A card swipe is an **expense on the swipe date**, in its category.
  "August spending" means what was swiped in August.
- The monthly **bill payment is a transfer** (bank → card). It is never an expense — the expense already happened at swipe time.
  This is what prevents double counting.
- A card's derived balance at any moment = the upcoming bill (a free feature).
- Refunds/reversals (e.g., a returned Amazon order) are **negative expenses in the original category** — August "Shopping" shrinks; refunds are not income.
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
Groceries & Vegetables · Eating Out · Office Food · Transport · Car & Maintenance · Travel & Vacation · Home · Utilities · Subscriptions · Clothes & Accessories · Healthy Lifestyle · Family · Seva · Cash Spends · ICICI Card Spends · Misc

Income categories: Salary · Interest · Cashback · Dividends · Freelance · Other Income

Merchant analytics are **not** user-facing in MVP; merchant/narration patterns exist internally only as inputs to categorization rules.

## 4. Statement import

- The single XLSX workbook with three sheets (SBI savings, HDFC savings, HDFC Millenia CC) is the **owner's manual collection convenience** — how the raw statements happen to be assembled today for understanding the data.
  It is **not** the app's ingestion contract.
  The app imports each bank's **native export directly** — SBI Savings, HDFC Savings and HDFC Millenia CC each as their own file, in the XLSX shape documented in [STATEMENT-DATA-EXPLORATION.md](STATEMENT-DATA-EXPLORATION.md) — not the combined workbook.
  One file per statement period per account; the CC's file is one statement cycle.
- **ICICI Amazon Pay CC is out of MVP scope** — its statements are PDF-only, and PDF parsing is a subproject we defer (D-17).
  Since the card is not a tracked account in MVP, its bill payment from HDFC/SBI cannot be a transfer; instead it is an **expense in category "ICICI Card Spends"** — the same blunt model as cash (§2.5): an opaque but honest bucket, visible in reports, until the card enters scope.
- Import is **idempotent**: re-importing an overlapping period must not create duplicates.
  Both bank exports are financial-year-to-date, so overlap occurs on the second import, not eventually.
  Strategy is §4.1.
- Every import creates an **import batch** that preserves the raw rows verbatim — the audit trail from any transaction back to its source line must never break.
- **Manual-entry collision**: on import, the app suggests merges between manual entries and imported rows (same account, same amount, ±3 days); owner confirms.
  Confirmed merge keeps imported facts + manual interpretation (category, notes).
- Initial backfill: **3 months** of history; more later if desired.

### 4.1 Deduplication and import integrity

No source provides a usable reference number: SBI's column is empty in every row and no export populates it, HDFC Savings' column is populated but not unique, and the credit card has no such column.
Row identity is therefore derived from row content, differently per source type.
Evidence for everything below is in [§6 of the exploration doc](STATEMENT-DATA-EXPLORATION.md).

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
- Balance model: **derived balances with reconciliation checkpoints** — balances are computed from transactions; the owner occasionally enters the real bank balance and the app surfaces any drift as a data-quality warning (missing imports).

## 6. Categorization automation

- MVP: **local rules engine only** — narration pattern → category (e.g., contains "SWIGGY" → Eating Out).
  Rules are user-editable data, not code.
- Phase 2: LLM-assisted categorization for narrations that no rule matches — only if the rule system proves insufficient.
  Sending narrations to an external LLM API is a privacy decision to revisit explicitly at that point, not a default.

## 7. Out of scope for MVP (explicit non-goals)

Splits (one transaction, many categories — data model may anticipate it, UI later) · reimbursement netting (v1.1, §2.7) · off-statement payroll contributions: EPF/VPF/ESPP/employer EPF (v1.1, §2.8 — designed, deliberately not built) · TDS and gross-salary reconstruction (v2, needs payslip import) · budgets and alerts · bill reminders · net worth · investment holdings tracking (v2) · tax computation and FY views (v2) · multi-currency · receipts/attachments · group/Splitwise features · EMI handling · multi-user (see §8).

## 8. Security & architecture posture (day 1)

- Cloud-hosted DB; the app is internet-facing even in single-user phase, therefore: real authentication from day 1 (no "localhost trust"), TLS everywhere, secrets in a secret store (never in the repo), encryption at rest for the DB, no financial data or PII in logs.
- **Multi-tenancy readiness, not multi-tenancy**: every domain table carries `user_id` from day 1; MVP has exactly one user.
  Auth hardening, invites, and isolation testing happen in the closed-circle phase.
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
| D-15 | 3-month initial backfill                                                                                | Bounded categorization effort                                         |
| D-16 | `user_id` on all tables from day 1; single hard-coded user in MVP                                      | Multi-tenancy readiness without building auth features early          |
| D-17 | ICICI Amazon Pay CC out of MVP (PDF-only statements); its bill payments = "ICICI Card Spends" expense   | Defers PDF parsing; keeps reports honest via an opaque bucket         |
| D-18 | MVP functional scope frozen as §10 (FR-1 … FR-8)                                                        | Prevents scope creep during build                                     |
| D-19 | **Model decided, delivery deferred to v1.1.** EPF/VPF/ESPP/employer-EPF will be off-statement transfers Payroll → Investments (§2.8). Salary income stays the net credit; TDS stays out of scope. MVP builds none of it | Investments must eventually reflect true total invested, and a virtual counterparty leaves the statement ledger and its balance chain untouched — but MVP stays small. Recorded now so the reasoning is not re-derived later |
| D-20 | Dedupe identity: bank rows keyed on `(user, account, date, signed amount, normalized narration, balance after)`; cards keyed per statement with whole-batch replacement (§4.1). Enforced by a DB unique constraint on a versioned fingerprint | No source has a usable reference number. Measured: each real file breaks a different shorter key; the full tuple is unique across all 482 rows |
| D-21 | An import batch commits only if its balance chain reconciles internally and joins onto the stored history; near-misses go to review, never auto-resolve (§4.1) | Turns import from plausible into provable, and catches missing statement periods for free. Verified 480/480 links on real data |
| D-22 | Import ingests each bank's native XLSX export directly, one file per account per statement period — not the combined manually-assembled workbook (§4) | The combined workbook is a personal convenience with a different, lossier shape; §4.1's dedupe design is already built against the native columns |

## 10. MVP functional requirements

Definition of done for MVP: _the owner imports 3 months of the three in-scope statements, categorizes them with rules + manual touch-up, and trusts the monthly dashboard numbers enough to act on them._

**FR-1 — Accounts.** The four accounts of §2.1 exist as seed data (no account-management UI).
Each shows a derived balance (§2.2, §5).

**FR-2 — Statement import.** Upload each bank's native statement export for the three in-scope accounts (SBI savings, HDFC savings, HDFC Millenia CC), per §4.
Import is idempotent across overlapping re-imports; every import creates a batch preserving raw rows; each transaction is traceable to its source row.
A post-import summary shows counts: new / duplicate / needs-attention.

**FR-3 — Manual quick entry.** Add a transaction (account, amount, date, category, needs/wants, note) in under 10 seconds.
Manual entries are editable; on later import, the app suggests merges with matching imported rows (same account + amount, ±3 days), confirm-first.

**FR-4 — Transfer matching.** A suggestion queue proposes transfer pairs (equal amount, opposite direction, ±3 days) across accounts — including CC bill payments.
Owner confirms or rejects; confirmed pairs are linked and excluded from income/expense reports.
Unmatched sides can also be manually marked as one-sided transfers (e.g., to Investments).

**FR-5 — Categorization.** Every expense gets exactly one flat category and an optional Needs/Wants tag.
A user-editable rules engine (narration pattern → category) applies on import.
A "needs review" inbox lists uncategorized/unmatched transactions; MVP succeeds when this inbox reaches zero for a month within minutes, not hours.

**FR-6 — Dashboard.** Per calendar month: category-wise expenses (primary view), income vs expense, savings capacity, and invested amount (§2.6).
Month navigation; no custom date ranges in MVP.

**FR-7 — Reconciliation checkpoints.** Owner enters an account's real balance as of a date; the app shows drift vs the derived balance as a data-quality warning.

**FR-8 — Authentication.** Real login from day 1 (app is internet-facing) for a single seeded user.
No registration, invites, or roles in MVP.
