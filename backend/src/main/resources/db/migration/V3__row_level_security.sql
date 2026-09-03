-- Row-Level Security (DM-30, §2.3): every domain table is filtered by the tenant id the
-- application sets once per transaction with SET LOCAL app.user_id. current_setting(..., true)
-- returns NULL when unset, so a forgotten SET LOCAL yields zero rows, not an error or a leak.
-- The table owner (ft_migrator) bypasses these policies regardless; ft_app is always subject to
-- them (§2.4).

ALTER TABLE app.users ENABLE ROW LEVEL SECURITY;

-- users has no user_id column: it is the tenant root, so the policy compares id itself.
CREATE POLICY tenant_isolation ON app.users
    FOR ALL
    USING (id = current_setting('app.user_id', true)::BIGINT)
    WITH CHECK (id = current_setting('app.user_id', true)::BIGINT);

ALTER TABLE app.accounts ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON app.accounts
    FOR ALL
    USING (user_id = current_setting('app.user_id', true)::BIGINT)
    WITH CHECK (user_id = current_setting('app.user_id', true)::BIGINT);

ALTER TABLE app.categories ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON app.categories
    FOR ALL
    USING (user_id = current_setting('app.user_id', true)::BIGINT)
    WITH CHECK (user_id = current_setting('app.user_id', true)::BIGINT);

ALTER TABLE app.category_rules ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON app.category_rules
    FOR ALL
    USING (user_id = current_setting('app.user_id', true)::BIGINT)
    WITH CHECK (user_id = current_setting('app.user_id', true)::BIGINT);

ALTER TABLE app.statement_imports ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON app.statement_imports
    FOR ALL
    USING (user_id = current_setting('app.user_id', true)::BIGINT)
    WITH CHECK (user_id = current_setting('app.user_id', true)::BIGINT);

ALTER TABLE app.statement_import_rows ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON app.statement_import_rows
    FOR ALL
    USING (user_id = current_setting('app.user_id', true)::BIGINT)
    WITH CHECK (user_id = current_setting('app.user_id', true)::BIGINT);

ALTER TABLE app.transactions ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON app.transactions
    FOR ALL
    USING (user_id = current_setting('app.user_id', true)::BIGINT)
    WITH CHECK (user_id = current_setting('app.user_id', true)::BIGINT);

ALTER TABLE app.transaction_links ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON app.transaction_links
    FOR ALL
    USING (user_id = current_setting('app.user_id', true)::BIGINT)
    WITH CHECK (user_id = current_setting('app.user_id', true)::BIGINT);

ALTER TABLE app.transaction_link_members ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON app.transaction_link_members
    FOR ALL
    USING (user_id = current_setting('app.user_id', true)::BIGINT)
    WITH CHECK (user_id = current_setting('app.user_id', true)::BIGINT);

ALTER TABLE app.dismissed_matches ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON app.dismissed_matches
    FOR ALL
    USING (user_id = current_setting('app.user_id', true)::BIGINT)
    WITH CHECK (user_id = current_setting('app.user_id', true)::BIGINT);

ALTER TABLE app.balance_checkpoints ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON app.balance_checkpoints
    FOR ALL
    USING (user_id = current_setting('app.user_id', true)::BIGINT)
    WITH CHECK (user_id = current_setting('app.user_id', true)::BIGINT);
