package com.financetracker.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Proves tenant isolation actually holds when connected as ft_app (DM-30, SR-03). */
class RowLevelSecurityTest extends SchemaTestBase {

    private static final String INSUFFICIENT_PRIVILEGE = "42501";
    private static final String INVALID_TEXT_REPRESENTATION = "22P02";

    @Test
    void noSettingReturnsZeroRowsOnEveryTable() throws SQLException {
        createTwoTenants();

        Connection app = appConnection();
        assertThat(scalar(app, "SELECT count(*) FROM app.users")).isZero();
        assertThat(scalar(app, "SELECT count(*) FROM app.accounts")).isZero();
        assertThat(scalar(app, "SELECT count(*) FROM app.transactions")).isZero();
    }

    @Test
    void settingUserIdShowsOnlyThatTenantsRows() throws SQLException {
        Tenant a = createTwoTenants().a();

        Connection app = appConnection();
        setUserId(app, a.userId());

        assertThat(ids(app, "SELECT id FROM app.users")).containsExactly(a.userId());
        assertThat(ids(app, "SELECT id FROM app.accounts")).containsExactly(a.accountId());
        assertThat(ids(app, "SELECT id FROM app.transactions")).containsExactly(a.transactionId());
    }

    @Test
    void insertRefusedWhenTheRowCarriesAnotherTenant() throws SQLException {
        TwoTenants tenants = createTwoTenants();

        Connection app = appConnection();
        setUserId(app, tenants.a().userId());

        SQLException refused = expectFailure(
                app,
                "INSERT INTO app.accounts (user_id, name, type, dedup_method) VALUES (?, 'should be refused', 'ASSET', 'NONE')",
                tenants.b().userId());
        assertThat(refused.getSQLState()).isEqualTo(INSUFFICIENT_PRIVILEGE);
    }

    @Test
    void updateRefusedWhenItMovesARowToAnotherTenant() throws SQLException {
        TwoTenants tenants = createTwoTenants();

        Connection app = appConnection();
        setUserId(app, tenants.a().userId());

        SQLException refused = expectFailure(
                app,
                "UPDATE app.accounts SET user_id = ? WHERE id = ?",
                tenants.b().userId(),
                tenants.a().accountId());
        assertThat(refused.getSQLState()).isEqualTo(INSUFFICIENT_PRIVILEGE);
    }

    @Test
    void deletingAnotherTenantsRowAffectsNoRows() throws SQLException {
        TwoTenants tenants = createTwoTenants();

        Connection app = appConnection();
        setUserId(app, tenants.a().userId());
        try (PreparedStatement statement = app.prepareStatement("DELETE FROM app.accounts WHERE id = ?")) {
            statement.setLong(1, tenants.b().accountId());
            assertThat(statement.executeUpdate()).isZero();
        }

        assertThat(scalar(tenants.migrator(), "SELECT count(*) FROM app.accounts WHERE id = ?", tenants.b().accountId()))
                .as("B's account was never deleted")
                .isEqualTo(1);
    }

    @Test
    void theSettingDoesNotSurviveThePreviousTransactionOnTheSameConnection() throws SQLException {
        Tenant a = createTwoTenants().a();

        Connection app = appConnection();
        setUserId(app, a.userId());
        assertThat(scalar(app, "SELECT count(*) FROM app.accounts")).isEqualTo(1);

        app.commit();

        // Once a SET LOCAL has touched app.user_id in this session, ending the transaction reverts it to '', not to unset, so a forgotten SET LOCAL on the next transaction fails the policy's cast rather than quietly returning zero rows.
        SQLException refused = expectFailure(app, "SELECT id FROM app.accounts");
        assertThat(refused.getSQLState())
                .as("a pooled connection must fail closed rather than reuse the previous transaction's tenant")
                .isEqualTo(INVALID_TEXT_REPRESENTATION);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "not-a-number"})
    void malformedUserIdFailsClosedInsteadOfReturningRows(String malformedUserId) throws SQLException {
        createTwoTenants();

        Connection app = appConnection();
        setUserId(app, malformedUserId);

        SQLException refused = expectFailure(app, "SELECT id FROM app.accounts");
        assertThat(refused.getSQLState()).isEqualTo(INVALID_TEXT_REPRESENTATION);
    }

    private TwoTenants createTwoTenants() throws SQLException {
        Connection migrator = migratorConnection();
        Tenant a = createTenant(migrator);
        Tenant b = createTenant(migrator);
        migrator.commit();
        return new TwoTenants(migrator, a, b);
    }

    private static Tenant createTenant(Connection migrator) throws SQLException {
        long userId = TestFixtures.insertUser(migrator);
        long accountId = TestFixtures.insertAccount(migrator, userId);
        long transactionId = TestFixtures.insertManualTransaction(migrator, userId, accountId, 100_00L, "fixture");
        return new Tenant(userId, accountId, transactionId);
    }

    private static long scalar(Connection connection, String sql, Object... parameters) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < parameters.length; i++) {
                statement.setObject(i + 1, parameters[i]);
            }
            try (ResultSet row = statement.executeQuery()) {
                row.next();
                return row.getLong(1);
            }
        }
    }

    private static List<Long> ids(Connection connection, String sql) throws SQLException {
        List<Long> ids = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                ids.add(rows.getLong(1));
            }
        }
        return ids;
    }

    private record Tenant(long userId, long accountId, long transactionId) {
    }

    /** The fixture connection is kept open so a test can query it directly: it bypasses RLS, so it sees a row regardless of what ft_app just did to it. */
    private record TwoTenants(Connection migrator, Tenant a, Tenant b) {
    }
}
