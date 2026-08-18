package de.tum.cit.aet.hephaestus.agent.handler;

import de.tum.cit.aet.hephaestus.agent.handler.spi.JobDeliveryException;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.core.auth.spi.AccountPreferencesQuery;
import de.tum.cit.aet.hephaestus.core.settings.spi.SilentModeQuery;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.Issue;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.IssueRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequest;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequestRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackSuppressionReason;
import de.tum.cit.aet.hephaestus.practices.review.PracticeReviewProperties;
import de.tum.cit.aet.hephaestus.workspace.RepositoryToMonitorRepository;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import de.tum.cit.aet.hephaestus.workspace.settings.PracticeReviewSettings;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

@Service
class PracticeFeedbackDeliveryPolicy {

    private final IssueRepository issueRepository;
    private final PullRequestRepository pullRequestRepository;
    private final RepositoryToMonitorRepository repositoryToMonitorRepository;
    private final WorkspaceRepository workspaceRepository;
    private final AccountPreferencesQuery accountPreferencesQuery;
    private final PracticeReviewProperties reviewProperties;
    private final SilentModeQuery silentModeQuery;

    PracticeFeedbackDeliveryPolicy(
        IssueRepository issueRepository,
        PullRequestRepository pullRequestRepository,
        RepositoryToMonitorRepository repositoryToMonitorRepository,
        WorkspaceRepository workspaceRepository,
        AccountPreferencesQuery accountPreferencesQuery,
        PracticeReviewProperties reviewProperties,
        SilentModeQuery silentModeQuery
    ) {
        this.issueRepository = issueRepository;
        this.pullRequestRepository = pullRequestRepository;
        this.repositoryToMonitorRepository = repositoryToMonitorRepository;
        this.workspaceRepository = workspaceRepository;
        this.accountPreferencesQuery = accountPreferencesQuery;
        this.reviewProperties = reviewProperties;
        this.silentModeQuery = silentModeQuery;
    }

    @Transactional(readOnly = true)
    Decision<Issue> evaluateIssue(AgentJob job) {
        if (silentModeQuery.isSilentModeEngaged()) {
            return Decision.suppressed(FeedbackSuppressionReason.INSTANCE_SILENCED);
        }
        long workspaceId = requireWorkspaceId(job);
        Workspace workspace = activePracticeWorkspace(workspaceId);
        if (workspace == null) {
            return Decision.denied();
        }
        JsonNode metadata = job.getMetadata();
        Issue issue = integralId(metadata, "issue_id")
            .flatMap(issueRepository::findByIdWithAuthorAndRepository)
            .orElse(null);
        if (!isEligibleTarget(issue, metadata, "issue_number", workspaceId) || issue.getAuthor() == null) {
            return Decision.suppressed(FeedbackSuppressionReason.ARTIFACT_GONE);
        }
        boolean closedWhenQueued = "closed".equalsIgnoreCase(metadata.path("state").asString(""));
        if (closedWhenQueued || issue.getState() == Issue.State.CLOSED) {
            return Decision.suppressed(FeedbackSuppressionReason.ARTIFACT_CLOSED);
        }
        if (!recipientAllowsDelivery(issue)) {
            return Decision.suppressed(FeedbackSuppressionReason.RECIPIENT_OPTED_OUT);
        }
        return Decision.allowed(issue);
    }

