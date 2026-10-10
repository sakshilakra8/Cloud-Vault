package com.shardvault.storage;

public class ObjectNotFoundException extends ProviderException {
    public ObjectNotFoundException(String objectKey) {
        super("Object not found: " + objectKey);
    }

    public ObjectNotFoundException(String objectKey, Throwable cause) {
        super("Object not found: " + objectKey, cause);
    }
}
