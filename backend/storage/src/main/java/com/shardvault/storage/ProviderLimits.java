package com.shardvault.storage;

import java.util.Optional;

public record ProviderLimits(Optional<Long> maxChunkBytes, Optional<Long> maxObjectBytes, Optional<String> rateLimitHint) {
    public ProviderLimits {
        maxChunkBytes = maxChunkBytes == null ? Optional.empty() : maxChunkBytes;
        maxObjectBytes = maxObjectBytes == null ? Optional.empty() : maxObjectBytes;
        rateLimitHint = rateLimitHint == null ? Optional.empty() : rateLimitHint;
    }

    public static ProviderLimits unlimited() {
        return new ProviderLimits(Optional.empty(), Optional.empty(), Optional.empty());
    }
}
