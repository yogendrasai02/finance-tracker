package com.financetracker.common.security;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;

import org.springframework.stereotype.Component;

/**
 * One Bucket4j bucket per key, each carrying two stacked limits so a fast burst and a slow, sustained attempt both trip it (D-40, SR-39).
 *
 * In-process and never evicted: today's traffic is one owner logging in from their own devices, which generates too few distinct keys for that to matter (D-40).
 * That stops being true once this application is reachable by people other than the owner, which the roadmap's closed-circle phase (SPEC.md) will do.
 * A flood of distinct, attacker-chosen emails and IPs would then grow this map without bound.
 * Tracked as a known gap in plans/STATUS.md rather than fixed now, so the tradeoff is a decision and not something later work discovers by accident.
 */
@Component
class LoginRateLimiter {

    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    private final int perMinute;

    private final int perHour;

    LoginRateLimiter(LoginRateLimitProperties properties) {
        this.perMinute = properties.perMinute();
        this.perHour = properties.perHour();
    }

    ConsumptionProbe tryConsume(String key) {
        return buckets.computeIfAbsent(key, ignored -> newBucket()).tryConsumeAndReturnRemaining(1);
    }

    private Bucket newBucket() {
        return Bucket.builder()
                .addLimit(limit -> limit.capacity(perMinute).refillGreedy(perMinute, Duration.ofMinutes(1)))
                .addLimit(limit -> limit.capacity(perHour).refillGreedy(perHour, Duration.ofHours(1)))
                .build();
    }
}
