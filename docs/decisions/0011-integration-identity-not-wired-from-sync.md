# ADR 0011: `integration_identity` is OAuth-fed, not sync-fed (today)

**Status:** Superseded by [ADR 0016](0016-unified-identity-keycloak-as-truth.md) (2026-05-27)
**Date:** 2026-05-25
**Authors:** Live-run audit (#1198 pass 13)

> **2026-05-27 supersede — three-layer identity model deleted.** The principal-engineer cleanup pass on #1198 (PE-DB + PE-IDM) confirmed the audit conclusion this ADR carries forward (`hephaestus_user`/`integration_identity` empty in every environment, zero callers, Stage B never landed) and acted on it: the entire `integration.identity` package — `HephaestusUser`, `IntegrationIdentity`, `JpaUserDirectory`, the two repositories, the `UserDirectory` SPI — was deleted, and the Liquibase create steps were removed from the unmerged `1779790459343_unified_integration_framework.xml` changelog. The auth model we actually ship is documented in [ADR 0016](0016-unified-identity-keycloak-as-truth.md): SCM `User` is the authoritative person row, Keycloak `sub` is persisted on `User.keycloak_subject` as the stable join key. This ADR remains in the tree as the historical record of the "we tried, we deferred, then we deleted" arc.

> **2026-05-26 amendment — AC#6 dropped from #1198.** The pre-workspace bind
> surface (`github_installation_unbound` table + `GithubInstallationBindingService`
> + `GithubInstallationController` `/bind` endpoint + `GithubInstallationCleanupJob`)
> was deleted from #1198 after PE-2 verified the table is provably empty in
> production: no code path ever writes to it and the bind controller always returns
> 404. The canonical install journey today is the **inline-create-from-installation**
> path in `GithubLifecycleListener.createOrUpdateFromInstallation()`, which creates
> the workspace + `Connection` row directly from the `installation.created` webhook.
> Stage B below (wiring sync to upsert into `integration_identity`) remains valid as
> the OAuth-link follow-up — it is no longer load-bearing for any install-bind CVE
> closure because the bind surface itself is gone.

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
2. ~~(Future) `GithubInstallationBindingService` — when a workspace admin binds a pre-observed installation.~~ **Deprecated when AC#6 was dropped from #1198 (2026-05-26):** the pre-workspace bind surface is gone; identity rows for installers now arrive (if at all) via path 1 only.

**Neither sync path** (`GitLabIssueProcessor.findOrCreateUser`, `GitLabMergeRequestProcessor.findOrCreateUser`, `GitLabGroupMemberSyncService.syncGroupMemberships`) calls `JpaUserDirectory.upsertFromVendor` or writes to `IntegrationIdentityRepository`. As a result, **observed contributors (PR authors, MR reviewers, group members) never end up in `integration_identity`** — only the legacy `user` table.

The Wave-1198 audit (pass 13) called this out as a "structural defect."

## Decision drivers

- ~~The Layer-3 table is **load-bearing for the GitHub-App install-bind CVE closure** (Wave 5 — `GithubInstallationBindingService.requireInstallerIdentityMatch` does
  `identityRepository.findByKindAndExternalId(GITHUB, installerGithubUserId)`). If no
  one ever populates `integration_identity` for GitHub users, the bind check has
  nothing to match against and every legitimate bind returns
  `InstallerIdentityNotLinkedException` (412 PRECONDITION_REQUIRED).~~
  **Obsolete (2026-05-26 — AC#6 dropped):** the bind surface was deleted, so the
  install-bind CVE closure no longer applies. The inline-create path in
  `GithubLifecycleListener` is authenticated implicitly by webhook signature +
  workspace-creation flow, not by a Layer-3 identity match.
- For GitLab, the same surface will matter once we add a "Connect GitLab" OAuth
  flow that needs to bind a vendor identity to a Hephaestus account.
- Auto-populating from sync would write rows with `hephaestus_user_id = null`
  (we don't know who they are until an OAuth-driven link happens). That's
  exactly what `IntegrationIdentity.hephaestus_user_id` was designed for —
  nullable, populated on link.

## Considered options

1. **Wire sync to auto-populate `integration_identity` with `hephaestus_user_id = null`.** Closes the "no rows ever" gap. Every observed vendor user gets a row immediately; OAuth link later fills in the Hephaestus user id.
2. **Lazily populate at link time only.** Smaller surface. (Historical: under the Wave-5 design this option failed the GitHub App install-bind CVE check for any user who hadn't OAuth-linked first. That tradeoff was deprecated when AC#6 was dropped — the bind check no longer exists.)
3. **Drop the `integration_identity` table.** ~~Hardly viable — the Wave 5 install-bind check depends on it.~~ Reconsidered post-AC#6-drop (2026-05-26): the table is still useful for the OAuth-link path (option 1) and for cross-workspace identity resolution; we keep it.

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

- ~~First production deployment that needs to bind a GitHub App installation
  *before* the installer has done OAuth-linking.~~ **Obsolete (AC#6 dropped):**
  the pre-workspace bind surface no longer exists; installations always go
  through the inline-create-from-installation path.
- A multi-tenant SaaS deployment where the same vendor user (`external_id`)
  appears across multiple Hephaestus workspaces — at that point Stage B's
  schema invariant is load-bearing for cross-workspace identity resolution.

## References

- Wave-1198 pass 13 PE audit (live run against `gitlab.lrz.de/hephaestustest`,
  2026-05-25), finding §3
- `integration/identity/JpaUserDirectory.java:35` — the `upsertFromVendor`
  entry point that needs to be called from sync
- ~~`integration/github/installation/GithubInstallationBindingService.java` —
  the consumer of `IntegrationIdentityRepository.findByKindAndExternalId`
  that depends on Stage B being shipped~~ **Deleted 2026-05-26 (AC#6 drop):**
  the only remaining consumer of `findByKindAndExternalId` is now
  `OAuthCallbackService`; Stage B is no longer load-bearing for any CVE
  closure, only for the OAuth-link UX.
- `integration/github/lifecycle/GithubLifecycleListener.java` —
  `createOrUpdateFromInstallation()` is the canonical install journey that
  superseded the dropped pre-workspace bind surface.
