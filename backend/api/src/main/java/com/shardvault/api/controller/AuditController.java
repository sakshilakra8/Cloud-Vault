package com.shardvault.api.controller;

import com.shardvault.api.security.UserPrincipal;
import com.shardvault.api.service.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/audit")
public class AuditController {
    private final AuditService audit;
    public AuditController(AuditService audit) { this.audit = audit; }

    @GetMapping
    public AuditService.Page query(Authentication auth, @RequestParam(required = false) Instant from,
                                   @RequestParam(required = false) Instant to,
                                   @RequestParam(required = false) String action,
                                   @RequestParam(defaultValue = "0") int page,
                                   @RequestParam(defaultValue = "50") int size) {
        return audit.query(userId(auth), isAdmin(auth), from, to, action, page, size);
    }

    @GetMapping(value = "/export.csv", produces = "text/csv")
    public ResponseEntity<String> export(Authentication auth, @RequestParam(required = false) Instant from,
                                         @RequestParam(required = false) Instant to,
                                         @RequestParam(required = false) String action,
                                         @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "1000") int size) {
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"audit.csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(audit.csv(userId(auth), isAdmin(auth), from, to, action, page, size));
    }

    private static UUID userId(Authentication auth) { return UUID.fromString(((UserPrincipal) auth.getPrincipal()).getId()); }
    private static boolean isAdmin(Authentication auth) { return auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")); }
}
