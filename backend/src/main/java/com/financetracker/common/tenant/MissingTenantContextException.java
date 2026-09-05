package com.financetracker.common.tenant;

import org.springframework.transaction.CannotCreateTransactionException;

/**
 * Thrown when a transaction would run with no tenant and was not declared as a system transaction.
 *
 * Failing here is the point.
 * Row-Level Security would return zero rows instead, so the bug would look like missing data rather than a defect, and could reach production unnoticed.
 */
public class MissingTenantContextException extends CannotCreateTransactionException {

    public MissingTenantContextException() {
        super("No tenant context for this transaction. "
                + "Wrap the work in CurrentTenantContext.runAs(userId, ...), "
                + "or in runAsSystem(...) if it legitimately has no user.");
    }
}
