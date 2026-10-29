package com.shardvault.storage;

import java.io.InputStream;
import java.util.Optional;

public interface StorageProvider {
    String id();

    ProviderType type();

    default ReliabilityTier reliability() { return ReliabilityTier.NORMAL; }

    void put(String objectKey, InputStream data, long length);

    /** Stores an object and returns its durable provider key when the backend assigns one. */
    default String putAndGetKey(String objectKey, InputStream data, long length) {
        put(objectKey, data, length);
        return objectKey;
    }

    InputStream get(String objectKey);

    void delete(String objectKey);

    boolean exists(String objectKey);

    HealthStatus health();

    Optional<Long> freeBytes();

    ProviderLimits limits();
}
