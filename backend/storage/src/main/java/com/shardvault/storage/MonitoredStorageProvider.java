package com.shardvault.storage;

import java.io.InputStream;
import java.time.Instant;
import java.util.Optional;

/** Records operation outcomes and enforces a telemetry-backed circuit breaker. */
public final class MonitoredStorageProvider implements StorageProvider, AutoCloseable {
    private final StorageProvider delegate;
    private final ProviderTelemetry telemetry;

    public MonitoredStorageProvider(StorageProvider delegate, ProviderTelemetry telemetry) {
        this.delegate = delegate;
        this.telemetry = telemetry;
    }

    private <T> T invoke(java.util.concurrent.Callable<T> operation) {
        if (!telemetry.circuitBreaker().tryAcquirePermission())
            throw new ProviderUnavailableException("Provider circuit is open: " + id());
        long started = System.nanoTime();
        try {
            T result = operation.call();
            telemetry.recordSuccess(elapsedMillis(started));
            return result;
        } catch (Exception ex) {
            telemetry.recordFailure(elapsedMillis(started));
            if (ex instanceof RuntimeException runtime) throw runtime;
            throw new ProviderUnavailableException("Provider operation failed: " + id(), ex);
        }
    }

    private static long elapsedMillis(long started) { return Math.max(0, (System.nanoTime() - started) / 1_000_000); }
    @Override public String id() { return delegate.id(); }
    @Override public ProviderType type() { return delegate.type(); }
    @Override public ReliabilityTier reliability() { return delegate.reliability(); }
    @Override public void put(String key, InputStream data, long length) { invoke(() -> { delegate.put(key, data, length); return null; }); }
    @Override public String putAndGetKey(String key, InputStream data, long length) { return invoke(() -> delegate.putAndGetKey(key, data, length)); }
    @Override public InputStream get(String key) { return invoke(() -> delegate.get(key)); }
    @Override public void delete(String key) { invoke(() -> { delegate.delete(key); return null; }); }
    @Override public boolean exists(String key) { return invoke(() -> delegate.exists(key)); }
    @Override public HealthStatus health() { return invoke(delegate::health); }
    @Override public Optional<Long> freeBytes() { return invoke(delegate::freeBytes); }
    @Override public ProviderLimits limits() { return delegate.limits(); }
    @Override public void close() {
        if (delegate instanceof AutoCloseable closeable) try { closeable.close(); }
        catch (Exception ex) { throw new ProviderUnavailableException("Provider close failed", ex); }
    }
}
