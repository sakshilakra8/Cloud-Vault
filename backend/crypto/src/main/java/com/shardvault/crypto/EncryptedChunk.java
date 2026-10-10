package com.shardvault.crypto;

import java.util.Arrays;

public record EncryptedChunk(
        long index,
        byte[] nonce,
        byte[] ciphertext,
        byte[] ciphertextSha256,
        int plainSize
) {
    public EncryptedChunk {
        nonce = Arrays.copyOf(nonce, nonce.length);
        ciphertext = Arrays.copyOf(ciphertext, ciphertext.length);
        ciphertextSha256 = Arrays.copyOf(ciphertextSha256, ciphertextSha256.length);
    }

    @Override
    public byte[] nonce() {
        return Arrays.copyOf(nonce, nonce.length);
    }

    @Override
    public byte[] ciphertext() {
        return Arrays.copyOf(ciphertext, ciphertext.length);
    }

    @Override
    public byte[] ciphertextSha256() {
        return Arrays.copyOf(ciphertextSha256, ciphertextSha256.length);
    }
}
