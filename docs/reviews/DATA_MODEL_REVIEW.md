# Data Model Review Report: DATA_MODEL.md

**Review Date**: 2026-08-23
**Target**: docs/DATA_MODEL.md (reviewed at Draft v0.1, fixes applied in Draft v0.2)
**Status**: Approved with Comments

## Executive Summary

The model is sound and well-argued.
Money is integer paise, the sign rule is uniform, raw import rows are kept verbatim, immutability is enforced by triggers, and the dedup design matches the real files.
The review found two items to fix before migrations and several to harden.
All Highs and Mediums are now applied to v0.2.
A few Low items are left open by choice.

## High findings — applied to v0.2

### H1. Deleting a linked transaction was not handled

A card bill payment is a transfer, matched to the bank debit as a two-member link.
Two flows delete a transaction: a card statement replace (§6.3) and a manual merge (DM-13).
Neither said what happens to a link that used the deleted row.
A replace could leave a transfer link with one member, or block the replace outright.
A merge could delete a row a link still pointed at.

The two deletes needed opposite handling, because they mean different things.

- **Manual merge** keeps the same real event, so the link **moves** to the surviving imported row.
- **Card replace** discards the row, so the link is **dissolved** and the surviving side reset to `UNCLASSIFIED`, back to the inbox for re-matching.

Applied: `transaction_link_members.transaction_id` is `ON DELETE RESTRICT` so the database blocks any silent orphaning; the two service flows encode the correct intent; both are covered by tests.
See §5.4, §6.3, §7.1, §7.4, new §7.5, §10, DM-22. SPEC §4.1 and D-27 record the user-visible half.

### H2. `transaction_type` could not express the unclassified state

SPEC v0.3 needs an imported row that no rule classified to count as neither income nor expense until reviewed (D-23).
The column had only `EXPENSE`, `INCOME`, `TRANSFER` and read as `NOT NULL`.

Applied (owner chose an explicit value over a null): `transaction_type` gains `UNCLASSIFIED`, `NOT NULL`, default `UNCLASSIFIED`.
The composite category foreign key already stops such a row from carrying a category, since no category has that `kind`.
The value doubles as the reset target in H1.
See §5.1, §5.3, §5.5, DM-20.

## Medium findings — applied to v0.2

| # | Finding | Fix |
| - | ------- | --- |
| M1 | Cross-tenant references were not structurally prevented | Composite tenant-carrying foreign keys on every relationship; parents add `UNIQUE (user_id, id)`. §2.2, DM-23 |
| M2 | `ON DELETE` behavior was unspecified | Stated as policy: `RESTRICT` on ledger and reference links, `CASCADE` only for a join child with its parent. §2, DM-24 |
| M3 | `dismissed_matches` could store a duplicate pair | Added `UNIQUE (user_id, match_type, transaction_id_a, transaction_id_b)`, which also serves the exclusion lookup. §7.3, §9, DM-26 |
| M4 | No record of how a category was assigned | Added `category_source` (`RULE`/`MANUAL`) and `category_rule_id`. A rule change re-runs only over its own rows. §5.1, §8.2, DM-21. SPEC §6 records the user-visible rule |
| M5 | Category seed count was asserted in the doc | Owner input: categories are user data, not schema. §8.1 no longer states a count |

## Owner-requested change — applied

**Timestamps.** Every table now has `created_at`; a table whose rows change also has `updated_at`.
Added `updated_at` to `users`, `accounts`, `statement_imports`, `statement_import_rows`, `categories`, `balance_checkpoints`, and `created_at` to `transaction_link_members`.
See the §2 convention and DM-25.

## Low findings — open by choice

- **No currency column.** Amounts are assumed INR. Multi-currency is out of scope (SPEC §7), and forex rows keep the USD figure in `raw_cells`. A `currency` column stays a cheap future hedge, not built now.
- **Value date is dropped.** HDFC's `Value Dt` differs from the posting date in 2 of 361 rows. Reports use the posting date, and `raw_cells` keeps the original. Left as is.
- **Completeness query depends on `period_end`.** The new per-month completeness state (SPEC D-24) reads each bank import's coverage. `period_start`/`period_end` are nullable. Consider requiring them for `ROW_FINGERPRINT` accounts if the query needs it.

## Consistency with the spec

The model now matches SPEC v0.3, including the `UNCLASSIFIED` flow (D-23), the card-replace inbox return (D-27), and the rule re-apply rule (§6).
New decisions DM-20 through DM-26 record the changes above.

## Schema Verification Summary

| Area | Status | Notes |
| :--- | :--- | :--- |
| Precision & Money Types | Pass | `BIGINT` paise, uniform sign, correct temporal types. Currency assumed INR (Low, open). |
| Multi-Tenancy Scoping | Pass (v0.2) | Composite tenant-carrying foreign keys on every relationship. |
| Ledger Integrity & Raw Preservation | Pass (v0.2) | Raw rows verbatim, immutability by trigger, and the linked-delete path now defined. |
| Constraints & Foreign Keys | Pass (v0.2) | `UNCLASSIFIED` state, explicit `ON DELETE`, dismissed-pair uniqueness, paired category-source check. |
| Indexing Strategy | Pass | Indexes match access paths; deliberate omissions recorded. |
| Decision Log Completeness | Pass | DM-01 through DM-26 record problem, options, and rationale. |
