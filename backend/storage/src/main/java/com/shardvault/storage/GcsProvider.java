package com.shardvault.storage;

import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;

import java.io.InputStream;
import java.nio.channels.Channels;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/** Google Cloud Storage provider. Authentication uses Application Default Credentials. */
public final class GcsProvider implements StorageProvider {
    private final String id;
    private final String bucket;
    private final ProviderLimits limits;
    private final Storage storage;

    public GcsProvider(String id, String bucket, ProviderLimits limits) {
        this(id, bucket, limits, StorageOptions.getDefaultInstance().getService());
    }

    GcsProvider(String id, String bucket, ProviderLimits limits, Storage storage) {
        this.id = id;
        this.bucket = bucket;
        this.limits = limits == null ? ProviderLimits.unlimited() : limits;
        this.storage = storage;
    }

    @Override public String id() { return id; }
    @Override public ProviderType type() { return ProviderType.GCS; }

    @Override
    public void put(String objectKey, InputStream data, long length) {
        try {
            storage.createFrom(BlobInfo.newBuilder(BlobId.of(bucket, objectKey)).build(), data);
        } catch (java.io.IOException | RuntimeException ex) {
            throw new ProviderUnavailableException("GCS put failed for " + objectKey, ex);
        }
    }

    @Override
    public InputStream get(String objectKey) {
        try {
            Blob blob = storage.get(BlobId.of(bucket, objectKey));
            if (blob == null) throw new ObjectNotFoundException(objectKey);
            return Channels.newInputStream(blob.reader());
        } catch (ObjectNotFoundException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new ProviderUnavailableException("GCS get failed for " + objectKey, ex);
        }
    }

    @Override
    public void delete(String objectKey) {
        try {
            storage.delete(BlobId.of(bucket, objectKey));
        } catch (RuntimeException ex) {
            throw new ProviderUnavailableException("GCS delete failed for " + objectKey, ex);
        }
    }

    @Override
    public boolean exists(String objectKey) {
        try { return storage.get(BlobId.of(bucket, objectKey)) != null; }
        catch (RuntimeException ex) { throw new ProviderUnavailableException("GCS exists failed for " + objectKey, ex); }
    }

    @Override
    public HealthStatus health() {
        Instant start = Instant.now();
        try {
            boolean up = storage.get(bucket) != null;
            return up ? HealthStatus.up(Duration.between(start, Instant.now()).toMillis())
                    : HealthStatus.down(Duration.between(start, Instant.now()).toMillis());
        } catch (RuntimeException ex) {
            return HealthStatus.down(Duration.between(start, Instant.now()).toMillis());
        }
    }

    @Override public Optional<Long> freeBytes() { return Optional.empty(); }
    @Override public ProviderLimits limits() { return limits; }
}
