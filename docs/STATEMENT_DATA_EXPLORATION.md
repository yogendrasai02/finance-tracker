# Statement Data Exploration

Status: Observations only — no data model, no import design, no decisions.
Last updated: 2026-08-21

## 0. Purpose and scope

This document records **what the real statement files actually look like**.
It is the factual input for topics like deduping & input format in [SPEC.md](SPEC.md).

Nothing here is a design decision, with one exception: §7 records a handful of gaps the files themselves couldn't answer, closed out by the owner's direct input rather than left open indefinitely.

Three files were examined, all downloaded from the respective bank's netbanking as XLSX:

| # | File | Account | Coverage |
| - | ---- | ---------------------- | -------- |
| 1 | `statements/SBI_Account_Stmt_FY.xlsx` | SBI Savings (asset) | 01-04-2026 → 20-08-2026 |
| 2 | `statements/HDFC_Account_Stmt_FY.xlsx` | HDFC Savings (asset) | 01-04-2026 → 19-08-2026 |
| 3 | `statements/HDFC_CC_Stmt_July.xlsx` | HDFC Millenia CC (liability) | Statement dated 12 Jul 2026 |

`statements/Bank_Account_Stmts_Curr_FY.xlsx` (the manually assembled 3-sheet workbook) was deliberately **not** examined.
It is a personal collection convenience, not a format the app imports.

**Row numbering convention.** All row numbers in this document are **1-indexed, matching the row number Excel itself shows** (the convention `openpyxl` uses).
A parser written with `pandas.read_excel(header=None)` will see these as **0-indexed** — subtract 1.
E.g. HDFC Savings' header row is Excel row 21 / pandas index 20; its ruler row is Excel row 22 / pandas index 21; data starts at Excel row 23 / pandas index 22.
Same offset applies to SBI (header 18 → index 17) and the CC (header 19 → index 18).

A second, independent exploration pass (pandas-based) was run separately and cross-checked against this one: header row positions, column layout, and the CC's fixed-index columns all matched exactly, with no discrepancies.

### Privacy note

The `statements/` directory is gitignored and untracked, and must stay that way.
The source files contain the account holder's name, account numbers, CIF/customer IDs, IFSC and MICR codes, branch contact details, salary amount, running balances, and **third parties' names, phone numbers and UPI VPAs**.

Every example in this document is masked.
Structure, delimiters, field widths and value *formats* are reproduced faithfully; identifiers, balances and counterparty details are replaced with placeholders.
Merchant brand names are kept, because they are not personal data and they matter for the categorization discussion.

---

## 1. SBI Savings

### 1.1 File shape

| Property | Value |
| -------- | ----- |
| Sheet name | `sheet` (single sheet, lowercase) |
| Used grid | `A1:F147` |
| Merged ranges | 22, all in the header block (rows 2–17) and footer block (rows 140–147) |
| Column header row | **18** |
| Data rows | **19–139** (121 transactions) |
| Footer | totals at rows 142–143, disclaimers at 145–147 |

Rows 2–17 are a key–value header block, but the keys and values are **not in separate cells**.
Each cell holds a whole `'Label  :  value'` string, e.g. `'Account Number  :  xxxx'`, `'IFSC Code  :  SBIN00xxxxx'`, `'Statement From  :  01-04-2026  to  20-08-2026'`.
Note the header block uses `dd-mm-yyyy` while the transaction rows use `dd/mm/yyyy`.

There are **no page-break artifacts inside the table** — no repeated header rows, no ruler rows.
The table is one clean contiguous block.

### 1.2 Columns

| Col | Header | Type | Notes |
| --- | ------ | ---- | ----- |
| A | `Date` | `str` | `dd/mm/yyyy`, 121/121 consistent |
| B | `Details` | `str` | narration; see §1.3 |
| C | `Ref No/Cheque No` | — | **empty in 121/121 rows** |
| D | `Debit` | `str` | e.g. `'5000.00'` |
| E | `Credit` | `str` | e.g. `'2114.50'` |
| F | `Balance` | `str` | running balance |

**Every cell in the table is a string.** No dates, no numbers — the export is entirely text.

