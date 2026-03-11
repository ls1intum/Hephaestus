package de.tum.in.www1.hephaestus.gitprovider.common.gitlab;

import de.tum.in.www1.hephaestus.gitprovider.commit.gitlab.GitLabCommitSyncService;
import de.tum.in.www1.hephaestus.gitprovider.issue.gitlab.GitLabIssueSyncService;
import de.tum.in.www1.hephaestus.gitprovider.issuedependency.gitlab.GitLabIssueDependencySyncService;
import de.tum.in.www1.hephaestus.gitprovider.issuetype.gitlab.GitLabIssueTypeSyncService;
import de.tum.in.www1.hephaestus.gitprovider.label.gitlab.GitLabLabelSyncService;
import de.tum.in.www1.hephaestus.gitprovider.milestone.gitlab.GitLabMilestoneSyncService;
import de.tum.in.www1.hephaestus.gitprovider.organization.gitlab.GitLabGroupMemberSyncService;
import de.tum.in.www1.hephaestus.gitprovider.organization.gitlab.GitLabGroupSyncService;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.gitlab.GitLabMergeRequestSyncService;
import de.tum.in.www1.hephaestus.gitprovider.repository.collaborator.gitlab.GitLabCollaboratorSyncService;
import de.tum.in.www1.hephaestus.gitprovider.subissue.gitlab.GitLabSubIssueSyncService;
import de.tum.in.www1.hephaestus.gitprovider.team.gitlab.GitLabTeamSyncService;
import jakarta.annotation.Nullable;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Holds all GitLab sync services to reduce constructor parameter count
 * in services that need to orchestrate multiple GitLab sync phases.
 * <p>
 * All services are conditionally available (null when not configured).
 */
@Component
@ConditionalOnProperty(prefix = "hephaestus.gitlab", name = "enabled", havingValue = "true")
public class GitLabSyncServiceHolder {

    private final GitLabGroupSyncService groupSyncService;
    private final GitLabGroupMemberSyncService groupMemberSyncService;
    private final GitLabLabelSyncService labelSyncService;
    private final GitLabMilestoneSyncService milestoneSyncService;
    private final GitLabIssueSyncService issueSyncService;
    private final GitLabMergeRequestSyncService mergeRequestSyncService;
    private final GitLabTeamSyncService teamSyncService;
    private final GitLabCollaboratorSyncService collaboratorSyncService;
    private final GitLabIssueTypeSyncService issueTypeSyncService;
    private final GitLabCommitSyncService commitSyncService;
    private final GitLabSubIssueSyncService subIssueSyncService;
    private final GitLabIssueDependencySyncService issueDependencySyncService;

    public GitLabSyncServiceHolder(
        @Nullable GitLabGroupSyncService groupSyncService,
        @Nullable GitLabGroupMemberSyncService groupMemberSyncService,
        @Nullable GitLabLabelSyncService labelSyncService,
        @Nullable GitLabMilestoneSyncService milestoneSyncService,
        @Nullable GitLabIssueSyncService issueSyncService,
        @Nullable GitLabMergeRequestSyncService mergeRequestSyncService,
        @Nullable GitLabTeamSyncService teamSyncService,
        @Nullable GitLabCollaboratorSyncService collaboratorSyncService,
        @Nullable GitLabIssueTypeSyncService issueTypeSyncService,
        @Nullable GitLabCommitSyncService commitSyncService,
        @Nullable GitLabSubIssueSyncService subIssueSyncService,
        @Nullable GitLabIssueDependencySyncService issueDependencySyncService
    ) {
        this.groupSyncService = groupSyncService;
        this.groupMemberSyncService = groupMemberSyncService;
        this.labelSyncService = labelSyncService;
        this.milestoneSyncService = milestoneSyncService;
        this.issueSyncService = issueSyncService;
        this.mergeRequestSyncService = mergeRequestSyncService;
        this.teamSyncService = teamSyncService;
        this.collaboratorSyncService = collaboratorSyncService;
        this.issueTypeSyncService = issueTypeSyncService;
        this.commitSyncService = commitSyncService;
        this.subIssueSyncService = subIssueSyncService;
        this.issueDependencySyncService = issueDependencySyncService;
    }

    @Nullable
    public GitLabGroupSyncService getGroupSyncService() {
        return groupSyncService;
    }

    @Nullable
    public GitLabGroupMemberSyncService getGroupMemberSyncService() {
        return groupMemberSyncService;
    }

    @Nullable
    public GitLabLabelSyncService getLabelSyncService() {
        return labelSyncService;
    }

    @Nullable
    public GitLabMilestoneSyncService getMilestoneSyncService() {
        return milestoneSyncService;
    }

    @Nullable
    public GitLabIssueSyncService getIssueSyncService() {
        return issueSyncService;
    }

    @Nullable
    public GitLabMergeRequestSyncService getMergeRequestSyncService() {
        return mergeRequestSyncService;
    }

    @Nullable
    public GitLabTeamSyncService getTeamSyncService() {
        return teamSyncService;
    }

    @Nullable
    public GitLabCollaboratorSyncService getCollaboratorSyncService() {
        return collaboratorSyncService;
    }

    @Nullable
    public GitLabIssueTypeSyncService getIssueTypeSyncService() {
        return issueTypeSyncService;
    }

    @Nullable
    public GitLabCommitSyncService getCommitSyncService() {
        return commitSyncService;
    }

    @Nullable
    public GitLabSubIssueSyncService getSubIssueSyncService() {
        return subIssueSyncService;
    }

    @Nullable
    public GitLabIssueDependencySyncService getIssueDependencySyncService() {
        return issueDependencySyncService;
    }
}
