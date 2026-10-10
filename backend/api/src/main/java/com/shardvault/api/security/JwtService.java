package com.shardvault.api.security;

import com.shardvault.api.exception.InvalidTokenException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

@Service
public class JwtService {

    private final SecretKey signingKey;
    private final long accessTokenExpirationSeconds;
    private final long refreshTokenExpirationSeconds;

    public JwtService(
            @Value("${shardvault.security.jwt.secret:c2hhcmR2YXVsdC1zdXBlci1zZWNyZXQtand0LWtleS1mb3Itc2lnbmluZy0yNTZiaXQ=}") String secret,
            @Value("${shardvault.security.jwt.access-token-expiration-seconds:900}") long accessTokenExpirationSeconds,
            @Value("${shardvault.security.jwt.refresh-token-expiration-seconds:604800}") long refreshTokenExpirationSeconds) {
        this.accessTokenExpirationSeconds = accessTokenExpirationSeconds;
        this.refreshTokenExpirationSeconds = refreshTokenExpirationSeconds;

        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(secret);
            if (keyBytes.length < 32) {
                // If decoded key is too short, pad or fallback to utf-8 bytes
                keyBytes = secret.getBytes(StandardCharsets.UTF_8);
            }
        } catch (IllegalArgumentException e) {
            keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        }

        if (keyBytes.length < 32) {
            byte[] padded = new byte[32];
            System.arraycopy(keyBytes, 0, padded, 0, Math.min(keyBytes.length, 32));
            keyBytes = padded;
        }

        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateAccessToken(String userId, String email, String role) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(accessTokenExpirationSeconds);

        return Jwts.builder()
                .subject(userId)
                .claim("email", email)
                .claim("role", role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    public Claims parseAndValidateToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            throw new InvalidTokenException("Access token has expired");
        } catch (JwtException | IllegalArgumentException e) {
            throw new InvalidTokenException("Invalid or malformed access token: " + e.getMessage());
        }
    }

    public String extractUserId(String token) {
        return parseAndValidateToken(token).getSubject();
    }

    public String extractEmail(String token) {
        return parseAndValidateToken(token).get("email", String.class);
    }

    public String extractRole(String token) {
        return parseAndValidateToken(token).get("role", String.class);
    }

    public long getAccessTokenExpirationSeconds() {
        return accessTokenExpirationSeconds;
    }

    public long getRefreshTokenExpirationSeconds() {
        return refreshTokenExpirationSeconds;
    }

    public SecretKey getSigningKey() {
        return signingKey;
    }
}
