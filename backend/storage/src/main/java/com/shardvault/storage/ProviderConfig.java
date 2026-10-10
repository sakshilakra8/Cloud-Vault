package com.shardvault.storage;

import java.net.URI;
import java.util.Map;
import java.util.Optional;

public final class ProviderConfig {
    private final Map<String, String> values;

    public ProviderConfig(Map<String, String> values) {
        this.values = Map.copyOf(values);
    }

    public String require(String key) {
        String value = values.get(key);
        if (value == null || value.isBlank()) {
            throw new ProviderConfigurationException("Missing provider config value: " + key);
        }
        return value;
    }

    public Optional<String> optional(String key) {
        String value = values.get(key);
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value);
    }

    public boolean bool(String key, boolean defaultValue) {
        return optional(key).map(Boolean::parseBoolean).orElse(defaultValue);
    }

    public Optional<URI> uri(String key) {
        return optional(key).map(URI::create);
    }
}
