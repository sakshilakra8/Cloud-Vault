package com.shardvault.api.metadata;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "folders")
public class FolderEntity {
    @Id
    public UUID id;
    public UUID userId;
    public UUID parentId;
    public String name;
    public Instant deletedAt;
    public Instant createdAt = Instant.now();
    public Instant updatedAt = Instant.now();
}
