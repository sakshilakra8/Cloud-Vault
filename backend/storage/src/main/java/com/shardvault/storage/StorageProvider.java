package com.shardvault.storage;

import java.io.InputStream;
import java.util.Optional;

public interface StorageProvider {
    String id();

    ProviderType type();

    void put(String objectKey, InputStream data, long length);

    InputStream get(String objectKey);

    void delete(String objectKey);

    boolean exists(String objectKey);

    HealthStatus health();

    Optional<Long> freeBytes();

    ProviderLimits limits();
}
