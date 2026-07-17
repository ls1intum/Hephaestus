# Instance Admin Area

The **instance admin** (a.k.a. super-admin) area lets an operator manage the whole Hephaestus
deployment — distinct from a **workspace admin**, whose powers are scoped to a single workspace.

## Who is an instance admin?

- An account with `Account.appRole == APP_ADMIN` (ADR 0017 native auth).
- The issuer mints the namespaced **`app_admin`** granted authority for such accounts
  (`JwtPrincipalFactory`). This is deliberately distinct from the per-workspace `admin` role, which
  is membership-derived and never appears in the JWT. `SecurityUtils.isSuperAdmin()` reads
  `app_admin`; `WorkspaceContextFilter` elevates an instance admin without a membership to
  workspace-**ADMIN** (never OWNER) for any **active** workspace — the GitLab admin model — and the
  elevation is audit-tagged (see below).
- The authority comes **only** from `appRole` — `JwtPrincipalFactory` strips any reserved authority
  (`app_admin`/`admin`) that might arrive via a grantable `account_feature` row, so an
  `/admin/users`-granted flag can never escalate to instance admin.
- First-admin bootstrap (no DB seed required) is covered separately in the
  `docs/runbooks/auth-cutover.md` runbook.

## The shell

The admin area is a **dedicated sidebar context** (`AppSidebar` `context === "admin"`) — its own
"Back to app" header with the workspace switcher suppressed (the GitLab/Grafana "admin area" pattern),
**not** a reuse of the mentor context. It is reachable from an `app_admin`-gated **"Instance admin"**
entry in the always-present sidebar footer, so a freshly bootstrapped admin with **zero workspaces**
can still reach it. The `/admin` route tree is guarded in `beforeLoad` (`isAppAdmin`), and every
endpoint below is enforced server-side by `@PreAuthorize("hasAuthority('app_admin')")` — the client
is not a security boundary.

## Endpoints

All under `/admin`, all gated by `hasAuthority('app_admin')`:

| Endpoint | Purpose |
| --- | --- |
| `GET /admin/users` (`adminListUsers`) | Paged account list |
| `PATCH /admin/users/{id}` (`adminUpdateUser`) | Change an account's app role (last-admin guard; can't self-demote; **step-up gated**) |
| `DELETE /admin/users/{id}/sessions` (`adminRevokeUserSessions`) | **Force sign-out**: revoke all of an account's active sessions. Because an impersonation token carries the target's account id as its subject, this also ends any in-flight impersonation **of** that account. Audited as `JWT_REVOKED`. |
| `POST /auth/impersonate` (`impersonate`) | Begin impersonating an account (mandatory reason; no self / no admin→admin; read-only by default via `ImpersonationGuard`; **step-up gated**) |
| `POST|PATCH|DELETE /admin/login-providers[/{id}]` | Manage the instance's sign-in providers. **Step-up gated** and audited as `LOGIN_PROVIDER_CHANGED` — a provider's `baseUrl` is the IdP this instance trusts. |
| `GET /admin/workspaces` (`adminListWorkspaces`) | **Metadata-only** overview of every workspace (slug, status, provider, owner login, member count, created-at). Cross-tenant via `@WorkspaceAgnostic`; **no tenant content** — reaching content is the audited impersonation path. |
| `GET /admin/audit` (`adminListAuthEvents`) | Read-only viewer over the append-only `auth_event` log (logins, impersonation, role changes, deletions). Paged, newest-first, filterable by event type; surfaces the `(account_id, acting_account_id)` pair so impersonated actions stay attributable. |

## Step-up re-auth gate (issue #1323)

The highest-risk admin actions — **impersonate-begin**, **app-role change**, and **login-provider
create/update/delete** — additionally require a *recent interactive sign-in*
([GitHub's "sudo mode"](https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/sudo-mode)
pattern). Login-provider mutation is on the list because a provider's `baseUrl` is the IdP this
instance trusts for every identity behind it: repointing it is an admin→admin account-takeover
primitive, and it would otherwise mint a token with a *fresh* `auth_time` — a bypass of the gate
itself.

- Every login (OAuth success or dev login) stamps the OIDC Core **`auth_time`** claim; silent
  refreshes carry it **unchanged** (a rotation is not an authentication event).
- `StepUpPolicy` denies the action unless `auth_time` is within
  `hephaestus.auth.step-up-max-age` (default **5m** — far tighter than GitHub sudo mode's 2h, which
  we can afford because re-auth here is a redirect, not a password prompt; a missing claim counts as
  stale, fail-safe). A non-positive window is a **startup failure** (it would lock every admin out
  with no recovery path); a window at/above `session-max-lifetime` boots with a WARN, because
  `auth_time` can never be older than the session ceiling, making the gate inert.
- The denial is a `403 application/problem+json` with `code = step_up_required` + `maxAgeSeconds`,
  and is **audited** on the action's own event type (`Result = FAILURE`,
  `failure_reason = step_up_required`) so blocked attempts sit next to successful ones in the viewer.
  This is *not* [RFC 9470](https://datatracker.ietf.org/doc/html/rfc9470)'s challenge (a `401` +
  `WWW-Authenticate: Bearer error="insufficient_user_authentication"`): that addresses an OAuth
  client holding a token from a separate authorization server, whereas Hephaestus is a first-party
  cookie BFF and its own issuer, so the challenge is delivered in-band via this API's usual
  ProblemDetail `code` convention.
