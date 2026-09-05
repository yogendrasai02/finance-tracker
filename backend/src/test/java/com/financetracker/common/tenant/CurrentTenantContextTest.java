package com.financetracker.common.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** The scoped runners are the only way to set a tenant, so these tests are what stop a value surviving on a pooled thread. */
class CurrentTenantContextTest {

    @AfterEach
    void contextIsEmptyAfterEveryTest() {
        assertThat(CurrentTenantContext.currentUserId()).isNull();
        assertThat(CurrentTenantContext.isSystem()).isFalse();
    }

    @Test
    void shouldExposeTheUserIdInsideTheScopeAndNothingOutsideIt() {
        Long inside = CurrentTenantContext.runAs(7L, CurrentTenantContext::currentUserId);

        assertThat(inside).isEqualTo(7L);
        assertThat(CurrentTenantContext.currentUserId()).isNull();
    }

    @Test
    void shouldClearTheContextWhenTheWorkThrows() {
        assertThatThrownBy(() -> CurrentTenantContext.runAs(7L, () -> {
            throw new IllegalStateException("work failed");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(CurrentTenantContext.currentUserId()).isNull();
    }

    @Test
    void shouldRestoreTheOuterTenantWhenScopesAreNested() {
        Long outerAfterInner = CurrentTenantContext.runAs(7L, () -> {
            CurrentTenantContext.runAs(8L, () -> null);
            return CurrentTenantContext.currentUserId();
        });

        assertThat(outerAfterInner).isEqualTo(7L);
    }

    @Test
    void shouldRestoreTheOuterTenantAfterASystemScope() {
        Long outerAfterSystem = CurrentTenantContext.runAs(7L, () -> {
            Long insideSystem = CurrentTenantContext.runAsSystem(CurrentTenantContext::currentUserId);
            assertThat(insideSystem).isNull();
            return CurrentTenantContext.currentUserId();
        });

        assertThat(outerAfterSystem).isEqualTo(7L);
    }

    @Test
    void shouldMarkOnlyASystemScopeAsSystem() {
        assertThat(CurrentTenantContext.runAsSystem(CurrentTenantContext::isSystem)).isTrue();
        assertThat(CurrentTenantContext.runAs(7L, CurrentTenantContext::isSystem)).isFalse();
    }

    @Test
    void shouldRefuseRequireUserIdWhenThereIsNoTenant() {
        assertThatThrownBy(CurrentTenantContext::requireUserId).isInstanceOf(MissingTenantContextException.class);
    }

    @Test
    void shouldReturnTheUserIdFromRequireUserIdInsideAScope() {
        assertThat(CurrentTenantContext.runAs(7L, CurrentTenantContext::requireUserId)).isEqualTo(7L);
    }
}
