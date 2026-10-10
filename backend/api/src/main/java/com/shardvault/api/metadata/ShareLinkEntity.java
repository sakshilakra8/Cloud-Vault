package com.shardvault.api.metadata;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "share_links")
public class ShareLinkEntity {
    @Id
    public UUID id;
    public UUID fileId;
    public String tokenHash;
    public Instant expiresAt;
    public Integer maxDownloads;
    public int downloadCount;
    public String passwordHash;
    public boolean revoked;
    public Instant createdAt = Instant.now();
}
