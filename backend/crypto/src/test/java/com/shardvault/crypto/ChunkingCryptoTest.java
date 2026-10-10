package com.shardvault.crypto;

import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChunkingCryptoTest {
    private static final String FILE_ID = "file-123";

    @Test
    void roundTripBoundarySizes() {
        int chunkSize = 1024;
        List<Integer> sizes = List.of(0, 1, chunkSize, chunkSize + 1);
        for (int size : sizes) {
            byte[] plaintext = patternBytes(size);
            SecretKey dek = fixedKey(7);
            ChunkingEncryptor encryptor = new ChunkingEncryptor(chunkSize);
            ChunkDecryptor decryptor = new ChunkDecryptor();

            EncryptionResult encrypted = encryptor.encryptToList(new ByteArrayInputStream(plaintext), dek, FILE_ID);
            ByteArrayOutputStream decrypted = new ByteArrayOutputStream();
            byte[] decryptedSha = decryptor.decryptInOrder(encrypted.chunks(), dek, FILE_ID, decrypted);

            assertArrayEquals(plaintext, decrypted.toByteArray(), "plaintext should round-trip for size " + size);
            assertArrayEquals(sha256(plaintext), encrypted.plaintextSha256(), "plaintext digest should match");
            assertArrayEquals(encrypted.plaintextSha256(), decryptedSha, "decrypt digest should match");
        }
    }

    @Test
    void generatedTwoGiBStreamRoundTripsUnderBoundedMemory() {
        long size = 2L * 1024L * 1024L * 1024L;
        SecretKey dek = fixedKey(9);
        ChunkingEncryptor encryptor = new ChunkingEncryptor();
        ChunkDecryptor decryptor = new ChunkDecryptor();
        DigestingOutputStream plaintextDigest = new DigestingOutputStream();

        byte[] encryptedSha = encryptor.encrypt(new GeneratedPatternInputStream(size), dek, FILE_ID, chunk -> {
            byte[] plain = decryptor.decryptChunk(chunk, dek, FILE_ID);
            try {
                plaintextDigest.write(plain);
            } catch (java.io.IOException ex) {
                throw new AssertionError(ex);
            }
        });

        assertEquals(size, plaintextDigest.bytesWritten());
        assertArrayEquals(encryptedSha, plaintextDigest.digest());
    }

    @Test
    void flippedBitInCiphertextIsDetectedByChecksum() {
        SecretKey dek = fixedKey(1);
        EncryptedChunk chunk = new ChunkingEncryptor(16)
                .encryptToList(new ByteArrayInputStream(patternBytes(64)), dek, FILE_ID)
                .chunks()
                .getFirst();

        byte[] ciphertext = chunk.ciphertext();
        ciphertext[0] ^= 0x01;
        EncryptedChunk tampered = new EncryptedChunk(
                chunk.index(),
                chunk.nonce(),
                ciphertext,
                chunk.ciphertextSha256(),
                chunk.plainSize());

        assertThrows(IntegrityException.class, () -> new ChunkDecryptor().decryptChunk(tampered, dek, FILE_ID));
    }

    @Test
    void swappedChunkMetadataIsDetectedByGcmAad() {
        SecretKey dek = fixedKey(2);
        List<EncryptedChunk> chunks = new ChunkingEncryptor(8)
                .encryptToList(new ByteArrayInputStream(patternBytes(24)), dek, FILE_ID)
                .chunks();

        EncryptedChunk second = chunks.get(1);
        EncryptedChunk secondPretendingToBeFirst = new EncryptedChunk(
                0,
                second.nonce(),
                second.ciphertext(),
                second.ciphertextSha256(),
                second.plainSize());

        assertThrows(TamperedDataException.class,
                () -> new ChunkDecryptor().decryptChunk(secondPretendingToBeFirst, dek, FILE_ID));
    }

    @Test
    void wrongAadFailsGcmAuthentication() {
        SecretKey dek = fixedKey(3);
        EncryptedChunk chunk = new ChunkingEncryptor(16)
                .encryptToList(new ByteArrayInputStream(patternBytes(16)), dek, FILE_ID)
                .chunks()
                .getFirst();

        assertThrows(TamperedDataException.class,
                () -> new ChunkDecryptor().decryptChunk(chunk, dek, "different-file-id"));
    }

    @Test
    void oneMillionGeneratedNoncesHaveNoDuplicates() {
        GcmNonceGenerator nonceGenerator = new GcmNonceGenerator(new SecureRandom());
        Set<ByteArrayKey> seen = new HashSet<>(1_100_000);
        for (int i = 0; i < 1_000_000; i++) {
            byte[] nonce = nonceGenerator.nextNonce();
            assertEquals(12, nonce.length);
            if (!seen.add(new ByteArrayKey(nonce))) {
                throw new AssertionError("Duplicate nonce at iteration " + i);
            }
        }
    }

    @Test
    void wrongKekFailsToUnwrap() {
        EnvelopeCryptoService wrapping = new EnvelopeCryptoService(Map.of(1, fixedKey(11)));
        EnvelopeCryptoService wrong = new EnvelopeCryptoService(Map.of(1, fixedKey(12)));

        WrappedDataKey wrapped = wrapping.wrap(wrapping.generateDataKey(), 1);

        assertThrows(TamperedDataException.class, () -> wrong.unwrap(wrapped));
    }

    @Test
    void kekVersionSelectionWorks() {
        SecretKey kekV1 = fixedKey(21);
        SecretKey kekV2 = fixedKey(22);
        EnvelopeCryptoService service = new EnvelopeCryptoService(Map.of(1, kekV1, 2, kekV2));
        SecretKey dek = service.generateDataKey();

        WrappedDataKey wrappedWithV2 = service.wrap(dek, 2);
        SecretKey unwrapped = service.unwrap(wrappedWithV2);

        assertEquals(2, wrappedWithV2.kekVersion());
        assertArrayEquals(dek.getEncoded(), unwrapped.getEncoded());
        assertThrows(CryptoException.class, () -> service.wrap(dek, 99));
    }

    private static SecretKey fixedKey(int seed) {
        byte[] key = new byte[32];
        Arrays.fill(key, (byte) seed);
        return new SecretKeySpec(key, "AES");
    }

    private static byte[] patternBytes(int size) {
        byte[] data = new byte[size];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) (i * 31 + 17);
        }
        return data;
    }

    private static byte[] sha256(byte[] bytes) {
        return ChunkingEncryptor.sha256(bytes);
    }

    private record ByteArrayKey(byte[] bytes) {
        private ByteArrayKey {
            bytes = bytes.clone();
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof ByteArrayKey key && Arrays.equals(bytes, key.bytes);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(bytes);
        }
    }

    private static final class GeneratedPatternInputStream extends InputStream {
        private final long size;
        private long position;

        private GeneratedPatternInputStream(long size) {
            this.size = size;
        }

        @Override
        public int read() {
            if (position >= size) {
                return -1;
            }
            int value = valueAt(position);
            position++;
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) {
            if (position >= size) {
                return -1;
            }
            int toRead = (int) Math.min(length, size - position);
            for (int i = 0; i < toRead; i++) {
                buffer[offset + i] = (byte) valueAt(position + i);
            }
            position += toRead;
            return toRead;
        }

        private static int valueAt(long position) {
            return (int) ((position * 31 + 17) & 0xff);
        }
    }

    private static final class DigestingOutputStream extends OutputStream {
        private final MessageDigest digest = ChunkingEncryptor.sha256();
        private long bytesWritten;

        @Override
        public void write(int value) {
            digest.update((byte) value);
            bytesWritten++;
        }

        @Override
        public void write(byte[] bytes, int offset, int length) {
            digest.update(bytes, offset, length);
            bytesWritten += length;
        }

        private long bytesWritten() {
            return bytesWritten;
        }

        private byte[] digest() {
            return digest.digest();
        }
    }
}
