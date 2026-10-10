package com.shardvault.crypto;

import java.security.SecureRandom;

final class GcmNonceGenerator {
    static final int GCM_NONCE_BYTES = 12;

    private final SecureRandom secureRandom;

    GcmNonceGenerator(SecureRandom secureRandom) {
        this.secureRandom = secureRandom;
    }

    byte[] nextNonce() {
        byte[] nonce = new byte[GCM_NONCE_BYTES];
        secureRandom.nextBytes(nonce);
        return nonce;
    }
}
