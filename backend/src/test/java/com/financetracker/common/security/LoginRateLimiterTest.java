package com.financetracker.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.bucket4j.ConsumptionProbe;

import org.junit.jupiter.api.Test;

/** The bucket math in isolation, with no Spring context and no HTTP involved. */
class LoginRateLimiterTest {

    private final LoginRateLimiter rateLimiter = new LoginRateLimiter(new LoginRateLimitProperties(5, 20));

    @Test
    void shouldAllowUpToThePerMinuteLimitThenReject() {
        for (int attempt = 1; attempt <= 5; attempt++) {
            assertThat(rateLimiter.tryConsume("same-key").isConsumed()).as("attempt %d", attempt).isTrue();
        }

        ConsumptionProbe sixth = rateLimiter.tryConsume("same-key");

        assertThat(sixth.isConsumed()).isFalse();
        assertThat(sixth.getNanosToWaitForRefill()).isPositive();
    }

    @Test
    void shouldTrackEachKeyIndependently() {
        for (int attempt = 1; attempt <= 5; attempt++) {
            rateLimiter.tryConsume("key-a");
        }

        assertThat(rateLimiter.tryConsume("key-a").isConsumed()).isFalse();
        assertThat(rateLimiter.tryConsume("key-b").isConsumed()).isTrue();
    }
}
