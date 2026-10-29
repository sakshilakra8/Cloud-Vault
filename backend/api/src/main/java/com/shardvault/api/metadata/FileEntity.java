package com.shardvault.api.metadata;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "files")
public class FileEntity {
    @Id
    public UUID id;
    public UUID userId;
    public UUID folderId;
    public String name;
    @Enumerated(EnumType.STRING)
    public FileStatus status;
    public long sizeBytes;
    public String mime;
    @Column(columnDefinition = "bytea")
    public byte[] dekWrapped;
    public int kekVersion = 1;
    @Column(columnDefinition = "bytea")
    public byte[] plaintextSha256;
    public String replicationPolicy = "STANDARD";
    public String replicationState = "HEALTHY";
    public UUID thumbnailFileId;
    public Instant deletedAt;
    public Instant createdAt = Instant.now();
    public Instant updatedAt = Instant.now();
}
