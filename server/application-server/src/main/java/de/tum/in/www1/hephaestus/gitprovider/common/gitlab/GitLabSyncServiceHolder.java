package de.tum.in.www1.hephaestus.gitprovider.common.gitlab;

import de.tum.in.www1.hephaestus.gitprovider.issue.gitlab.GitLabIssueSyncService;
import de.tum.in.www1.hephaestus.gitprovider.label.gitlab.GitLabLabelSyncService;
import de.tum.in.www1.hephaestus.gitprovider.milestone.gitlab.GitLabMilestoneSyncService;
import de.tum.in.www1.hephaestus.gitprovider.organization.gitlab.GitLabGroupSyncService;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.gitlab.GitLabMergeRequestSyncService;
import jakarta.annotation.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Holds all GitLab sync service providers to reduce constructor parameter count
 * in services that need to orchestrate multiple GitLab sync phases.
 */
@Component
@ConditionalOnProperty(prefix = "hephaestus.gitlab", name = "enabled", havingValue = "true")
public class GitLabSyncServiceHolder {

    private final ObjectProvider<GitLabGroupSyncService> groupSyncServiceProvider;
    private final ObjectProvider<GitLabLabelSyncService> labelSyncServiceProvider;
    private final ObjectProvider<GitLabMilestoneSyncService> milestoneSyncServiceProvider;
    private final ObjectProvider<GitLabIssueSyncService> issueSyncServiceProvider;
    private final ObjectProvider<GitLabMergeRequestSyncService> mergeRequestSyncServiceProvider;

    public GitLabSyncServiceHolder(
        ObjectProvider<GitLabGroupSyncService> groupSyncServiceProvider,
        ObjectProvider<GitLabLabelSyncService> labelSyncServiceProvider,
        ObjectProvider<GitLabMilestoneSyncService> milestoneSyncServiceProvider,
        ObjectProvider<GitLabIssueSyncService> issueSyncServiceProvider,
        ObjectProvider<GitLabMergeRequestSyncService> mergeRequestSyncServiceProvider
    ) {
        this.groupSyncServiceProvider = groupSyncServiceProvider;
        this.labelSyncServiceProvider = labelSyncServiceProvider;
        this.milestoneSyncServiceProvider = milestoneSyncServiceProvider;
        this.issueSyncServiceProvider = issueSyncServiceProvider;
        this.mergeRequestSyncServiceProvider = mergeRequestSyncServiceProvider;
    }

    @Nullable
    public GitLabGroupSyncService getGroupSyncService() {
        return groupSyncServiceProvider.getIfAvailable();
    }

    @Nullable
    public GitLabLabelSyncService getLabelSyncService() {
        return labelSyncServiceProvider.getIfAvailable();
    }

    @Nullable
    public GitLabMilestoneSyncService getMilestoneSyncService() {
        return milestoneSyncServiceProvider.getIfAvailable();
    }

    @Nullable
    public GitLabIssueSyncService getIssueSyncService() {
        return issueSyncServiceProvider.getIfAvailable();
    }

    @Nullable
    public GitLabMergeRequestSyncService getMergeRequestSyncService() {
        return mergeRequestSyncServiceProvider.getIfAvailable();
    }
}
