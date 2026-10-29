package com.shardvault.api.config;

import com.shardvault.crypto.ChunkDecryptor;
import com.shardvault.crypto.ChunkingEncryptor;
import com.shardvault.crypto.EnvelopeCryptoService;
import com.shardvault.storage.LocalFsProvider;
import com.shardvault.storage.StorageProvider;
import com.shardvault.storage.ProviderRegistry;
import com.shardvault.storage.StorageRouter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;

import javax.crypto.SecretKey;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import javax.sql.DataSource;

@Configuration
public class CryptoStorageConfig {

    @Bean
    public ProviderRegistry providerRegistry(DataSource dataSource) {
        return new ProviderRegistry(dataSource);
    }

    @Bean
    @DependsOn("entityManagerFactory")
    public StorageRouter storageRouter(
            ProviderRegistry registry,
            DataSource dataSource,
            @Value("${shardvault.routing.weights.reliability:0.40}") double reliability,
            @Value("${shardvault.routing.weights.speed:0.25}") double speed,
            @Value("${shardvault.routing.weights.free-space:0.20}") double freeSpace,
            @Value("${shardvault.routing.weights.cost:0.15}") double cost) {
        new org.springframework.jdbc.core.JdbcTemplate(dataSource)
                .query("select id from providers where enabled = true", (rs, row) -> rs.getString(1))
                .forEach(id -> registry.initializeTelemetry(java.util.List.of(id)));
        Map<String, Double> costs = new org.springframework.jdbc.core.JdbcTemplate(dataSource)
                .query("select id, coalesce(cost_score, 0) from providers", (rs, row) -> Map.entry(rs.getString(1), rs.getDouble(2)))
                .stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        return registry.router(new StorageRouter.Weights(reliability, speed, freeSpace, cost), costs);
    }

    @Bean
    public EnvelopeCryptoService envelopeCryptoService(
            @Value("${shardvault.master-key-b64:}") String masterKeyB64,
            @Value("${shardvault.master-key-b64-v1:}") String masterKeyV1) {

        Map<Integer, SecretKey> keys = new HashMap<>();
        String keyToUse = (masterKeyB64 != null && !masterKeyB64.isBlank()) ? masterKeyB64 : masterKeyV1;

        if (keyToUse == null || keyToUse.isBlank()) {
            // Default 32-byte key for local dev / testing if none provided
            keyToUse = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=";
        }

        keys.put(1, EnvelopeCryptoService.decodeAes256Key(keyToUse));
        return new EnvelopeCryptoService(keys);
    }

    @Bean
    @DependsOn("entityManagerFactory")
    public ChunkingEncryptor chunkingEncryptor(
            @Value("${shardvault.chunk-size-bytes:4194304}") int chunkSizeBytes,
            DataSource dataSource) {
        Long providerMax = new org.springframework.jdbc.core.JdbcTemplate(dataSource)
                .queryForObject("select min(max_chunk_bytes) from providers where enabled = true and max_chunk_bytes is not null", Long.class);
        // AES-GCM appends a 16-byte tag to each encrypted chunk stored by providers.
        int effectiveSize = (int) Math.min(chunkSizeBytes, Math.max(1, (providerMax == null ? chunkSizeBytes : providerMax) - 16));
        return new ChunkingEncryptor(effectiveSize);
    }

    @Bean
    public ChunkDecryptor chunkDecryptor() {
        return new ChunkDecryptor();
    }

    @Bean
    @DependsOn("entityManagerFactory")
    public StorageProvider storageProvider(
            @Value("${shardvault.default-provider-id:local-dev}") String defaultProviderId,
            @Value("${shardvault.local-provider-root:./data/local-provider}") String rootPath,
            ProviderRegistry registry) {
        var configuredProviders = registry.loadEnabledProviders();
        var configured = configuredProviders.stream().filter(provider -> provider.id().equals(defaultProviderId)).findFirst();
        if (configured.isPresent()) {
            configuredProviders.stream().filter(provider -> provider != configured.get()).filter(AutoCloseable.class::isInstance)
                    .map(AutoCloseable.class::cast).forEach(closeable -> { try { closeable.close(); } catch (Exception ignored) { } });
            return configured.get();
        }
        configuredProviders.stream().filter(AutoCloseable.class::isInstance).map(AutoCloseable.class::cast)
                .forEach(closeable -> { try { closeable.close(); } catch (Exception ignored) { } });
        return new LocalFsProvider(defaultProviderId, Path.of(rootPath));
    }
}
