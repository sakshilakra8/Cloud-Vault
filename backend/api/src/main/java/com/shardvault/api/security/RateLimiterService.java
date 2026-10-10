package com.shardvault.api.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Service
public class RateLimiterService {

    private final long authPerIpLimit;
    private final long authPerIpDurationSeconds;
    private final long apiPerUserLimit;
    private final long apiPerUserDurationSeconds;

    private final Map<String, Bucket> authBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> apiBuckets = new ConcurrentHashMap<>();

    public record RateLimitDecision(boolean allowed, long retryAfterSeconds, long remainingTokens) {}

    public RateLimiterService(
            @Value("${shardvault.security.rate-limit.auth-per-ip-limit:5}") long authPerIpLimit,
            @Value("${shardvault.security.rate-limit.auth-per-ip-duration-seconds:60}") long authPerIpDurationSeconds,
            @Value("${shardvault.security.rate-limit.api-per-user-limit:60}") long apiPerUserLimit,
            @Value("${shardvault.security.rate-limit.api-per-user-duration-seconds:60}") long apiPerUserDurationSeconds) {
        this.authPerIpLimit = authPerIpLimit;
        this.authPerIpDurationSeconds = authPerIpDurationSeconds;
        this.apiPerUserLimit = apiPerUserLimit;
        this.apiPerUserDurationSeconds = apiPerUserDurationSeconds;
    }

    public RateLimitDecision tryConsumeAuth(String clientIp) {
        Bucket bucket = authBuckets.computeIfAbsent(clientIp, k -> createBucket(authPerIpLimit, authPerIpDurationSeconds));
        return consume(bucket);
    }

    public RateLimitDecision tryConsumeApi(String userOrIpKey) {
        Bucket bucket = apiBuckets.computeIfAbsent(userOrIpKey, k -> createBucket(apiPerUserLimit, apiPerUserDurationSeconds));
        return consume(bucket);
    }

    private Bucket createBucket(long capacity, long durationSeconds) {
        Bandwidth limit = Bandwidth.builder()
                .capacity(capacity)
                .refillGreedy(capacity, Duration.ofSeconds(durationSeconds))
                .build();
        return Bucket.builder()
                .addLimit(limit)
                .build();
    }

    private RateLimitDecision consume(Bucket bucket) {
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (probe.isConsumed()) {
            return new RateLimitDecision(true, 0, probe.getRemainingTokens());
        } else {
            long nanos = probe.getNanosToWaitForRefill();
            long retryAfterSeconds = Math.max(1, TimeUnit.NANOSECONDS.toSeconds(nanos));
            return new RateLimitDecision(false, retryAfterSeconds, 0);
        }
    }

    // Visible for testing to clear rate limits
    public void reset() {
        authBuckets.clear();
        apiBuckets.clear();
    }
}
