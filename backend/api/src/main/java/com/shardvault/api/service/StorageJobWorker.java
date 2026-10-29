package com.shardvault.api.service;

import com.shardvault.storage.ProviderRegistry;
import com.shardvault.storage.ProviderUnavailableException;
import com.shardvault.storage.ReliabilityTier;
import com.shardvault.storage.StorageProvider;
import com.shardvault.storage.StorageRouter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.*;

/** Low-rate outbox worker for replica repair and sampled integrity scrubbing. */
@Component
public class StorageJobWorker {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final ProviderRegistry registry;
    private final StorageRouter router;
    private final ChunkReplicaReader reader;

    public StorageJobWorker(JdbcTemplate jdbc, org.springframework.transaction.PlatformTransactionManager transactionManager,
                            ProviderRegistry registry, StorageRouter router, ChunkReplicaReader reader) {
        this.jdbc = jdbc; this.transactions = new TransactionTemplate(transactionManager);
        this.registry = registry; this.router = router; this.reader = reader;
    }

    @Scheduled(fixedDelayString = "${shardvault.routing.worker-delay-ms:5000}")
    public void processOne() {
        Job job = transactions.execute(status -> {
            List<Job> jobs = jdbc.query("select id, job_type, chunk_id, attempts from storage_jobs where status in ('PENDING','RETRY') and available_at <= CURRENT_TIMESTAMP order by created_at for update skip locked limit 1",
                    (rs, row) -> new Job(rs.getObject(1, UUID.class), rs.getString(2), rs.getObject(3, UUID.class), rs.getInt(4)));
            if (jobs.isEmpty()) return null;
            Job selected = jobs.getFirst();
            jdbc.update("update storage_jobs set status = 'PROCESSING', locked_at = CURRENT_TIMESTAMP where id = ?", selected.id());
            return selected;
        });
        if (job == null) return;
        try {
            if (job.type().equals("SCRUB")) reader.readVerified(job.chunkId());
            else repair(job.chunkId());
            jdbc.update("update storage_jobs set status = 'DONE', completed_at = CURRENT_TIMESTAMP, locked_at = null, last_error = null where id = ?", job.id());
        } catch (Exception ex) {
            int attempts = job.attempts() + 1;
            if (attempts >= 10) {
                jdbc.update("update storage_jobs set status = 'DEAD_LETTER', attempts = ?, locked_at = null, last_error = ? where id = ?",
                        attempts, safeError(ex), job.id());
            } else {
                long delaySeconds = Math.min(3600, 5L << Math.min(9, attempts));
                jdbc.update("update storage_jobs set status = 'RETRY', attempts = ?, available_at = ?, locked_at = null, last_error = ? where id = ?",
                        attempts, Instant.now().plusSeconds(delaySeconds), safeError(ex), job.id());
            }
        }
    }

    @Scheduled(cron = "${shardvault.routing.scrub-cron:0 17 3 * * *}")
    public void enqueueScrubSample() {
        List<UUID> ids = jdbc.query("select id from chunks order by random() limit 20", (rs, row) -> rs.getObject(1, UUID.class));
        ids.forEach(id -> jdbc.update("insert into storage_jobs(id, job_type, chunk_id, status, available_at) values (?, 'SCRUB', ?, 'PENDING', CURRENT_TIMESTAMP)", UUID.randomUUID(), id));
    }

    private void repair(UUID chunkId) {
        byte[] ciphertext = reader.readVerified(chunkId);
        Target target = jdbc.queryForObject("select c.plain_size + 16, f.replication_policy from chunks c join files f on f.id = c.file_id where c.id = ?",
                (rs, row) -> new Target(rs.getLong(1), rs.getString(2)), chunkId);
        int desired = switch (target.policy().toUpperCase(Locale.ROOT)) {
            case "IMPORTANT" -> 2;
            case "CRITICAL" -> 3;
            default -> 1;
        };
        List<String> allProviders = jdbc.query("select provider_id from chunk_replicas where chunk_id = ?", (rs, row) -> rs.getString(1), chunkId);
        List<String> healthyProviders = jdbc.query("select provider_id from chunk_replicas where chunk_id = ? and status = 'HEALTHY'", (rs, row) -> rs.getString(1), chunkId);
        if (healthyProviders.size() >= desired) return;
        boolean hasNormalSource = jdbc.queryForObject("select count(*) from chunk_replicas r join providers p on p.id = r.provider_id where r.chunk_id = ? and r.status = 'HEALTHY' and p.type <> 'TELEGRAM_BOT'", Integer.class, chunkId) > 0;
        if (!hasNormalSource) throw new ProviderUnavailableException("No normal-reliability source replica is available");
        List<StorageProvider> enabled = registry.loadEnabledProviders();
        try {
            List<StorageRouter.Candidate> candidates = router.candidates(enabled, enabled.stream().map(StorageProvider::id).collect(java.util.stream.Collectors.toSet()),
                    target.encryptedSize(), target.encryptedSize(), Set.copyOf(allProviders), 0, true);
            if (candidates.isEmpty()) throw new ProviderUnavailableException("No eligible provider for repair");
            StorageProvider destination = candidates.getFirst().provider();
            String durableKey = destination.putAndGetKey("repair/" + chunkId + "/" + UUID.randomUUID(), new ByteArrayInputStream(ciphertext), ciphertext.length);
            jdbc.update("insert into chunk_replicas(id, chunk_id, provider_id, object_key, status) values (?, ?, ?, ?, 'HEALTHY')",
                    UUID.randomUUID(), chunkId, destination.id(), durableKey);
            updateFileState(chunkId, desired);
        } finally {
            enabled.stream().filter(AutoCloseable.class::isInstance).map(AutoCloseable.class::cast)
                    .forEach(closeable -> { try { closeable.close(); } catch (Exception ignored) { } });
        }
    }

    private void updateFileState(UUID chunkId, int desired) {
        jdbc.update("update files set replication_state = case when not exists (select 1 from chunks c where c.file_id = files.id and (select count(*) from chunk_replicas r where r.chunk_id = c.id and r.status = 'HEALTHY') < ?) then 'HEALTHY' else 'DEGRADED' end where id = (select file_id from chunks where id = ?)", desired, chunkId);
    }

    private static String safeError(Exception ex) {
        String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
        return message.substring(0, Math.min(1024, message.length()));
    }

    private record Job(UUID id, String type, UUID chunkId, int attempts) {}
    private record Target(long encryptedSize, String policy) {}
}
