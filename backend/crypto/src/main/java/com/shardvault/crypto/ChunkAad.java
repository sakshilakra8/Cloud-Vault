package com.shardvault.crypto;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

final class ChunkAad {
    private static final byte[] PREFIX = "ShardVault.chunk.v1".getBytes(StandardCharsets.UTF_8);

    private ChunkAad() {
    }

    static byte[] bytes(String fileId, long chunkIndex) {
        byte[] fileIdBytes = fileId.getBytes(StandardCharsets.UTF_8);
        // Length-prefixing prevents ambiguous AAD encodings if file ids contain separators.
        return ByteBuffer.allocate(PREFIX.length + Integer.BYTES + fileIdBytes.length + Long.BYTES)
                .put(PREFIX)
                .putInt(fileIdBytes.length)
                .put(fileIdBytes)
                .putLong(chunkIndex)
                .array();
    }
}
