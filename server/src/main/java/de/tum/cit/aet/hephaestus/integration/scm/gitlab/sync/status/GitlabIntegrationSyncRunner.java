package de.tum.cit.aet.hephaestus.integration.scm.gitlab.sync.status;

import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationRef;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationSyncRunner;
import de.tum.cit.aet.hephaestus.integration.core.spi.SyncExecutionHandle;
import de.tum.cit.aet.hephaestus.integration.core.sync.SyncJobType;
import de.tum.cit.aet.hephaestus.integration.scm.gitlab.sync.GitlabDataSyncScheduler;
import de.tum.cit.aet.hephaestus.integration.scm.gitlab.sync.backfill.GitLabHistoricalBackfillService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

/**
 * GitLab {@link IntegrationSyncRunner}: the manual "Sync now" / "Backfill now" job bodies invoked by
 * {@code SyncJobService.run}.
 */
@Component
@ConditionalOnBean(GitlabDataSyncScheduler.class)
public class GitlabIntegrationSyncRunner implements IntegrationSyncRunner {

    private static final Logger log = LoggerFactory.getLogger(GitlabIntegrationSyncRunner.class);

    private final GitlabDataSyncScheduler dataSyncScheduler;
    private final GitLabHistoricalBackfillService backfillService;

    public GitlabIntegrationSyncRunner(
        GitlabDataSyncScheduler dataSyncScheduler,
        GitLabHistoricalBackfillService backfillService
    ) {
        this.dataSyncScheduler = dataSyncScheduler;
        this.backfillService = backfillService;
    }

    @Override
    public IntegrationKind kind() {
        return IntegrationKind.GITLAB;
    }

    /**
     * The same warning-aware body the daily scheduler uses, threaded with the job handle.
     *
     * <p>{@code type} is forwarded rather than dropped: it is what decides whether the body ends with a
     * deletion sweep ({@code GitLabDeletionSweepService}). A {@code RECONCILIATION} run set-differences
     * the full upstream issue/merge-request IID set against the local mirror and tombstones what
     * upstream no longer has (fail-closed); an {@code INITIAL} run does not, because a mirror still
     * being populated has nothing stale in it. This is the only difference between the two on GitLab.
     */
    @Override
    public void reconcile(IntegrationRef ref, SyncExecutionHandle handle, SyncJobType type) {
        dataSyncScheduler.syncWorkspaceNow(ref.workspaceId(), handle, type);
        if (handle.isCancellationRequested()) {
            handle.reportCancelled();
        }
    }

    /**
     * Manual backfill is always offered: {@code hephaestus.sync.backfill.enabled} gates the scheduled
     * backfill cycle, not the vendor's ability to backfill on request. {@link
     * GitLabHistoricalBackfillService#runBackfillPass} honors that distinction, so gating the
     * capability here would make an administratively paused cycle also un-resumable by hand — the
     * exact situation manual backfill exists for.
     */
    @Override
    public boolean supportsBackfill() {
        return true;
    }

    /**
     * Runs one backfill batch per pending repository in this workspace, then returns — the same
     * per-repository step (cooldown and initial-sync gates included) the 60s scheduler tick performs,
     * but scoped to one connection and under this job's own progress reporting and cooperative
     * cancellation.
     *
     * <p>Deliberately one pass and no more. {@link GitLabHistoricalBackfillService} puts every
     * repository that did work on a five-minute cooldown and skips cooled-down repositories, so a
     * second pass here could only skip exactly what the first pass advanced. The remaining history is
     * drained by the scheduled cycle at that cadence; one click is one batch per repository.
     */
    @Override
    public void backfill(IntegrationRef ref, SyncExecutionHandle handle) {
        long workspaceId = ref.workspaceId();

        int processed = backfillService.runBackfillPass(workspaceId, handle);

        if (handle.isCancellationRequested()) {
            // The pass returns early on a cancel checkpoint between repositories; declare it so the job
            // finalizes CANCELLED rather than SUCCEEDED.
            handle.reportCancelled();
            return;
        }
        if (processed == 0) {
            // No repository advanced: backfill is complete, or every pending one is gated (cooldown /
            // rate limit). Indistinguishable from here, so surface it as a warning rather than a clean
            // success — matching GithubIntegrationSyncRunner.
            handle.reportWarnings();
            log.info("Manual GitLab backfill advanced no repository: workspaceId={}", workspaceId);
        }
    }
}
