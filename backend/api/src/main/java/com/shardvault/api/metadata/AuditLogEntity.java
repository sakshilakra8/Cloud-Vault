package com.shardvault.api.metadata;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_logs")
public class AuditLogEntity {
    @Id
    public UUID id;
    public UUID userId;
    public String action;
    @Column(columnDefinition = "jsonb")
    public String metadata;
    public Instant createdAt = Instant.now();
}
