package com.shardvault.api.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
public class QuotaService {
    private final JdbcTemplate jdbc;
    private final long defaultQuotaBytes;
    public QuotaService(JdbcTemplate jdbc, @Value("${shardvault.quota.default-bytes:5368709120}") long defaultQuotaBytes) {
        this.jdbc = jdbc; this.defaultQuotaBytes = defaultQuotaBytes;
    }

    @Transactional
    public void reserve(UUID userId, long expectedBytes) {
        if (expectedBytes <= 0) throw new IllegalArgumentException("Expected upload size must be positive");
        jdbc.update("insert into user_quotas(user_id, quota_bytes) values (?, ?) on conflict(user_id) do nothing", userId, defaultQuotaBytes);
        int changed = jdbc.update("update user_quotas set reserved_bytes = reserved_bytes + ?, updated_at = CURRENT_TIMESTAMP where user_id = ? and used_bytes + reserved_bytes + ? <= quota_bytes",
                expectedBytes, userId, expectedBytes);
        if (changed != 1) throw new QuotaExceededException("Storage quota exceeded");
    }

    @Transactional
    public void commit(UUID userId, long reservedBytes, long actualBytes) {
        if (reservedBytes < 0 || actualBytes < 0) throw new IllegalArgumentException("Byte counts must not be negative");
        jdbc.update("update user_quotas set reserved_bytes = greatest(0, reserved_bytes - ?), used_bytes = used_bytes + ?, updated_at = CURRENT_TIMESTAMP where user_id = ?",
                reservedBytes, actualBytes, userId);
    }

    @Transactional
    public void release(UUID userId, long reservedBytes) {
        jdbc.update("update user_quotas set reserved_bytes = greatest(0, reserved_bytes - ?), updated_at = CURRENT_TIMESTAMP where user_id = ?", reservedBytes, userId);
    }

    public Map<String, Long> usage(UUID userId) {
        jdbc.update("insert into user_quotas(user_id, quota_bytes) values (?, ?) on conflict(user_id) do nothing", userId, defaultQuotaBytes);
        long used = jdbc.queryForObject("select coalesce(sum(size_bytes), 0) from files where user_id = ? and deleted_at is null", Long.class, userId);
        jdbc.update("update user_quotas set used_bytes = ?, updated_at = CURRENT_TIMESTAMP where user_id = ?", used, userId);
        return jdbc.queryForMap("select quota_bytes, used_bytes, reserved_bytes from user_quotas where user_id = ?", userId)
                .entrySet().stream().collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, e -> ((Number) e.getValue()).longValue()));
    }

    @Transactional
    public void setQuota(UUID userId, long quotaBytes) {
        if (quotaBytes < 0) throw new IllegalArgumentException("Quota must not be negative");
        jdbc.update("insert into user_quotas(user_id, quota_bytes) values (?, ?) on conflict(user_id) do update set quota_bytes = excluded.quota_bytes, updated_at = CURRENT_TIMESTAMP", userId, quotaBytes);
    }
}
