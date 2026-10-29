package com.shardvault.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfEnvironmentVariable(named = "SHARDVAULT_TEST_TELEGRAM_BOT_TOKEN", matches = ".+")
@EnabledIfEnvironmentVariable(named = "SHARDVAULT_TEST_TELEGRAM_CHAT_ID", matches = ".+")
class TelegramBotProviderContractTest extends StorageProviderContractTest {
    private StorageProvider provider;
    private String runId;

    @BeforeEach
    void setUp() {
        runId = UUID.randomUUID().toString();
        provider = new ReliableStorageProvider(new TelegramBotProvider("telegram-test",
                System.getenv("SHARDVAULT_TEST_TELEGRAM_BOT_TOKEN"),
                System.getenv("SHARDVAULT_TEST_TELEGRAM_CHAT_ID"), ProviderLimits.unlimited()));
        assertTrue(provider.health().up());
        assertEquals(ReliabilityTier.LOW, provider.reliability());
        assertTrue(provider.limits().maxChunkBytes().orElseThrow() <= TelegramBotProvider.MAX_CHUNK_BYTES);
    }

    @Override protected StorageProvider provider() { return provider; }
    @Override protected String runId() { return runId; }
}
