# Security review (ASVS-lite)

This is an implementation checklist, not a formal penetration test or ASVS certification.

| Area | Control / current implementation | Verification |
|---|---|---|
| Authentication | Argon2id password encoder, short-lived JWT, hashed rotating refresh tokens | Auth service and API tests |
| Authorization | Stateless Spring Security, role checks, owner identifiers in resource queries | Add/keep cross-user IDOR tests for every resource endpoint |
| Input handling | Bean validation, safe filename handling, LocalFS canonical path checks and symlink rejection | `LocalFsProviderContractTest`; API validation tests |
| Output headers | Content-Disposition filename sanitization; `X-Content-Type-Options: nosniff`; restrictive API-doc CSP | Controller/security tests |
| Share credentials | Random tokens stored as SHA-256 hashes; constant-time hash comparison; atomic download counter | Share service tests, including concurrent limit test |
| Abuse controls | IP and user rate limiting, temporary auth lockout, bounded upload/request sizes | Rate-limit and lockout tests |
| Secrets | Environment/cloud credential references; JSON logs omit event messages and exception payloads | Review log configuration and deployment environment |
| Transport | Caddy automatic TLS; secure refresh cookie in production | Deploy behind Caddy; validate TLS externally |
| Errors | RFC 7807 response mapping without stack traces | API error tests; inspect production responses |
| Dependencies | Trivy filesystem scan in CI | `.github/workflows/ci.yml`; review findings before release |

Remaining release checks: complete a route-by-route IDOR audit (including provider/admin endpoints), verify all rate-limited endpoints under real Redis, and run a dependency scan against the final built image. Do not expose Prometheus/Actuator publicly.
