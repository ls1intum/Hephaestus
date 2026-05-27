package de.tum.cit.aet.hephaestus.integration.scm.gitlab.common;

import de.tum.cit.aet.hephaestus.integration.scm.gitlab.commit.GitLabCommitBackfillService;
import de.tum.cit.aet.hephaestus.integration.scm.gitlab.commit.GitLabCommitMergeRequestLinker;
import de.tum.cit.aet.hephaestus.integration.scm.gitlab.commit.GitLabCommitSyncService;
import de.tum.cit.aet.hephaestus.integration.scm.gitlab.issue.GitLabIssueSyncService;
import de.tum.cit.aet.hephaestus.integration.scm.gitlab.issuedependency.GitLabIssueDependencySyncService;
import de.tum.cit.aet.hephaestus.integration.scm.gitlab.issuetype.GitLabIssueTypeSyncService;
import de.tum.cit.aet.hephaestus.integration.scm.gitlab.label.GitLabLabelSyncService;
import de.tum.cit.aet.hephaestus.integration.scm.gitlab.milestone.GitLabMilestoneSyncService;
import de.tum.cit.aet.hephaestus.integration.scm.gitlab.organization.GitLabGroupMemberSyncService;
import de.tum.cit.aet.hephaestus.integration.scm.gitlab.organization.GitLabGroupSyncService;
import de.tum.cit.aet.hephaestus.integration.scm.gitlab.pullrequest.GitLabMergeRequestSyncService;
import de.tum.cit.aet.hephaestus.integration.scm.gitlab.repository.collaborator.GitLabCollaboratorSyncService;
import de.tum.cit.aet.hephaestus.integration.scm.gitlab.subissue.GitLabSubIssueSyncService;
import de.tum.cit.aet.hephaestus.integration.scm.gitlab.team.GitLabTeamSyncService;
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
    private final GitLabCommitBackfillService commitBackfillService;
    private final GitLabCommitMergeRequestLinker commitMergeRequestLinker;
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
        @Nullable GitLabCommitBackfillService commitBackfillService,
        @Nullable GitLabCommitMergeRequestLinker commitMergeRequestLinker,
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
        this.commitBackfillService = commitBackfillService;
        this.commitMergeRequestLinker = commitMergeRequestLinker;
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
    public GitLabCommitBackfillService getCommitBackfillService() {
        return commitBackfillService;
    }

    @Nullable
    public GitLabCommitMergeRequestLinker getCommitMergeRequestLinker() {
        return commitMergeRequestLinker;
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
