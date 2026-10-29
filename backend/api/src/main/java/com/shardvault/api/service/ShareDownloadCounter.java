package com.shardvault.api.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/** Atomic database-backed claim for one share download slot. */
@Repository
public class ShareDownloadCounter {
    private final JdbcTemplate jdbc;
    public ShareDownloadCounter(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public boolean tryConsume(UUID shareId) {
        return jdbc.update("update share_links set download_count = download_count + 1 where id = ? and revoked = false and (expires_at is null or expires_at > CURRENT_TIMESTAMP) and (max_downloads is null or download_count < max_downloads)", shareId) == 1;
    }
}
