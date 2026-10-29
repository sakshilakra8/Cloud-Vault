package com.shardvault.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

public final class LocalFsProvider implements StorageProvider {
    private final String id;
    private final Path root;
    private final ProviderLimits limits;

    public LocalFsProvider(String id, Path root) {
        this(id, root, ProviderLimits.unlimited());
    }

    public LocalFsProvider(String id, Path root, ProviderLimits limits) {
        this.id = id;
        this.limits = limits;
        try {
            Path configuredRoot = root.toAbsolutePath().normalize();
            Files.createDirectories(configuredRoot);
            this.root = configuredRoot.toRealPath();
        } catch (IOException ex) {
            throw new ProviderConfigurationException("Unable to create local provider root", ex);
        }
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public ProviderType type() {
        return ProviderType.LOCAL_FS;
    }

    @Override
    public void put(String objectKey, InputStream data, long length) {
        Path target = resolveObjectKey(objectKey);
        try {
            Files.createDirectories(target.getParent());
            Files.copy(data, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new ProviderUnavailableException("LocalFS put failed for " + objectKey, ex);
        }
    }

    @Override
    public InputStream get(String objectKey) {
        Path target = resolveObjectKey(objectKey);
        if (!Files.exists(target)) {
            throw new ObjectNotFoundException(objectKey);
        }
        try {
            return Files.newInputStream(target);
        } catch (IOException ex) {
            throw new ProviderUnavailableException("LocalFS get failed for " + objectKey, ex);
        }
    }

    @Override
    public void delete(String objectKey) {
        try {
            Files.deleteIfExists(resolveObjectKey(objectKey));
        } catch (IOException ex) {
            throw new ProviderUnavailableException("LocalFS delete failed for " + objectKey, ex);
        }
    }

    @Override
    public boolean exists(String objectKey) {
        return Files.exists(resolveObjectKey(objectKey));
    }

    @Override
    public HealthStatus health() {
        Instant start = Instant.now();
        try {
            Files.createDirectories(root);
            return HealthStatus.up(Duration.between(start, Instant.now()).toMillis());
        } catch (IOException ex) {
            return HealthStatus.down(Duration.between(start, Instant.now()).toMillis());
        }
    }

    @Override
    public Optional<Long> freeBytes() {
        try {
            FileStore store = Files.getFileStore(root);
            return Optional.of(store.getUsableSpace());
        } catch (IOException ex) {
            return Optional.empty();
        }
    }

    @Override
    public ProviderLimits limits() {
        return limits;
    }

    private Path resolveObjectKey(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            throw new ProviderConfigurationException("objectKey must not be blank");
        }
        Path key = Path.of(objectKey);
        if (key.isAbsolute() || java.util.Arrays.stream(objectKey.replace('\\', '/').split("/"))
                .anyMatch(segment -> segment.isBlank() || segment.equals(".") || segment.equals(".."))) {
            throw new ProviderConfigurationException("objectKey contains an invalid path segment");
        }
        Path resolved = root.resolve(key).normalize();
        if (!resolved.startsWith(root)) {
            throw new ProviderConfigurationException("objectKey escapes provider root");
        }
        Path cursor = root;
        for (Path segment : root.relativize(resolved)) {
            cursor = cursor.resolve(segment);
            if (Files.isSymbolicLink(cursor)) throw new ProviderConfigurationException("Symbolic links are not valid object paths");
        }
        return resolved;
    }
}