- The SPA reacts with a **"Confirm access"** dialog (`ConfirmAccessDialog`) offering the providers of
  the same **type** as the admin's primary identity (best-effort — it falls back to all sign-in
  providers rather than render an unusable empty dialog, e.g. when the primary identity is link-only).
  Signing in with a *different* provider does not confirm access: it starts a fresh login for that
  identity, so the retried admin action just 403s again (an annoyance, not an escalation — the wrong
  session lacks `app_admin`). Re-auth is a full-page round-trip, so the in-flight dialog state does not
  survive it: the admin lands back on the same page and re-initiates the action.

This is deliberately **not** a second factor (ADR 0017: GitHub OAuth2 cannot be
`prompt=login`-challenged, so the IdP may redirect silently — and GitLab could be, but the codebase
deliberately forbids the `openid` scope for it, so the gate is uniformly weak *by choice*). Its
value against the threat it targets — a **hijacked admin session** — is real regardless: completing
the re-auth requires the browser's *upstream IdP session*, which a stolen Hephaestus cookie does not
include, so a hijacked session loses these powers `step-up-max-age` after the victim's last login.

Note the composed exposure: passing the gate once buys an impersonation that runs for
`impersonation-max-lifetime` (1h) without re-gating, so the worst-case window for a hijacked admin
session is `step-up-max-age + impersonation-max-lifetime` — 65m by default, not 5m.

## Impersonation time-box

`begin` stamps an absolute ceiling `imp_exp` (`hephaestus.auth.impersonation-max-lifetime`, default
1h) and the issuer caps every token's `exp` at `min(now + accessTtl, imp_exp, session_exp)`, so no
token can outlive it. `refresh` auto-exits shortly *before* the ceiling (`act` dropped,
`IMPERSONATION_END` audited, reason `EXPIRED`) rather than letting the session die in a hard 401 —
see `AuthSessionService.IMPERSONATION_EXIT_SKEW` for why the look-ahead is what makes that branch
reachable at all. It only ever shortens an impersonation. The operator's own `session_exp` and
`auth_time` ride through the sub-session and are restored on (auto-)exit, so impersonating cannot
mint a fresh unlimited operator session. A refresh also auto-exits if the target was promoted to
`APP_ADMIN` mid-session (`begin` refuses admin→admin; a rotation must not smuggle it in). Covered
end-to-end by `ImpersonationLifecycleIntegrationTest`.

## Elevation tagging (issue #1323)

When an instance admin reaches a workspace **via cross-workspace elevation** (no native membership;
`WorkspaceContextFilter`), the access is tagged in the audit trail as a **`WORKSPACE_ELEVATION`**
`auth_event` carrying `(account_id, workspace_id)`. The per-request filter decision is de-duplicated
per `(account, workspace)` in a 15-minute in-memory window (`WorkspaceElevationAuditAdapter`) so the log
marks elevated access *sessions*, not every request. The window is per-pod and claimed only after a
successful write, so a restart, a second pod, or a failed write all produce another row — never a
missing one. The viewer lists it (filterable, with the workspace in the detail sheet) at ordinary
`info` severity — see the `HIGH_RISK_EVENTS` rationale in `auditFormat.ts`.

The trail is therefore a **sampled** record of which admin reached which workspace when — not a
per-request log of what they did. Reaching tenant *content* attributably is the impersonation path.

## Deferred / follow-up

- **Banner countdown** for the impersonation time-box (the server side is binding; a visible
  countdown in `ImpersonationBanner` is a UX nicety).
- **`APP_AUDITOR`** read-only tier: cut as YAGNI for a single-operator instance; the enum + authority
  design makes later reintroduction ~1 day.
- **Instance-provided LLM resources (BYO vs pooled)**: deferred. The needed invariants (PROXY mode,
  no-internet `NetworkPolicy`, server-side key injection, operator-global override) already ship;
  per-workspace budgets should be **bought** via self-hosted LiteLLM virtual keys behind the existing
  proxy, not built as a bespoke entity/precedence/metering subsystem.
