# ADR 0024: Integration sync lifecycle — drift tombstones and mirror erasure are two different operations

**Status:** Accepted
**Date:** 2026-07-18
**Authors:** Felix T.J. Dietrich
**Builds on:** [ADR 0015](0015-unified-integration-framework.md) (integration framework), [ADR 0008](0008-webhook-runtime-role.md) (webhook runtime role, non-redeliverable deliveries), [ADR 0023](0023-outline-documentation-integration.md) (Outline as a content source)

## Context

Hephaestus mirrors third-party content — GitHub and GitLab issues/pull requests/merge requests and
their reviews and comments, Slack messages, Outline documents — into PostgreSQL, because practice
detection and mentor context read the mirror, not the vendor. Two questions about that mirror had
never been answered the same way twice across the four integrations.

**"Upstream no longer has this row."** Every sync path is upsert-only, so a deleted artifact
survives locally forever. Webhooks cannot close the gap: this deployment's inbound deliveries are
not redeliverable (ADR 0008), GitHub has no `pull_request.deleted` action at all, and GitLab emits
no issue- or merge-request-deletion event whatsoever. A phantom row inflates the counts the admin
UI reports and keeps feeding detection as if it were live.

**"This workspace's basis for holding the mirror is gone."** Slack and Outline hard-deleted their
mirrored rows on both admin disconnect and workspace purge. GitHub erased only on the vendor-side
`installation.deleted` webhook — an admin disconnect erased nothing. GitLab, which has no
vendor-side uninstall signal at all because it authenticates with a user PAT, erased nothing on any
trigger: its mirror was un-erasable except by hand. Workspace purge cleared monitors and local git
clones and left every mirrored row in Postgres. `docs/admin/dsms/record-of-processing.md` meanwhile
promised that repository artefacts are removed "when the last workspace stops monitoring the source
repository" — true only for the App-uninstall webhook and single-repo monitor removal.

The two questions look similar and are not. Answering them with one mechanism is the failure mode
this ADR exists to prevent.

## Decision drivers

- A wrong deletion is invisible; a phantom row is visible and self-correcting. Any inference-driven
  removal must therefore be biased hard toward doing nothing.
- SCM tables carry no `workspace_id`. `repository` and its cascade are instance-global and genuinely
  shared between workspaces that monitor the same source repository
  (`BaseGitServiceEntity` keys on `(provider_id, native_id)`).
- Regulators treat a soft-deleted row that remains queryable as retained personal data. A retention
  trigger that only sets a flag has not erased anything.
- Adding a vendor should stay flat in cost (ADR 0015): one shape per concern, not one per vendor.

## Considered options

1. **One deletion mechanism for both questions** — tombstone everywhere, or hard-delete everywhere.
2. **Two mechanisms, deliberately sharing no code**, each wired to its own triggers.
3. **Status quo** — per-vendor ad-hoc handling.

## Decision

Option 2. The mirror has exactly two deletion semantics, and neither may be implemented in terms of
the other.

### (a) Drift tombstone — recoverable, inferred from absence

A `RECONCILIATION` pass may set a `deleted_at` marker on rows the upstream listing no longer
contains: `GitHubDeletionSweepService`, `GitLabDeletionSweepService`, and
`OutlineDocumentSyncService#tombstoneVanished`. Slack does not sweep (see Consequences).

Deletion is authorized by **provable completeness and nothing else**. `UpstreamListing.complete()`
starts `false` and is set only on the one clean exit: pagination ran to `hasNextPage == false`, no
rate-limit abort, no GraphQL error, no exception, no page-cap truncation, no cancellation, and a
node count that agrees exactly with the server's own `totalCount`. Any doubt — including a doubt
the code did not anticipate — skips the entity class and deletes nothing. A partial listing is
never merged with a previous one. Outline's equivalent: a truncated enumeration throws rather than
returning a short list, and a budget-exhausted pass tombstones nothing.

Tombstoning rather than deleting is what makes acting on inference tolerable: `upsertCore` clears
`deleted_at`, so a wrongly-swept repository heals itself on the next ordinary sync.
`INITIAL` jobs deliberately do not sweep — a mirror still being populated has nothing stale in it,
and every row not yet fetched would look like an upstream deletion to a set difference. For the
same reason a manual `INITIAL` is rejected at the API (`SyncStatusService`); only `RECONCILIATION`
and `BACKFILL` are client-triggerable.

### (b) Mirror erasure — irreversible, triggered by a lawful-basis change

Two triggers, one choke point per integration, identical row set from either:

- Admin disconnect (`PATCH /workspaces/{slug}/connections/{id}/status` → `UNINSTALLED`), whose
  `revoke` callback runs inside the fenced `ConnectionService#disconnect` transaction — stale sync
  leases reaped, running jobs cancelled or refused with a retryable 409, so sync is provably
  stopped before erasure runs.
- Workspace purge (`WorkspaceStatus.PURGED`), via a `WorkspacePurgeContributor` at order `-200`.
  `PURGED` is a soft delete, so `ON DELETE CASCADE` on `workspace_id` never fires and every module
  must delete its own rows.

The choke points are `SlackWorkspaceContentEraser`, the Outline strategy's document/collection
deletes, and — new — `ScmWorkspaceContentEraser` for GitHub and GitLab. All are hard `DELETE`s,
including of any rows a drift sweep had tombstoned.

