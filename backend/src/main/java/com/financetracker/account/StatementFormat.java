package com.financetracker.account;

/**
 * The statement export an account's imports are parsed as (D-43, DM-42).
 *
 * The names are the stored values, so renaming a constant needs a migration.
 * It lives in the account feature because the statement feature depends on accounts, never the other way round.
 */
public enum StatementFormat {
    SBI_SAVINGS_XLSX,
    HDFC_SAVINGS_XLSX,
    HDFC_CC_XLSX
}
