package com.shardvault.api.metadata;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "upload_sessions")
public class UploadSessionEntity {
    @Id
    public UUID id;
    public UUID fileId;
    public UUID userId;
    public UUID folderId;
    public String fileName;
    public String mime;
    public Long expectedSize;
    public long partSize;
    @Enumerated(EnumType.STRING)
    public UploadStatus status;
    public Instant createdAt = Instant.now();
    public Instant updatedAt = Instant.now();
    public Instant expiresAt;
}
