# Storage providers

Providers are selected from the `providers` table. `config_ref` is an environment
variable prefix, not a place for credentials. The API accepts only this prefix
and provider metadata; secrets remain in environment variables or cloud identity
credentials. Enable a provider only after verifying its configuration and health.

## S3-compatible providers

Backblaze B2 and Cloudflare R2 use the existing `S3CompatibleProvider`; they do
not require a provider-specific implementation. Use provider type
`BACKBLAZE_B2` or `CLOUDFLARE_R2`, and set endpoint, region, bucket, path-style
mode, access key ID and secret key using the examples in `.env.example`.

B2 endpoint and region must match the bucket's B2 region. R2 uses the account
endpoint and region `auto`. These providers do not expose a reliable free-space
estimate through the S3 API. Their object size and request limits depend on the
account and service plan.

## Google Cloud Storage

Set `GCS_DEV_BUCKET` (or the corresponding config-ref prefix bucket variable)
and configure Application Default Credentials. For local service-account
credentials, set `GOOGLE_APPLICATION_CREDENTIALS` to the JSON key file path; in
production prefer the workload identity attached to the runtime. Grant the
identity object read, create, and delete permissions on the target bucket.
GCS reports no free-byte estimate. The provider streams objects using the GCS
client library and uses the normal provider limits configured in the database.

## Telegram Bot API (experimental)

Create a bot, add it to a private channel with permission to post and delete
messages, then configure the bot token and channel chat ID in environment
variables. Use provider type `TELEGRAM_BOT`. Uploads use `sendDocument`; the
provider key is the returned Telegram `file_id` plus `message_id`. Downloads use
`getFile` and the Telegram file endpoint; deletion uses `deleteMessage` and
health checks use `getMe`.

Telegram's Bot API download limit is about 20 MB. The provider advertises and
enforces a maximum chunk size of 15 MiB, even if a larger limit is configured.
It paces sends at 20 messages per minute per provider chat and honors
`retry_after` on 429 responses. It reports reliability tier `LOW`; use it only
for replicas and never as the only copy of an object. Keep chunks encrypted
before sending. Telegram is not a general-purpose object store and may apply
additional account or API restrictions.

The current upload pipeline should choose an effective chunk size no larger
than both the configured chunk size and the selected provider's
`limits().maxChunkBytes()`. Provider keys that differ from caller keys must be
persisted from `putAndGetKey` so later downloads and deletions use the durable
provider identifier.

## Admin endpoints

All endpoints require the `ADMIN` role:

* `POST /api/v1/admin/providers` registers provider metadata (`id`, `name`,
  `type`, `configRef`, and optional size limits). Credentials are not accepted.
* `POST /api/v1/admin/providers/{id}/enable` and `/disable` toggle routing.
* `POST /api/v1/admin/providers/{id}/health` runs a provider health check.
* `GET /api/v1/admin/providers/{id}/usage` reports replica count and logical
  bytes represented by replicas.

## Credential-gated integration tests

The GCS contract suite runs when `SHARDVAULT_TEST_GCS_BUCKET` is set and ADC is
available. The Telegram contract suite runs when both
`SHARDVAULT_TEST_TELEGRAM_BOT_TOKEN` and
`SHARDVAULT_TEST_TELEGRAM_CHAT_ID` are set. Without those variables the tests
are skipped automatically. The existing MinIO suite exercises S3-compatible
operations locally; use real B2/R2 credentials and the same S3 provider contract
for service-specific verification.
