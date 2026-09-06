-- Server-side sessions, moved out of JVM memory into Postgres (D-34).
-- Columns, constraints and indexes are copied from spring-session-jdbc 4.1.1's own org/springframework/session/jdbc/schema-postgresql.sql, not guessed: only the schema qualifier and the identifier case differ, and Postgres folds an unquoted identifier to lowercase regardless of how the source file wrote it.
-- Lives in auth schema, not app: a session is what establishes the tenant, so it cannot carry RLS or a user_id without being circular, and app's convention is that every table in it does (RlsCoverageTest, SchemaConventionsTest).

CREATE TABLE auth.spring_session (
    primary_id             CHAR(36)    NOT NULL,
    session_id             CHAR(36)    NOT NULL,
    creation_time          BIGINT      NOT NULL,
    last_access_time       BIGINT      NOT NULL,
    max_inactive_interval  INT         NOT NULL,
    expiry_time            BIGINT      NOT NULL,
    principal_name         VARCHAR(100),
    CONSTRAINT spring_session_pk PRIMARY KEY (primary_id)
);

-- ux: unique index, ix: regular, non-unique index
CREATE UNIQUE INDEX spring_session_ux_session_id ON auth.spring_session (session_id);
CREATE INDEX spring_session_ix_expiry_time ON auth.spring_session (expiry_time);
CREATE INDEX spring_session_ix_principal_name ON auth.spring_session (principal_name);

CREATE TABLE auth.spring_session_attributes (
    session_primary_id  CHAR(36)     NOT NULL,
    attribute_name       VARCHAR(200) NOT NULL,
    attribute_bytes       BYTEA        NOT NULL,
    CONSTRAINT spring_session_attributes_pk PRIMARY KEY (session_primary_id, attribute_name),
    CONSTRAINT spring_session_attributes_fk FOREIGN KEY (session_primary_id)
        REFERENCES auth.spring_session (primary_id) ON DELETE CASCADE
);
