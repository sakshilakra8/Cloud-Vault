package com.shardvault.api.repository;

import com.shardvault.api.metadata.ProviderEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CloudProviderRepository extends JpaRepository<ProviderEntity, String> {
    long countById(String id);
}
