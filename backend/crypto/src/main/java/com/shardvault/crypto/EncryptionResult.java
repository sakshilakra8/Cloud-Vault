package com.shardvault.crypto;

import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

public record EncryptionResult(List<EncryptedChunk> chunks, byte[] plaintextSha256) {
    public EncryptionResult {
        chunks = List.copyOf(new ArrayList<>(chunks));
        plaintextSha256 = plaintextSha256.clone();
    }

    @Override
    public byte[] plaintextSha256() {
        return plaintextSha256.clone();
    }

    public String plaintextSha256Hex() {
        return HexFormat.of().formatHex(plaintextSha256);
    }
}
