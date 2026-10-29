package com.shardvault.storage;

import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;

import java.io.InputStream;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class ReliableStorageProvider implements StorageProvider, AutoCloseable {
    private final StorageProvider delegate;
    private final ReliabilityConfig reliabilityConfig;
    private final Retry retry;
    private final ExecutorService executorService;

    public ReliableStorageProvider(StorageProvider delegate) {
        this(delegate, ReliabilityConfig.defaults());
    }

    public ReliableStorageProvider(StorageProvider delegate, ReliabilityConfig reliabilityConfig) {
        this.delegate = delegate;
        this.reliabilityConfig = reliabilityConfig;
        this.retry = Retry.of(delegate.id(), RetryConfig.custom()
                .maxAttempts(reliabilityConfig.maxAttempts())
                .intervalFunction(IntervalFunction.ofExponentialRandomBackoff(
                        reliabilityConfig.initialBackoff().toMillis(), 2.0, 0.5))
                .retryExceptions(ProviderUnavailableException.class)
                .ignoreExceptions(ObjectNotFoundException.class, ProviderAuthFailedException.class)
                .build());
        this.executorService = Executors.newCachedThreadPool();
    }

    @Override
    public String id() {
        return delegate.id();
    }

    @Override
    public ProviderType type() {
        return delegate.type();
    }

    @Override
    public ReliabilityTier reliability() { return delegate.reliability(); }

    @Override
    public void put(String objectKey, InputStream data, long length) {
        if (data.markSupported()) {
            data.mark(Integer.MAX_VALUE);
            runWithRetry("put " + objectKey, () -> {
                data.reset();
                delegate.put(objectKey, data, length);
                return null;
            });
            return;
        }

        // One-shot streams cannot be replayed without spooling the whole object; use a bounded single attempt.
        callBounded("put " + objectKey, () -> {
            delegate.put(objectKey, data, length);
            return null;
        });
    }

    @Override
    public String putAndGetKey(String objectKey, InputStream data, long length) {
        if (data.markSupported()) {
            data.mark(Integer.MAX_VALUE);
            return runWithRetry("put " + objectKey, () -> {
                data.reset();
                return delegate instanceof ReliableStorageProvider reliable
                        ? reliable.putAndGetKey(objectKey, data, length)
                        : putAndGetKey(delegate, objectKey, data, length);
            });
        }
        return callBounded("put " + objectKey, () -> putAndGetKey(delegate, objectKey, data, length));
    }

    private static String putAndGetKey(StorageProvider provider, String objectKey, InputStream data, long length) {
        return provider.putAndGetKey(objectKey, data, length);
    }

    @Override
    public InputStream get(String objectKey) {
        return runWithRetry("get " + objectKey, () -> delegate.get(objectKey));
    }

    @Override
    public void delete(String objectKey) {
        runWithRetry("delete " + objectKey, () -> {
            delegate.delete(objectKey);
            return null;
        });
    }

    @Override
    public boolean exists(String objectKey) {
        return runWithRetry("exists " + objectKey, () -> delegate.exists(objectKey));
    }

    @Override
    public HealthStatus health() {
        return runWithRetry("health", delegate::health);
    }

    @Override
    public Optional<Long> freeBytes() {
        return runWithRetry("freeBytes", delegate::freeBytes);
    }

    @Override
    public ProviderLimits limits() {
        return delegate.limits();
    }

    @Override
    public void close() {
        executorService.shutdownNow();
        if (delegate instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception ex) {
                throw new ProviderUnavailableException("Failed to close provider " + id(), ex);
            }
        }
    }

    private <T> T runWithRetry(String operation, Callable<T> callable) {
        try {
            return retry.executeCallable(() -> callBounded(operation, callable));
        } catch (Exception ex) {
            throw map(operation, ex);
        }
    }

    private <T> T callBounded(String operation, Callable<T> callable) {
        Future<T> future = executorService.submit(callable);
        try {
            Duration timeout = reliabilityConfig.timeout();
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException ex) {
            future.cancel(true);
            throw new ProviderUnavailableException("Provider operation timed out: " + operation, ex);
        } catch (CancellationException ex) {
            throw new ProviderUnavailableException("Provider operation cancelled: " + operation, ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ProviderUnavailableException("Provider operation interrupted: " + operation, ex);
        } catch (Exception ex) {
            throw map(operation, ex);
        }
    }

    private ProviderException map(String operation, Exception ex) {
        Throwable cause = ex.getCause() == null ? ex : ex.getCause();
        if (cause instanceof ProviderException providerException) {
            return providerException;
        }
        return new ProviderUnavailableException("Provider operation failed: " + operation, cause);
    }
}
