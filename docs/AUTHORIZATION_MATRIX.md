# WhatPlan authorization matrix

This is the application-level policy for the shared-schema couple model. It complements PostgreSQL row-level security; neither layer is considered a replacement for the other.

| Identity | Private couple content | Personal reviews/comments | Global catalogs/settings | Onboarding |
| --- | --- | --- | --- | --- |
| `USER` with active membership | Read and mutate content in the one active couple | Read shared summaries; create/update/delete only their own review or comment | Read catalogs; no global administration | Create/join a couple, manage its invitation, or leave |
| `USER` without active membership | No private tenant context; private lookups must behave as not found | None | Read only where an endpoint is intentionally public to authenticated users | Create or accept an invitation |
| `ADMIN` with active membership | Normal member access to their own couple; an explicit admin workspace may target another validated couple | Own reviews/comments as a member; in an audited admin workspace, manage content for that selected couple | Manage global catalogs, permitted settings, users, couples and audit history | Create/join normally; admin couple/member management is separately audited |
| `ADMIN` without active membership | No private tenant context unless an explicit admin workspace selects a validated couple | None outside a selected admin workspace | Manage global catalogs, users, couples and audit history | Create/join normally; admin couple/member management is separately audited |
| Anonymous/invalid token | None | None | Only explicitly public endpoints | Login/refresh/logout only |

Authorization invariants:

1. A `USER` receives `CoupleContext` only from their active membership. An `ADMIN` may additionally request `X-WhatPlan-Admin-Couple`; the backend accepts it only for an authenticated local `ADMIN`, verifies the couple exists, and installs both `AdminCoupleContext` and `CoupleContext` for that request.
2. Admin couple scope never bypasses PostgreSQL RLS or private repository scoping. Closed couples are read-only; membership changes go through `/api/admin` and are audited. The selected scope is cleared after every request.
3. Private root IDs are loaded using a repository method scoped by the current couple. Nested resources are reached only after their scoped aggregate is loaded, or through a child query that is itself scoped by `couple_id`.
4. A member review/comment mutation checks both the couple-scoped resource lookup and the authenticated author's local user ID. An administrator can manage any author's content only while the validated admin couple scope is active. Failure is indistinguishable from absence (`404`).
5. PostgreSQL RLS is the independent data-layer guard. Missing tenant context must not widen a query or allow a private insert/update.

The JWT filter clears both couple contexts in `finally`. Admin mutation audits store actor, selected couple, normalized route, method, status and time; they never store request bodies, passwords, invitation tokens or query strings. This matrix does not describe cached client data, legal retention, or external support procedures; those are covered by separate roadmap items.
