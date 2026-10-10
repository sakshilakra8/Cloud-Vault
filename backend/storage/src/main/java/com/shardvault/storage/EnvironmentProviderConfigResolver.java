package com.shardvault.storage;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class EnvironmentProviderConfigResolver implements ProviderConfigResolver {
    @Override
    public ProviderConfig resolve(String configRef) {
        if (configRef == null || configRef.isBlank()) {
            throw new ProviderConfigurationException("Provider config_ref is required");
        }
        String prefix = configRef.toUpperCase(Locale.ROOT);
        Map<String, String> values = new HashMap<>();
        copy(prefix, "ROOT", "root", values);
        copy(prefix, "ENDPOINT_OVERRIDE", "endpointOverride", values);
        copy(prefix, "REGION", "region", values);
        copy(prefix, "BUCKET", "bucket", values);
        copy(prefix, "PATH_STYLE_ACCESS", "pathStyleAccess", values);
        copy(prefix, "ACCESS_KEY_ID", "accessKeyId", values);
        copy(prefix, "SECRET_ACCESS_KEY", "secretAccessKey", values);
        copy(prefix, "RATE_LIMIT_HINT", "rateLimitHint", values);
        return new ProviderConfig(values);
    }

    private static void copy(String prefix, String suffix, String key, Map<String, String> values) {
        String value = System.getenv(prefix + "_" + suffix);
        if (value != null) {
            values.put(key, value);
        }
    }
}
