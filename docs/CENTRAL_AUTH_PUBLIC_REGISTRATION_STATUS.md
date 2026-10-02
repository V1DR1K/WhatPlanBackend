# Central authentication: public registration release gate

## What this repository verifies

WhatPlan delegates credential handling to the external central-auth service. The backend client currently calls these upstream paths:

| WhatPlan operation | Central-auth path currently called |
| --- | --- |
| Login | `POST /api/login` |
| Refresh | `POST /api/refresh` |
| Logout | `POST /api/logout` |
| Current account | `GET /api/me` |
| Change password | `POST /api/change-password` |

The local `User` stores no password hash. Automatic local provisioning is restricted to `USER`; configuring `AUTH_DEFAULT_ROLE=ADMIN` now prevents startup. Login and refresh fail closed unless the central response identifies the account as `ACTIVE`. The role remains sourced from the local account for existing users; a privileged role must be granted by an explicit, audited administrative operation.

## Not verified / not implemented

No central-auth source repository, release/version, OpenAPI document, or integration environment is present in the WhatPlan workspace. The client has no registration, email-verification, password-recovery, account-deactivation, or account-deletion operation. The required request/response schemas, username/password rules, verification states, reset-token semantics, account lifecycle, and refresh revocation on account closure therefore cannot be safely inferred. No integration test against the real issuer can be written or run from the available artifacts.

Do not expose a public registration endpoint, fabricate upstream routes, or treat this item as complete until the central service contract is verified. Adding a proxy with guessed paths would create an apparently working but unverified security boundary.

## Evidence needed to close C11

Obtain from the central-auth service owner:

1. Repository and immutable release/commit identifier, plus a staging endpoint and test credentials/flow.
2. Versioned contract for registration, optional email verification, login, refresh, logout, current-account status, password recovery/change, and account deactivation/deletion.
3. Documented username/password constraints, anti-enumeration behavior, rate-limit expectations, and exact account states; unverified or disabled accounts must not receive usable WhatPlan access tokens.
4. Proof that user self-registration cannot select a role and that administrative grants are explicit and audited.
5. Proof that account closure and password recovery revoke or rotate active refresh sessions, and a defined policy for the user's WhatPlan content/couple membership on closure.
6. Integration evidence that the actual issuer satisfies `docs/JWT_AUTH_CONTRACT.md`, including status transitions and refresh-token revocation.

Once supplied, implement a thin `CentralAuthClient` adapter with bounded timeouts, route-specific rate limits, sanitized errors, and tests against a controllable staging contract. Keep password verification and reset secrets exclusively in central auth. C11 remains an external release blocker until that verification passes.
