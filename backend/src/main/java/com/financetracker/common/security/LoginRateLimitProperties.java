package com.financetracker.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** SR-39's login limit (D-40): a tight per-minute ceiling and a looser per-hour one, stacked on the same key. */
@ConfigurationProperties(prefix = "ft.login-rate-limit")
record LoginRateLimitProperties(@DefaultValue("5") int perMinute, @DefaultValue("20") int perHour) {
}
