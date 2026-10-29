package com.shardvault.api.service;

import com.shardvault.storage.ProviderException;
import com.shardvault.storage.ProviderRegistry;
import com.shardvault.storage.StorageProvider;
import com.shardvault.storage.StorageRouter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.security.MessageDigest;
import java.util.*;

/** Reads and verifies chunk ciphertext, failing over and queuing repair for bad copies. */
@Service
public class ChunkReplicaReader {
    private final JdbcTemplate jdbc;
    private final ProviderRegistry registry;
    private final StorageRouter router;

    public ChunkReplicaReader(JdbcTemplate jdbc, ProviderRegistry registry, StorageRouter router) {
        this.jdbc = jdbc; this.registry = registry; this.router = router;
    }

    public byte[] readVerified(UUID chunkId) {
        ChunkMetadata chunk = jdbc.queryForObject("select ciphertext_sha256, plain_size from chunks where id = ?",
                (rs, row) -> new ChunkMetadata(rs.getBytes(1), rs.getLong(2) + 16), chunkId);
        if (chunk == null) throw new NoSuchElementException("Chunk not found");
        List<Replica> replicas = jdbc.query("select provider_id, object_key from chunk_replicas where chunk_id = ? and status = 'HEALTHY'",
                (rs, row) -> new Replica(rs.getString(1), rs.getString(2)), chunkId);
        List<StorageProvider> providers = registry.loadEnabledProviders();
        try {
            Set<String> ids = replicas.stream().map(Replica::providerId).collect(java.util.stream.Collectors.toSet());
            Map<String, Replica> byProvider = new HashMap<>(); replicas.forEach(r -> byProvider.put(r.providerId(), r));
            List<StorageRouter.Candidate> candidates = router.candidates(providers, ids, chunk.encryptedSize(), 0, Set.of(), 0, true);
            for (StorageRouter.Candidate candidate : candidates) {
                StorageProvider provider = candidate.provider();
                Replica replica = byProvider.get(provider.id());
                if (replica == null) continue;
                try (InputStream input = provider.get(replica.objectKey())) {
                    byte[] ciphertext = input.readAllBytes();
                    if (MessageDigest.isEqual(MessageDigest.getInstance("SHA-256").digest(ciphertext), chunk.sha256())) return ciphertext;
                    markBad(chunkId, provider.id(), "CORRUPT");
                } catch (Exception ex) {
                    markBad(chunkId, provider.id(), "SUSPECT");
                }
            }
        } finally {
            providers.stream().filter(AutoCloseable.class::isInstance).map(AutoCloseable.class::cast)
                    .forEach(closeable -> { try { closeable.close(); } catch (Exception ignored) { } });
        }
        throw new com.shardvault.storage.ProviderUnavailableException("No replica passed ciphertext verification for chunk " + chunkId);
    }

    private void markBad(UUID chunkId, String providerId, String state) {
        jdbc.update("update chunk_replicas set status = ? where chunk_id = ? and provider_id = ?", state, chunkId, providerId);
        jdbc.update("update files set replication_state = 'DEGRADED' where id = (select file_id from chunks where id = ?)", chunkId);
        jdbc.update("insert into storage_jobs(id, job_type, chunk_id, status, available_at) values (?, 'REPAIR', ?, 'PENDING', CURRENT_TIMESTAMP)", UUID.randomUUID(), chunkId);
    }

    private record ChunkMetadata(byte[] sha256, long encryptedSize) {}
    private record Replica(String providerId, String objectKey) {}
}
