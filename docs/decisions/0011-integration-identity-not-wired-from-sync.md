# ADR 0011: `integration_identity` is OAuth-fed, not sync-fed (today)

**Status:** Accepted (with follow-up commitment)
**Date:** 2026-05-25
**Authors:** Live-run audit (#1198 pass 13)

## Context

The Wave-1198 "three-layer identity model" introduced:

- **Layer 1** `integration.registry.Connection` — workspace × kind × instance_key
- **Layer 2** `integration.identity.HephaestusUser` — one row per real person (Keycloak)
- **Layer 3** `integration.identity.IntegrationIdentity` — one row per `(kind, integration_instance_id, external_id)`

During live verification on 2026-05-25 against `gitlab.lrz.de/hephaestustest` we observed:

```
SELECT count(*) FROM integration_identity;  -- 0
SELECT count(*) FROM hephaestus_user;       -- 0
```

…despite the framework having syncly:

- discovered 69 GitLab projects
- synced 306 issues + 205 PRs
- persisted 252 PR review threads + 251 PR review comments
- successfully run `GitLabGroupMemberSyncService` (`memberCount=2`, `complete=true`)

`IntegrationIdentity` is only populated today from two paths:

1. `OAuthCallbackController` / `OAuthCallbackService` — when a real human completes an OAuth flow and we link their Keycloak subject to the vendor identity.
2. (Future) `GithubInstallationBindingService` — when a workspace admin binds a pre-observed installation.

**Neither sync path** (`GitLabIssueProcessor.findOrCreateUser`, `GitLabMergeRequestProcessor.findOrCreateUser`, `GitLabGroupMemberSyncService.syncGroupMemberships`) calls `JpaUserDirectory.upsertFromVendor` or writes to `IntegrationIdentityRepository`. As a result, **observed contributors (PR authors, MR reviewers, group members) never end up in `integration_identity`** — only the legacy `user` table.

The Wave-1198 audit (pass 13) called this out as a "structural defect."

## Decision drivers

- The Layer-3 table is **load-bearing for the GitHub-App install-bind CVE closure** (Wave 5 — `GithubInstallationBindingService.requireInstallerIdentityMatch` does
  `identityRepository.findByKindAndExternalId(GITHUB, installerGithubUserId)`). If no
  one ever populates `integration_identity` for GitHub users, the bind check has
  nothing to match against and every legitimate bind returns
  `InstallerIdentityNotLinkedException` (412 PRECONDITION_REQUIRED).
- For GitLab, the same surface will matter once we add a "Connect GitLab" OAuth
  flow that needs to bind a vendor identity to a Hephaestus account.
- Auto-populating from sync would write rows with `hephaestus_user_id = null`
  (we don't know who they are until an OAuth-driven link happens). That's
  exactly what `IntegrationIdentity.hephaestus_user_id` was designed for —
  nullable, populated on link.

## Considered options

1. **Wire sync to auto-populate `integration_identity` with `hephaestus_user_id = null`.** Closes the "no rows ever" gap. Every observed vendor user gets a row immediately; OAuth link later fills in the Hephaestus user id.
2. **Lazily populate at link time only.** Smaller surface, but the GitHub App install-bind CVE check fails for any user who hasn't OAuth-linked first, even if the framework has observed them via installation webhooks.
3. **Drop the table.** Hardly viable — the Wave 5 install-bind check depends on it.

## Decision

**Option 1**, with an explicit two-stage rollout:

- **Stage A** (this PR — Wave 1198 pass 13): document the gap (this ADR), add an
  ArchUnit-style observability lever (count of `integration_identity` rows in
  `IntegrationConsumerHealthIndicator` details) so the gap is visible to
  operators on every health probe rather than hidden in a DB query nobody runs.
- **Stage B** (follow-up issue): wire `GitLabGroupMemberSyncService` and
  `BaseGitLabProcessor.findOrCreateUser` (and their GitHub counterparts) to call
  `userDirectory.upsertFromVendor(kind, integration_instance_id, external_id,
  external_login, external_email)` after the legacy `user` upsert. The
  `hephaestus_user_id` stays null until OAuth-link.

Stage B is non-trivial because:
- `integration_instance_id` semantics differ per family (Wave-1198 ADR: SCM →
  `git_provider.id`; messaging/knowledge → `connection.id`).
- The arch test `IntegrationKindBoundaryTest` must allow `JpaUserDirectory`
  imports from the sync paths.
- Upsert needs to be idempotent across re-syncs and across the legacy `user`
  table that already has the same external_id.

## Consequences

**Positive:**
- Stage A makes the empty-table state explicit (operator sees `identityCount: 0`
  in actuator health details, can decide whether that's expected for the
  current deployment).
- Stage B closes the install-bind CVE's "no row to match" failure mode — a
  legitimate bind for someone who has been observed in a webhook but hasn't
  done OAuth still works.

**Negative:**
- Stage A is observability-only. The empty table remains empty until Stage B
  ships. The CVE closure path stays at 412 for any not-yet-OAuth-linked user.
- Stage B writes potentially many rows (one per observed contributor across
  all 69 repos in our test workspace = potentially hundreds), most with
  `hephaestus_user_id = null`. That's correct by design but inflates the table.

## Revisit trigger

- First production deployment that needs to bind a GitHub App installation
  *before* the installer has done OAuth-linking.
- Or: a multi-tenant SaaS deployment where the same vendor user (`external_id`)
  appears across multiple Hephaestus workspaces — at that point Stage B's
  schema invariant is load-bearing for cross-workspace identity resolution.

## References

- Wave-1198 pass 13 PE audit (live run against `gitlab.lrz.de/hephaestustest`,
  2026-05-25), finding §3
- `integration/identity/JpaUserDirectory.java:35` — the `upsertFromVendor`
  entry point that needs to be called from sync
- `integration/github/installation/GithubInstallationBindingService.java` —
  the consumer of `IntegrationIdentityRepository.findByKindAndExternalId`
  that depends on Stage B being shipped
