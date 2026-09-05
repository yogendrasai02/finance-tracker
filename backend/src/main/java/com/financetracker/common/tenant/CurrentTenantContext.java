package com.financetracker.common.tenant;

/**
 * Holds the tenant for the current thread, so {@link TenantAwareJpaTransactionManager} can apply it to every transaction.
 *
 * There is no public setter on purpose.
 * A caller that could set the value could also forget to clear it, and a stale value on a pooled thread is read by the next request as if it belonged to that user.
 * The scoped runners below always restore the previous value, so that failure cannot happen (SR-01).
 */
public final class CurrentTenantContext {

    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();

    private static final ThreadLocal<Boolean> SYSTEM = new ThreadLocal<>();

    private CurrentTenantContext() {
    }

    /** Runs work as the given user, restoring whatever was in place before. */
    public static <T, E extends Exception> T runAs(long userId, TenantWork<T, E> work) throws E {
        return run(userId, false, work);
    }

    /**
     * Runs work with no tenant, for the few paths that legitimately have none: the login lookup, the credential bootstrap, and startup tasks.
     * Row-Level Security still applies, so a domain query inside a system transaction returns zero rows rather than everyone's rows.
     */
    public static <T, E extends Exception> T runAsSystem(TenantWork<T, E> work) throws E {
        return run(null, true, work);
    }

    public static Long currentUserId() {
        return USER_ID.get();
    }

    public static long requireUserId() {
        Long userId = USER_ID.get();
        if (userId == null) {
            throw new MissingTenantContextException();
        }
        return userId;
    }

    static boolean isSystem() {
        return Boolean.TRUE.equals(SYSTEM.get());
    }

    private static <T, E extends Exception> T run(Long userId, boolean system, TenantWork<T, E> work) throws E {
        Long previousUserId = USER_ID.get();
        Boolean previousSystem = SYSTEM.get();
        apply(userId, system);
        try {
            return work.get();
        } finally {
            apply(previousUserId, Boolean.TRUE.equals(previousSystem));
        }
    }

    /** Removes rather than nulls, so a thread returned to the pool carries nothing. */
    private static void apply(Long userId, boolean system) {
        if (userId == null) {
            USER_ID.remove();
        } else {
            USER_ID.set(userId);
        }
        if (system) {
            SYSTEM.set(Boolean.TRUE);
        } else {
            SYSTEM.remove();
        }
    }

    @FunctionalInterface
    public interface TenantWork<T, E extends Exception> {
        T get() throws E;
    }
}
