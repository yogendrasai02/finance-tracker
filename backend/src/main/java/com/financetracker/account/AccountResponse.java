package com.financetracker.account;

/**
 * What FR-1 exposes about an account.
 *
 * No balance field yet: a derived balance needs the {@code transactions} read path, which does not exist yet (STATUS.md known gap).
 * No {@code dedupMethod}: that is an import implementation detail, not something a caller of this endpoint needs.
 */
public record AccountResponse(long id, String name, String type, boolean active) {

    static AccountResponse from(Account account) {
        return new AccountResponse(account.getId(), account.getName(), account.getType(), account.isActive());
    }
}