Amount formatting in data rows is clean: always 2 decimal places, **no thousand separators, no `CR`/`DR` suffix** (verified across all 363 amount cells).
The footer summary row uses a different format entirely — Indian lakh grouping plus a suffix: `'1,04,132.36CR'`.
So the summary block cannot be parsed with the same rules as the table.

D and E are mutually exclusive — exactly one is populated per row (79 debit rows, 42 credit rows).

### 1.3 The `Details` field — wrapped and truncated

Two independent forms of damage, both applied by the bank:

**(a) A hard line wrap.**
Every one of the 121 narrations contains **exactly one `\n`**, inserted at a fixed column, mid-token:

```
' WDL TFR   UPI/DR/109541529452/Groww/ICIC/growws\n tock/Debit f   00976xxxxxxxx AT 20480 <BRANCH>'
```

`growws` + `tock` is `growwstock` split by the wrap.
The newline position is not constant (48 in 74 rows, but also 24, 36, 47, …) — it depends on content, so it cannot be assumed.

Each wrapped line is left-padded with one space (the string also *starts* with a space).
Therefore the correct unwrap is to **delete `\n` and the single space following it** — not to replace the newline with a space:

| Operation | Result |
| --------- | ------ |
| `' '.join(s.split())` | `...growws tock...` ❌ corrupts the token |
| `s.replace('\n ', '')` then collapse spaces | `...growwstock...` ✅ |

Same effect on a name: `'Mr. KA\n TIKIREDDY YOGEND'` → `KATIKIREDDY YOGEND`, which is correct.

**(b) Truncation.**
Narration length caps at **98 characters** (63 of 121 rows sit exactly at 98).
Sub-fields inside the narration are independently truncated to fixed widths:

- payee name → **8 characters**: `PRIYANKA`, `DeveshGa`, `SBI Mutu`, `IRCTC Ra`, `INDSTOCK`
- VPA → truncated: `growwstock`, `irctcpgonl`, `sbi.camspa`, `indmoneymf`
- transaction remark → 4 characters: `Spli`, `Dinn`, `Paym`, `Recu`, `Mand`, `Debit f`

The truncation is destructive — `SBI Mutu` and `SBI Mutual Fund` are not recoverable from the file.

### 1.4 Narration grammar

After unwrapping, narrations fall into a small number of shapes (`#` = digits):

| Count | Shape |
| ----- | ----- |
| 55 | `WDL TFR UPI/DR/<12-digit RRN>/<payee8>/<bank4>/<vpa>/<remark> <ref#> AT <branch code> <BRANCH>` |
| 36 | `DEP TFR UPI/CR/<12-digit RRN>/<payer8>/<bank4>/... AT <branch code> <BRANCH>` |
| 10 | `DIRECT DR <#> OF Mr. <NAME> AT <branch code> <BRANCH>` |
| 10 | `DEBIT ACHDr NACH<#> BDACH-SBISMSMF` |
| 7 | `DEP TFR IMPS/<#>/RE#-XX#-<NAME> /IMPS tran <#> AT ...` |
| 4 | `CEMTEX DEP ...` |
| 3 | `DEP TFR NEFT*<IFSC>*<UTR>*<COUNTERPARTY> AT ...` |
| 2 | `INTERES T ...` (interest credit — note the split word) |
| 1 each | `ATM WDL`, `POS ATM`, `DEBIT ATMCard`, `CR I ...` |

The leading token pair (`WDL TFR`, `DEP TFR`, `DIRECT DR`, `DEBIT`, `CEMTEX DEP`, `ATM WDL`) is a usable transaction-kind marker.

### 1.5 Quirks worth flagging

**Genuine identical rows exist.**
Five groups of two rows each are byte-identical on `(date, debit, credit, narration)`:

```
r27  07/04/2026  5000.00  ' DEBIT   ACHDr NACH00000\n 000022163 BDACH-SBISMSMF'
r28  07/04/2026  5000.00  ' DEBIT   ACHDr NACH00000\n 000022163 BDACH-SBISMSMF'
```

These are two real SIP installments debited the same day, not a duplicated row.
The pattern repeats monthly (07 Apr, 07 May, 08 Jun, 07 Jul, 07 Aug).
With column C empty, **the file contains no field that distinguishes them.**

