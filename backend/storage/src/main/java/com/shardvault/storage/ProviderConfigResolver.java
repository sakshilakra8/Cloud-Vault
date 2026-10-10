package com.shardvault.storage;

public interface ProviderConfigResolver {
    ProviderConfig resolve(String configRef);
}