**Erasure is orphan-guarded because SCM rows are shared.** `ScmWorkspaceContentEraser` removes
*this* workspace's `repository_to_monitor` rows, flushes, and delegates each repository to
`WorkspaceRepositoryMonitorService#deleteRepositoryIfOrphaned`, which drops the shared row only when
no monitor anywhere still points at it. A repository another tenant still monitors survives — the
surviving tenant's basis persists, while the erased tenant's access path (monitor row, NATS consumer
filter, and every workspace-scoped query) is gone. The org tier (`team`, `team_membership`,
`organization_membership`) has its own equivalent guard, since no repository cascade reaches it: it
is erased only when no other non-purged workspace is bound to the same `Organization`. The
`Organization` row itself is global and is unlinked, never deleted. There is no global delete
anywhere in the eraser.

Derived rows go first, while the artifacts they reference still exist: the eraser publishes a
synchronous in-transaction `ScmMirrorErasedEvent`, and `practices` and `activity` listen. They are
not reachable from the repository cascade — `observation.artifact_id` and `feedback.artifact_id` are
soft references with no FK, and the `evidence` jsonb quotes mirrored diff and comment text verbatim.
An event rather than an inbound port because both modules already depend on `workspace`, so a port
would close a Spring Modulith cycle.

### (c) Retained on purpose

`sync_job`, `connection_activity` and `connection_audit` survive both erase triggers for all four
integration kinds. They are operational audit — kind, type, status, timestamps, no mirrored
third-party content — capped per connection by the sync-job pruner, and retaining them uniformly is
what makes a disconnect auditable after the fact. Global identity rows (`user`, `organization`,
`identity_provider`) are never touched: they are cross-tenant shared, and person-level erasure is a
separate operator process.

### (d) Legal framing

Disconnect/purge erasure implements **Art. 5(1)(e) storage limitation**, not Art. 17. Art. 17 is a
*data subject's* right exercised against the controller; disconnect is a *customer admin's* action.
Once the integration — the sole purpose for holding the mirror — is severed, retention has no basis.
An actual Art. 17 request against a person's data across workspaces, including `user` and account
rows, remains the separate operator-executed process the DSMS record describes. The disconnect
control must not be labelled "GDPR erasure" in the UI or in operator docs. Erasing the mirror never
touches the source: content on GitHub, gitlab.lrz.de, Slack or Outline is not modified.

This is also why (a) can never implement (b): a tombstoned row is still queryable retained personal
data. And why (b) can never implement (a): a hard delete destroys data the drift sweep expects to be
able to resurrect on the next upsert.

### (e) Identity healing across rename and transfer

A repository whose name changes upstream must not sever sync. GitHub `repository` lifecycle events
are therefore derived **org-scoped** as `github.<owner>.?.repository` and the handler is registered
on the `organization.` tier — the repo-name token is unstable across rename/transfer, so a
repo-tier subject built from the payload's new name matches neither the monitored-repo filter nor
the org filter and would be silently ACK-dropped, freezing that repository's data. This mirrors
GitLab, which already org-scopes its `project` events. The handler re-keys the mirrored row by the
stable `(native_id, provider_id)` and then re-keys **every** monitor tracking that repository —
repositories are shared, so one event can affect several workspaces — never by name alone.

## Consequences

**Positive.** The four integrations answer the same two questions the same way; adding a vendor
means choosing whether it sweeps, not inventing a deletion model. A GitLab-connected workspace can
erase its mirror at all, for the first time. Purge and disconnect provably reach the same end state,
which is directly testable as equal table counts. The DSMS record can state removal semantics that
the code actually implements.

**Negative / accepted.**

- **Erasure is irreversible and reconnect is a fresh initial sync.** Disconnecting an SCM connection
  destroys mirrored content, derived observations and feedback, and local clones for repositories
  this workspace was the last to monitor. Reconnecting re-fetches from the vendor and cannot restore
  what the vendor no longer has. This is a behaviour change for operators upgrading past this
  release — see `docs/contributor/migration-unified-integration.md`.
- **Cross-owner GitHub transfer does not heal in real time.** A transfer to a *different* owner
  derives the new owner's subject and so reaches no filter of the old workspace. It heals on the
  next reconcile pass, which resolves the monitor by `native_id`. GitLab has the identical residual
  for a transfer across root groups. Repository-scoped events published between the rename and the
  consumer-filter rebuild were never pending for the durable consumer and are not replayed; the next
  reconcile's full pass backfills them.
- **Legacy rows without `native_id`** fall back to a previous-`owner/name` lookup, which a
  simultaneous rename-and-transfer can miss. Those rows acquire a `native_id` on their next sync.
- **Slack does not sweep, by design.** A message absent from a `conversations.history` page usually
  means pagination truncation, a filtered subtype, or a thread reply — inferring deletion from
  absence would mass-delete. Slack-side deletions arrive as `message_deleted` (tombstone) and
  `channel_deleted` (erase our copy) events in every consent state.
- **Operational audit outlives the content it describes.** A disconnected workspace's `sync_job` and
  `connection_activity` history remains queryable. Accepted per (c); revisit if any of those tables
  starts carrying vendor content.
- **Backups.** No off-host backup of the Postgres volume is configured, so no "put beyond use"
  machinery is needed today — only the documented statement. Configuring one makes documenting the
  backup erasure cycle a prerequisite, not a follow-up.

## Revisit trigger

Any of: a vendor ships a reliable deletion webhook plus redelivery, making absence-inference
unnecessary for that integration; an off-host backup is configured, which makes the erasure guarantee
incomplete until a documented backup cycle exists; `sync_job` or `connection_activity` gains a column
carrying mirrored third-party content, which would move it from "retained audit" to "must be erased";
or SCM entities gain a `workspace_id`, which would retire the orphan guard in favour of scoped
deletes.
