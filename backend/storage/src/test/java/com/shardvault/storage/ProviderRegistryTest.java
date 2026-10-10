package com.shardvault.storage;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProviderRegistryTest {
    @TempDir
    Path tempDir;

    @Test
    void loadsEnabledProvidersAndResolvesConfigRefOutsideDatabase() throws SQLException {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:providers;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.execute("""
                    create table providers (
                        id varchar(64) primary key,
                        type varchar(64) not null,
                        config_ref varchar(128) not null,
                        max_chunk_bytes bigint,
                        max_object_bytes bigint,
                        enabled boolean not null
                    )
                    """);
            statement.execute("""
                    insert into providers(id, type, config_ref, max_chunk_bytes, max_object_bytes, enabled)
                    values ('local-a', 'LOCAL_FS', 'LOCAL_A', 4194304, null, true),
                           ('disabled', 'LOCAL_FS', 'DISABLED', null, null, false)
                    """);
        }

        ProviderRegistry registry = new ProviderRegistry(
                dataSource,
                new MapProviderConfigResolver(Map.of("LOCAL_A", new ProviderConfig(Map.of(
                        "root", tempDir.toString(),
                        "rateLimitHint", "dev-only"
                )))),
                new ReliabilityConfig(Duration.ofSeconds(5), 1, Duration.ofMillis(10)));

        List<StorageProvider> providers = registry.loadEnabledProviders();

        assertEquals(1, providers.size());
        StorageProvider provider = providers.getFirst();
        assertEquals("local-a", provider.id());
        assertEquals(ProviderType.LOCAL_FS, provider.type());
        assertEquals(Optional.of(4_194_304L), provider.limits().maxChunkBytes());
        assertEquals(Optional.of("dev-only"), provider.limits().rateLimitHint());
        assertTrue(provider.health().up());
    }
}
