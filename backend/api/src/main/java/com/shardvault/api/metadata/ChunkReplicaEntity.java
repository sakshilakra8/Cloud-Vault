package com.shardvault.api.metadata;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "chunk_replicas")
public class ChunkReplicaEntity {
    @Id
    public UUID id;
    public UUID chunkId;
    public String providerId;
    public String objectKey;
    public String status;
    public Instant lastVerifiedAt;
}
