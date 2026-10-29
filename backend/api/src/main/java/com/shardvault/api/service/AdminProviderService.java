package com.shardvault.api.service;

import com.shardvault.api.metadata.ProviderEntity;
import com.shardvault.api.repository.CloudProviderRepository;
import com.shardvault.storage.HealthStatus;
import com.shardvault.storage.ProviderRegistry;
import com.shardvault.storage.ProviderType;
import com.shardvault.storage.ProviderTelemetry;
import com.shardvault.storage.StorageProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.util.Locale;
import java.util.Map;

@Service
public class AdminProviderService {
    private final CloudProviderRepository providers;
    private final JdbcTemplate jdbc;
    private final ProviderRegistry registry;

    public AdminProviderService(CloudProviderRepository providers, JdbcTemplate jdbc, ProviderRegistry registry) {
        this.providers = providers;
        this.jdbc = jdbc;
        this.registry = registry;
    }

    @Transactional
    public ProviderEntity register(String id, String name, String type, String configRef, Long maxChunkBytes, Long maxObjectBytes) {
        if (providers.existsById(id)) throw new IllegalArgumentException("Provider id is already registered");
        ProviderType providerType = ProviderType.valueOf(type.toUpperCase(Locale.ROOT));
        if (id.isBlank() || id.length() > 64 || configRef.isBlank() || configRef.length() > 64)
            throw new IllegalArgumentException("Provider id or config reference is invalid");
        ProviderEntity provider = new ProviderEntity();
        provider.id = id;
        provider.name = name;
        provider.type = providerType.name();
        provider.configRef = configRef;
        provider.enabled = false;
        provider.health = "unknown";
        provider.maxChunkBytes = maxChunkBytes;
        provider.maxObjectBytes = maxObjectBytes;
        return providers.save(provider);
    }

    @Transactional
    public ProviderEntity setEnabled(String id, boolean enabled) {
        ProviderEntity provider = providers.findById(id).orElseThrow(() -> new java.util.NoSuchElementException("Provider not found"));
        provider.enabled = enabled;
        return providers.save(provider);
    }

    @Transactional
    public Map<String, Object> health(String id) {
        ProviderEntity entity = providers.findById(id).orElseThrow(() -> new java.util.NoSuchElementException("Provider not found"));
        StorageProvider provider = registry.loadProvider(id).orElseThrow(() -> new java.util.NoSuchElementException("Provider configuration could not be loaded"));
        try {
            HealthStatus status = provider.health();
            entity.health = status.up() ? "up" : "down";
            providers.save(entity);
            return Map.of("id", id, "status", entity.health, "latencyMillis", status.latencyMs());
        } finally {
            if (provider instanceof AutoCloseable closeable) {
                try { closeable.close(); } catch (Exception ignored) { }
            }
        }
    }

    public Map<String, Object> usage(String id) {
        if (!providers.existsById(id)) throw new java.util.NoSuchElementException("Provider not found");
        long replicas = jdbc.queryForObject("select count(*) from chunk_replicas where provider_id = ?", Long.class, id);
        long bytes = jdbc.queryForObject("select coalesce(sum(c.plain_size), 0) from chunk_replicas r join chunks c on c.id = r.chunk_id where r.provider_id = ?", Long.class, id);
        return Map.of("providerId", id, "replicaCount", replicas, "logicalBytes", bytes);
    }

    public Map<String, Object> stats() {
        return registry.telemetrySnapshot().entrySet().stream().collect(java.util.stream.Collectors.toMap(
                Map.Entry::getKey, e -> Map.of("successRate", e.getValue().successRate(),
                        "ewmaLatencyMillis", e.getValue().ewmaLatencyMillis(),
                        "freeSpaceRatio", e.getValue().freeSpaceRatio(),
                        "circuit", e.getValue().circuitBreaker().getState().name())));
    }

    public Map<String, Long> replicationOverview() {
        long healthy = jdbc.queryForObject("select count(*) from files where replication_state = 'HEALTHY'", Long.class);
        long degraded = jdbc.queryForObject("select count(*) from files where replication_state = 'DEGRADED'", Long.class);
        long queued = jdbc.queryForObject("select count(*) from storage_jobs where status in ('PENDING','RETRY')", Long.class);
        long deadLetter = jdbc.queryForObject("select count(*) from storage_jobs where status = 'DEAD_LETTER'", Long.class);
        return Map.of("healthyFiles", healthy, "degradedFiles", degraded, "queuedJobs", queued, "deadLetterJobs", deadLetter);
    }

    public Map<String, Object> triggerRepair(java.util.UUID chunkId) {
        int exists = jdbc.queryForObject("select count(*) from chunks where id = ?", Integer.class, chunkId);
        if (exists == 0) throw new java.util.NoSuchElementException("Chunk not found");
        java.util.UUID jobId = java.util.UUID.randomUUID();
        jdbc.update("insert into storage_jobs(id, job_type, chunk_id, status, available_at) values (?, 'REPAIR', ?, 'PENDING', CURRENT_TIMESTAMP)", jobId, chunkId);
        return Map.of("jobId", jobId, "chunkId", chunkId, "status", "PENDING");
    }

    public void simulateFailure(String providerId) {
        ProviderTelemetry telemetry = registry.telemetrySnapshot().get(providerId);
        if (telemetry == null) throw new java.util.NoSuchElementException("Provider is not loaded");
        for (int i = 0; i < 5; i++) telemetry.recordFailure(1);
    }
}
