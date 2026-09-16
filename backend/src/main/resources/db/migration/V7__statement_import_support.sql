-- FR-2 support: which parser an account's statements go through (DM-42), and why a held import was held (DM-43).

ALTER TABLE app.accounts
    ADD COLUMN statement_format TEXT;

ALTER TABLE app.accounts
    ADD CONSTRAINT chk_accounts_statement_format CHECK (
        statement_format IS NULL
        OR statement_format IN ('SBI_SAVINGS_XLSX', 'HDFC_SAVINGS_XLSX', 'HDFC_CC_XLSX')
    ),
    -- A format decides which dedup path its rows take, so a savings format on a card account would send rows through the wrong checks.
    ADD CONSTRAINT chk_accounts_statement_format_dedup CHECK (
        statement_format IS NULL
        OR (statement_format IN ('SBI_SAVINGS_XLSX', 'HDFC_SAVINGS_XLSX') AND dedup_method = 'ROW_FINGERPRINT')
        OR (statement_format = 'HDFC_CC_XLSX' AND dedup_method = 'STATEMENT_BATCH')
    );

-- Matches V4's seeded accounts by name and dedup method.
-- An UPDATE that matches nothing still succeeds, so StatementImportSupportSchemaTest asserts the seeded formats.
UPDATE app.accounts
SET statement_format = v.statement_format
FROM (VALUES
    ('SBI Savings', 'ROW_FINGERPRINT', 'SBI_SAVINGS_XLSX'),
    ('HDFC Savings', 'ROW_FINGERPRINT', 'HDFC_SAVINGS_XLSX'),
    ('HDFC Millenia', 'STATEMENT_BATCH', 'HDFC_CC_XLSX')
) AS v (name, dedup_method, statement_format)
WHERE app.accounts.name = v.name
  AND app.accounts.dedup_method = v.dedup_method;

ALTER TABLE app.statement_imports
    ADD COLUMN hold_reason     TEXT,
    ADD COLUMN hold_row_number INT;

ALTER TABLE app.statement_imports
    ADD CONSTRAINT chk_statement_imports_hold_reason CHECK (
        hold_reason IS NULL
        OR hold_reason IN (
            'BALANCE_CHAIN_BROKEN',
            'STATEMENT_TOTALS_MISMATCH',
            'DOES_NOT_CONTINUE_HISTORY',
            'DUPLICATE_ROWS_IN_FILE',
            'FINANCE_CHARGES_NOT_SUPPORTED'
        )
    ),
    -- A held import must say why once the request that held it has ended, and a committed one has nothing to explain.
    ADD CONSTRAINT chk_statement_imports_hold_reason_paired CHECK ((status = 'HELD') = (hold_reason IS NOT NULL)),
    ADD CONSTRAINT chk_statement_imports_hold_row_number_paired CHECK (hold_row_number IS NULL OR hold_reason IS NOT NULL);
