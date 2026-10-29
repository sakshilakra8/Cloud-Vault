package com.shardvault.api.service;

public enum ReplicationPolicy {
    STANDARD(1), IMPORTANT(2), CRITICAL(3);
    private final int copies;
    ReplicationPolicy(int copies) { this.copies = copies; }
    public int copies() { return copies; }

    public static ReplicationPolicy defaultFor(long fileBytes, String mimeType) {
        String mime = mimeType == null ? "" : mimeType.toLowerCase(java.util.Locale.ROOT);
        if (mime.startsWith("application/pkcs") || mime.contains("backup") || fileBytes >= 5L * 1024 * 1024 * 1024) return CRITICAL;
        if (mime.startsWith("video/") || fileBytes >= 512L * 1024 * 1024) return IMPORTANT;
        return STANDARD;
    }
}
