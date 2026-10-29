package com.shardvault.api.controller;

import com.shardvault.api.security.UserPrincipal;
import com.shardvault.api.service.QuotaService;
import com.shardvault.api.service.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class QuotaController {
    private final QuotaService quotas;
    private final AuditService audit;
    public QuotaController(QuotaService quotas, AuditService audit) { this.quotas = quotas; this.audit = audit; }
    public record QuotaRequest(@PositiveOrZero long quotaBytes) {}

    @GetMapping("/quota/usage")
    public Map<String, Long> usage(Authentication auth) { return quotas.usage(userId(auth)); }

    @PutMapping("/admin/quotas/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, String> setQuota(@PathVariable UUID userId, @Valid @RequestBody QuotaRequest request,
                                        Authentication auth, HttpServletRequest http) {
        quotas.setQuota(userId, request.quotaBytes());
        UUID adminId = UUID.fromString(((UserPrincipal) auth.getPrincipal()).getId());
        audit.record(adminId, "ADMIN_QUOTA_CHANGE", "USER", userId.toString(), "SUCCESS", http.getRemoteAddr(), http.getHeader("User-Agent"));
        return Map.of("userId", userId.toString(), "status", "updated");
    }

    private static UUID userId(Authentication auth) { return UUID.fromString(((UserPrincipal) auth.getPrincipal()).getId()); }
}
