create table users (
    id uuid primary key,
    email text not null unique,
    display_name text,
    created_at timestamptz not null default now()
);

create table refresh_tokens (
    id uuid primary key,
    user_id uuid not null references users(id) on delete cascade,
    token_hash text not null unique,
    expires_at timestamptz not null,
    revoked_at timestamptz
);

create table folders (
    id uuid primary key,
    user_id uuid not null references users(id) on delete cascade,
    parent_id uuid references folders(id) on delete set null,
    name text not null,
    deleted_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);
create index idx_folders_user_parent on folders(user_id, parent_id);

create table files (
    id uuid primary key,
    user_id uuid not null references users(id) on delete cascade,
    folder_id uuid references folders(id) on delete set null,
    name text not null,
    status text not null,
    size_bytes bigint not null default 0,
    mime text,
    dek_wrapped bytea,
    kek_version integer not null default 1,
    plaintext_sha256 bytea,
    replication_policy text not null default 'single',
    thumbnail_file_id uuid,
    deleted_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);
create index idx_files_user_folder_name on files(user_id, folder_id, lower(name));
create index idx_files_user_deleted on files(user_id, deleted_at);

create table chunks (
    id uuid primary key,
    file_id uuid not null references files(id) on delete cascade,
    idx bigint not null,
    plain_size integer not null,
    nonce bytea not null,
    ciphertext_sha256 bytea not null,
    created_at timestamptz not null default now(),
    unique(file_id, idx)
);

create table providers (
    id text primary key,
    type text not null,
    name text not null,
    config_ref text not null,
    health text not null default 'unknown',
    free_bytes bigint,
    cost_score numeric,
    max_chunk_bytes bigint,
    max_object_bytes bigint,
    enabled boolean not null default true
);
create index idx_providers_enabled on providers(enabled);

create table chunk_replicas (
    id uuid primary key,
    chunk_id uuid not null references chunks(id) on delete cascade,
    provider_id text not null references providers(id),
    object_key text not null,
    status text not null,
    last_verified_at timestamptz,
    unique(chunk_id, provider_id)
);
create index idx_chunk_replicas_chunk on chunk_replicas(chunk_id);
create unique index idx_chunk_replicas_provider_object on chunk_replicas(provider_id, object_key);

create table share_links (
    id uuid primary key,
    file_id uuid not null references files(id) on delete cascade,
    token_hash text not null unique,
    expires_at timestamptz,
    max_downloads integer,
    download_count integer not null default 0,
    password_hash text,
    revoked boolean not null default false,
    created_at timestamptz not null default now()
);

create table audit_logs (
    id uuid primary key,
    user_id uuid references users(id) on delete set null,
    action text not null,
    metadata jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now()
);
create index idx_audit_logs_user_created on audit_logs(user_id, created_at desc);

create table upload_sessions (
    id uuid primary key,
    file_id uuid not null references files(id) on delete cascade,
    user_id uuid not null references users(id) on delete cascade,
    folder_id uuid references folders(id) on delete set null,
    file_name text not null,
    mime text,
    expected_size bigint,
    part_size bigint not null,
    status text not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    expires_at timestamptz not null
);
create index idx_upload_sessions_status_expires on upload_sessions(status, expires_at);

create table upload_parts (
    id uuid primary key,
    upload_session_id uuid not null references upload_sessions(id) on delete cascade,
    part_number integer not null,
    object_key text not null,
    length bigint not null,
    sha256 bytea not null,
    created_at timestamptz not null default now(),
    unique(upload_session_id, part_number)
);
