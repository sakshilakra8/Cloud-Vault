package com.shardvault.api.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class TransferMetrics {
    private final Counter downloadBytes;
    private final JdbcTemplate jdbc;

    public TransferMetrics(MeterRegistry registry, JdbcTemplate jdbc) {
        this.downloadBytes = Counter.builder("shardvault.download.bytes").description("Verified ciphertext bytes served").register(registry);
        Counter.builder("shardvault.upload.bytes").description("Accepted upload bytes").register(registry);
        this.jdbc = jdbc;
        io.micrometer.core.instrument.Gauge.builder("shardvault.replication.queue.depth", this, TransferMetrics::queueDepth).register(registry);
        io.micrometer.core.instrument.Gauge.builder("shardvault.replication.degraded.files", this, TransferMetrics::degradedFiles).register(registry);
    }

    public void recordDownloadBytes(long bytes) { if (bytes > 0) downloadBytes.increment(bytes); }
    public long queueDepth() { return jdbc.queryForObject("select count(*) from storage_jobs where status in ('PENDING','RETRY','PROCESSING')", Long.class); }
    public long degradedFiles() { return jdbc.queryForObject("select count(*) from files where replication_state = 'DEGRADED'", Long.class); }
}
