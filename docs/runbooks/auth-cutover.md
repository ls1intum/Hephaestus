# Runbook: Keycloak → Spring-native auth cutover

Operational guide for shipping and operating the auth replacement (ADR 0017). Read
[`auth-architecture.md`](../auth-architecture.md) first.

## Pre-launch checklist

- [ ] `HEPHAESTUS_AUTH_ISSUER` set to the public origin (`https://hephaestus.aet.cit.tum.de`).
- [ ] `HEPHAESTUS_AUTH_STATE_COOKIE_KEY` set to a base64-encoded 32-byte value (NOT blank —
      blank falls back to an ephemeral per-boot key and abandons in-flight logins on restart).
- [ ] `GITHUB_OAUTH_CLIENT_ID` / `GITHUB_OAUTH_CLIENT_SECRET` registered (GitHub OAuth App,
      callback `https://<host>/login/oauth2/code/github`).
- [ ] `GITLAB_LRZ_OAUTH_CLIENT_ID` / `GITLAB_LRZ_OAUTH_CLIENT_SECRET` registered (gitlab.lrz.de
      application, callback `https://<host>/login/oauth2/code/gitlab-lrz`,
      scopes `openid profile email read_user`).
- [ ] Reverse proxy (Coolify / TUM) verified to NOT inject a `Domain=` on Set-Cookie — a
      `__Host-` cookie with a Domain attribute is silently dropped by browsers → infinite
      redirect loop. Smoke-test the full login on staging behind the real proxy.
- [ ] Stock Postgres 16/17 is sufficient — only the `citext` extension is needed and it ships
      with the official images. `auth_event` partitioning is self-managed in-app by
      `AuthEventPartitionManager` (create-ahead + 12-month retention); `pg_partman` is NOT
      required and must NOT be registered for this table (it would conflict with the in-app
      manager). Liquibase seeds the DEFAULT + prev/current/next month partitions so the first
      insert is safe immediately.
- [ ] `jwt_signing_key` seeded (auto-seeds on first boot via `AuthJwtConfig.seedKeysOnStartup`).
- [ ] TLS 1.3 + HSTS preload confirmed at the edge.

## Rollback

The cutover commit (the one that flips the resource-server decoder from Keycloak to
`RevocationAwareJwtDecoder` and deletes Keycloak) is intentionally a single revertable
commit. For 30 days post-launch:

1. `git revert <cutover-sha>` restores the Keycloak resource-server chain.
2. Re-enable the Keycloak service (kept at `docker-compose.keycloak-backup.yaml`, gitignored
   from the primary compose) and restore `KEYCLOAK_*` env vars.
3. Existing `account` / `identity_link` rows are harmless to leave in place — the reverted
   code simply ignores them.

Data note: no destructive migration runs at cutover. The `user → external_actor` Java rename
keeps the SQL table name `"user"`, and `workspace_membership.user_id → account_id` is the only
column rename — it carries a Liquibase rollback.

## JWK rotation

- Two active keys at a time. To rotate: call `JwtSigningKeyService.rotate()` (exposed via an
  admin endpoint / scheduled task). It inserts a new active key; the previous key keeps
  verifying for the JWT max-TTL window (15 min), then is swept.
- Across pods, publish `auth.signing-key.rotated` on NATS so every pod calls
  `invalidateCache()`. Verify with a blue-green smoke test: rotate, then confirm a token
  signed by the old key still verifies on a pod that has reloaded.
- **Lost key disaster:** if `jwt_signing_key` is lost/corrupted, all sessions are invalid.
  Recovery: clear the table, restart (auto-seeds a fresh key), all users re-login. No data
  loss beyond active sessions.

## Revocation

- "Sign out everywhere" / account-delete sets `issued_jwt.revoked_at` and publishes
  `auth.jwt.revoked`. Pods invalidate their Caffeine entry on receive; worst-case staleness
  is the cache TTL (1 min) if NATS is degraded.
- The `issued_jwt` table is swept of expired rows by a scheduled job; alert if its row count
  grows > 2× baseline (sweep broken).

## Impersonation

- Every `IMPERSONATION_BEGIN` is audited with `(account_id=target, acting_account_id=operator)`
  and a mandatory reason. Alert on every begin during normal hours (not just failures).
- Impersonation sessions are read-only unless the operator sends
  `X-Impersonation-Allow-Writes: true` after a second confirmation.

## Key SLOs to dashboard

- login success rate > 99% (5-min window) → page
- `auth.jwt.decoder.cache.hit_ratio` > 95% → page (revocation table hot)
- revocation propagation p99 < 500ms → page (NATS lagging)
- JWK rotation success = 100%

## Known follow-ups (not blocking launch)

NATS revocation wiring, Bucket4j rate limits, CSP enforcement (report-only first), the
`JwtClaimsLogScrubber` Logback filter, the 48-hour hard-delete sweep job, and full
OpenTelemetry tracing are tracked as named follow-up issues in ADR 0017.
