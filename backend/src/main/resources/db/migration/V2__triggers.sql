-- updated_at stamping (DM-25) and the DM-02 immutability rules for imported transactions and their raw source rows (§5.4, §6.2).
-- No GRANT EXECUTE needed below: Postgres grants EXECUTE on a new function to PUBLIC by default, unlike tables.
--
-- Every RAISE EXCEPTION message below names the column and the rule only, never a value (SR-25): the message reaches the log, and old/new amounts and narrations must never appear there.

CREATE FUNCTION app.set_updated_at() RETURNS TRIGGER
LANGUAGE plpgsql AS $$
BEGIN
    NEW.updated_at := now();
    RETURN NEW;
END;
$$;

CREATE FUNCTION app.transactions_block_fact_update() RETURNS TRIGGER
LANGUAGE plpgsql AS $$
BEGIN
    -- source itself can never change, on any row, regardless of what it currently is.
    IF NEW.source IS DISTINCT FROM OLD.source THEN
        RAISE EXCEPTION 'cannot change source on a transaction';
    END IF;

    IF OLD.source = 'IMPORTED' THEN
        IF NEW.id IS DISTINCT FROM OLD.id THEN
            RAISE EXCEPTION 'cannot change id on an IMPORTED transaction';
        ELSIF NEW.user_id IS DISTINCT FROM OLD.user_id THEN
            RAISE EXCEPTION 'cannot change user_id on an IMPORTED transaction';
        ELSIF NEW.account_id IS DISTINCT FROM OLD.account_id THEN
            RAISE EXCEPTION 'cannot change account_id on an IMPORTED transaction';
        ELSIF NEW.txn_date IS DISTINCT FROM OLD.txn_date THEN
            RAISE EXCEPTION 'cannot change txn_date on an IMPORTED transaction';
        ELSIF NEW.txn_time IS DISTINCT FROM OLD.txn_time THEN
            RAISE EXCEPTION 'cannot change txn_time on an IMPORTED transaction';
        ELSIF NEW.amount_paise IS DISTINCT FROM OLD.amount_paise THEN
            RAISE EXCEPTION 'cannot change amount_paise on an IMPORTED transaction';
        ELSIF NEW.narration IS DISTINCT FROM OLD.narration THEN
            RAISE EXCEPTION 'cannot change narration on an IMPORTED transaction';
        ELSIF NEW.narration_normalized IS DISTINCT FROM OLD.narration_normalized THEN
            RAISE EXCEPTION 'cannot change narration_normalized on an IMPORTED transaction';
        ELSIF NEW.balance_after_paise IS DISTINCT FROM OLD.balance_after_paise THEN
            RAISE EXCEPTION 'cannot change balance_after_paise on an IMPORTED transaction';
        ELSIF NEW.statement_import_id IS DISTINCT FROM OLD.statement_import_id THEN
            RAISE EXCEPTION 'cannot change statement_import_id on an IMPORTED transaction';
        ELSIF NEW.source_row_id IS DISTINCT FROM OLD.source_row_id THEN
            RAISE EXCEPTION 'cannot change source_row_id on an IMPORTED transaction';
        ELSIF NEW.source_row_fingerprint IS DISTINCT FROM OLD.source_row_fingerprint THEN
            RAISE EXCEPTION 'cannot change source_row_fingerprint on an IMPORTED transaction';
        ELSIF NEW.fingerprint_version IS DISTINCT FROM OLD.fingerprint_version THEN
            RAISE EXCEPTION 'cannot change fingerprint_version on an IMPORTED transaction';
        END IF;
    END IF;

    RETURN NEW;
END;
$$;

CREATE FUNCTION app.transactions_block_delete() RETURNS TRIGGER
LANGUAGE plpgsql AS $$
DECLARE
    import_status TEXT;
BEGIN
    IF OLD.source <> 'IMPORTED' THEN
        RETURN OLD;
    END IF;

    SELECT status INTO import_status FROM app.statement_imports WHERE id = OLD.statement_import_id;

    IF import_status IS DISTINCT FROM 'REPLACED' THEN
        RAISE EXCEPTION 'cannot delete an IMPORTED transaction unless its statement import is REPLACED';
    END IF;

    RETURN OLD;
