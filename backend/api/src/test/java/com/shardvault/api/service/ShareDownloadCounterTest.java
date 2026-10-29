package com.shardvault.api.service;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class ShareDownloadCounterTest {
    private final java.util.Map<UUID, String> tokens = new java.util.concurrent.ConcurrentHashMap<>();
    private JdbcTemplate jdbc;
    private ShareDownloadCounter counter;

    @BeforeEach void setUp() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:share-counter-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("create table share_links(id uuid primary key, file_id uuid, token_hash varchar(64), download_count int not null, revoked boolean not null, expires_at timestamp with time zone, max_downloads int, password_hash varchar(255), created_at timestamp with time zone)");
        counter = new ShareDownloadCounter(jdbc);
    }

    @Test void fiftyConcurrentRequestsConsumeExactlyFiveSlots() throws Exception {
        UUID share = insert(5, false, Instant.now().plusSeconds(60));
        int requests = 50;
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successful = new AtomicInteger();
        var pool = Executors.newFixedThreadPool(requests);
        try {
            for (int i = 0; i < requests; i++) pool.submit(() -> {
                try { start.await(); if (counter.tryConsume(share)) successful.incrementAndGet(); }
                catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
            });
            start.countDown();
            pool.shutdown();
            while (!pool.isTerminated()) Thread.sleep(10);
            assertEquals(5, successful.get());
            assertEquals(5, jdbc.queryForObject("select download_count from share_links where id = ?", Integer.class, share));
        } finally { pool.shutdownNow(); }
    }

    @Test void expiredRevokedAndExhaustedLinksCannotConsume() {
        UUID expired = insert(5, false, Instant.now().minusSeconds(5));
        UUID revoked = insert(5, true, Instant.now().plusSeconds(60));
        UUID exhausted = insert(5, false, Instant.now().plusSeconds(60));
        jdbc.update("update share_links set download_count = 5 where id = ?", exhausted);
        assertEquals(false, counter.tryConsume(expired));
        assertEquals(false, counter.tryConsume(revoked));
        assertEquals(false, counter.tryConsume(exhausted));
    }

    @Test void metadataReportsExpiredExhaustedAndRevokedAndRejectsWrongPassword() {
        jdbc.execute("create table files(id uuid primary key, name varchar(100), size_bytes bigint, mime varchar(100), deleted_at timestamp with time zone)");
        jdbc.execute("create table audit_logs(id uuid, user_id uuid, action varchar(40), metadata varchar(20), ip_address varchar(64), user_agent varchar(512), resource_type varchar(24), resource_id varchar(64), result varchar(16), created_at timestamp with time zone)");
        UUID fileId = UUID.randomUUID();
        jdbc.update("insert into files(id,name,size_bytes,mime,deleted_at) values(?, 'test.bin', 7, 'application/octet-stream', null)", fileId);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(encoder.matches("correct", "encoded")).thenReturn(true);
        ShareService service = new ShareService(jdbc, encoder, counter);
        UUID expired = insertShare(fileId, Instant.now().minusSeconds(1), null, 0, false, null);
        UUID revoked = insertShare(fileId, Instant.now().plusSeconds(60), null, 0, true, null);
        UUID exhausted = insertShare(fileId, Instant.now().plusSeconds(60), 1, 1, false, null);
        String protectedToken = "protected-token";
        insertShare(fileId, Instant.now().plusSeconds(60), null, 0, false, "encoded", protectedToken);
        assertEquals(ShareService.ShareState.EXPIRED, service.metadata(token(expired), null, "ip", "ua").state());
        assertEquals(ShareService.ShareState.REVOKED, service.metadata(token(revoked), null, "ip", "ua").state());
        assertEquals(ShareService.ShareState.LIMIT_REACHED, service.metadata(token(exhausted), null, "ip", "ua").state());
        assertThrows(ResponseStatusException.class, () -> service.metadata(protectedToken, "wrong", "ip", "ua"));
        assertEquals(ShareService.ShareState.ACTIVE, service.metadata(protectedToken, "correct", "ip", "ua").state());
    }

    private UUID insert(int limit, boolean revoked, Instant expiresAt) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into share_links(id,download_count,revoked,expires_at,max_downloads) values(?,0,?,?,?)", id, revoked, expiresAt, limit);
        return id;
    }

    private UUID insertShare(UUID fileId, Instant expiry, Integer max, int count, boolean revoked, String password) {
        return insertShare(fileId, expiry, max, count, revoked, password, UUID.randomUUID().toString());
    }
    private UUID insertShare(UUID fileId, Instant expiry, Integer max, int count, boolean revoked, String password, String token) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into share_links(id,file_id,token_hash,expires_at,max_downloads,download_count,password_hash,revoked,created_at) values(?,?,?,?,?,?,?, ?, CURRENT_TIMESTAMP)",
                id, fileId, ShareService.sha256(token), expiry, max, count, password, revoked);
        tokens.put(id, token);
        return id;
    }
    private String token(UUID shareId) { return tokens.get(shareId); }
}
