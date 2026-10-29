package com.shardvault.api.repository;

import com.shardvault.api.domain.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.time.Instant;

@Repository
public interface UserRepository extends JpaRepository<UserEntity, String> {
    Optional<UserEntity> findByEmail(String email);
    boolean existsByEmail(String email);

    @Modifying
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Query("UPDATE UserEntity u SET u.failedLoginAttempts = :attempts, u.lockoutUntil = :lockoutUntil WHERE u.id = :id")
    void recordFailedLogin(@Param("id") String id, @Param("attempts") int attempts, @Param("lockoutUntil") Instant lockoutUntil);
}
