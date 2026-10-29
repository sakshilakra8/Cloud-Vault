package com.shardvault.storage;

public class ProviderUnavailableException extends ProviderException {
    public ProviderUnavailableException(String message) {
        super(message, null);
    }
    public ProviderUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
