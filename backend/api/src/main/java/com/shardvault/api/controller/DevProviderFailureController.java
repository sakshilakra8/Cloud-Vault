package com.shardvault.api.controller;

import com.shardvault.api.service.AdminProviderService;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@Profile("dev")
@PreAuthorize("hasRole('ADMIN')")
@RequestMapping("/api/v1/admin/providers")
public class DevProviderFailureController {
    private final AdminProviderService service;
    public DevProviderFailureController(AdminProviderService service) { this.service = service; }
    @PostMapping("/{id}/simulate-failure")
    public Map<String, String> simulateFailure(@PathVariable String id) {
        service.simulateFailure(id);
        return Map.of("providerId", id, "circuit", "OPEN");
    }
}
