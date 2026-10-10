package com.shardvault.crypto;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

public final class EnvelopeCryptoService {
    private static final int AES_256_BYTES = 32;
    private static final int GCM_TAG_BITS = 128;
    private static final byte[] WRAP_AAD_PREFIX = "ShardVault.DEK.wrap.v1".getBytes(java.nio.charset.StandardCharsets.UTF_8);

    private final Map<Integer, SecretKey> keyEncryptionKeys;
    private final SecureRandom secureRandom;
    private final GcmNonceGenerator nonceGenerator;

    public EnvelopeCryptoService(Map<Integer, SecretKey> keyEncryptionKeys) {
        this(keyEncryptionKeys, new SecureRandom());
    }

    public EnvelopeCryptoService(Map<Integer, SecretKey> keyEncryptionKeys, SecureRandom secureRandom) {
        if (keyEncryptionKeys.isEmpty()) {
            throw new IllegalArgumentException("At least one KEK is required");
        }
        this.keyEncryptionKeys = Map.copyOf(keyEncryptionKeys);
        this.secureRandom = secureRandom;
        this.nonceGenerator = new GcmNonceGenerator(secureRandom);
    }

    public static EnvelopeCryptoService fromEnvironment() {
        Map<Integer, SecretKey> keys = new HashMap<>();
        String unversioned = System.getenv("MASTER_KEY_B64");
        if (unversioned != null && !unversioned.isBlank()) {
            keys.put(1, decodeAes256Key(unversioned));
        }

        System.getenv().forEach((name, value) -> {
            if (name.startsWith("MASTER_KEY_B64_V") && !value.isBlank()) {
                String versionText = name.substring("MASTER_KEY_B64_V".length());
                try {
                    keys.put(Integer.parseInt(versionText), decodeAes256Key(value));
                } catch (NumberFormatException ex) {
                    throw new CryptoException("Invalid KEK env var version: " + name, ex);
                }
            }
        });
        return new EnvelopeCryptoService(keys);
    }

    public SecretKey generateDataKey() {
        byte[] key = new byte[AES_256_BYTES];
        secureRandom.nextBytes(key);
        return new SecretKeySpec(key, "AES");
    }

    public WrappedDataKey wrap(SecretKey dek, int kekVersion) {
        SecretKey kek = keyEncryptionKeys.get(kekVersion);
        if (kek == null) {
            throw new CryptoException("No KEK configured for version " + kekVersion);
        }
        byte[] nonce = nonceGenerator.nextNonce();
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, kek, new GCMParameterSpec(GCM_TAG_BITS, nonce));
            cipher.updateAAD(wrapAad(kekVersion));
            return new WrappedDataKey(kekVersion, nonce, cipher.doFinal(dek.getEncoded()));
        } catch (GeneralSecurityException ex) {
            throw new CryptoException("Failed to wrap DEK", ex);
        }
    }

    public SecretKey unwrap(WrappedDataKey wrapped) {
        SecretKey kek = keyEncryptionKeys.get(wrapped.kekVersion());
        if (kek == null) {
            throw new CryptoException("No KEK configured for version " + wrapped.kekVersion());
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, kek, new GCMParameterSpec(GCM_TAG_BITS, wrapped.nonce()));
            cipher.updateAAD(wrapAad(wrapped.kekVersion()));
            byte[] dek = cipher.doFinal(wrapped.ciphertext());
            if (dek.length != AES_256_BYTES) {
                throw new CryptoException("Unwrapped DEK is not 256 bits");
            }
            return new SecretKeySpec(dek, "AES");
        } catch (AEADBadTagException ex) {
            throw new TamperedDataException("Wrapped DEK failed authentication", ex);
        } catch (GeneralSecurityException ex) {
            throw new CryptoException("Failed to unwrap DEK", ex);
        }
    }

    public static SecretKey decodeAes256Key(String base64) {
        byte[] raw = Base64.getDecoder().decode(base64);
        if (raw.length != AES_256_BYTES) {
            throw new CryptoException("MASTER_KEY_B64 must decode to exactly 32 bytes");
        }
        return new SecretKeySpec(raw, "AES");
    }

    private static byte[] wrapAad(int kekVersion) {
        byte[] version = Integer.toString(kekVersion).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] aad = new byte[WRAP_AAD_PREFIX.length + 1 + version.length];
        System.arraycopy(WRAP_AAD_PREFIX, 0, aad, 0, WRAP_AAD_PREFIX.length);
        aad[WRAP_AAD_PREFIX.length] = 0;
        System.arraycopy(version, 0, aad, WRAP_AAD_PREFIX.length + 1, version.length);
        return aad;
    }
}
