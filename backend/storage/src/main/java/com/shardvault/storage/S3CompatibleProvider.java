package com.shardvault.storage;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

public final class S3CompatibleProvider implements StorageProvider, AutoCloseable {
    private final String id;
    private final String bucket;
    private final ProviderLimits limits;
    private final S3Client s3;

    public S3CompatibleProvider(S3CompatibleProviderConfig config) {
        this(config, buildClient(config));
    }

    public S3CompatibleProvider(S3CompatibleProviderConfig config, S3Client s3) {
        this.id = config.id();
        this.bucket = config.bucket();
        this.limits = config.limits();
        this.s3 = s3;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public ProviderType type() {
        return ProviderType.S3_COMPATIBLE;
    }

    @Override
    public void put(String objectKey, InputStream data, long length) {
        try {
            s3.putObject(PutObjectRequest.builder().bucket(bucket).key(objectKey).build(),
                    RequestBody.fromInputStream(data, length));
        } catch (RuntimeException ex) {
            throw mapException("S3 put failed for " + objectKey, objectKey, ex);
        }
    }

    @Override
    public InputStream get(String objectKey) {
        try {
            ResponseInputStream<GetObjectResponse> response = s3.getObject(
                    GetObjectRequest.builder().bucket(bucket).key(objectKey).build());
            return response;
        } catch (RuntimeException ex) {
            throw mapException("S3 get failed for " + objectKey, objectKey, ex);
        }
    }

    @Override
    public void delete(String objectKey) {
        try {
            s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(objectKey).build());
        } catch (RuntimeException ex) {
            throw mapException("S3 delete failed for " + objectKey, objectKey, ex);
        }
    }

    @Override
    public boolean exists(String objectKey) {
        try {
            s3.headObject(HeadObjectRequest.builder().bucket(bucket).key(objectKey).build());
            return true;
        } catch (NoSuchKeyException ex) {
            return false;
        } catch (S3Exception ex) {
            if (ex.statusCode() == 404) {
                return false;
            }
            throw mapException("S3 exists failed for " + objectKey, objectKey, ex);
        } catch (RuntimeException ex) {
            throw mapException("S3 exists failed for " + objectKey, objectKey, ex);
        }
    }

    @Override
    public HealthStatus health() {
        Instant start = Instant.now();
        try {
            s3.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
            return HealthStatus.up(Duration.between(start, Instant.now()).toMillis());
        } catch (RuntimeException ex) {
            return HealthStatus.down(Duration.between(start, Instant.now()).toMillis());
        }
    }

    @Override
    public Optional<Long> freeBytes() {
        return Optional.empty();
    }

    @Override
    public ProviderLimits limits() {
        return limits;
    }

    @Override
    public void close() {
        s3.close();
    }

    private static S3Client buildClient(S3CompatibleProviderConfig config) {
        S3Client.builder();
        var builder = S3Client.builder()
                .region(Region.of(config.region()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(config.accessKeyId(), config.secretAccessKey())))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(config.pathStyleAccess())
                        .build());
        config.endpointOverride().ifPresent(builder::endpointOverride);
        return builder.build();
    }

    static ProviderException mapException(String message, String objectKey, RuntimeException ex) {
        if (ex instanceof ObjectNotFoundException
                || ex instanceof ProviderAuthFailedException
                || ex instanceof ProviderUnavailableException) {
            return (ProviderException) ex;
        }
        if (ex instanceof NoSuchKeyException) {
            return new ObjectNotFoundException(objectKey, ex);
        }
        if (ex instanceof S3Exception s3Exception) {
            int status = s3Exception.statusCode();
            if (status == 401 || status == 403) {
                return new ProviderAuthFailedException(message, ex);
            }
            if (status == 404) {
                return new ObjectNotFoundException(objectKey, ex);
            }
            return new ProviderUnavailableException(message, ex);
        }
        if (ex instanceof SdkClientException) {
            return new ProviderUnavailableException(message, ex);
        }
        return new ProviderUnavailableException(message, ex);
    }
}
