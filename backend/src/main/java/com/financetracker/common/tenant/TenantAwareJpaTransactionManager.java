package com.financetracker.common.tenant;

import jakarta.persistence.EntityManagerFactory;

import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Applies the tenant to every transaction, so no service method has to remember to do it (SR-02, DM-30).
 *
 * The setting is issued through the transaction's own {@code EntityManager}, which is the only way to be sure it lands on the connection Hibernate goes on to use.
 * Hibernate acquires that connection lazily, so setting it on a connection taken from the pool separately would be applied to the wrong one, and every query would then return zero rows with no error.
 *
 * {@code set_config(..., true)} is local to the transaction, so one request's tenant cannot be read by the next request that borrows the same connection.
 * Every transaction sets the value, including the ones with no user, so what a connection carries never depends on which transaction used it last.
 */
public class TenantAwareJpaTransactionManager extends JpaTransactionManager {

    private static final String APPLY_TENANT = "select set_config('app.user_id', :userId, true)";

    /**
     * The value a system transaction runs with.
     *
     * A system transaction cannot simply leave the setting alone.
     * Once a connection has carried a tenant, ending that transaction reverts {@code app.user_id} to an empty string rather than to unset, and the policies then fail their {@code ::BIGINT} cast, which is the deliberate fail-closed behaviour for a forgotten tenant.
     * Setting a value no row can ever have keeps that failure for genuinely forgotten context, while a system transaction reads zero domain rows instead of erroring.
     * Identity columns start at 1 and never go negative.
     */
    private static final String NO_TENANT = "-1";

    public TenantAwareJpaTransactionManager(EntityManagerFactory entityManagerFactory) {
        super(entityManagerFactory);
    }

    /**
     * The check runs before {@code super.doBegin}, so a transaction with no tenant is refused before an EntityManager or a connection has been taken.
     */
    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
        Long userId = CurrentTenantContext.currentUserId();
        if (userId == null && !CurrentTenantContext.isSystem()) {
            throw new MissingTenantContextException();
        }

        super.doBegin(transaction, definition);

        applyTenant(userId == null ? NO_TENANT : Long.toString(userId));
    }

    private void applyTenant(String userId) {
        EntityManagerHolder holder =
                (EntityManagerHolder) TransactionSynchronizationManager.getResource(obtainEntityManagerFactory());
        if (holder == null) {
            throw new IllegalStateException("The transaction has no EntityManager bound, so the tenant cannot be applied.");
        }

        holder.getEntityManager()
                .createNativeQuery(APPLY_TENANT)
                .setParameter("userId", userId)
                .getSingleResult();
    }
}
