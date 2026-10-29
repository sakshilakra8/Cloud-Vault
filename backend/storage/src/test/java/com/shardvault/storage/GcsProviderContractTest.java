package com.shardvault.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfEnvironmentVariable(named = "SHARDVAULT_TEST_GCS_BUCKET", matches = ".+")
@EnabledIfEnvironmentVariable(named = "GOOGLE_APPLICATION_CREDENTIALS", matches = ".+")
class GcsProviderContractTest extends StorageProviderContractTest {
    private StorageProvider provider;
    private String runId;

    @BeforeEach
    void setUp() {
        runId = UUID.randomUUID().toString();
        provider = new ReliableStorageProvider(new GcsProvider("gcs-test", System.getenv("SHARDVAULT_TEST_GCS_BUCKET"), ProviderLimits.unlimited()));
        assertTrue(provider.health().up());
    }

    @Override protected StorageProvider provider() { return provider; }
    @Override protected String runId() { return runId; }
}
