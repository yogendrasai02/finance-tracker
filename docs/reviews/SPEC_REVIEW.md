# Specification Review Report: SPEC.md

**Review Date**: 2026-08-23
**Target**: docs/SPEC.md (reviewed at Draft v0.2, fixes applied in Draft v0.3)
**Status**: Approved with Comments

## Executive Summary

The spec is strong and ready to build from.
The money model is correct: integer paise, three transaction types, transfers kept out of income and expense, card swipe as expense and bill payment as transfer.
The decision log is thorough and the phase lines are clear.
No blockers and no High findings remained after recheck.
The five Medium findings have been applied to SPEC v0.3.
A few Low findings stay open, and two data-model follow-ups are listed for the next review.

## How the two earlier "High" findings were graded down

The first review draft raised two High findings. Both were rechecked with the owner and lowered.

- **Default transaction type at import.**
  First framed as a double-counting risk. That was wrong.
  The spec and data model already keep a confirmed transfer out of income and expense, so there is no double count.
  The real gap is narrower: the spec did not define the type a row gets before review, and a one-sided transfer to Investments has no matcher to prompt it.
  Lowered to Medium and fixed in v0.3.

- **Rules set category, not type.**
  Not needed for MVP.
  The owner marks recurring investment transfers by hand, about ten rows a month, which fits the 5-minute import target.
  The data model already supports one-sided transfers, so auto-typing can be added later with no schema change.
  An LLM classifier could pick both category and type in one step, which removes the gap.
  Lowered to Low.

## Medium findings — applied to SPEC v0.3

| # | Finding | Fix in v0.3 |
| - | ------- | ----------- |
| M1 | Default type at import was undefined; one-sided transfers had nothing to prompt a review | §2.2 adds the **unclassified** state and the rule that one-sided transfers are found from the inbox. §5 says reports count only classified rows. FR-5 lists unclassified rows and one-sided transfers in the inbox. D-23 |
| M2 | Month completeness was not shown; card spend for a month is not final until its statement arrives | §5 adds a per-month completeness state and marks recent card spend provisional. FR-6 shows both. D-24 |
| M3 | Forex fee and GST rows had no category | §3 adds **Bank Charges & Fees** and says the markup and GST rows go there. D-25 |
| M4 | Refund month behavior was unclear | §2.3 states a refund is dated when credited, counted in that month, and a category total can go negative |
| M5 | Near-miss resolution steps were undefined | §4.1 and FR-5 add the two actions: keep as new, or treat as duplicate. D-26 |

## Low findings — still open

- **Rules set category, not type** (§6, FR-4): in MVP the owner marks transfers to Investments by hand. Consider a rule that marks a transfer, or an LLM classifier, later. Accepted MVP cost.
- **Transfer-match ranking** (§2.4, FR-4): matching on amount, sign, and time can suggest unrelated pairs. Confirm-first keeps data safe. When there is more than one candidate, rank with narration signals (self-name, shared RRN, exploration §5.3).
- **Sharing model not stated** (§1, §8): "closed-circle sharing" could mean isolated per-user data or a shared household view. The `user_id`-per-row design fits the first. One clarifying line would help.
- **UX targets need a measure** (§1, FR-3): say from which action to which action the 10-second and 5-minute targets are timed, so they can be tested.
- **"Transfers into the Investments account" wording** (§2.6): reads as rows inside Investments, but the model computes the figure from one-sided links. Align the wording.
- **FR-7 liability balance** (§8.3): the model enters a card balance as the amount owed; FR-7 does not say so. Add a one-line note.

## Resolved by the owner

- **Backfill amount.** Not a worry. The owner imports the current FY statements, which the FY-to-date exports supply in one file per account, then reviews and actions the rows. §4 and D-15 updated.

## Data-model follow-ups (for the data-model review)

The v0.3 spec changes need two matching changes in DATA_MODEL.md.

1. **Unclassified state.** `transactions.transaction_type` is currently `NOT NULL` with values EXPENSE, INCOME, TRANSFER. It must allow an unclassified state, either nullable or a fourth value. Check the composite foreign key `(category_id, transaction_type) → categories (id, kind)` still holds when the type is unset.
2. **Category seed count.** §8.1 seeds 16 expense categories. Adding **Bank Charges & Fees** makes it 17.

## Checklist Verification Summary

| Area | Status | Notes |
| :--- | :--- | :--- |
| Financial Mental Model | Pass (v0.3) | Money model correct; default type and one-sided transfers now defined. |
| Indian Financial Realities | Pass (v0.3) | Statement handling strong; forex/fee category and card-cycle completeness added. |
| Data Lifecycle & Mutability | Pass | Immutability, manual merge, and retire-not-delete are specified. |
| Multi-Tenancy Readiness | Pass | `user_id` and real auth from day 1. Sharing model worth one clarifying line. |
| Decision Log & Scope | Pass | Full decision log through D-26, explicit phase tags, protected non-goals. |
