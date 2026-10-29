package com.shardvault.api.controller;

import com.shardvault.api.metadata.ProviderEntity;
import com.shardvault.api.service.AdminProviderService;
import com.shardvault.api.service.AuditService;
import com.shardvault.api.security.UserPrincipal;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.Authentication;

@RestController
@RequestMapping("/api/v1/admin/providers")
@PreAuthorize("hasRole('ADMIN')")
public class AdminProviderController {
    private final AdminProviderService service;
    private final AuditService audit;
    public AdminProviderController(AdminProviderService service, AuditService audit) { this.service = service; this.audit = audit; }

    public record RegisterProviderRequest(
            @NotBlank @Size(max = 64) String id,
            @NotBlank @Size(max = 255) String name,
            @NotBlank String type,
            @NotBlank @Size(max = 64) String configRef,
            @Positive Long maxChunkBytes,
            @Positive Long maxObjectBytes) {}

    @PostMapping
    public ProviderEntity register(@Valid @RequestBody RegisterProviderRequest request, Authentication auth, HttpServletRequest http) {
        ProviderEntity result = service.register(request.id(), request.name(), request.type(), request.configRef(), request.maxChunkBytes(), request.maxObjectBytes());
        record(auth, "ADMIN_PROVIDER_REGISTER", result.id, http);
        return result;
    }

    @PostMapping("/{id}/enable")
    public ProviderEntity enable(@PathVariable String id, Authentication auth, HttpServletRequest http) { var p = service.setEnabled(id, true); record(auth, "ADMIN_PROVIDER_ENABLE", id, http); return p; }

    @PostMapping("/{id}/disable")
    public ProviderEntity disable(@PathVariable String id, Authentication auth, HttpServletRequest http) { var p = service.setEnabled(id, false); record(auth, "ADMIN_PROVIDER_DISABLE", id, http); return p; }

    @PostMapping("/{id}/health")
    public Map<String, Object> health(@PathVariable String id, Authentication auth, HttpServletRequest http) { var result = service.health(id); record(auth, "ADMIN_PROVIDER_HEALTH", id, http); return result; }

    @GetMapping("/{id}/usage")
    public Map<String, Object> usage(@PathVariable String id) { return service.usage(id); }

    @GetMapping("/stats")
    public Map<String, Object> stats() { return service.stats(); }

    @GetMapping("/replication")
    public Map<String, Long> replication() { return service.replicationOverview(); }

    @PostMapping("/repair/{chunkId}")
    public Map<String, Object> repair(@PathVariable UUID chunkId, Authentication auth, HttpServletRequest http) { var result = service.triggerRepair(chunkId); record(auth, "ADMIN_REPAIR_TRIGGER", chunkId.toString(), http); return result; }

    private void record(Authentication auth, String action, String resource, HttpServletRequest http) {
        UUID userId = UUID.fromString(((UserPrincipal) auth.getPrincipal()).getId());
        audit.record(userId, action, "PROVIDER", resource, "SUCCESS", http.getRemoteAddr(), http.getHeader("User-Agent"));
    }
}
