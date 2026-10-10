package com.shardvault.storage;

import org.junit.jupiter.api.BeforeEach;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;

import java.net.URI;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers(disabledWithoutDocker = true)
class S3CompatibleProviderMinioContractTest extends StorageProviderContractTest {
    private static final String ACCESS_KEY = "minioadmin";
    private static final String SECRET_KEY = "minioadmin";
    private static final String BUCKET = "shardvault-test";

    @Container
    static final GenericContainer<?> MINIO = new GenericContainer<>("minio/minio:RELEASE.2024-10-13T13-34-11Z")
            .withExposedPorts(9000)
            .withEnv("MINIO_ROOT_USER", ACCESS_KEY)
            .withEnv("MINIO_ROOT_PASSWORD", SECRET_KEY)
            .withCommand("server", "/data")
            .waitingFor(Wait.forHttp("/minio/health/ready").forPort(9000).forStatusCode(200));

    private StorageProvider provider;
    private String runId;

    @BeforeEach
    void setUp() {
        runId = UUID.randomUUID().toString();
        URI endpoint = URI.create("http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000));
        createBucket(endpoint);
        provider = new ReliableStorageProvider(new S3CompatibleProvider(new S3CompatibleProviderConfig(
                "minio-test",
                Optional.of(endpoint),
                "us-east-1",
                BUCKET,
                true,
                ACCESS_KEY,
                SECRET_KEY,
                ProviderLimits.unlimited()
        )), new ReliabilityConfig(Duration.ofSeconds(10), 3, Duration.ofMillis(25)));
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

    private static void createBucket(URI endpoint) {
        try (S3Client client = S3Client.builder()
                .endpointOverride(endpoint)
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(ACCESS_KEY, SECRET_KEY)))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build()) {
            try {
                client.createBucket(CreateBucketRequest.builder().bucket(BUCKET).build());
            } catch (BucketAlreadyOwnedByYouException ignored) {
                // Shared static MinIO container keeps the bucket between contract test methods.
            }
        }
    }
}
