package de.tum.cit.aet.hephaestus.integration.scm.github.sync.status;

import de.tum.cit.aet.hephaestus.integration.core.framework.SyncSchedulerProperties;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationRef;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationSyncRunner;
import de.tum.cit.aet.hephaestus.integration.core.spi.SyncTargetProvider;
import de.tum.cit.aet.hephaestus.integration.core.spi.SyncTargetProvider.SyncTarget;
import de.tum.cit.aet.hephaestus.integration.core.sync.SyncJobHandle;
import de.tum.cit.aet.hephaestus.integration.scm.github.sync.GithubDataSyncService;
import de.tum.cit.aet.hephaestus.integration.scm.github.sync.backfill.GitHubHistoricalBackfillService;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * GitHub {@link IntegrationSyncRunner}: the manual "Sync now" / "Backfill now" job bodies invoked by
 * {@code SyncJobService.run} (design doc §3.2, §3.4).
 */
@Component
public class GithubIntegrationSyncRunner implements IntegrationSyncRunner {

    private static final Logger log = LoggerFactory.getLogger(GithubIntegrationSyncRunner.class);

    private final GithubDataSyncService dataSyncService;
    private final SyncTargetProvider syncTargetProvider;
    private final GitHubHistoricalBackfillService backfillService;
    private final SyncSchedulerProperties syncSchedulerProperties;

    public GithubIntegrationSyncRunner(
        GithubDataSyncService dataSyncService,
        SyncTargetProvider syncTargetProvider,
        GitHubHistoricalBackfillService backfillService,
        SyncSchedulerProperties syncSchedulerProperties
    ) {
        this.dataSyncService = dataSyncService;
        this.syncTargetProvider = syncTargetProvider;
        this.backfillService = backfillService;
        this.syncSchedulerProperties = syncSchedulerProperties;
    }

    @Override
    public IntegrationKind kind() {
        return IntegrationKind.GITHUB;
    }

    /**
     * The same per-scope full sync the daily cron runs ({@link
     * GithubDataSyncService#syncAllRepositories}), threaded with the job handle so cancellation is
     * observed between repositories — and inside the rate-limit wait, in bounded slices — and coarse
     * repos-done/repos-total progress is reported after each repository.
     */
    @Override
    public void reconcile(IntegrationRef ref, SyncJobHandle handle) {
        dataSyncService.syncAllRepositories(ref.workspaceId(), handle);
    }

    @Override
    public boolean supportsBackfill() {
        return true;
    }

    /**
     * Drives historical backfill batch-by-batch across every monitored repository in the connection
     * until all are complete or cancellation is requested — the same per-repository step ({@link
     * GitHubHistoricalBackfillService#runBackfillBatch}, cooldowns/rate-limit gate included) the 60s
     * scheduler tick performs, but under this job's own progress reporting and cooperative
     * cancellation instead of the scheduler's fire-and-forget cadence.
     */
    @Override
    public void backfill(IntegrationRef ref, SyncJobHandle handle) {
        long workspaceId = ref.workspaceId();
        int batchSize = syncSchedulerProperties.backfill().batchSize();
        int batchesRun = 0;

        while (!handle.isCancellationRequested()) {
            List<SyncTarget> pending = syncTargetProvider
                .getSyncTargetsForScope(workspaceId)
                .stream()
                .filter(target -> !target.isBackfillComplete())
                .toList();
            if (pending.isEmpty()) {
                break;
            }

            boolean anyWork = false;
            for (SyncTarget target : pending) {
                if (handle.isCancellationRequested()) {
                    break;
                }
                boolean didWork = backfillService.runBackfillBatch(target, batchSize);
                anyWork = anyWork || didWork;
                batchesRun++;
                handle.progress(
                    batchesRun,
                    null,
                    Map.<String, Object>of(
                        "currentRepository",
                        target.repositoryNameWithOwner(),
                        "reposPending",
                        pending.size()
                    )
                );
            }

            if (!anyWork) {
                // Every pending repo was skipped this pass (cooldown / rate limit) — avoid a tight
                // spin loop; the scheduled backfill cycle keeps making progress at its own cadence.
                log.info(
                    "Manual backfill paused: reason=noProgressThisPass, workspaceId={}, reposPending={}",
                    workspaceId,
                    pending.size()
                );
                break;
            }
        }
    }
}
