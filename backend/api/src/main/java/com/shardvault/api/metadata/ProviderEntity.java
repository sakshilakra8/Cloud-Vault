package com.shardvault.api.metadata;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "providers")
public class ProviderEntity {
    @Id
    public String id;
    public String type;
    public String name;
    public String configRef;
    public String health;
    public Long freeBytes;
    public BigDecimal costScore;
    public Long maxChunkBytes;
    public Long maxObjectBytes;
    public boolean enabled;
}
