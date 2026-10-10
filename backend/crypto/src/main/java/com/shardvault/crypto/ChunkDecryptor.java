package com.shardvault.crypto;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.io.IOException;
import java.io.OutputStream;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

public final class ChunkDecryptor {
    private static final int GCM_TAG_BITS = 128;

    public byte[] decryptChunk(EncryptedChunk chunk, SecretKey dek, String fileId) {
        verifyCiphertextSha256(chunk);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, dek, new GCMParameterSpec(GCM_TAG_BITS, chunk.nonce()));
            cipher.updateAAD(ChunkAad.bytes(fileId, chunk.index()));
            return cipher.doFinal(chunk.ciphertext());
        } catch (AEADBadTagException ex) {
            throw new TamperedDataException("Chunk " + chunk.index() + " failed authentication", ex);
        } catch (GeneralSecurityException ex) {
            throw new CryptoException("Failed to decrypt chunk " + chunk.index(), ex);
        }
    }

    public byte[] decryptInOrder(List<EncryptedChunk> chunks, SecretKey dek, String fileId, OutputStream plaintext) {
        List<EncryptedChunk> ordered = chunks.stream()
                .sorted(Comparator.comparingLong(EncryptedChunk::index))
                .toList();
        MessageDigest plaintextDigest = ChunkingEncryptor.sha256();
        long expectedIndex = 0;
        for (EncryptedChunk chunk : ordered) {
            if (chunk.index() != expectedIndex) {
                throw new IntegrityException("Missing or duplicate chunk at index " + expectedIndex);
            }
            byte[] plain = decryptChunk(chunk, dek, fileId);
            plaintextDigest.update(plain);
            try {
                plaintext.write(plain);
            } catch (IOException ex) {
                throw new CryptoException("Failed to write plaintext", ex);
            }
            expectedIndex++;
        }
        return plaintextDigest.digest();
    }

    private static void verifyCiphertextSha256(EncryptedChunk chunk) {
        byte[] actual = ChunkingEncryptor.sha256(chunk.ciphertext());
        if (!MessageDigest.isEqual(actual, chunk.ciphertextSha256())) {
            throw new IntegrityException("Ciphertext SHA-256 mismatch for chunk " + chunk.index()
                    + ": expected " + HexFormat.of().formatHex(chunk.ciphertextSha256())
                    + ", got " + HexFormat.of().formatHex(actual));
        }
    }
}