**The UPI RRN is only inside the narration.** 65 of 121 rows carry a 12-digit RRN after `UPI/DR/`, `UPI/CR/` or `UPI/REF/`.
The remaining 56 rows (ACH, NEFT, IMPS, ATM, interest) have no RRN in that position.

---

## 2. HDFC Savings

### 2.1 File shape

| Property | Value |
| -------- | ----- |
| Sheet name | `Sheet 1` (note the space) |
| Used grid | `A1:G409` |
| Merged ranges | **none** |
| Column header row | **21** |
| Ruler row | **22** — a row of `'********'` strings, must be skipped |
| Data rows | **23–383** (361 transactions) |
| Footer | ruler at 385, `STATEMENT SUMMARY :-` block at 388–393, generation metadata at 396, disclaimers 401–409 |

Header block (rows 1–18) is again `'Label :value'` strings, but spread across two column groups (A and E).

Like SBI, **no repeated headers or page breaks inside the table** — 361 contiguous data rows, and all 26 non-data rows in the scanned region occur *after* the table ends.
This is notably cleaner than the PDF form of the same statement.

Row 396 carries an audit-useful line: `'Generated On:' | '20-AUG-2026 23:04:36' | 'Generated By:' | <cust id> | 'Requesting Branch Code:' | 'NET'`.

### 2.2 Columns

| Col | Header | Type | Notes |
| --- | ------ | ---- | ----- |
| A | `Date` | `str` | **`dd/mm/yy` — two-digit year** |
| B | `Narration` | `str` | see §2.3 |
| C | `Chq./Ref.No.` | `str` | zero-padded to 16 chars |
| D | `Value Dt` | `str` | `dd/mm/yy` |
| E | `Withdrawal Amt.` | `int` or `float` | **numeric, mixed types** |
| F | `Deposit Amt.` | `int` or `float` | numeric |
| G | `Closing Balance` | `float` | numeric |

This is the opposite of SBI: amounts arrive as **binary floats**, not strings.
324 rows have an `int` withdrawal, 17 have a `float` withdrawal, 19 an `int` deposit, 1 a `float` deposit.
The type varies per row depending on whether the value has paise.

`Value Dt` differs from `Date` in only **2 of 361** rows — rare, but non-zero.

### 2.3 `Chq./Ref.No.`

| Pattern | Count |
| ------- | ----- |
| 16 digits (zero-left-padded 12-digit UPI RRN) | 357 |
| 15 digits | 3 |
| `HDFCH` + 11 digits | 1 |

Not unique: the value `'000000000000000'` appears on **3 different rows**.
So the reference number is populated but is not by itself an identifier.

For UPI rows the value is the same 12-digit RRN that also appears inside the narration, zero-padded:
narration `...-203051801380-SENT USING PAYTM U` ↔ ref `'0000203051801380'`.

### 2.4 `Narration`

**Single line — no embedded newline** (unlike SBI).
Max length **107**, and it is truncated mid-word: 200+ rows end in `'-SENT USING PAYTM U'` (`USING` cut from `USER`/`UPI`).

Hyphen-delimited, most commonly 6 or 7 segments:

```
UPI-<MERCHANT OR PAYEE>-<vpa>@<psp>-<IFSC>-<12-digit RRN>-<remark, truncated>
```

Real-shape examples, masked:

```
UPI-FOODBOOK-PAYTM-79xxxxxx@PTYBL-YESB0PTMUPI-2030518xxxxx-SENT USING PAYTM U
UPI-<PERSON NAME>-<phone>@AXL-IDIB000M006-3000025xxxxx-SENT USING PAYTM U
UPI-SSLV HOSPITALITY SOL-PINELABS.10xxxxxx@HDFCBANK-HDFC0MERUPI-2031368xxxxx-SENT USING PAYTM U
UPI-AMAZON PAY-AMAZONPAY@APL-UTIB0000100-6093919xxxxx-REQUEST FROM AMAZO
```

Merchant/payee is segment 2 — a far better categorization input than SBI's 8-character stub.

Distribution of narration prefixes:

