package com.shardvault.api.controller;

import com.shardvault.api.dto.AuthResponse;
import com.shardvault.api.dto.LoginRequest;
import com.shardvault.api.dto.RegisterRequest;
import com.shardvault.api.dto.UserDto;
import com.shardvault.api.security.UserPrincipal;
import com.shardvault.api.service.AuthService;
import com.shardvault.api.service.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final AuditService auditService;

    public AuthController(AuthService authService, AuditService auditService) {
        this.authService = authService;
        this.auditService = auditService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request, HttpServletRequest http) {
        var result = authService.register(request);
        auditService.record(UUID.fromString(result.response().user().id()), "AUTH_REGISTER", "USER", result.response().user().id(), "SUCCESS", http.getRemoteAddr(), http.getHeader("User-Agent"));
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.SET_COOKIE, result.refreshCookie().toString())
                .body(result.response());
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        AuthService.AuthResult result;
        try { result = authService.login(request); }
        catch (RuntimeException ex) {
            auditService.record(null, "AUTH_LOGIN", "USER", null, "FAILURE", http.getRemoteAddr(), http.getHeader("User-Agent"));
            throw ex;
        }
        auditService.record(UUID.fromString(result.response().user().id()), "AUTH_LOGIN", "USER", result.response().user().id(), "SUCCESS", http.getRemoteAddr(), http.getHeader("User-Agent"));
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, result.refreshCookie().toString())
                .body(result.response());
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            @CookieValue(name = "refreshToken", required = false) String cookieRefreshToken,
            @RequestHeader(name = "X-Refresh-Token", required = false) String headerRefreshToken,
            HttpServletRequest http) {
        String token = (cookieRefreshToken != null && !cookieRefreshToken.isBlank())
                ? cookieRefreshToken
                : headerRefreshToken;
        var result = authService.refresh(token);
        auditService.record(UUID.fromString(result.response().user().id()), "AUTH_REFRESH", "USER", result.response().user().id(), "SUCCESS", http.getRemoteAddr(), http.getHeader("User-Agent"));
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, result.refreshCookie().toString())
                .body(result.response());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = "refreshToken", required = false) String cookieRefreshToken,
            @RequestHeader(name = "X-Refresh-Token", required = false) String headerRefreshToken,
            Authentication authentication, HttpServletRequest http) {
        String token = (cookieRefreshToken != null && !cookieRefreshToken.isBlank())
                ? cookieRefreshToken
                : headerRefreshToken;
        ResponseCookie clearCookie = authService.logout(token);
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal principal)
            auditService.record(UUID.fromString(principal.getId()), "AUTH_LOGOUT", "USER", principal.getId(), "SUCCESS", http.getRemoteAddr(), http.getHeader("User-Agent"));
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, clearCookie.toString())
                .build();
    }

    @PostMapping("/logout-all")
    public ResponseEntity<Void> logoutAll(Authentication authentication, HttpServletRequest http) {
        String userId = authentication != null ? authentication.getName() : null;
        ResponseCookie clearCookie = authService.logoutAll(userId);
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal principal)
            auditService.record(UUID.fromString(principal.getId()), "AUTH_LOGOUT_ALL", "USER", principal.getId(), "SUCCESS", http.getRemoteAddr(), http.getHeader("User-Agent"));
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, clearCookie.toString())
                .build();
    }

    @GetMapping("/me")
    public ResponseEntity<UserDto> me(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal principal) {
            return ResponseEntity.ok(new UserDto(principal.getId(), principal.getEmail(), principal.getRole()));
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
}
