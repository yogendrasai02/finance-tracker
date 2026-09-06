package com.financetracker.common.security;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** SR-38's absolute session lifetime, enforced by {@link AbsoluteSessionTimeoutFilter} on top of Spring Session's own idle timeout. */
@ConfigurationProperties(prefix = "ft.session")
record SessionProperties(Duration absoluteTimeout) {
}
