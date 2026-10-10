package com.shardvault.api.service;

import com.shardvault.api.domain.RefreshTokenEntity;
import com.shardvault.api.domain.UserEntity;
import com.shardvault.api.domain.UserRole;
import com.shardvault.api.dto.AuthResponse;
import com.shardvault.api.dto.LoginRequest;
import com.shardvault.api.dto.RegisterRequest;
import com.shardvault.api.dto.UserDto;
import com.shardvault.api.exception.AccountLockedException;
import com.shardvault.api.exception.InvalidTokenException;
import com.shardvault.api.exception.TokenReuseException;
import com.shardvault.api.repository.RefreshTokenRepository;
import com.shardvault.api.repository.UserRepository;
import com.shardvault.api.security.JwtService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    private final boolean cookieSecure;
    private final String cookieSameSite;
    private final int maxFailedAttempts;
    private final int lockoutDurationMinutes;
    private final String dummyPasswordHash;

    private final SecureRandom secureRandom = new SecureRandom();

    public record AuthResult(AuthResponse response, ResponseCookie refreshCookie) {}

    public AuthService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            @Value("${shardvault.security.cookie.secure:false}") boolean cookieSecure,
            @Value("${shardvault.security.cookie.same-site:Strict}") String cookieSameSite,
            @Value("${shardvault.security.lockout.max-failed-attempts:5}") int maxFailedAttempts,
            @Value("${shardvault.security.lockout.lockout-duration-minutes:15}") int lockoutDurationMinutes) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.cookieSecure = cookieSecure;
        this.cookieSameSite = cookieSameSite;
        this.maxFailedAttempts = maxFailedAttempts;
        this.lockoutDurationMinutes = lockoutDurationMinutes;
        this.dummyPasswordHash = passwordEncoder.encode("dummy-password-for-timing-safety-1234");
    }

    @Transactional
    public AuthResult register(RegisterRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase();

        if (userRepository.existsByEmail(normalizedEmail)) {
            // Breach-safe error: do not explicitly state whether the email exists
            throw new IllegalArgumentException("Registration could not be completed with the provided credentials. Please check your details or sign in.");
        }

        String userId = UUID.randomUUID().toString();
        String hashedPassword = passwordEncoder.encode(request.password());

        UserEntity user = new UserEntity(userId, normalizedEmail, hashedPassword, UserRole.ROLE_USER.getAuthority());
        userRepository.save(user);

        return createAuthSession(user);
    }

    @Transactional
    public AuthResult login(LoginRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase();
        Optional<UserEntity> userOpt = userRepository.findByEmail(normalizedEmail);

        if (userOpt.isEmpty()) {
            // Timing attack defense: run password hashing against dummy hash
            passwordEncoder.matches(request.password(), dummyPasswordHash);
            throw new BadCredentialsException("Invalid email or password");
        }

        UserEntity user = userOpt.get();

        if (user.getLockoutUntil() != null && user.getLockoutUntil().isAfter(Instant.now())) {
            long remainingSeconds = Duration.between(Instant.now(), user.getLockoutUntil()).getSeconds();
            throw new AccountLockedException("Account is temporarily locked due to repeated failed login attempts.", remainingSeconds);
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            int attempts = user.getFailedLoginAttempts() + 1;
            user.setFailedLoginAttempts(attempts);
            if (attempts >= maxFailedAttempts) {
                user.setLockoutUntil(Instant.now().plus(Duration.ofMinutes(lockoutDurationMinutes)));
            }
            userRepository.save(user);
            throw new BadCredentialsException("Invalid email or password");
        }

        // Reset failed login attempts on successful login
        user.setFailedLoginAttempts(0);
        user.setLockoutUntil(null);
        userRepository.save(user);

        return createAuthSession(user);
    }

    @Transactional
    public AuthResult refresh(String plainRefreshToken) {
        if (plainRefreshToken == null || plainRefreshToken.isBlank()) {
            throw new InvalidTokenException("Refresh token is required");
        }

        String tokenHash = hashToken(plainRefreshToken);
        Optional<RefreshTokenEntity> tokenOpt = refreshTokenRepository.findByTokenHash(tokenHash);

        if (tokenOpt.isEmpty()) {
            throw new InvalidTokenException("Invalid refresh token");
        }

        RefreshTokenEntity tokenEntity = tokenOpt.get();

        // Check for refresh token reuse
        if (tokenEntity.isRevoked()) {
            // Revoke the entire family
            refreshTokenRepository.revokeFamily(tokenEntity.getFamilyId());
            throw new TokenReuseException("Refresh token reuse detected. Token family has been revoked.");
        }

        if (tokenEntity.getExpiresAt().isBefore(Instant.now())) {
            throw new InvalidTokenException("Refresh token has expired");
        }

        // Revoke the old token (rotation)
        tokenEntity.setRevoked(true);
        refreshTokenRepository.save(tokenEntity);

        // Fetch user
        UserEntity user = userRepository.findById(tokenEntity.getUserId())
                .orElseThrow(() -> new InvalidTokenException("User no longer exists"));

        // Issue new token in the SAME family
        String newPlainRefreshToken = generateSecureToken();
        String newTokenHash = hashToken(newPlainRefreshToken);
        Instant newExpiry = Instant.now().plus(Duration.ofSeconds(jwtService.getRefreshTokenExpirationSeconds()));

        RefreshTokenEntity newTokenEntity = new RefreshTokenEntity(
                UUID.randomUUID().toString(),
                user.getId(),
                newTokenHash,
                tokenEntity.getFamilyId(),
                newExpiry
        );
        refreshTokenRepository.save(newTokenEntity);

        String accessToken = jwtService.generateAccessToken(user.getId(), user.getEmail(), user.getRole());
        ResponseCookie cookie = buildRefreshCookie(newPlainRefreshToken, jwtService.getRefreshTokenExpirationSeconds());

        AuthResponse authResponse = AuthResponse.of(
                accessToken,
                jwtService.getAccessTokenExpirationSeconds(),
                new UserDto(user.getId(), user.getEmail(), user.getRole())
        );

        return new AuthResult(authResponse, cookie);
    }

    @Transactional
    public ResponseCookie logout(String plainRefreshToken) {
        if (plainRefreshToken != null && !plainRefreshToken.isBlank()) {
            String tokenHash = hashToken(plainRefreshToken);
            refreshTokenRepository.findByTokenHash(tokenHash).ifPresent(t -> {
                t.setRevoked(true);
                refreshTokenRepository.save(t);
            });
        }
        return buildRefreshCookie("", 0);
    }

    @Transactional
    public ResponseCookie logoutAll(String userId) {
        if (userId != null && !userId.isBlank()) {
            refreshTokenRepository.revokeAllForUser(userId);
        }
        return buildRefreshCookie("", 0);
    }

    private AuthResult createAuthSession(UserEntity user) {
        String familyId = UUID.randomUUID().toString();
        String plainRefreshToken = generateSecureToken();
        String tokenHash = hashToken(plainRefreshToken);
        Instant expiry = Instant.now().plus(Duration.ofSeconds(jwtService.getRefreshTokenExpirationSeconds()));

        RefreshTokenEntity refreshTokenEntity = new RefreshTokenEntity(
                UUID.randomUUID().toString(),
                user.getId(),
                tokenHash,
                familyId,
                expiry
        );
        refreshTokenRepository.save(refreshTokenEntity);

        String accessToken = jwtService.generateAccessToken(user.getId(), user.getEmail(), user.getRole());
        ResponseCookie cookie = buildRefreshCookie(plainRefreshToken, jwtService.getRefreshTokenExpirationSeconds());

        AuthResponse authResponse = AuthResponse.of(
                accessToken,
                jwtService.getAccessTokenExpirationSeconds(),
                new UserDto(user.getId(), user.getEmail(), user.getRole())
        );

        return new AuthResult(authResponse, cookie);
    }

    private ResponseCookie buildRefreshCookie(String value, long maxAgeSeconds) {
        return ResponseCookie.from("refreshToken", value)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(cookieSameSite)
                .path("/api/v1/auth")
                .maxAge(maxAgeSeconds)
                .build();
    }

    private String generateSecureToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
}
