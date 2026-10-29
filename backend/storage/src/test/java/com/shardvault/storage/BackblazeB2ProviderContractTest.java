package com.shardvault.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.net.URI;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfEnvironmentVariable(named = "BACKBLAZE_TEST_ENDPOINT_OVERRIDE", matches = ".+")
@EnabledIfEnvironmentVariable(named = "BACKBLAZE_TEST_REGION", matches = ".+")
@EnabledIfEnvironmentVariable(named = "BACKBLAZE_TEST_BUCKET", matches = ".+")
@EnabledIfEnvironmentVariable(named = "BACKBLAZE_TEST_ACCESS_KEY_ID", matches = ".+")
@EnabledIfEnvironmentVariable(named = "BACKBLAZE_TEST_SECRET_ACCESS_KEY", matches = ".+")
class BackblazeB2ProviderContractTest extends StorageProviderContractTest {
    private StorageProvider provider;
    private String runId;
    @BeforeEach void setUp() {
        runId = UUID.randomUUID().toString();
        var config = new S3CompatibleProviderConfig("b2-test", Optional.of(URI.create(System.getenv("BACKBLAZE_TEST_ENDPOINT_OVERRIDE"))),
                System.getenv("BACKBLAZE_TEST_REGION"), System.getenv("BACKBLAZE_TEST_BUCKET"), true,
                System.getenv("BACKBLAZE_TEST_ACCESS_KEY_ID"), System.getenv("BACKBLAZE_TEST_SECRET_ACCESS_KEY"), ProviderLimits.unlimited());
        provider = new ReliableStorageProvider(new S3CompatibleProvider(config, ProviderType.BACKBLAZE_B2,
                software.amazon.awssdk.services.s3.S3Client.builder()
                        .endpointOverride(URI.create(System.getenv("BACKBLAZE_TEST_ENDPOINT_OVERRIDE")))
                        .region(software.amazon.awssdk.regions.Region.of(System.getenv("BACKBLAZE_TEST_REGION")))
                        .credentialsProvider(software.amazon.awssdk.auth.credentials.StaticCredentialsProvider.create(
                                software.amazon.awssdk.auth.credentials.AwsBasicCredentials.create(System.getenv("BACKBLAZE_TEST_ACCESS_KEY_ID"), System.getenv("BACKBLAZE_TEST_SECRET_ACCESS_KEY"))))
                        .serviceConfiguration(software.amazon.awssdk.services.s3.S3Configuration.builder().pathStyleAccessEnabled(true).build()).build()),
                new ReliabilityConfig(Duration.ofSeconds(20), 3, Duration.ofMillis(100)));
        assertTrue(provider.health().up());
    }
    @Override protected StorageProvider provider() { return provider; }
    @Override protected String runId() { return runId; }
}
