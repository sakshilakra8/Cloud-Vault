# Storage routing, replication, and failover

The router first filters to enabled providers with a non-open circuit, enough
known free space, and `maxChunkBytes >= ciphertext size`. Providers that cannot
report free space are treated as having unknown capacity and remain eligible;
the provider will return a write failure if it is full. Existing replica
provider IDs are excluded when selecting another copy.

The score is a weighted sum (weights are configurable in `application.yml`):

```text
score = reliability_weight * rolling_success_rate
      + speed_weight * (1 - normalized_EWMA_latency)
      + free_space_weight * free_space_ratio
      + cost_weight * (1 - normalized_cost_score)
```

Defaults are 0.40 reliability, 0.25 speed, 0.20 free space, and 0.15 cost.
Chunk index rotates ties/order within the normal reliability tier so a large
file does not place every chunk on the same provider. Low reliability providers
are ordered after standard providers and are ineligible for a sole copy.
Free-space score is normalized against the largest known free-byte value among
eligible providers. Providers that cannot report capacity use their telemetry
ratio (initially 0.5); known insufficient capacity is always a hard rejection.

`ProviderTelemetry` tracks rolling outcomes, EWMA latency, free-space ratio,
and a Resilience4j circuit breaker. The breaker opens after its configured
failure window, rejects calls while open, then allows a half-open probe after
the open wait duration. The current default monitor uses a five-call window and
30 second open interval.

Replication policy values are `STANDARD` (one copy), `IMPORTANT` (two distinct
providers), and `CRITICAL` (three distinct providers). Files record a
`replication_state` of `HEALTHY` or `DEGRADED`. The database outbox table
`storage_jobs` supports asynchronous replication, repair, and scrub work with
retry and dead-letter states. Work should be rate-limited and batched so user
requests retain provider capacity.

Reads should try healthy replicas in router order, verify the ciphertext
SHA-256 before returning bytes, mark failures as `SUSPECT` or `CORRUPT`, and
enqueue repair before trying the next replica. An object is returned only after
one replica passes the checksum. Repair copies ciphertext directly so it never
needs plaintext keys. Scrubbing samples stored ciphertext and schedules repair
when verification fails.

Provider health and telemetry are process-local in this implementation, while
the provider catalog and replication job outbox are database-backed. A restart
resets rolling scores and circuits. The repository currently has no file upload
or download controller, so the router primitives and job schema are present but
the complete API path and MinIO stop/failover demo still require that pipeline.

## Failure behavior

* A disabled, open-circuit, undersized, known-full, or already-used provider is
  skipped before scoring.
* A provider timeout or I/O error records a failed operation and opens its
  circuit when the threshold is reached.
* A checksum mismatch marks that replica corrupt, queues repair, and falls back
  to the next healthy replica.
* If no healthy standard replica is available, reads fail with an availability
  error rather than returning unverified ciphertext.
* Repair retries use exponential backoff and move to a dead-letter state after
  the retry limit; an admin can trigger another repair job.
