package com.shardvault.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalFsProviderContractTest extends StorageProviderContractTest {
    @TempDir
    Path tempDir;

    private StorageProvider provider;
    private String runId;

    @BeforeEach
    void setUp() {
        runId = UUID.randomUUID().toString();
        provider = new ReliableStorageProvider(
                new LocalFsProvider("local-test", tempDir),
                new ReliabilityConfig(Duration.ofSeconds(5), 3, Duration.ofMillis(10)));
        assertTrue(provider.health().up());
    }

    @Override
    protected StorageProvider provider() {
        return provider;
    }

    @Override
    protected String runId() {
        return runId;
    }
}
