package de.tum.cit.aet.hephaestus.integration.scm.gitlab.sync.status;

import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationRef;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationSyncRunner;
import de.tum.cit.aet.hephaestus.integration.core.sync.SyncJobHandle;
import de.tum.cit.aet.hephaestus.integration.scm.gitlab.workspace.GitLabWorkspaceDataSyncTrigger;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

/**
 * GitLab's {@link IntegrationSyncRunner}: the {@code SyncJob} execution body for both
 * {@code INITIAL} and {@code RECONCILIATION} jobs.
 *
 * <p>{@link #reconcile} reuses {@link GitLabWorkspaceDataSyncTrigger}'s cancellable overload
 * verbatim — the same {@code initialize()} + {@code syncFullData()} sequence the workspace
 * lifecycle path already runs — rather than duplicating it (design doc §3.4: "already implements
 * the SPI — reuse as sync body, no refactor needed").
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
@ConditionalOnBean(GitLabWorkspaceDataSyncTrigger.class)
public class GitlabIntegrationSyncRunner implements IntegrationSyncRunner {

    private final GitLabWorkspaceDataSyncTrigger trigger;

    public GitlabIntegrationSyncRunner(GitLabWorkspaceDataSyncTrigger trigger) {
        this.trigger = trigger;
    }

    @Override
    public IntegrationKind kind() {
        return IntegrationKind.GITLAB;
    }

    @Override
    public void reconcile(IntegrationRef ref, SyncJobHandle handle) {
        trigger.syncAllRepositories(ref.workspaceId(), handle::isCancellationRequested);
    }
}
