-- FR-8 credentials on the tenant root, and the one lookup that has to work before any tenant id is known.
-- No password_algorithm column: the encoder writes an {algorithm} prefix into the hash itself, so the algorithm travels with the value and can differ per row while it is being changed.

ALTER TABLE app.users
    ADD COLUMN password_hash       TEXT,
    ADD COLUMN password_updated_at TIMESTAMPTZ,
    ADD COLUMN last_login_at       TIMESTAMPTZ,
    ADD COLUMN status              TEXT NOT NULL DEFAULT 'ACTIVE';

ALTER TABLE app.users
    ADD CONSTRAINT chk_users_status CHECK (status IN ('ACTIVE', 'DISABLED')),
    -- Refuses any value that is not an {algorithm}-prefixed hash, so a raw password cannot be stored in this column by mistake (SR-40).
    ADD CONSTRAINT chk_users_password_hash_encoded CHECK (password_hash IS NULL OR password_hash LIKE '{%}%'),
    -- Login matches the email exactly, so a row stored in mixed case would be unreachable rather than merely inconvenient.
    ADD CONSTRAINT chk_users_email_lowercase CHECK (email = lower(email));

-- The RLS policy on app.users compares id against app.user_id, but login has to find the row before any id exists.
-- SECURITY DEFINER runs the body as the function's owner, ft_migrator, which owns the table and therefore skips its policies (DM-32).
-- This is the only path that reads a user row without a tenant id: ft_app still gets zero rows from a direct SELECT.
-- It is deliberately narrow — one parameter, no dynamic SQL, a fixed search_path, and only the columns login needs.
--
-- This depends on app.users never gaining FORCE ROW LEVEL SECURITY, which would make the owner subject to its own policy and leave this function returning nothing.
CREATE FUNCTION app.find_login_identity(p_email TEXT)
RETURNS TABLE (id BIGINT, password_hash TEXT, status TEXT)
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = app, pg_catalog
AS $$
    SELECT u.id, u.password_hash, u.status
    FROM app.users u
    WHERE u.email = p_email;
$$;

-- Postgres grants EXECUTE on a new function to PUBLIC, which is what makes the revoke necessary rather than the grant.
REVOKE EXECUTE ON FUNCTION app.find_login_identity(TEXT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION app.find_login_identity(TEXT) TO ft_app;
