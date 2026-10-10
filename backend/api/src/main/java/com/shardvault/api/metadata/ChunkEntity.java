package com.shardvault.api.metadata;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "chunks")
public class ChunkEntity {
    @Id
    public UUID id;
    public UUID fileId;
    public long idx;
    public int plainSize;
    @Column(columnDefinition = "bytea")
    public byte[] nonce;
    @Column(columnDefinition = "bytea")
    public byte[] ciphertextSha256;
    public Instant createdAt = Instant.now();
}
