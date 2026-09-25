# Rate limiting and upstream timeouts

WhatPlan enforces request limits in the backend using Redis so multiple backend replicas share the same counters. The Redis service is on the private Compose network only, requires `REDIS_PASSWORD`, has no published port, uses volatile storage, and is capped at 128 MB. `noeviction` is intentional: if Redis runs out of memory, new counter writes fail and protected requests return `503` instead of silently losing counters. Redis restart/failover resets counters, so the reverse proxy/WAF should also enforce coarse connection and request limits.

Current fixed-window policies:

| Request | Limit | Window | Identity |
| --- | ---: | ---: | --- |
| `POST /api/auth/login` | 10 | 15 minutes | Client IP |
| `POST /api/auth/refresh` | 60 per IP and 10 per refresh cookie | 5 minutes | Separate IP and refresh-cookie counters (cookie hashed before Redis key storage) |
| `POST /api/couple/invitations` | 20 | 5 minutes | Authenticated local account, otherwise client IP |
| `POST /api/couple/invitations/accept` | 15 | 5 minutes | Authenticated local account, otherwise client IP |
| `GET /api/tmdb/**` | 30 | 1 minute | Authenticated local account, otherwise client IP |
| Multipart `POST /api/**` | 20 | 1 hour | Authenticated local account, otherwise client IP |
| `GET /api/**` photo/media routes | 1,200 | 5 minutes | Authenticated local account, otherwise client IP |

These are abuse controls, not product quotas. Invitation creation has its own couple-scoped daily quota. Tune limits with production traffic and edge protections; do not raise login or invitation limits just to hide a client retry loop.

## Proxy configuration

Set `TRUSTED_PROXY_ADDRESSES` to a comma-separated list of exact IP addresses for the reverse proxies that connect directly to the backend (for example `10.0.0.5,2001:db8::5`). CIDR ranges are not accepted. Leave it empty if requests connect directly. The backend ignores `X-Forwarded-For` from untrusted peers and walks the forwarded chain from the nearest trusted hop. The proxy must replace/append that header correctly and must not expose a path that bypasses the proxy while retaining a trusted source address.

## Authentication-service timeouts

Configure `AUTH_SERVICE_CONNECT_TIMEOUT_SECONDS` (1–30, default 3) and `AUTH_SERVICE_READ_TIMEOUT_SECONDS` (1–60, default 5). Network failures/timeouts become a sanitized `503`. Requests are not automatically retried: login, refresh, and logout are stateful and automatic retries can duplicate effects or rotate refresh credentials unexpectedly.

## Operations

- Provide a long random `REDIS_PASSWORD` through the deployment secret manager; do not commit it to `.env` or source control.
- Alert on Redis memory pressure, backend `503` rate-limit responses, Redis connection errors, and Redis restarts.
- Protect Redis availability and access at the host/network level. Do not publish port 6379.
- Apply request/body-size limits and coarse rate limits at the public edge as defense in depth. The edge must also limit expensive non-multipart requests and paths not covered by application policies.
- Rate keys include a SHA-256 digest of the identity; raw IP addresses and refresh credentials are not used as Redis key values.
