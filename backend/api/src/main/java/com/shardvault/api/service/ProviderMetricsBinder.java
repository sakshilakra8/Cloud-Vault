package com.shardvault.api.service;

import com.shardvault.storage.ProviderRegistry;
import com.shardvault.storage.ProviderTelemetry;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ProviderMetricsBinder {
    private final ProviderRegistry providers;
    private final MeterRegistry meters;
    private final Set<String> bound = ConcurrentHashMap.newKeySet();

    public ProviderMetricsBinder(ProviderRegistry providers, MeterRegistry meters) {
        this.providers = providers; this.meters = meters;
    }

    @Scheduled(fixedDelayString = "${shardvault.metrics.provider-refresh-ms:15000}")
    public void bindProviderMetrics() {
        providers.telemetrySnapshot().forEach((id, telemetry) -> {
            if (!bound.add(id)) return;
            Gauge.builder("shardvault.storage.provider.success.rate", telemetry, ProviderTelemetry::successRate)
                    .tag("provider_id", id).register(meters);
            Gauge.builder("shardvault.storage.provider.error.rate", telemetry, t -> 1 - t.successRate())
                    .tag("provider_id", id).register(meters);
            Gauge.builder("shardvault.storage.provider.ewma.latency", telemetry, ProviderTelemetry::ewmaLatencyMillis)
                    .tag("provider_id", id).register(meters);
            Gauge.builder("shardvault.storage.provider.circuit.open", telemetry, t -> t.circuitAllowsProbe() ? 0 : 1)
                    .tag("provider_id", id).register(meters);
        });
    }
}
