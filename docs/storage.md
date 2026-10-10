# ShardVault Storage Provider Layer

Layer 2 defines one storage backend contract and two implementations:

- `LocalFsProvider` for development and contract tests.
- `S3CompatibleProvider` for MinIO now and S3-compatible providers such as Backblaze B2
  or Cloudflare R2 later by changing configuration only.

## Contract

All providers implement `StorageProvider`:

```java
String id();
ProviderType type();
void put(String objectKey, InputStream data, long length);
InputStream get(String objectKey);
void delete(String objectKey);
boolean exists(String objectKey);
HealthStatus health();
Optional<Long> freeBytes();
ProviderLimits limits();
```

The provider receives opaque object keys and encrypted bytes from later layers. It does
not know file ids, users, chunks, or plaintext metadata.

## Reliability

`ReliableStorageProvider` decorates any provider with:

- operation timeout
- Resilience4j retry
- exponential backoff with jitter
- consistent exception mapping

`put` retries only when the supplied `InputStream` is repeatable. One-shot streams get a
single bounded attempt because retrying them would require buffering or spooling the
whole object.

## Provider Registry

`ProviderRegistry` loads enabled providers from the `providers` table:

```sql
select id, type, config_ref, max_chunk_bytes, max_object_bytes
from providers
where enabled = true
order by id
```

The database stores `config_ref`, not secrets. `EnvironmentProviderConfigResolver`
expands a ref such as `MINIO_DEV` into env vars:

- `MINIO_DEV_ENDPOINT_OVERRIDE`
- `MINIO_DEV_REGION`
- `MINIO_DEV_BUCKET`
- `MINIO_DEV_PATH_STYLE_ACCESS`
- `MINIO_DEV_ACCESS_KEY_ID`
- `MINIO_DEV_SECRET_ACCESS_KEY`

For local filesystem providers:

- `LOCAL_DEV_ROOT`
- `LOCAL_DEV_RATE_LIMIT_HINT`

## Tests

`StorageProviderContractTest` is the reusable provider compliance suite. A new provider
must implement `StorageProvider` and pass this test class.

Current concrete contract tests:

- `LocalFsProviderContractTest`
- `S3CompatibleProviderMinioContractTest`

Run:

```bash
./mvnw verify
```

On Windows:

```powershell
.\mvnw.cmd verify
```
