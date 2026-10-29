package com.shardvault.storage;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;

/** Rolling provider health and latency measurements used by routing and failover. */
public final class ProviderTelemetry {
    private final Deque<Boolean> recentOutcomes = new ArrayDeque<>();
    private final CircuitBreaker circuitBreaker;
    private volatile double ewmaLatencyMillis = 250.0;
    private volatile double freeSpaceRatio = 0.5;

    public ProviderTelemetry(String providerId, int failureThreshold, Duration openDuration) {
        circuitBreaker = CircuitBreaker.of(providerId, CircuitBreakerConfig.custom()
                .failureRateThreshold(100)
                .minimumNumberOfCalls(failureThreshold)
                .slidingWindowSize(Math.max(2, failureThreshold))
                .permittedNumberOfCallsInHalfOpenState(1)
                .waitDurationInOpenState(openDuration)
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .build());
    }

    public void recordSuccess(long latencyMillis) {
        recordOutcome(true);
        updateLatency(latencyMillis);
        circuitBreaker.onSuccess(Math.max(0, latencyMillis), java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    public void recordFailure(long latencyMillis) {
        recordOutcome(false);
        updateLatency(latencyMillis);
        circuitBreaker.onError(Math.max(0, latencyMillis), java.util.concurrent.TimeUnit.MILLISECONDS, new ProviderUnavailableException("Provider call failed"));
    }

    private synchronized void recordOutcome(boolean success) {
        recentOutcomes.addLast(success);
        while (recentOutcomes.size() > 100) recentOutcomes.removeFirst();
    }

    private synchronized void updateLatency(long latencyMillis) {
        double value = Math.max(0, latencyMillis);
        ewmaLatencyMillis = ewmaLatencyMillis * 0.8 + value * 0.2;
    }

    public synchronized double successRate() {
        if (recentOutcomes.isEmpty()) return 1.0;
        return recentOutcomes.stream().filter(Boolean::booleanValue).count() / (double) recentOutcomes.size();
    }

    public double ewmaLatencyMillis() { return ewmaLatencyMillis; }
    public double freeSpaceRatio() { return freeSpaceRatio; }
    public void setFreeSpaceRatio(double value) { freeSpaceRatio = Math.max(0, Math.min(1, value)); }
    public boolean circuitClosed() { return circuitBreaker.getState() == CircuitBreaker.State.CLOSED; }
    public boolean circuitAllowsProbe() {
        CircuitBreaker.State state = circuitBreaker.getState();
        return state == CircuitBreaker.State.CLOSED || state == CircuitBreaker.State.HALF_OPEN;
    }
    public CircuitBreaker circuitBreaker() { return circuitBreaker; }
}
