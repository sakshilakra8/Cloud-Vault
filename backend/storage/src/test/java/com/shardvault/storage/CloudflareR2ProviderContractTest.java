package com.shardvault.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.net.URI;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfEnvironmentVariable(named = "CLOUDFLARE_TEST_ENDPOINT_OVERRIDE", matches = ".+")
@EnabledIfEnvironmentVariable(named = "CLOUDFLARE_TEST_BUCKET", matches = ".+")
@EnabledIfEnvironmentVariable(named = "CLOUDFLARE_TEST_ACCESS_KEY_ID", matches = ".+")
@EnabledIfEnvironmentVariable(named = "CLOUDFLARE_TEST_SECRET_ACCESS_KEY", matches = ".+")
class CloudflareR2ProviderContractTest extends StorageProviderContractTest {
    private StorageProvider provider;
    private String runId;
    @BeforeEach void setUp() {
        runId = UUID.randomUUID().toString();
        URI endpoint = URI.create(System.getenv("CLOUDFLARE_TEST_ENDPOINT_OVERRIDE"));
        String accessKey = System.getenv("CLOUDFLARE_TEST_ACCESS_KEY_ID");
        String secret = System.getenv("CLOUDFLARE_TEST_SECRET_ACCESS_KEY");
        var config = new S3CompatibleProviderConfig("r2-test", Optional.of(endpoint), "auto", System.getenv("CLOUDFLARE_TEST_BUCKET"), true,
                accessKey, secret, ProviderLimits.unlimited());
        provider = new ReliableStorageProvider(new S3CompatibleProvider(config, ProviderType.CLOUDFLARE_R2,
                software.amazon.awssdk.services.s3.S3Client.builder().endpointOverride(endpoint)
                        .region(software.amazon.awssdk.regions.Region.of("auto"))
                        .credentialsProvider(software.amazon.awssdk.auth.credentials.StaticCredentialsProvider.create(
                                software.amazon.awssdk.auth.credentials.AwsBasicCredentials.create(accessKey, secret)))
                        .serviceConfiguration(software.amazon.awssdk.services.s3.S3Configuration.builder().pathStyleAccessEnabled(true).build()).build()),
                new ReliabilityConfig(Duration.ofSeconds(20), 3, Duration.ofMillis(100)));
        assertTrue(provider.health().up());
    }
    @Override protected StorageProvider provider() { return provider; }
    @Override protected String runId() { return runId; }
}
