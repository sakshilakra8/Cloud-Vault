package com.shardvault.api.config;

import com.shardvault.crypto.EnvelopeCryptoService;
import com.shardvault.storage.LocalFsProvider;
import com.shardvault.storage.ReliabilityConfig;
import com.shardvault.storage.ReliableStorageProvider;
import com.shardvault.storage.StorageProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.crypto.SecretKey;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

@Configuration
class PipelineConfig {
    @Bean
    EnvelopeCryptoService envelopeCryptoService(
            @Value("${shardvault.master-key-b64:}") String masterKey,
            @Value("${shardvault.master-key-b64-v1:}") String masterKeyV1) {
        String configured = !masterKeyV1.isBlank() ? masterKeyV1 : masterKey;
        if (configured.isBlank()) {
            throw new IllegalStateException("MASTER_KEY_B64 or MASTER_KEY_B64_V1 must be configured");
        }
        SecretKey kek = EnvelopeCryptoService.decodeAes256Key(configured);
        return new EnvelopeCryptoService(Map.of(1, kek));
    }

    @Bean
    StorageProvider storageProvider(
            @Value("${shardvault.default-provider-id}") String providerId,
            @Value("${shardvault.local-provider-root}") String localProviderRoot) {
        return new ReliableStorageProvider(
                new LocalFsProvider(providerId, Path.of(localProviderRoot)),
                new ReliabilityConfig(Duration.ofSeconds(30), 3, Duration.ofMillis(100)));
    }
}
