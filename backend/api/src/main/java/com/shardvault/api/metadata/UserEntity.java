package com.shardvault.api.metadata;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
public class UserEntity {
    @Id
    public UUID id;
    public String email;
    public String displayName;
    public Instant createdAt = Instant.now();
}
