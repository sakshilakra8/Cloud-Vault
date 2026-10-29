package com.shardvault.storage;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Chooses providers after strict eligibility filters and ranks them by live telemetry. */
public final class StorageRouter {
    public record Weights(double reliability, double speed, double freeSpace, double cost) {
        public Weights {
            if (reliability < 0 || speed < 0 || freeSpace < 0 || cost < 0
                    || reliability + speed + freeSpace + cost <= 0) {
                throw new IllegalArgumentException("Router weights must be non-negative with a positive total");
            }
        }
        public static Weights defaults() { return new Weights(0.40, 0.25, 0.20, 0.15); }
    }

    public record Candidate(StorageProvider provider, double score) {}

    private final Weights weights;
    private final Map<String, ProviderTelemetry> telemetry;
    private final Map<String, Double> costScores;

    public StorageRouter(Weights weights, Map<String, ProviderTelemetry> telemetry, Map<String, Double> costScores) {
        this.weights = weights == null ? Weights.defaults() : weights;
        this.telemetry = java.util.Collections.unmodifiableMap(telemetry);
        this.costScores = Map.copyOf(costScores);
    }

    public List<Candidate> candidates(List<StorageProvider> providers, Set<String> enabledIds, long chunkBytes,
                                      long requiredFreeBytes, Set<String> excludedIds, long chunkIndex,
                                      boolean includeLowReliabilityForReplicas) {
        Set<String> excluded = excludedIds == null ? Set.of() : excludedIds;
        List<Candidate> eligible = new ArrayList<>();
        double maxLatency = telemetry.values().stream().mapToDouble(ProviderTelemetry::ewmaLatencyMillis).max().orElse(1.0);
        double maxCost = costScores.values().stream().mapToDouble(Double::doubleValue).max().orElse(1.0);
        Map<String, Optional<Long>> freeSpace = new java.util.HashMap<>();
        for (StorageProvider provider : providers) {
            if (!enabledIds.contains(provider.id()) || excluded.contains(provider.id())) continue;
            try { freeSpace.put(provider.id(), provider.freeBytes()); }
            catch (ProviderException ex) { freeSpace.put(provider.id(), Optional.of(-1L)); }
        }
        long maxKnownFree = freeSpace.values().stream().flatMap(Optional::stream).filter(v -> v >= 0).mapToLong(Long::longValue).max().orElse(0);
        for (StorageProvider provider : providers) {
            if (!enabledIds.contains(provider.id()) || excluded.contains(provider.id())) continue;
            ProviderTelemetry metrics = telemetry.get(provider.id());
            if (metrics == null || !metrics.circuitAllowsProbe()) continue;
            if (provider.limits().maxChunkBytes().filter(max -> chunkBytes > max).isPresent()) continue;
            Optional<Long> available = freeSpace.getOrDefault(provider.id(), Optional.empty());
            if (available.filter(bytes -> bytes < 0 || bytes < requiredFreeBytes).isPresent()) continue;
            if (provider.reliability() == ReliabilityTier.LOW && !includeLowReliabilityForReplicas) continue;
            double latencyScore = maxLatency <= 0 ? 1 : 1 - Math.min(1, metrics.ewmaLatencyMillis() / maxLatency);
            double cost = costScores.getOrDefault(provider.id(), 0.0);
            double costScore = maxCost <= 0 ? 1 : 1 - Math.min(1, cost / maxCost);
            double freeSpaceScore = available.isPresent() && maxKnownFree > 0
                    ? Math.min(1, available.get() / (double) maxKnownFree) : metrics.freeSpaceRatio();
            double score = weights.reliability() * metrics.successRate()
                    + weights.speed() * latencyScore
                    + weights.freeSpace() * freeSpaceScore
                    + weights.cost() * costScore;
            eligible.add(new Candidate(provider, score));
        }
        eligible.sort(Comparator.comparingDouble(Candidate::score).reversed());
        // Keep low reliability providers as additional replicas after normal providers.
        if (includeLowReliabilityForReplicas) {
            eligible.sort(Comparator.comparing((Candidate c) -> c.provider().reliability() == ReliabilityTier.LOW)
                    .thenComparing(Comparator.comparingDouble(Candidate::score).reversed()));
        }
        if (eligible.size() > 1) {
            int normalCount = (int) eligible.stream().filter(c -> c.provider().reliability() != ReliabilityTier.LOW).count();
            List<Candidate> spread = new ArrayList<>(eligible.size());
            spread.addAll(rotate(eligible.subList(0, normalCount), chunkIndex));
            spread.addAll(rotate(eligible.subList(normalCount, eligible.size()), chunkIndex));
            return List.copyOf(spread);
        }
        return List.copyOf(eligible);
    }

    private static List<Candidate> rotate(List<Candidate> source, long seed) {
        if (source.size() < 2) return source;
        int offset = Math.floorMod(seed, source.size());
        List<Candidate> rotated = new ArrayList<>(source.size());
        rotated.addAll(source.subList(offset, source.size()));
        rotated.addAll(source.subList(0, offset));
        return rotated;
    }

    public List<StorageProvider> chooseDistinctReplicas(List<Candidate> candidates, int copies) {
        if (copies < 1) throw new IllegalArgumentException("copies must be positive");
        List<StorageProvider> selected = new ArrayList<>(copies);
        Set<String> seen = new HashSet<>();
        for (Candidate candidate : candidates) {
            if (seen.add(candidate.provider().id())) selected.add(candidate.provider());
            if (selected.size() == copies) break;
        }
        if (selected.stream().allMatch(p -> p.reliability() == ReliabilityTier.LOW)) {
            throw new ProviderUnavailableException("At least one non-low-reliability provider is required for a chunk");
        }
        if (selected.size() < copies) throw new ProviderUnavailableException("Insufficient distinct eligible providers for requested replication");
        return List.copyOf(selected);
    }
}
