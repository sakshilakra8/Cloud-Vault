CREATE TABLE IF NOT EXISTS folders (
    id VARCHAR(64) PRIMARY KEY,
    owner_id VARCHAR(64) NOT NULL,
    parent_id VARCHAR(64),
    name VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_parent_folder FOREIGN KEY (parent_id) REFERENCES folders(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS files (
    id VARCHAR(64) PRIMARY KEY,
    owner_id VARCHAR(64) NOT NULL,
    folder_id VARCHAR(64),
    name VARCHAR(255) NOT NULL,
    size_bytes BIGINT NOT NULL,
    mime_type VARCHAR(128) NOT NULL,
    checksum_sha256 VARCHAR(64) NOT NULL,
    encrypted_dek_b64 TEXT NOT NULL,
    dek_iv_b64 VARCHAR(64) NOT NULL,
    dek_auth_tag_b64 VARCHAR(64) NOT NULL,
    master_key_version INT NOT NULL,
    chunk_size_bytes INT NOT NULL,
    total_chunks INT NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_file_folder FOREIGN KEY (folder_id) REFERENCES folders(id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS file_chunks (
    file_id VARCHAR(64) NOT NULL,
    chunk_index INT NOT NULL,
    storage_provider_id VARCHAR(64) NOT NULL,
    storage_key VARCHAR(512) NOT NULL,
    plain_size_bytes INT NOT NULL,
    encrypted_size_bytes INT NOT NULL,
    nonce_b64 VARCHAR(64) NOT NULL,
    tag_b64 VARCHAR(64) NOT NULL,
    chunk_sha256 VARCHAR(64) NOT NULL,
    PRIMARY KEY (file_id, chunk_index),
    CONSTRAINT fk_chunk_file FOREIGN KEY (file_id) REFERENCES files(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS share_links (
    id VARCHAR(64) PRIMARY KEY,
    resource_type VARCHAR(16) NOT NULL,
    resource_id VARCHAR(64) NOT NULL,
    owner_id VARCHAR(64) NOT NULL,
    token VARCHAR(128) NOT NULL UNIQUE,
    permission VARCHAR(16) NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_folders_owner ON folders(owner_id);
CREATE INDEX IF NOT EXISTS idx_files_owner ON files(owner_id);
CREATE INDEX IF NOT EXISTS idx_files_folder ON files(folder_id);
CREATE INDEX IF NOT EXISTS idx_share_token ON share_links(token);