| Prefix | Count | Meaning |
| ------ | ----- | ------- |
| `UPI-` | 297 | UPI |
| `ACH D-` | 20 | ACH debit (SIP/mandate), e.g. `ACH D- NSECLEARINGLIMITED-436xxxxx` |
| `IMPS-` | 10 | IMPS, e.g. `IMPS-<RRN>-<SELF NAME>-SBIN-XXXXXXX1740-IMPS TRANSACTION` |
| `ACH C-` | 5 | ACH credit |
| `FT-` | 4 | fund transfer — includes the **salary credit**: `FT-  JPMCSALARYXXXXX...` |
| `IB BILLPAY DR` | 3 | netbanking bill payment |
| `RFX ...` | 3 | forex, e.g. `RFX 170426BTT00030 USD52.76@93.615` |
| `<ref> DPO<#> CGST` / `SGST` | 6 | tax rows paired with each `RFX` row |
| `CHDF…/ICICI BANK CREDIT CA` | 3 | **ICICI credit card bill payment** (see §5.8) |
| `NEFT DR`, `INTEREST PAID TILL 3…`, `<#> TATA MOTO…` | 1 each | |

Note `IMPS-` rows: the counterparty is the account holder themself at `SBIN` — these are the self-transfers between HDFC and SBI.

Forex purchases produce **three rows** each: one `RFX` row with the USD amount and rate, plus a CGST row and an SGST row sharing the same `<ref>BTT<#>` token.

---

## 3. HDFC Millenia Credit Card

### 3.1 File shape

Structurally the most hostile of the three.

| Property | Value |
| -------- | ----- |
| Sheet name | `Statement` |
| Used grid | `A1:Y58` — **25 columns wide** |
| Merged ranges | **~230**, pervasive throughout |
| Column header row | **19** |
| Transaction rows | **20–37** (18 transactions) |
| Trailer | Reward Points Summary (39–41), Rewards Program Summary (43–47), GST Summary (49–51), disclaimers (53–58) |

The 25-column width is entirely an artifact of merged cells used for visual layout.
There are 7 logical columns, and they sit at **fixed, non-contiguous 0-based indices**:

| Index | Header |
| ----- | ------ |
| 0 | `Transaction type` |
| 4 | `Primary / Addon Customer Name` |
| 9 | `Date & Time` |
| 12 | `Description` |
| 18 | `REWARDS` |
| 20 | `AMT` |
| 23 | `Debit / Credit` |

Header block (rows 1–12) is a two-column key/value layout, this time with keys and values in **separate cells** (unlike both bank statements): `'Payment Due Date' … '01 Aug, 2026'`, `'Statement Date' … '12 Jul, 2026'`, `'Total Amount Due' … '12,621.00'`, `'Credit Limit'`, `'Available Limit'`, `'Available Cash Limit'`.
Note the third date format in this project: **`dd Mon, yyyy`**.

### 3.2 Columns

All values are strings.

- **`Date & Time`** — `'12/06/2026 / 11:08'`. Four-digit year, and a literal ` / ` separating date from time.
  Bank-generated rows (fees, GST) use `00:00`, so the time is only meaningful for real swipes.
- **`AMT`** — string **with comma grouping**: `'10,031.00'`, `'1,647.00'`. Third amount format in the project.
- **`Debit / Credit`** — `'Cr'` on credits, **empty on debits**. The sign is encoded by absence, not by a value.
- **`REWARDS`** — column exists, **empty in all 18 rows**.
- **`Transaction type`** — `Domestic` (12 rows) or `International` (6 rows).

### 3.3 No reference number, no balance

There is **no reference/UTR column and no running balance column** anywhere in the transaction table.
The only reconciliation anchors are the Account Summary block at rows 15–16:

```
Opening Bal  −  Payment/Credit  +  Purchases/Debits  +  Finance Charges  =  Total Dues
 10,030.73        10,031.00         12,621.43            0.00              12,621.00
```

