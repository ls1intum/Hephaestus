package de.tum.cit.aet.hephaestus.integration.core.sync;

/**
 * What kind of pass a {@link SyncJob} represents.
 *
 * <p>The 60s-tick scheduler slices that most integrations already run do NOT create job rows
 * (they'd be noise) — only these three coarser, admin-visible passes are recorded.
 */
public enum SyncJobType {
    /**
     * First sync after a Connection goes ACTIVE (or a newly-monitored resource is added).
     *
     * <p>Runs the incremental fetch only. It deliberately does NOT sweep for deletions: a mirror
     * that is being populated for the first time has nothing stale in it, and every row it has not
     * fetched yet would look like an upstream deletion to a set difference.
     */
    INITIAL,
    /**
     * Periodic re-sync that repairs webhook drift.
     *
     * <p>Runs everything {@link #INITIAL} does, and — <em>for some integrations</em> — additionally
     * repairs drift that upserts cannot see, by inferring upstream deletion from absence.
     *
     * <p>Deletion repair is needed because every other path is upsert-only, so an entity deleted
     * upstream is caught only by a webhook, and webhooks here are not redeliverable (ADR-0008).
     * A single missed delivery otherwise leaves a phantom row that never expires.
     *
     * <p><strong>What this type actually means depends on the integration.</strong> Every
     * integration below records a job row of this type, but they do not do the same work, so
     * "Reconciliation · Succeeded" in the job history does not by itself imply anything was or
     * could have been removed:
     *
     * <ul>
     *   <li><strong>GitHub</strong> — the only true sweep. {@code GitHubDeletionSweepService}
     *       set-differences the full upstream issue/pull-request number set against the local
     *       mirror and tombstones what upstream no longer has. Fail-closed: it removes nothing for
     *       a repository whose upstream listing it cannot prove complete. This is the only thing
     *       separating this type from {@link #INITIAL} on GitHub. It matters most for pull
     *       requests, for which GitHub emits no {@code deleted} event whatsoever.
     *   <li><strong>Outline</strong> — genuinely tombstones. Every clean enumeration retires the
     *       mirrored documents it did not see ({@code OutlineDocumentSyncService.tombstoneVanished});
     *       a budget-exhausted pass skips tombstoning rather than guess.
     *   <li><strong>GitLab</strong> — <em>no deletion repair at all.</em>
     *       {@code GitlabIntegrationSyncRunner.reconcile} ignores the type, there is no sweep, and
     *       no {@code processDeleted} handler exists on any GitLab path. Both the UI trigger and
     *       the cron nonetheless record this type, so a GitLab job reading
     *       "Reconciliation · Succeeded" means only "re-read upstream" — it cannot remove anything,
     *       and a GitLab issue/MR deleted upstream stays in the mirror indefinitely.
     *   <li><strong>Slack</strong> — no sweep by design. Deletions arrive solely via the
     *       {@code message_deleted} event; history pagination cannot distinguish a deleted message
     *       from a truncated page, a filtered subtype or a thread reply, so inferring deletion from
     *       absence would mass-delete. The cost is that a missed {@code message_deleted} is
     *       unrepairable — this pass will not fix it.
     * </ul>
     */
    RECONCILIATION,
    /** Historical backfill of pre-existing data, bounded by a horizon/checkpoint. */
    BACKFILL,
}
