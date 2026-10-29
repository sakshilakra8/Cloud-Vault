package com.shardvault.api.service;

import com.shardvault.storage.*;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Application-facing routing facade used by upload, replica, and repair workflows. */
@Service
public class StorageRoutingService {
    private final ProviderRegistry registry;
    private final StorageRouter router;
    public StorageRoutingService(ProviderRegistry registry, StorageRouter router) {
        this.registry = registry; this.router = router;
    }

    public List<StorageRouter.Candidate> candidates(long ciphertextBytes, long requiredFreeBytes,
                                                     Set<String> alreadyUsed, long chunkIndex,
                                                     boolean additionalReplica) {
        List<StorageProvider> providers = registry.loadEnabledProviders();
        Set<String> enabled = providers.stream().map(StorageProvider::id).collect(java.util.stream.Collectors.toSet());
        return router.candidates(providers, enabled, ciphertextBytes, requiredFreeBytes,
                alreadyUsed, chunkIndex, additionalReplica);
    }

    public List<StorageProvider> replicas(long ciphertextBytes, long requiredFreeBytes,
                                          Set<String> alreadyUsed, long chunkIndex, int copies) {
        List<StorageRouter.Candidate> eligible = candidates(ciphertextBytes, requiredFreeBytes, alreadyUsed, chunkIndex, copies > 1);
        return router.chooseDistinctReplicas(eligible, copies);
    }

    public Map<String, ProviderTelemetry> telemetry() { return registry.telemetrySnapshot(); }
}