Summing the 18 transaction rows reproduces the middle two figures **exactly**: debits `12,621.43`, credits `10,031.00`.
But the stated identity gives `10,030.73 − 10,031.00 + 12,621.43 + 0.00 = 12,621.16`, while `Total Amount Due` prints `12,621.00`.
**HDFC rounds the amount due down to whole rupees**; the 16 paise carry silently.
A naive "derived card balance must equal Total Amount Due" check will fail on rounding, not on data error.

### 3.4 Ordering and content

Rows are **not chronological**.
All 12 `Domestic` rows come first (12/06 → 08/07), then all 6 `International` rows restart at 24/06.

Of the 18 rows, only 1 is a credit — the card bill payment:

```
23/06/2026 / 10:16 | 'CREDIT CARD PAYMENT Net Banking (Ref# 000000000006230xxxxxxxx)' | '10,031.00' | 'Cr'
```

This is the row that must pair with the corresponding debit in the HDFC savings statement.

Merchant descriptions are `MERCHANT + CITY` concatenated with no delimiter, and truncated:

```
'ZEPTO MARKETPLACE PRI BANGALORE '     → Zepto Marketplace Private, Bangalore
'VIJETHA SUPERMARKETS P VHYDERABAD '   → note the mangled 'P V' + 'HYDERABAD'
'PTM*RELIANCE RETAIL L NOIDA '         → Paytm-routed
'ANTHROPIC* CLAUDE SUB SAN FRANCISC '  → truncated city
```

The merchant portion is capped around 22 characters, and the city runs straight into it — `VIJETHA SUPERMARKETS P V` + `HYDERABAD` has no separator at all.
All descriptions carry a trailing space.

**Fee and tax rows link to their parent transaction by an embedded `Ref#`.**
Three bank-generated rows carry `(Ref# <token>)` in the description, and the tokens match:

