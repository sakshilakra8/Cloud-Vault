package com.shardvault.api.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RateLimiterServiceTest {
    @Test
    void limitsAuthRequestsPerIpAndReturnsRetryDelay() {
        RateLimiterService limiter = new RateLimiterService(5, 60, 60, 60);
        for (int i = 0; i < 5; i++) assertTrue(limiter.tryConsumeAuth("192.0.2.1").allowed());
        var rejected = limiter.tryConsumeAuth("192.0.2.1");
        assertFalse(rejected.allowed());
        assertTrue(rejected.retryAfterSeconds() > 0);
        assertTrue(limiter.tryConsumeAuth("192.0.2.2").allowed());
    }
}
