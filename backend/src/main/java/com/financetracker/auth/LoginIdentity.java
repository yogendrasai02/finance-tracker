package com.financetracker.auth;

/**
 * What the application is allowed to know about a user before it knows who is asking.
 *
 * The columns come from {@code app.find_login_identity}, which is the only read of a user row that works with no tenant set (DM-40).
 * It carries no display name and nothing financial, so a bug on this path cannot leak more than the credential check needs.
 */
public interface LoginIdentity {

    long getId();

    String getPasswordHash();

    String getStatus();
}
