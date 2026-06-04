# Instance Admin Area

The **instance admin** (a.k.a. super-admin) area lets an operator manage the whole Hephaestus
deployment — distinct from a **workspace admin**, whose powers are scoped to a single workspace.

## Who is an instance admin?

- An account with `Account.appRole == APP_ADMIN` (ADR 0017 native auth).
- The issuer mints the namespaced **`app_admin`** granted authority for such accounts
  (`JwtPrincipalFactory`). This is deliberately distinct from the per-workspace `admin` role, which
  is membership-derived and never appears in the JWT. `SecurityUtils.isSuperAdmin()` reads
  `app_admin` and auto-elevates an instance admin to workspace-admin level **only for workspaces they
  belong to**.
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
| `PATCH /admin/users/{id}` (`adminUpdateUser`) | Change an account's app role (last-admin guard; can't self-demote) |
| `DELETE /admin/users/{id}/sessions` (`adminRevokeUserSessions`) | **Force sign-out**: revoke all of an account's active sessions. Because an impersonation token carries the target's account id as its subject, this also ends any in-flight impersonation **of** that account. Audited as `JWT_REVOKED`. |
| `POST /auth/impersonate` (`impersonate`) | Begin impersonating an account (mandatory reason; no self / no admin→admin; read-only by default via `ImpersonationGuard`) |
| `GET /admin/workspaces` (`adminListWorkspaces`) | **Metadata-only** overview of every workspace (slug, status, provider, owner login, member count, created-at). Cross-tenant via `@WorkspaceAgnostic`; **no tenant content** — reaching content is the audited impersonation path. |
| `GET /admin/audit` (`adminListAuthEvents`) | Read-only viewer over the append-only `auth_event` log (logins, impersonation, role changes, deletions). Paged, newest-first, filterable by event type; surfaces the `(account_id, acting_account_id)` pair so impersonated actions stay attributable. |

## Impersonation time-box

`begin` stamps an absolute ceiling `imp_exp` (`hephaestus.auth.impersonation-max-lifetime`, default
1h); the issuer caps each token's `exp` at `min(now + accessTtl, imp_exp)`, and `refresh` drops the
`act` claim (auto-exit) once it passes. **Note:** there is currently no proactive/silent refresh
caller, so a session already ends at the access-token expiry (`accessTtl`, ~15m) — shorter than the
ceiling. The `imp_exp` machinery is therefore future-proofing that becomes the binding limit only once
silent refresh is wired; until then the de-facto impersonation time-box is `accessTtl`.

## Deferred / follow-up

- **Step-up re-auth gate** for impersonate-begin + role-change. Hephaestus owns no first factor for
  GitHub (plain OAuth2, no `prompt=login`), so a local fresh-re-auth gate is a deliberate-second-step
  / audit control, not a true second factor. Deferred to a focused PR.
- **Elevation tagging** (`elevated_via_instance_admin`): make an instance admin's cross-workspace
  access distinguishable in the audit trail. Needs a new `auth_event` type (a CHECK-constraint
  migration) + a log-volume decision — its own slice.
- **Banner countdown / proactive refresh**: only meaningful once the session-refresh decision is made
  (see above).
- **`APP_AUDITOR`** read-only tier: cut as YAGNI for a single-operator instance; the enum + authority
  design makes later reintroduction ~1 day.
- **Instance-provided LLM resources (BYO vs pooled)**: deferred. The needed invariants (PROXY mode,
  no-internet `NetworkPolicy`, server-side key injection, operator-global override) already ship;
  per-workspace budgets should be **bought** via self-hosted LiteLLM virtual keys behind the existing
  proxy, not built as a bespoke entity/precedence/metering subsystem.
