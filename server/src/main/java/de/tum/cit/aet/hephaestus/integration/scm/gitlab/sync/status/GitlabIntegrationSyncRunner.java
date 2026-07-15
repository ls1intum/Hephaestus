package de.tum.cit.aet.hephaestus.integration.scm.gitlab.sync.status;

import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationRef;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationSyncRunner;
import de.tum.cit.aet.hephaestus.integration.core.spi.SyncExecutionHandle;
import de.tum.cit.aet.hephaestus.integration.scm.gitlab.sync.GitlabDataSyncScheduler;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

/** Runs GitLab reconciliation through the existing cancellable sync path. */
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
    public void reconcile(IntegrationRef ref, SyncExecutionHandle handle) {
        dataSyncScheduler.syncWorkspaceNow(ref.workspaceId(), handle);
        if (handle.isCancellationRequested()) {
            handle.reportCancelled();
        }
    }
}
