package com.shardvault.storage;

import java.time.Duration;

public record ReliabilityConfig(Duration timeout, int maxAttempts, Duration initialBackoff) {
    public ReliabilityConfig {
        if (timeout == null || timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be at least 1");
        }
        if (initialBackoff == null || initialBackoff.isNegative() || initialBackoff.isZero()) {
            throw new IllegalArgumentException("initialBackoff must be positive");
        }
    }

    public static ReliabilityConfig defaults() {
        return new ReliabilityConfig(Duration.ofSeconds(10), 3, Duration.ofMillis(100));
    }
}