    @Transactional(readOnly = true)
    Decision<PullRequest> evaluatePullRequest(AgentJob job) {
        if (silentModeQuery.isSilentModeEngaged()) {
            return Decision.suppressed(FeedbackSuppressionReason.INSTANCE_SILENCED);
        }
        long workspaceId = requireWorkspaceId(job);
        Workspace workspace = activePracticeWorkspace(workspaceId);
        if (workspace == null) {
            return Decision.denied();
        }
        JsonNode metadata = job.getMetadata();
        PullRequest pullRequest = integralId(metadata, "pull_request_id")
            .flatMap(pullRequestRepository::findByIdWithAuthorAndRepository)
            .orElse(null);
        if (!isEligibleTarget(pullRequest, metadata, "pr_number", workspaceId) || pullRequest.getAuthor() == null) {
            return Decision.suppressed(FeedbackSuppressionReason.ARTIFACT_GONE);
        }
        if (pullRequest.getState() == Issue.State.CLOSED) {
            return Decision.suppressed(FeedbackSuppressionReason.ARTIFACT_CLOSED);
        }

        PracticeReviewSettings settings = workspace.getReviewSettings();
        if (
            pullRequest.getState() == Issue.State.MERGED &&
            !settings.resolveDeliverToMerged(reviewProperties.deliverToMerged())
        ) {
            return Decision.suppressed(FeedbackSuppressionReason.ARTIFACT_MERGED);
        }
        if (!recipientAllowsDelivery(pullRequest)) {
            return Decision.suppressed(FeedbackSuppressionReason.RECIPIENT_OPTED_OUT);
        }
        return Decision.allowed(pullRequest);
    }

    static boolean matchesArtifact(Issue artifact, JsonNode metadata, String numberKey) {
        return (
            artifact.getDeletedAt() == null &&
            artifact.getRepository() != null &&
            artifact.getRepository().getId() != null &&
            metadata != null &&
            metadata.path("repository_id").isIntegralNumber() &&
            metadata.path("repository_id").asLong() == artifact.getRepository().getId() &&
            metadata.path("repository_full_name").isString() &&
            metadata.path("repository_full_name").asString().equals(artifact.getRepository().getNameWithOwner()) &&
            metadata.path(numberKey).isIntegralNumber() &&
            metadata.path(numberKey).asInt() == artifact.getNumber()
        );
    }

    private boolean isEligibleTarget(
        @Nullable Issue artifact,
        @Nullable JsonNode metadata,
        String numberKey,
        long workspaceId
    ) {
        return (
            artifact != null &&
            matchesArtifact(artifact, metadata, numberKey) &&
            repositoryToMonitorRepository.existsByWorkspaceIdAndNameWithOwner(
                workspaceId,
                artifact.getRepository().getNameWithOwner()
            )
        );
    }

    private boolean recipientAllowsDelivery(Issue artifact) {
        return accountPreferencesQuery
            .preferencesForUserId(artifact.getAuthor().getId())
            .map(AccountPreferencesQuery.PreferencesView::practiceFeedbackDeliveryEnabled)
            .orElse(true);
    }

    private @Nullable Workspace activePracticeWorkspace(long workspaceId) {
        return workspaceRepository
            .findById(workspaceId)
            .filter(workspace -> workspace.getStatus() == Workspace.WorkspaceStatus.ACTIVE)
            .filter(workspace -> Boolean.TRUE.equals(workspace.getFeatures().getPracticesEnabled()))
            .orElse(null);
    }

    private static java.util.Optional<Long> integralId(@Nullable JsonNode metadata, String key) {
        if (metadata == null || !metadata.path(key).isIntegralNumber()) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(metadata.path(key).asLong());
    }

    private static long requireWorkspaceId(AgentJob job) {
        if (job.getWorkspace() == null || job.getWorkspace().getId() == null) {
            throw new JobDeliveryException("Job has no workspace: jobId=" + job.getId());
        }
        return job.getWorkspace().getId();
    }

    record Decision<T extends Issue>(@Nullable T artifact, @Nullable FeedbackSuppressionReason suppressionReason) {
        static <T extends Issue> Decision<T> allowed(T artifact) {
            return new Decision<>(artifact, null);
        }

        static <T extends Issue> Decision<T> suppressed(FeedbackSuppressionReason reason) {
            return new Decision<>(null, reason);
        }

        static <T extends Issue> Decision<T> denied() {
            return new Decision<>(null, null);
        }

        boolean allowed() {
            return artifact != null;
        }
    }
}
