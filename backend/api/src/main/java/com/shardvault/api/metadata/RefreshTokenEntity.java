package com.shardvault.api.metadata;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refresh_tokens")
public class RefreshTokenEntity {
    @Id
    public UUID id;
    public UUID userId;
    public String tokenHash;
    public Instant expiresAt;
    public Instant revokedAt;
}
