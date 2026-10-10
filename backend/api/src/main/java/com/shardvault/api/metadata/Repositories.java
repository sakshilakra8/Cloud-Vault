package com.shardvault.api.metadata;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface UserRepository extends JpaRepository<UserEntity, UUID> {
}

interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity, UUID> {
}

interface FolderRepository extends JpaRepository<FolderEntity, UUID> {
    Page<FolderEntity> findByUserIdAndParentIdAndDeletedAtIsNull(UUID userId, UUID parentId, Pageable pageable);

    Page<FolderEntity> findByUserIdAndParentIdIsNullAndDeletedAtIsNull(UUID userId, Pageable pageable);
}

interface FileRepository extends JpaRepository<FileEntity, UUID> {
    @Query("""
            select f from FileEntity f
            where f.userId = :userId
              and (:folderId is null and f.folderId is null or f.folderId = :folderId)
              and f.deletedAt is null
              and (:search is null or lower(f.name) like lower(concat('%', :search, '%')))
            """)
    Page<FileEntity> listActive(UUID userId, UUID folderId, String search, Pageable pageable);

    Page<FileEntity> findByUserIdAndDeletedAtIsNotNull(UUID userId, Pageable pageable);
}

interface ChunkRepository extends JpaRepository<ChunkEntity, UUID> {
    List<ChunkEntity> findByFileIdOrderByIdxAsc(UUID fileId);

    List<ChunkEntity> findByFileIdAndIdxBetweenOrderByIdxAsc(UUID fileId, long from, long to);
}

interface ProviderJpaRepository extends JpaRepository<ProviderEntity, String> {
}

interface ChunkReplicaRepository extends JpaRepository<ChunkReplicaEntity, UUID> {
    List<ChunkReplicaEntity> findByChunkId(UUID chunkId);

    List<ChunkReplicaEntity> findByChunkIdIn(List<UUID> chunkIds);
}

interface UploadSessionRepository extends JpaRepository<UploadSessionEntity, UUID> {
    List<UploadSessionEntity> findByStatusAndExpiresAtBefore(UploadStatus status, Instant expiresAt);
}

interface UploadPartRepository extends JpaRepository<UploadPartEntity, UUID> {
    Optional<UploadPartEntity> findByUploadSessionIdAndPartNumber(UUID uploadSessionId, int partNumber);

    List<UploadPartEntity> findByUploadSessionIdOrderByPartNumberAsc(UUID uploadSessionId);
}

interface ShareLinkRepository extends JpaRepository<ShareLinkEntity, UUID> {
}

interface AuditLogRepository extends JpaRepository<AuditLogEntity, UUID> {
}
