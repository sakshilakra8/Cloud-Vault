package com.shardvault.api.service;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class ShareService {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final ShareDownloadCounter downloadCounter;
    private final SecureRandom random = new SecureRandom();

    public ShareService(JdbcTemplate jdbc, PasswordEncoder passwordEncoder, ShareDownloadCounter downloadCounter) {
        this.jdbc = jdbc; this.passwordEncoder = passwordEncoder; this.downloadCounter = downloadCounter;
    }

    @Transactional
    public CreatedShare create(UUID ownerId, UUID fileId, Instant expiresAt, Integer maxDownloads, String password,
                               String ip, String userAgent) {
        FileMetadata file = jdbc.queryForObject("select id, name, size_bytes, mime from files where id = ? and user_id = ? and deleted_at is null",
                (rs, row) -> new FileMetadata(rs.getObject(1, UUID.class), rs.getString(2), rs.getLong(3), rs.getString(4)), fileId, ownerId);
        byte[] bytes = new byte[32]; random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String tokenHash = sha256(token);
        UUID shareId = UUID.randomUUID();
        String passwordHash = password == null || password.isBlank() ? null : passwordEncoder.encode(password);
        jdbc.update("insert into share_links(id, file_id, token_hash, expires_at, max_downloads, download_count, password_hash, revoked, created_at) values (?, ?, ?, ?, ?, 0, ?, false, CURRENT_TIMESTAMP)",
                shareId, fileId, tokenHash, expiresAt, maxDownloads, passwordHash);
        audit(ownerId, "SHARE_CREATE", "FILE", fileId.toString(), "SUCCESS", ip, userAgent);
        return new CreatedShare(shareId, token, expiresAt, maxDownloads, file.name(), file.sizeBytes(), file.mime());
    }

    @Transactional(readOnly = true)
    public ShareMetadata metadata(String token, String password, String ip, String userAgent) {
        ShareRow row = lookup(token);
        ShareState state = state(row);
        if (state == ShareState.ACTIVE) checkPassword(row, password, ip, userAgent);
        if (state != ShareState.ACTIVE) {
            audit(null, "SHARE_ACCESS_METADATA", "FILE", row.fileId().toString(), state.name(), ip, userAgent);
            return new ShareMetadata(row.id(), null, 0, null, state, row.expiresAt(), row.maxDownloads(), row.downloadCount(), row.passwordHash() != null);
        }
        FileMetadata file = file(row.fileId());
        audit(null, "SHARE_ACCESS_METADATA", "FILE", row.fileId().toString(), "SUCCESS", ip, userAgent);
        return new ShareMetadata(row.id(), file.name(), file.sizeBytes(), file.mime(), state, row.expiresAt(), row.maxDownloads(), row.downloadCount(), row.passwordHash() != null);
    }

    /** Atomically consumes one slot. Concurrent callers cannot exceed max_downloads. */
    @Transactional
    public SharedDownload consume(String token, String password, String ip, String userAgent) {
        ShareRow row = lookup(token);
        ShareState state = state(row);
        if (state != ShareState.ACTIVE) {
            audit(null, "SHARE_ACCESS", "FILE", row.fileId().toString(), state.name(), ip, userAgent);
            throw stateException(state);
        }
        checkPassword(row, password, ip, userAgent);
        if (!downloadCounter.tryConsume(row.id())) throw stateException(state(lookup(token)));
        FileMetadata file = file(row.fileId());
        audit(null, "SHARE_ACCESS", "FILE", row.fileId().toString(), "SUCCESS", ip, userAgent);
        return new SharedDownload(row.fileId(), file.name(), file.sizeBytes(), file.mime());
    }

    @Transactional
    public void revoke(UUID ownerId, UUID shareId, String ip, String userAgent) {
        int changed = jdbc.update("update share_links s set revoked = true where s.id = ? and exists (select 1 from files f where f.id = s.file_id and f.user_id = ?)", shareId, ownerId);
        if (changed != 1) throw new java.util.NoSuchElementException("Share link not found");
        audit(ownerId, "SHARE_REVOKE", "SHARE", shareId.toString(), "SUCCESS", ip, userAgent);
    }

    private ShareRow lookup(String token) {
        if (token == null || token.length() > 128) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Share link not found");
        String hash = sha256(token);
        var rows = jdbc.query("select id, file_id, token_hash, expires_at, max_downloads, download_count, password_hash, revoked from share_links where token_hash = ?",
                (rs, row) -> new ShareRow(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getString(3),
                        rs.getTimestamp(4) == null ? null : rs.getTimestamp(4).toInstant(),
                        (Integer) rs.getObject(5), rs.getInt(6), rs.getString(7), rs.getBoolean(8)), hash);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Share link not found");
        ShareRow row = rows.getFirst();
        if (!MessageDigest.isEqual(HexFormat.of().parseHex(row.tokenHash()), HexFormat.of().parseHex(hash)))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Share link not found");
        return row;
    }

    private ShareState state(ShareRow row) {
        if (row.revoked()) return ShareState.REVOKED;
        if (row.expiresAt() != null && !row.expiresAt().isAfter(Instant.now())) return ShareState.EXPIRED;
        if (row.maxDownloads() != null && row.downloadCount() >= row.maxDownloads()) return ShareState.LIMIT_REACHED;
        return ShareState.ACTIVE;
    }

    private void checkPassword(ShareRow row, String password, String ip, String userAgent) {
        if (row.passwordHash() != null && (password == null || !passwordEncoder.matches(password, row.passwordHash()))) {
            audit(null, "SHARE_ACCESS", "FILE", row.fileId().toString(), "FAILURE", ip, userAgent);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Share password is incorrect");
        }
    }

    private ResponseStatusException stateException(ShareState state) {
        return new ResponseStatusException(state == ShareState.ACTIVE ? HttpStatus.CONFLICT : HttpStatus.GONE,
                "Share link is " + state.name());
    }

    private FileMetadata file(UUID id) {
        return jdbc.queryForObject("select id, name, size_bytes, mime from files where id = ? and deleted_at is null",
                (rs, row) -> new FileMetadata(rs.getObject(1, UUID.class), rs.getString(2), rs.getLong(3), rs.getString(4)), id);
    }

    private void audit(UUID userId, String action, String resourceType, String resourceId, String result, String ip, String userAgent) {
        jdbc.update("insert into audit_logs(id, user_id, action, metadata, ip_address, user_agent, resource_type, resource_id, result, created_at) values (?, ?, ?, '{}', ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)",
                UUID.randomUUID(), userId, action, ip, userAgent, resourceType, resourceId, result);
    }

    public static String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception ex) { throw new IllegalStateException("SHA-256 unavailable", ex); }
    }

    public enum ShareState { ACTIVE, EXPIRED, LIMIT_REACHED, REVOKED }
    public record CreatedShare(UUID id, String token, Instant expiresAt, Integer maxDownloads, String fileName, long sizeBytes, String mime) {}
    public record ShareMetadata(UUID id, String fileName, long sizeBytes, String mime, ShareState state, Instant expiresAt, Integer maxDownloads, int downloads, boolean passwordRequired) {}
    public record SharedDownload(UUID fileId, String fileName, long sizeBytes, String mime) {}
    private record FileMetadata(UUID id, String name, long sizeBytes, String mime) {}
    private record ShareRow(UUID id, UUID fileId, String tokenHash, Instant expiresAt, Integer maxDownloads, int downloadCount, String passwordHash, boolean revoked) {}
}
