package com.shardvault.api.security;

import com.shardvault.api.exception.InvalidTokenException;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {
    private final JwtService service = new JwtService(
            "c2hhcmR2YXVsdC1zdXBlci1zZWNyZXQtand0LWtleS1mb3Itc2lnbmluZy0yNTZiaXQ=", 900, 604800);

    @Test
    void acceptsValidAccessTokenAndRejectsTampering() {
        String token = service.generateAccessToken("user-1", "user@example.com", "ROLE_USER");
        assertEquals("user-1", service.parseAndValidateToken(token).getSubject());
        String tampered = token.substring(0, token.length() - 2) + "aa";
        assertThrows(InvalidTokenException.class, () -> service.parseAndValidateToken(tampered));
        assertThrows(InvalidTokenException.class, () -> service.parseAndValidateToken("not.a.jwt"));
    }

    @Test
    void rejectsExpiredAccessToken() {
        Instant now = Instant.now();
        String expired = Jwts.builder().subject("user-1")
                .issuedAt(Date.from(now.minusSeconds(120)))
                .expiration(Date.from(now.minusSeconds(60)))
                .signWith(service.getSigningKey(), Jwts.SIG.HS256).compact();
        assertThrows(InvalidTokenException.class, () -> service.parseAndValidateToken(expired));
    }
}
