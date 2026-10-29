# Sharing, audit logs, and quotas

## Share links

Create a read-only file share with `POST /api/v1/shares` using `fileId` and
optional `expiresAt`, `maxDownloads`, and `password`. The returned token is
shown once; the database stores only its SHA-256 hash. Passwords use the
configured Spring `PasswordEncoder`. Share metadata is available at
`GET /s/{token}` and a download at `GET /s/{token}/download`. Supply an optional
password in `X-Share-Password`. Downloads support one RFC 7233 byte range per
request. Public share routes are IP rate limited.

Link state is `ACTIVE`, `EXPIRED`, `LIMIT_REACHED`, or `REVOKED`. Download
capacity is enforced with one conditional SQL update, so parallel requests
cannot claim the same final slot. Revocation is owner scoped at
`DELETE /api/v1/shares/{shareId}`. Folder sharing and viewer ACLs are not
implemented in this layer.

The public endpoint currently streams verified encrypted chunk bytes. The
repository does not have a complete upload/download API or a share decryption
format, so clients still need the file decryption metadata/protocol from the
eventual file API.

## Audit

`GET /api/v1/audit` returns paginated audit records and accepts `from`, `to`,
`action`, `page`, and `size`. Users see their own events; `ROLE_ADMIN` can query
all users. `GET /api/v1/audit/export.csv` exports the same filtered page as CSV.
Audit records are inserted only; no application update/delete path is exposed.
Events currently include register/login/logout, share create/revoke/download,
admin provider management, quota changes, repair requests, IP, user agent,
resource, and result. Upload, file download, and delete events will be emitted
when those API routes exist.

## Quotas

`GET /api/v1/quota/usage` returns quota, used, and reserved bytes. The default
quota is 5 GiB and can be set with `SHARDVAULT_DEFAULT_QUOTA_BYTES`. Admins can
change a user's limit using `PUT /api/v1/admin/quotas/{userId}` with
`{"quotaBytes":...}`. `QuotaService.reserve` atomically reserves expected bytes
before upload initialization; `commit` and `release` settle that reservation.
The upload initialization API is not present yet, so that API must call
`reserve` before accepting a body.
