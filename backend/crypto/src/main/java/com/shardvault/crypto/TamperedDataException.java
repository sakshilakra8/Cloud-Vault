package com.shardvault.crypto;

public class TamperedDataException extends RuntimeException {
    public TamperedDataException(String message, Throwable cause) {
        super(message, cause);
    }
}
