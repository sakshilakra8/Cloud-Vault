package com.shardvault.storage;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class StorageRouterTest {
    @Test void ranksHigherReliabilityFirst() {
        var reliable = provider("reliable", Optional.empty(), ReliabilityTier.NORMAL);
        var unreliable = provider("unreliable", Optional.empty(), ReliabilityTier.NORMAL);
        var telemetry = Map.of("reliable", telemetry("reliable", 10, 0), "unreliable", telemetry("unreliable", 10, 0));
        telemetry.get("unreliable").recordFailure(10);
        var result = router(new StorageRouter.Weights(1, 0, 0, 0), telemetry, Map.of())
                .candidates(List.of(unreliable, reliable), Set.of("reliable", "unreliable"), 100, 0, Set.of(), 0, false);
        assertEquals("reliable", result.getFirst().provider().id());
    }

    @Test void ranksLowerLatencyFirst() {
        var fast = provider("fast", Optional.empty(), ReliabilityTier.NORMAL);
        var slow = provider("slow", Optional.empty(), ReliabilityTier.NORMAL);
        var fastStats = telemetry("fast", 10, 0);
        var slowStats = telemetry("slow", 10, 0);
        fastStats.recordSuccess(1);
        slowStats.recordSuccess(1000);
        var result = router(new StorageRouter.Weights(0, 1, 0, 0), Map.of("fast", fastStats, "slow", slowStats), Map.of())
                .candidates(List.of(slow, fast), Set.of("slow", "fast"), 100, 0, Set.of(), 0, false);
        assertEquals("fast", result.getFirst().provider().id());
    }

    @Test void ranksFreeSpaceRatioAndCost() {
        var room = provider("room", Optional.empty(), ReliabilityTier.NORMAL);
        var cramped = provider("cramped", Optional.empty(), ReliabilityTier.NORMAL);
        var a = telemetry("room", 10, 0); a.setFreeSpaceRatio(0.9);
        var b = telemetry("cramped", 10, 0); b.setFreeSpaceRatio(0.1);
        var spaceResult = router(new StorageRouter.Weights(0, 0, 1, 0), Map.of("room", a, "cramped", b), Map.of())
                .candidates(List.of(cramped, room), Set.of("room", "cramped"), 10, 0, Set.of(), 0, false);
        assertEquals("room", spaceResult.getFirst().provider().id());
        var costResult = router(new StorageRouter.Weights(0, 0, 0, 1), Map.of("room", a, "cramped", b), Map.of("room", 0.1, "cramped", 0.9))
                .candidates(List.of(cramped, room), Set.of("room", "cramped"), 10, 0, Set.of(), 0, false);
        assertEquals("room", costResult.getFirst().provider().id());
    }

    @Test void hardFiltersDisabledOpenOversizeInsufficientAndDuplicateReplicas() {
        var healthy = provider("healthy", Optional.of(500L), ReliabilityTier.NORMAL);
        var tooSmall = provider("too-small", Optional.of(10L), ReliabilityTier.NORMAL);
        var noSpace = provider("no-space", Optional.of(500L), ReliabilityTier.NORMAL, Optional.of(20L));
        var open = telemetry("open", 2, 0);
        open.recordFailure(1); open.recordFailure(1);
        var metrics = Map.of("healthy", telemetry("healthy", 5, 0), "too-small", telemetry("too-small", 5, 0),
                "no-space", telemetry("no-space", 5, 0), "open", open);
        var result = router(StorageRouter.Weights.defaults(), metrics, Map.of())
                .candidates(List.of(healthy, tooSmall, noSpace, provider("open", Optional.empty(), ReliabilityTier.NORMAL)),
                        Set.of("healthy", "too-small", "no-space"), 100, 50, Set.of(), 0, false);
        assertEquals(List.of("healthy"), result.stream().map(c -> c.provider().id()).toList());
        assertEquals(List.of("healthy"), router(StorageRouter.Weights.defaults(), metrics, Map.of()).chooseDistinctReplicas(result, 1).stream().map(StorageProvider::id).toList());
        assertThrows(ProviderUnavailableException.class, () -> router(StorageRouter.Weights.defaults(), metrics, Map.of()).chooseDistinctReplicas(result, 2));
    }

    @Test void spreadsLargeFileChunksAndKeepsLowTierAsAdditionalCopyOnly() {
        var normalA = provider("normal-a", Optional.empty(), ReliabilityTier.NORMAL);
        var normalB = provider("normal-b", Optional.empty(), ReliabilityTier.NORMAL);
        var low = provider("telegram", Optional.empty(), ReliabilityTier.LOW);
        var metrics = Map.of("normal-a", telemetry("normal-a", 10, 0), "normal-b", telemetry("normal-b", 10, 0), "telegram", telemetry("telegram", 10, 0));
        var router = router(StorageRouter.Weights.defaults(), metrics, Map.of());
        var providers = List.of(normalA, normalB, low);
        var enabled = Set.of("normal-a", "normal-b", "telegram");
        var chunk0 = router.candidates(providers, enabled, 100, 0, Set.of(), 0, true);
        var chunk1 = router.candidates(providers, enabled, 100, 0, Set.of(), 1, true);
        assertNotEquals(chunk0.getFirst().provider().id(), chunk1.getFirst().provider().id());
        assertEquals("telegram", router.chooseDistinctReplicas(chunk0, 3).getLast().id());
        assertThrows(ProviderUnavailableException.class, () -> router.chooseDistinctReplicas(List.of(new StorageRouter.Candidate(low, 1)), 1));
    }

    private static StorageRouter router(StorageRouter.Weights weights, Map<String, ProviderTelemetry> telemetry, Map<String, Double> costs) {
        return new StorageRouter(weights, telemetry, costs);
    }
    private static ProviderTelemetry telemetry(String id, int threshold, long wait) {
        return new ProviderTelemetry(id, threshold, Duration.ofMillis(wait > 0 ? wait : 60_000));
    }
    private static StorageProvider provider(String id, Optional<Long> maxChunk, ReliabilityTier tier) {
        return provider(id, maxChunk, tier, Optional.empty());
    }
    private static StorageProvider provider(String id, Optional<Long> maxChunk, ReliabilityTier tier, Optional<Long> freeBytes) {
        return new StorageProvider() {
            public String id() { return id; }
            public ProviderType type() { return tier == ReliabilityTier.LOW ? ProviderType.TELEGRAM_BOT : ProviderType.LOCAL_FS; }
            public ReliabilityTier reliability() { return tier; }
            public void put(String key, InputStream data, long length) { }
            public InputStream get(String key) { return new ByteArrayInputStream(new byte[0]); }
            public void delete(String key) { }
            public boolean exists(String key) { return false; }
            public HealthStatus health() { return HealthStatus.up(1); }
            public Optional<Long> freeBytes() { return freeBytes; }
            public ProviderLimits limits() { return new ProviderLimits(maxChunk, Optional.empty(), Optional.empty()); }
        };
    }
}
