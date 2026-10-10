package com.shardvault.crypto;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public final class ChunkingEncryptor {
    public static final int DEFAULT_CHUNK_SIZE_BYTES = 4 * 1024 * 1024;
    private static final int GCM_TAG_BITS = 128;

    private final int chunkSizeBytes;
    private final GcmNonceGenerator nonceGenerator;

    public ChunkingEncryptor() {
        this(DEFAULT_CHUNK_SIZE_BYTES);
    }

    public ChunkingEncryptor(int chunkSizeBytes) {
        this(chunkSizeBytes, new SecureRandom());
    }

    public ChunkingEncryptor(int chunkSizeBytes, SecureRandom secureRandom) {
        if (chunkSizeBytes <= 0) {
            throw new IllegalArgumentException("chunkSizeBytes must be positive");
        }
        this.chunkSizeBytes = chunkSizeBytes;
        this.nonceGenerator = new GcmNonceGenerator(secureRandom);
    }

    public byte[] encrypt(InputStream plaintext, SecretKey dek, String fileId, Consumer<EncryptedChunk> chunkSink) {
        return encrypt(plaintext, dek, fileId, 0, chunkSink);
    }

    public byte[] encrypt(InputStream plaintext, SecretKey dek, String fileId, long startingIndex, Consumer<EncryptedChunk> chunkSink) {
        Objects.requireNonNull(chunkSink, "chunkSink");
        MessageDigest plaintextDigest = sha256();
        byte[] buffer = new byte[chunkSizeBytes];
        long index = startingIndex;
        try {
            int plainSize;
            while ((plainSize = readChunk(plaintext, buffer)) != -1) {
                plaintextDigest.update(buffer, 0, plainSize);
                byte[] nonce = nonceGenerator.nextNonce();
                byte[] ciphertext = encryptChunk(buffer, plainSize, dek, fileId, index, nonce);
                chunkSink.accept(new EncryptedChunk(index, nonce, ciphertext, sha256(ciphertext), plainSize));
                index++;
            }
            return plaintextDigest.digest();
        } catch (IOException ex) {
            throw new CryptoException("Failed to read plaintext stream", ex);
        }
    }

    public EncryptionResult encryptToList(InputStream plaintext, SecretKey dek, String fileId) {
        List<EncryptedChunk> chunks = new ArrayList<>();
        byte[] plaintextSha256 = encrypt(plaintext, dek, fileId, chunks::add);
        return new EncryptionResult(chunks, plaintextSha256);
    }

    private byte[] encryptChunk(byte[] buffer, int plainSize, SecretKey dek, String fileId, long index, byte[] nonce) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, dek, new GCMParameterSpec(GCM_TAG_BITS, nonce));
            cipher.updateAAD(ChunkAad.bytes(fileId, index));
            return cipher.doFinal(buffer, 0, plainSize);
        } catch (GeneralSecurityException ex) {
            throw new CryptoException("Failed to encrypt chunk " + index, ex);
        }
    }

    private static int readChunk(InputStream input, byte[] buffer) throws IOException {
        int total = 0;
        while (total < buffer.length) {
            int read = input.read(buffer, total, buffer.length - total);
            if (read == -1) {
                break;
            }
            total += read;
        }
        return total == 0 ? -1 : total;
    }

    static byte[] sha256(byte[] bytes) {
        return sha256().digest(bytes);
    }

    static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException ex) {
            throw new CryptoException("SHA-256 is not available", ex);
        }
    }
}
