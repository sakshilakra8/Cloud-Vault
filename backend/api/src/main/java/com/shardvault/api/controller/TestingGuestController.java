package com.shardvault.api.controller;

import com.shardvault.api.dto.AuthResponse;
import com.shardvault.api.dto.RegisterRequest;
import com.shardvault.api.service.AuthService;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Issues isolated guest identities only when the testing Spring profile is active. */
@RestController
@Profile("testing")
public class TestingGuestController {
    private final AuthService authService;

    public TestingGuestController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/api/v1/auth/guest")
    public ResponseEntity<AuthResponse> createGuest() {
        UUID unique = UUID.randomUUID();
        String email = "guest-" + unique + "@testing.invalid";
        String password = "Aa1!" + unique.toString().toUpperCase();
        AuthService.AuthResult result = authService.register(new RegisterRequest(email, password));
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.SET_COOKIE, result.refreshCookie().toString())
                .body(result.response());
    }
}
