package com.shardvault.storage;

import java.net.URI;
import java.util.Optional;

public record S3CompatibleProviderConfig(
        String id,
        Optional<URI> endpointOverride,
        String region,
        String bucket,
        boolean pathStyleAccess,
        String accessKeyId,
        String secretAccessKey,
        ProviderLimits limits
) {
    public S3CompatibleProviderConfig {
        endpointOverride = endpointOverride == null ? Optional.empty() : endpointOverride;
        if (id == null || id.isBlank()) {
            throw new ProviderConfigurationException("S3 provider id is required");
        }
        if (region == null || region.isBlank()) {
            throw new ProviderConfigurationException("S3 provider region is required");
        }
        if (bucket == null || bucket.isBlank()) {
            throw new ProviderConfigurationException("S3 provider bucket is required");
        }
        if (accessKeyId == null || accessKeyId.isBlank() || secretAccessKey == null || secretAccessKey.isBlank()) {
            throw new ProviderConfigurationException("S3 provider credentials are required");
        }
        limits = limits == null ? ProviderLimits.unlimited() : limits;
    }
}
