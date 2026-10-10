package com.shardvault.api.metadata;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "upload_parts")
public class UploadPartEntity {
    @Id
    public UUID id;
    public UUID uploadSessionId;
    public int partNumber;
    public String objectKey;
    public long length;
    @Column(columnDefinition = "bytea")
    public byte[] sha256;
    public Instant createdAt = Instant.now();
}