| Row | Description | Ref# |
| --- | ----------- | ---- |
| r30 | `1.75% on all DCC Trans action (Ref# ST2618900840...)` | `ST2618900840…` |
| r36 | `IGST-VPS…- RATE 18.0 -36 (Ref# ST2618900840...)` | `ST2618900840…` |
| r34 | `OPENROUTER, INC NEW YORK` (parent, no Ref#) | — |
| r35 | `IGST-VPS…- RATE 18.0 -36 (Ref# MT2618800760...)` | `MT2618800760…` |
| r37 | `CONSOLIDATED FCY MARKU P FEE (Ref# MT2618800760...)` | `MT2618800760…` |

So a foreign transaction generates a markup fee row and an IGST row that share a Ref#, but the **originating purchase row itself carries no Ref#** — the link back to the purchase is not present in the file.

Also note `'1.75% on all DCC Trans action'` — `Transaction` is split by the same fixed-width truncation seen elsewhere.

### 3.5 Coverage

One file = one statement cycle, and the cycle is **not a calendar month**: statement dated 12 Jul 2026, transactions from 12/06 to 12/07.
Monthly reporting on calendar months means a 3-calendar-month backfill needs roughly 4 statement files.

---

## 4. Side-by-side

| | SBI Savings | HDFC Savings | HDFC Millenia CC |
| - | ----------- | ------------ | ---------------- |
| Sheet name | `sheet` | `Sheet 1` | `Statement` |
| Header row | 18 | 21 (+ ruler at 22) | 19 |
| Data rows | 121 | 361 | 18 |
| Logical columns | 6, contiguous | 7, contiguous | 7, at indices 0/4/9/12/18/20/23 |
| Merged cells in table | no | no | yes, pervasive |
| Date format | `dd/mm/yyyy` | `dd/mm/yy` | `dd/mm/yyyy / HH:MM` |
| Header-block date format | `dd-mm-yyyy` | `dd/mm/yyyy` | `dd Mon, yyyy` |
| Amount type | `str`, plain | `int`/`float` | `str`, comma-grouped |
| Sign convention | Debit / Credit columns | Withdrawal / Deposit columns | one `AMT` + `'Cr'` marker |
| Running balance | yes | yes | **no** |
| Reference number | **column empty**; RRN inside narration (65/121) | dedicated column, 16-char padded | **none** |
| Narration newline | 1 per row, mid-token | none | none |
| Narration truncated | yes, 98 chars + per-field stubs | yes, 107 chars | yes, ~22-char merchant |
| Counterparty quality | 8-char stub | full merchant name | merchant + city, run together |
| Chronological | yes | yes | **no** (grouped by Domestic/International) |
| Exact-duplicate rows | **5 groups** | none | none |
| File granularity | full FY to date | full FY to date | one statement cycle |

Three files, three date formats, three amount encodings, three sign conventions, and three different answers to "is there a reference number".

---

## 5. Observations that will shape later decisions

**5.1 No reference-number-based key works across all three sources.**
HDFC savings has a reference column that is populated but non-unique.
SBI has an empty reference column, an RRN recoverable from narration for 54% of rows, and five confirmed groups of genuinely identical rows.
The CC file has no reference at all.
See §6 — the running balance, not the reference number, turns out to be the field that closes this gap for the two bank accounts.

**5.2 Re-import overlap is a real scenario, not a hypothetical.**
Both bank files are "financial year to date" exports.
Downloading again next month re-supplies every row already imported — 121 and 361 rows respectively.
Idempotent import will be exercised on the very second import, not eventually.

**5.3 The UPI RRN is a genuine cross-account matching signal, but thin.**
Exactly **one** RRN appears in both bank files, and it is a real self-transfer: ₹50,000 credited to SBI and withdrawn from HDFC on the same date, same RRN.
That validates the idea, but one match across 482 rows means RRN cannot be the primary mechanism for transfer matching — most self-transfers here are IMPS, where HDFC records the RRN and SBI records a differently-formatted IMPS reference.

**5.4 Precision handling differs per source before the app sees the data.**
SBI gives exact decimal strings.
HDFC savings gives IEEE floats that already passed through Excel.
The CC gives comma-formatted strings.
Converting to integer paise has to happen at parse time, and the float path is the one that needs care.

**5.5 Bank-side truncation is lossy and permanent.**
SBI's 8-character payee stubs (`SBI Mutu`, `IRCTC Ra`, `DeveshGa`) cannot be expanded from the file.
The same real-world merchant appears with different text in different sources.
Rule-based categorization will need per-source patterns, not one shared pattern set.

**5.6 Investment outflows are highly recognisable.**
`Groww`, `INDmoney`, `INDSTOCK`, `SBI Mutu`, `NSECLEARINGLIMITED` (ACH), `EDELWEISS MUTUA`, `GROWW INVEST TE` (NEFT credits) all appear as stable tokens.
A virtual Investments account has a strong signal to work from.

**5.7 Same-day repeated SIP debits are the sharpest edge case found.**
Two ₹5,000 debits, same date, same narration, no reference number, five times over five months.
Any dedupe rule based on `(account, date, amount, narration)` collapses real transactions — confirmed by measurement in §6.1.
The balance column separates them.

**5.8 The ICICI card is visible only as an opaque outflow.**
The HDFC savings statement contains `CHDF…/ICICI BANK CREDIT CA` rows — the ICICI bill payments, visible only as an outflow with a truncated narration and no itemisation.
Whatever bucket a design gives this card, that outflow is all the data offers without PDF parsing.

**5.9 Forex spending is split across multiple rows in both sources.**
HDFC savings: `RFX` + CGST + SGST (three rows, linked by a shared token).
CC: purchase + markup fee + IGST (fee and tax linked by `Ref#`, but the purchase itself is not).
Reporting a foreign purchase as one economic event will require joining rows.

**5.10 The XLSX exports are structurally cleaner than expected.**
No repeated page headers, no interleaved footers, no split tables.
The header/footer blocks sit strictly outside the transaction table in all three files.
The CC file's merged-cell layout is the only real structural obstacle, and even there the logical columns sit at fixed indices.

---

## 6. Follow-up verification: the running balance

Added 2026-08-20, after confirming that **no SBI export format exposes the reference number**.
This section is measurement, not design — the numbers below come from running the check over the real files.

### 6.1 The running balance is the missing discriminator

The two rows that no other field could separate differ in the balance column:

```
r27  07/04/2026  −5,000.00  'DEBIT ACHDr NACH…BDACH-SBISMSMF'  balance 33,699.40
r28  07/04/2026  −5,000.00  'DEBIT ACHDr NACH…BDACH-SBISMSMF'  balance 28,699.40
```

Candidate keys were tested for uniqueness across every data row in both bank files.
Narration was normalised first (unwrap `\n `, collapse whitespace); amounts and balances converted to signed integer paise:

| Candidate key | SBI (121 rows) | HDFC Savings (361 rows) |
| ------------- | -------------- | ----------------------- |
| `(date, amount, narration)` | **5 collision groups** | unique |
| `(date, amount, balance)` | unique | **1 collision group** |
| `balance` alone | 2 collision groups | 2 collision groups |
| **`(date, amount, narration, balance)`** | **unique** | **unique** |

Each file breaks a *different* three-field key, and the four-field key survives both.
The HDFC collision is the interesting one — `('30/05/26', −348.00, closing 283,407.26)` occurs twice, because an offsetting credit returned the ledger to the same balance and the same amount was debited again.
Narration separated them.

A re-import overlap was simulated by re-keying a later, overlapping slice of each file against the full set:

```
SBI:  81 overlapping rows  -> 81 matched, 0 false-new
HDFC: 321 overlapping rows -> 321 matched, 0 false-new
```

### 6.2 The balance also forms an integrity chain

For every row, `balance[i] == balance[i-1] + signed_amount[i]` was checked:

| File | Links checked | Breaks |
| ---- | ------------- | ------ |
| SBI | 120 | **0** |
| HDFC Savings | 360 | **0** |

The balance implied by the first data row reproduces the statement's stated opening balance exactly in both files (SBI `56,938.90`, HDFC `833,922.57`), and the last row reproduces the stated closing balance.

Two consequences worth recording:

- **The parse is arithmetically verifiable end to end.** A dropped row, a misparsed amount, or a mis-sorted file breaks the chain at a known position.
- **Integer-paise conversion is exact for both encodings.** HDFC's binary floats converted without drift when routed through a decimal conversion on the string form; SBI's decimal strings are exact by construction.

### 6.3 The credit card has no balance, but has its own check

The CC file has no running balance.
It has two other properties that compensate:

- **Statement cycles are disjoint.** A transaction appears in exactly one statement, so cross-file overlap does not occur — only re-upload of the same statement file does.
- **The Account Summary block is a closing equation.** Summing the transaction rows reproduces `Purchases/Debits` (`12,621.43`) and `Payment/Credit` (`10,031.00`) exactly, so an import can be reconciled against figures the file itself states — subject to the whole-rupee rounding of `Total Amount Due` noted in §3.3.

So all three sources carry an internal arithmetic self-check, just not the same one.

---

## 7. Closed out by owner input (2026-08-21)

These six were gaps a single file sample couldn't answer.
Closed by the owner rather than by further inspection — recorded here so the reasoning isn't lost, not reopened later without a reason to:

1. **CC statement download options.** Confirmed: this XLSX is the only format the HDFC netbanking app offers for the card — no variant exposes a per-transaction reference number.
   Statement-scoped identity is therefore not a workaround pending a better export; it's the only option, permanently.
2. **Format stability across statement periods.** Not verifiable from a single sample of each file — there's no way to know in advance whether a bank will change its layout.
   Accepted as an ordinary integration risk rather than something to keep chasing.
   Practical default for the parser: locate the header row by matching expected column names, not a hardcoded row index, so a layout change fails loudly instead of silently misreading data.
3. **Pagination on longer periods.** Same situation — unverifiable from one sample, accepted as a risk rather than pursued further.
   Practical default: read data rows until a row stops matching the expected shape, rather than assuming a fixed row count.
4. **Two-digit year century rule.** Resolved: treat any two-digit year as `20xx`.
   No 19xx rollover handling needed.
5. **Reference-number anomalies** (15-digit, `HDFCH`-prefixed, all-zero values). Not investigated further — these are genuine values in an unmodified, real export, not artifacts introduced by handling.
   The dedupe approach already doesn't depend on this column being unique or fully populated, so the anomalies need accommodating, not explaining.
6. **CC row ordering.** Same basis — accepted as-is from the real export.
   The credit card's identity scheme doesn't depend on row order, so this doesn't block anything either.
