package de.tum.cit.aet.hephaestus.integration.scm.gitlab.sync.status;

import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationRef;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationSyncRunner;
import de.tum.cit.aet.hephaestus.integration.core.sync.SyncJobHandle;
import de.tum.cit.aet.hephaestus.integration.scm.gitlab.sync.GitlabDataSyncScheduler;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

/**
 * Runs initial and reconciliation GitLab jobs through the existing cancellable sync path.
 *
 * <p>Cancellation is cooperative and best-effort: {@link GitLabWorkspaceDataSyncTrigger} threads
 * {@link SyncJobHandle#isCancellationRequested()} down into the per-repository loop in
 * {@code GitLabWorkspaceInitializationService.syncFullData}, so a cancel request stops the pass
 * between repositories rather than mid-repository. Progress is not reported via
 * {@link SyncJobHandle#progress} here — the full-sync body does not compute a stable repo count
 * up front (group project discovery happens as the first phase), so there is no total to report
 * against ahead of time; job rows still get {@code itemsProcessed}/{@code itemsTotal} null, which
 * the UI renders as an indeterminate spinner rather than a determinate bar.
 *
 * <p>Backfill is out of scope for v1: GitLab's commit backfill ({@code GitLabHistoricalBackfillService})
 * runs on its own always-on schedule, independent of the {@code SyncJob} guard, and is not (yet)
 * wired as an explicit, separately-triggerable {@code BACKFILL} job. {@link #supportsBackfill()}
 * stays {@code false} (the interface default) so a manual backfill trigger 409s cleanly instead of
 * silently no-op'ing.
 *
 * <p>Only registered when the GitLab trigger is on the classpath — under the webhook-only runtime
 * role the GitLab stack is gated off, matching {@link GitLabWorkspaceDataSyncTrigger}'s own gating.
 */
@Component
@ConditionalOnBean(GitlabDataSyncScheduler.class)
public class GitlabIntegrationSyncRunner implements IntegrationSyncRunner {

    private final GitlabDataSyncScheduler dataSyncScheduler;

    public GitlabIntegrationSyncRunner(GitlabDataSyncScheduler dataSyncScheduler) {
        this.dataSyncScheduler = dataSyncScheduler;
    }

    @Override
    public IntegrationKind kind() {
        return IntegrationKind.GITLAB;
    }

    @Override
    public void reconcile(IntegrationRef ref, SyncJobHandle handle) {
        dataSyncScheduler.syncWorkspaceNow(ref.workspaceId(), handle);
        if (handle.isCancellationRequested()) {
            handle.reportCancelled();
        }
    }
}
