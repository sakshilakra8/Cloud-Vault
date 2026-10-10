package com.shardvault.storage;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

abstract class StorageProviderContractTest {
    protected abstract StorageProvider provider();

    @Test
    void putGetExistsDeleteRoundTrip() throws Exception {
        String key = objectKey("round-trip");
        byte[] data = bytes("hello storage");

        provider().put(key, new ByteArrayInputStream(data), data.length);

        assertTrue(provider().exists(key));
        try (InputStream input = provider().get(key)) {
            assertArrayEquals(data, input.readAllBytes());
        }

        provider().delete(key);
        assertFalse(provider().exists(key));
    }

    @Test
    void overwriteReplacesExistingObject() throws Exception {
        String key = objectKey("overwrite");
        byte[] first = bytes("first");
        byte[] second = bytes("second");

        provider().put(key, new ByteArrayInputStream(first), first.length);
        provider().put(key, new ByteArrayInputStream(second), second.length);

        try (InputStream input = provider().get(key)) {
            assertArrayEquals(second, input.readAllBytes());
        }
    }

    @Test
    void missingObjectThrowsObjectNotFound() {
        assertThrows(ObjectNotFoundException.class, () -> provider().get(objectKey("missing")));
    }

    @Test
    void largeObjectRoundTrips() throws Exception {
        String key = objectKey("large");
        byte[] data = patternBytes(8 * 1024 * 1024 + 123);

        provider().put(key, new ByteArrayInputStream(data), data.length);

        try (InputStream input = provider().get(key)) {
            assertArrayEquals(data, input.readAllBytes());
        }
    }

    @Test
    void concurrentPutsUseIndependentObjectKeys() throws Exception {
        int count = 16;
        var executor = Executors.newFixedThreadPool(4);
        try {
            List<Callable<Void>> writes = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                int index = i;
                writes.add(() -> {
                    byte[] data = patternBytes(1024 + index);
                    provider().put(objectKey("concurrent-" + index), new ByteArrayInputStream(data), data.length);
                    return null;
                });
            }
            for (var future : executor.invokeAll(writes)) {
                future.get();
            }

            for (int i = 0; i < count; i++) {
                assertTrue(provider().exists(objectKey("concurrent-" + i)));
            }
        } finally {
            executor.shutdownNow();
        }
    }

    protected String objectKey(String suffix) {
        return "contract/" + runId() + "/" + suffix;
    }

    protected abstract String runId();

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] patternBytes(int size) {
        byte[] bytes = new byte[size];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) (i * 17 + 3);
        }
        return bytes;
    }
}
