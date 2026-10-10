package com.shardvault.storage;

public class ProviderConfigurationException extends ProviderException {
    public ProviderConfigurationException(String message) {
        super(message);
    }

    public ProviderConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
