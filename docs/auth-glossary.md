# Auth glossary

Single source of truth for the ubiquitous language of the `core.auth` module. Mirrored in
`server/src/main/java/de/tum/cit/aet/hephaestus/core/auth/package-info.java` and enforced by
the `AccountReferenceBoundaryTest` and `ExternalActorIsolationTest` ArchUnit tests.

| Term | Meaning | Lives in | Identified by |
|---|---|---|---|
| **`Account`** | The Hephaestus-native principal. One row per human (post-MVP also per service principal) that has authenticated. Carries `app_role`, `type` (`HUMAN`/`SERVICE`), `status`, contact email, preferences. | `core.auth.domain.Account` | surrogate `id` (BIGINT) |
| **`IdentityLink`** | Federated-login association between an `Account` and an IdP subject. Per Issue #1200's spec; includes `team_id` for Slack-like multi-tenant IdPs. **The only thing login lookup ever queries** — never `email`. | `core.auth.domain.IdentityLink` | surrogate `id`; unique on `(git_provider_id, subject, COALESCE(team_id, ''))` and partial-unique on `(account_id, git_provider_id, team_id) WHERE disabled_at IS NULL` |
| **`ExternalActor`** | Read-only mirror of a git-provider account (USER / ORGANIZATION / BOT). Authors activity (PRs, issues, reviews). **Not a principal** — does not log in, does not have preferences, does not impersonate. Renamed from the prior `gitprovider.user.User` which was a denormalized cache. | `gitprovider.actor.ExternalActor` (renamed; SQL table still `"user"` until next planned squash) | `(git_provider_id, native_id)` |
| **`Account` vs `ExternalActor`** | Two distinct entities. `WorkspaceMembership`, `AccountFeature`, `IdentityLink`, `oauth_authorized_client`, `issued_jwt` reference `Account`. `Issue.author_id`, `PullRequest.merged_by`, review comments, team memberships reference `ExternalActor`. A single login flow may create both: one `Account` (the principal that will hold the cookie) plus one `IdentityLink` whose `external_actor_id` points to the matching `ExternalActor` row already synced from the IdP. | — | — |
| **`WorkspaceMembership`** | `Account` ↔ `Workspace` ↔ `WorkspaceRole`. Column `user_id` is renamed to `account_id` as part of this PR. | `workspace.WorkspaceMembership` | composite `(workspace_id, account_id)` |
| **`AccountFeature`** | Per-account feature opt-in. Replaces the Keycloak `mentor_access` realm role (and similar). Composite PK `(account_id, flag)`. | `core.auth.domain.AccountFeature` | composite `(account_id, flag)` |
| **`AuthEvent`** | Append-only auth / impersonation event. Monthly RANGE-partitioned on `occurred_at`, managed by `pg_partman` (create-ahead + 12-month retention; maintenance via `AuthEventPartitionMaintenance`). Records `(account_id, acting_account_id)` for every impersonation (`act`-claim) action. INSERT-only at the SQL-grant level in non-test environments. | `core.auth.audit.AuthEvent` | composite `(id, occurred_at)` (Postgres partitioning requires it) |
| **`IssuedJwt`** | Revocation list for Hephaestus-issued cookie JWTs. Inserted at issuance; consulted by `RevocationAwareJwtDecoder` on every request via an indexed `jti` lookup; a *negative* cache holds REVOKED verdicts only (TTL sheds replay load, never a false positive). Effective on every pod within DB lag — no cross-pod protocol. | `core.auth.jwt.IssuedJwt` | `jti` (UUID) |
| **`JwtSigningKey`** | Hephaestus's own ES256 JWT-signing key set. Two active keys at a time. Private keys are sealed under the **system** master key (AAD = `system:jwt_signing_key.private_key_pem`) — distinct from the tenant-bound AAD domain used by `CredentialBundleConverter` for per-workspace integration secrets (confused-deputy defense). | `core.auth.jwt.JwtSigningKey` | `kid` |
| **`LoginProvider`** | An instance-scoped OAuth login provider (a sign-in option: GitHub, GitLab.com, self-hosted GitLab). **One per SCM instance** — `UNIQUE(type, base_url)`. Env-seeds the defaults on first boot; an instance admin manages the rest at runtime (`/admin/login-providers`). The client secret is sealed at rest by `EncryptedStringConverter` (AES-256-GCM) and never returned. Authentication only — distinct from a workspace's SCM data-source `Connection`. (Replaced the former per-workspace `Connection` of `kind=OIDC_LOGIN_*`; see ADR 0017's Stage B-2 update.) | `core.auth.provider.LoginProvider` | `registration_id`; `UNIQUE(type, base_url)` |

## Forbidden patterns (ArchUnit-enforced)

- `org.keycloak.*` imports — anywhere.
- `com.auth0.jwt.*` imports — anywhere (post-eviction).
- `Jwt.getTokenValue()` / `.getToken()` — only inside `core.auth.jwt.*`.
- `UserRepository.findByEmail(...)` callers — never reachable from a `@RestController`. The repository method is renamed `findContactByEmail` to make accidental auth-lookup grepable.
- New `@RestController` without `@Tag`; new public mapping without `@Operation(summary, operationId)`.

## JWT claim shape

Strict subset of standard OIDC ID-Token claims:

| Claim | Type | Notes |
|---|---|---|
| `iss` | string | `https://hephaestus.aet.cit.tum.de` (env-configurable). |
| `sub` | string | `Account.id` as decimal. |
| `aud` | string | `hephaestus-spa` for the SPA cookie. Future audiences allowed. |
| `jti` | UUID | Inserted into `issued_jwt`. |
| `iat`, `exp` | Unix seconds | 15-minute TTL. |
| `scope` | space-delimited | Encodes `app_role` (`USER`/`APP_ADMIN`) + active feature flag keys. |
| `act` | object | Only present when impersonating. Per RFC 8693 — `{ "sub": "<impersonator_account_id>" }`. |

No proprietary claim names. This preserves the option to mount Spring Authorization Server as
a second issuer later (for Issue #1200 third-party clients) without changing the
resource-server.
