# FinanceTracker — Product Specification (Phase 0)

Status: Draft v0.1 — pending confirmation of open items (see §9)
Last updated: 2026-08-19

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

Deferred account: **ICICI Amazon Pay CC** — out of MVP because its statements are PDF-only (D-17).
Until it enters scope, its bill payments are expenses in "ICICI Card Spends" (see §4), not transfers.

Explicitly **not** accounts:

- **UPI apps (GPay, PhonePe, Paytm)** — they are payment _channels_ over bank accounts, not places money sits.
  No wallet balances are maintained by the owner.
  At most, channel may appear as a transaction attribute derived from narration.
- **Cash** — see §2.5. No cash account in MVP.
- **EPF/VPF and anything deducted pre-salary-credit** — invisible until v2 (see D-09).

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
  - **Invested (month M)** = sum of transfers into the Investments account in M.

### 2.7 Reimbursements (v1.1, not MVP)

When the owner pays a group bill and is partially repaid: incoming repayments get **linked** to the original expense, and reports show the **net** amount as the owner's expense.
Requires the transaction-link mechanism (shared with transfer matching), so it ships immediately after MVP.
Until then, group-bill months read slightly inflated — known and accepted.

## 3. Categories

- **Flat taxonomy** (no hierarchy), owner-defined, target ≤ 18 expense categories.
- Every transaction gets exactly one category in MVP (no splits — see §7).
- Independent **Needs/Wants tag** on expense transactions (owner thinks in these terms).

Initial expense categories (owner's draft, to be refined during first imports):
Groceries & Vegetables · Eating Out · Office Food · Transport · Car & Maintenance · Travel & Vacation · Home · Utilities · Subscriptions · Clothes & Accessories · Healthy Lifestyle · Family · Seva · Cash Spends · ICICI Card Spends · Misc

Income categories: Salary · Interest · Cashback · Dividends · Freelance · Other Income

Merchant analytics are **not** user-facing in MVP; merchant/narration patterns exist internally only as inputs to categorization rules.

## 4. Statement import

- Sources (MVP): a single XLSX workbook (`statements/Bank_Account_Stmts_Curr_FY.xlsx` layout) with three sheets — SBI savings, HDFC savings, HDFC Millenia credit card.
- **ICICI Amazon Pay CC is out of MVP scope** — its statements are PDF-only, and PDF parsing is a subproject we defer (D-17).
  Since the card is not a tracked account in MVP, its bill payment from HDFC/SBI cannot be a transfer; instead it is an **expense in category "ICICI Card Spends"** — the same blunt model as cash (§2.5): an opaque but honest bucket, visible in reports, until the card enters scope.
- Import is **idempotent**: re-importing an overlapping period must not create duplicates.
  Exact dedupe strategy is decided after inspecting real statement columns (whether stable reference numbers / UTRs exist).
- Every import creates an **import batch** that preserves the raw rows verbatim — the audit trail from any transaction back to its source line must never break.
- **Manual-entry collision**: on import, the app suggests merges between manual entries and imported rows (same account, same amount, ±3 days); owner confirms.
  Confirmed merge keeps imported facts + manual interpretation (category, notes).
- Initial backfill: **3 months** of history; more later if desired.

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

Splits (one transaction, many categories — data model may anticipate it, UI later) · reimbursement netting (v1.1, §2.7) · budgets and alerts · bill reminders · net worth · investment holdings tracking (v2) · tax computation and FY views (v2) · multi-currency · receipts/attachments · group/Splitwise features · EMI handling · multi-user (see §8).

## 8. Security & architecture posture (day 1)

- Cloud-hosted DB; the app is internet-facing even in single-user phase, therefore: real authentication from day 1 (no "localhost trust"), TLS everywhere, secrets in a secret store (never in the repo), encryption at rest for the DB, no financial data or PII in logs.
- **Multi-tenancy readiness, not multi-tenancy**: every domain table carries `user_id` from day 1; MVP has exactly one user.
  Auth hardening, invites, and isolation testing happen in the closed-circle phase.
- Stack (decided in earlier discussion): React + TypeScript frontend, Spring Boot backend, PostgreSQL, monorepo.
  Migrations via Flyway. Financial logic covered by tests before UI polish.

## 9. Open items

| #   | Item                                                                                     | Status                            |
| --- | ---------------------------------------------------------------------------------------- | --------------------------------- |
| O-1 | Credit-card-as-liability-account model (§2.3)                                            | ✅ Confirmed by owner, 2026-08-19 |
| O-2 | Inspect real columns of the statement workbook (3 sheets) to choose dedupe keys          | Open — do during data-model step  |
| O-3 | Category list refinement (owner said "there are more") — can evolve during first imports | Open — not blocking               |

## 10. Decision log

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
| D-09 | **Net salary only in MVP.** EPF/VPF/TDS (pre-credit deductions) invisible until v2 investment tracking | Statements are the source of truth; documented so it is not forgotten |
| D-10 | Two savings metrics: capacity (income−expense) and invested (transfers to Investments)                | They answer different questions; owner wants both                     |
| D-11 | Calendar-month reporting                                                                                | Simpler, comparable periods                                           |
| D-12 | Flat categories (≤ ~18) + independent Needs/Wants tag                                                  | Owner thinks in these dimensions; hierarchy deferred                  |
| D-13 | Derived balances + manual reconciliation checkpoints                                                    | Savings picture + data-quality tripwire without perfection burden     |
| D-14 | Rules-based categorization first; LLM only if rules fall short (privacy revisit)                        | Local-first privacy posture                                           |
| D-15 | 3-month initial backfill                                                                                | Bounded categorization effort                                         |
| D-16 | `user_id` on all tables from day 1; single hard-coded user in MVP                                      | Multi-tenancy readiness without building auth features early          |
| D-17 | ICICI Amazon Pay CC out of MVP (PDF-only statements); its bill payments = "ICICI Card Spends" expense   | Defers PDF parsing; keeps reports honest via an opaque bucket         |
| D-18 | MVP functional scope frozen as §11 (FR-1 … FR-8)                                                        | Prevents scope creep during build                                     |

## 11. MVP functional requirements

Definition of done for MVP: _the owner imports 3 months of the three in-scope statements, categorizes them with rules + manual touch-up, and trusts the monthly dashboard numbers enough to act on them._

**FR-1 — Accounts.** The four accounts of §2.1 exist as seed data (no account-management UI).
Each shows a derived balance (§2.2, §5).

**FR-2 — Statement import.** Upload the XLSX workbook; parse the three sheet formats (SBI savings, HDFC savings, HDFC Millenia CC).
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
