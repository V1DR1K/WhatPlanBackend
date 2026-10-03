# WhatPlan authorization matrix

This is the application-level policy for the shared-schema couple model. It complements PostgreSQL row-level security; neither layer is considered a replacement for the other.

| Identity | Private couple content | Personal reviews/comments | Global catalogs/settings | Onboarding |
| --- | --- | --- | --- | --- |
| `USER` with active membership | Read and mutate content in the one active couple | Read shared summaries; create/update/delete only their own review or comment | Read catalogs; no global administration | Create/join a couple, manage its invitation, or leave |
| `USER` without active membership | No private tenant context; private lookups must behave as not found | None | Read only where an endpoint is intentionally public to authenticated users | Create or accept an invitation |
| `ADMIN` with active membership | Read and mutate content in their own couple, using the same membership and RLS checks as `USER` | Read shared summaries; create/update/delete only their own review or comment | Manage global catalogs and the explicitly global settings permitted by endpoint policy | No bypass of member checks |
| `ADMIN` without active membership | No private tenant context; administration does not grant support access | None | Manage global catalogs and the explicitly global settings permitted by endpoint policy | Create/join a couple without bypassing member checks |
| Anonymous/invalid token | None | None | Only explicitly public endpoints | Login/refresh/logout only |

Authorization invariants:

1. Couple identity is looked up from the authenticated local user's active membership. Caller-supplied tenant headers are not used.
2. Both roles receive a `CoupleContext` only from their own active membership. `ADMIN` adds global catalog permissions; it neither excludes a member from their own historical content nor grants access to another couple. Any future support access requires a separately designed and audited capability.
3. Private root IDs are loaded using a repository method scoped by the current couple. Nested resources are reached only after their scoped aggregate is loaded, or through a child query that is itself scoped by `couple_id`.
4. A review/comment mutation checks both the couple-scoped resource lookup and the authenticated author's local user ID. Failure is indistinguishable from absence (`404`).
5. PostgreSQL RLS is the independent data-layer guard. Missing tenant context must not widen a query or allow a private insert/update.

The JWT filter clears `CoupleContext` in `finally`. This matrix does not describe cached client data, legal retention, or external support procedures; those are covered by separate roadmap items.
