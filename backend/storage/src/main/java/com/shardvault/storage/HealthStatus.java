package com.shardvault.storage;

public record HealthStatus(boolean up, long latencyMs) {
    public HealthStatus {
        if (latencyMs < 0) {
            throw new IllegalArgumentException("latencyMs must be non-negative");
        }
    }

    public static HealthStatus up(long latencyMs) {
        return new HealthStatus(true, latencyMs);
    }

    public static HealthStatus down(long latencyMs) {
        return new HealthStatus(false, latencyMs);
    }
}
