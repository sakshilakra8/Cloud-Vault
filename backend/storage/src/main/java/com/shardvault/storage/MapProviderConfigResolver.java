package com.shardvault.storage;

import java.util.Map;

public final class MapProviderConfigResolver implements ProviderConfigResolver {
    private final Map<String, ProviderConfig> configs;

    public MapProviderConfigResolver(Map<String, ProviderConfig> configs) {
        this.configs = Map.copyOf(configs);
    }

    @Override
    public ProviderConfig resolve(String configRef) {
        ProviderConfig config = configs.get(configRef);
        if (config == null) {
            throw new ProviderConfigurationException("No config found for ref " + configRef);
        }
        return config;
    }
}
