package com.shardvault.api.service;

public class QuotaExceededException extends RuntimeException {
    public QuotaExceededException(String message) { super(message); }
}
