package com.shardvault.crypto;

import java.util.Arrays;

public record WrappedDataKey(int kekVersion, byte[] nonce, byte[] ciphertext) {
    public WrappedDataKey {
        nonce = Arrays.copyOf(nonce, nonce.length);
        ciphertext = Arrays.copyOf(ciphertext, ciphertext.length);
    }

    @Override
    public byte[] nonce() {
        return Arrays.copyOf(nonce, nonce.length);
    }

    @Override
    public byte[] ciphertext() {
        return Arrays.copyOf(ciphertext, ciphertext.length);
    }
}
