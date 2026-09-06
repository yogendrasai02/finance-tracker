package com.financetracker.auth;

import java.util.Optional;

import com.financetracker.user.User;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * The one privileged read in the application, kept in its own package so it is easy to find and easy to review.
 *
 * Calls must run inside {@code CurrentTenantContext.runAsSystem(...)}: login has no tenant yet, and every other read of {@code app.users} is hidden by Row-Level Security until one exists.
 * The aliases are quoted because Postgres folds unquoted identifiers to lowercase, and the projection matches on the property name (AS passwordHash: becomes passwordhash without quotes)
 * 
 * Note: Repository and not JpaRepository because the former is a marker interface with not methods and the latter would give this interface a lot of methods.
 */
interface LoginIdentityRepository extends Repository<User, Long> {

    @Query(
            value = """
                    SELECT id AS "id", password_hash AS "passwordHash", status AS "status"
                    FROM app.find_login_identity(:email)
                    """,
            nativeQuery = true)
    Optional<LoginIdentity> findByEmail(@Param("email") String email);
}
