# ShardVault

Secure multi-cloud encrypted file storage platform.

## Current Layers

- Layer 1: `backend/crypto` implements streaming AES-GCM chunk encryption and envelope
  DEK wrapping.
- Layer 2: `backend/storage` implements the storage provider abstraction, LocalFS,
  S3-compatible storage, reliability wrapping, provider registry loading, and provider
  contract tests.

## Test

```bash
./mvnw verify
```

On Windows:

```powershell
.\mvnw.cmd verify
```