END;
$$;

CREATE FUNCTION app.import_rows_block_update() RETURNS TRIGGER
LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.user_id IS DISTINCT FROM OLD.user_id THEN
        RAISE EXCEPTION 'cannot change user_id on a statement_import_rows row';
    ELSIF NEW.statement_import_id IS DISTINCT FROM OLD.statement_import_id THEN
        RAISE EXCEPTION 'cannot change statement_import_id on a statement_import_rows row';
    ELSIF NEW.row_number IS DISTINCT FROM OLD.row_number THEN
        RAISE EXCEPTION 'cannot change row_number on a statement_import_rows row';
    ELSIF NEW.raw_cells IS DISTINCT FROM OLD.raw_cells THEN
        RAISE EXCEPTION 'cannot change raw_cells on a statement_import_rows row';
    END IF;

    RETURN NEW;
END;
$$;

CREATE FUNCTION app.import_rows_block_delete() RETURNS TRIGGER
LANGUAGE plpgsql AS $$
DECLARE
    import_status TEXT;
BEGIN
    SELECT status INTO import_status FROM app.statement_imports WHERE id = OLD.statement_import_id;

    IF import_status IS DISTINCT FROM 'HELD' THEN
        RAISE EXCEPTION 'cannot delete a statement_import_rows row unless its import is HELD';
    END IF;

    RETURN OLD;
END;
$$;

CREATE TRIGGER trg_users_set_updated_at
    BEFORE UPDATE ON app.users
    FOR EACH ROW EXECUTE FUNCTION app.set_updated_at();

CREATE TRIGGER trg_accounts_set_updated_at
    BEFORE UPDATE ON app.accounts
    FOR EACH ROW EXECUTE FUNCTION app.set_updated_at();

CREATE TRIGGER trg_categories_set_updated_at
    BEFORE UPDATE ON app.categories
    FOR EACH ROW EXECUTE FUNCTION app.set_updated_at();

CREATE TRIGGER trg_category_rules_set_updated_at
    BEFORE UPDATE ON app.category_rules
    FOR EACH ROW EXECUTE FUNCTION app.set_updated_at();

CREATE TRIGGER trg_statement_imports_set_updated_at
    BEFORE UPDATE ON app.statement_imports
    FOR EACH ROW EXECUTE FUNCTION app.set_updated_at();

-- Alphabetically, block_update fires before set_updated_at, so a rejected update never bothers
-- stamping a timestamp on a row it isn't going to write.
CREATE TRIGGER trg_statement_import_rows_block_update
    BEFORE UPDATE ON app.statement_import_rows
    FOR EACH ROW EXECUTE FUNCTION app.import_rows_block_update();

CREATE TRIGGER trg_statement_import_rows_set_updated_at
    BEFORE UPDATE ON app.statement_import_rows
    FOR EACH ROW EXECUTE FUNCTION app.set_updated_at();

CREATE TRIGGER trg_statement_import_rows_block_delete
    BEFORE DELETE ON app.statement_import_rows
    FOR EACH ROW EXECUTE FUNCTION app.import_rows_block_delete();

CREATE TRIGGER trg_transactions_block_fact_update
    BEFORE UPDATE ON app.transactions
    FOR EACH ROW EXECUTE FUNCTION app.transactions_block_fact_update();

CREATE TRIGGER trg_transactions_set_updated_at
    BEFORE UPDATE ON app.transactions
    FOR EACH ROW EXECUTE FUNCTION app.set_updated_at();

CREATE TRIGGER trg_transactions_block_delete
    BEFORE DELETE ON app.transactions
    FOR EACH ROW EXECUTE FUNCTION app.transactions_block_delete();

CREATE TRIGGER trg_balance_checkpoints_set_updated_at
    BEFORE UPDATE ON app.balance_checkpoints
    FOR EACH ROW EXECUTE FUNCTION app.set_updated_at();
