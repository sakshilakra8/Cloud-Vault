package com.shardvault.api.config;

import com.shardvault.crypto.ChunkDecryptor;
import com.shardvault.crypto.ChunkingEncryptor;
import com.shardvault.crypto.EnvelopeCryptoService;
import com.shardvault.storage.LocalFsProvider;
import com.shardvault.storage.StorageProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.crypto.SecretKey;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

@Configuration
public class CryptoStorageConfig {

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
    public ChunkingEncryptor chunkingEncryptor(@Value("${shardvault.chunk-size-bytes:4194304}") int chunkSizeBytes) {
        return new ChunkingEncryptor(chunkSizeBytes);
    }

    @Bean
    public ChunkDecryptor chunkDecryptor() {
        return new ChunkDecryptor();
    }

    @Bean
    public StorageProvider storageProvider(
            @Value("${shardvault.default-provider-id:local-dev}") String defaultProviderId,
            @Value("${shardvault.local-provider-root:./data/local-provider}") String rootPath) {
        return new LocalFsProvider(defaultProviderId, Path.of(rootPath));
    }
}
