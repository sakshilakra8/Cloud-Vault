package com.shardvault.api.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Commits readiness after the synchronous first replica, then writes replication work to the outbox. */
@Service
public class ReplicationCoordinator {
    private final JdbcTemplate jdbc;
    public ReplicationCoordinator(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional
    public void firstReplicaCommitted(UUID fileId) {
        String policy = jdbc.queryForObject("select replication_policy from files where id = ?", String.class, fileId);
        int desired = switch (policy.toUpperCase(java.util.Locale.ROOT)) {
            case "IMPORTANT" -> 2;
            case "CRITICAL" -> 3;
            default -> 1;
        };
        jdbc.update("update files set status = 'READY', replication_state = case when ? = 1 then 'HEALTHY' else 'DEGRADED' end, updated_at = CURRENT_TIMESTAMP where id = ?", desired, fileId);
        if (desired > 1) {
            jdbc.update("insert into storage_jobs(id, job_type, chunk_id, status, available_at) " +
                            "select gen_random_uuid(), 'REPLICATE', c.id, 'PENDING', CURRENT_TIMESTAMP from chunks c " +
                            "where c.file_id = ? and (select count(*) from chunk_replicas r where r.chunk_id = c.id and r.status = 'HEALTHY') < ?",
                    fileId, desired);
        }
    }
}
