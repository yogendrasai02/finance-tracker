-- FR-1 seed data: one user, the four accounts (§4.2), and the SPEC §3 categories.
-- This file is public (DM-16 keeps seed data out of V1), so the email below is a placeholder, not the owner's real address (SECURITY.md §2) — FR-8 replaces it with a real credential later.

INSERT INTO app.users (email, display_name)
VALUES ('owner@financetracker.local', 'Owner');

INSERT INTO app.accounts (user_id, name, type, dedup_method)
SELECT u.id, v.name, v.type, v.dedup_method
FROM app.users u
CROSS JOIN (VALUES
    ('HDFC Savings', 'ASSET', 'ROW_FINGERPRINT'),
    ('SBI Savings', 'ASSET', 'ROW_FINGERPRINT'),
    ('HDFC Millenia', 'LIABILITY', 'STATEMENT_BATCH'),
    ('Investments', 'VIRTUAL', 'NONE')
) AS v (name, type, dedup_method)
WHERE u.email = 'owner@financetracker.local';

INSERT INTO app.categories (user_id, name, kind)
SELECT u.id, v.name, v.kind
FROM app.users u
CROSS JOIN (VALUES
    ('Groceries & Vegetables', 'EXPENSE'),
    ('Eating Out', 'EXPENSE'),
    ('Office Food', 'EXPENSE'),
    ('Transport', 'EXPENSE'),
    ('Car & Maintenance', 'EXPENSE'),
    ('Travel & Vacation', 'EXPENSE'),
    ('Home', 'EXPENSE'),
    ('Utilities', 'EXPENSE'),
    ('Subscriptions', 'EXPENSE'),
    ('Clothes & Accessories', 'EXPENSE'),
    ('Healthy Lifestyle', 'EXPENSE'),
    ('Family', 'EXPENSE'),
    ('Seva', 'EXPENSE'),
    ('Bank Charges & Fees', 'EXPENSE'),
    ('Cash Spends', 'EXPENSE'),
    ('ICICI Card Spends', 'EXPENSE'),
    ('Misc', 'EXPENSE'),
    ('Salary', 'INCOME'),
    ('Interest', 'INCOME'),
    ('Cashback', 'INCOME'),
    ('Dividends', 'INCOME'),
    ('Freelance', 'INCOME'),
    ('Other Income', 'INCOME')
) AS v (name, kind)
WHERE u.email = 'owner@financetracker.local';
