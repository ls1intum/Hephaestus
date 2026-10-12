package de.tum.cit.aet.hephaestus.agent.job;

import de.tum.cit.aet.hephaestus.agent.context.providers.LinkedWorkItemContentSource;
import de.tum.cit.aet.hephaestus.agent.context.providers.ReviewRepositoryPreparer;
import de.tum.cit.aet.hephaestus.integration.core.events.EventContext;
import de.tum.cit.aet.hephaestus.integration.core.signal.DiscoveredVia;
import de.tum.cit.aet.hephaestus.integration.core.signal.SignalRecorder;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequestRepository;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceResolver;

/**
 * Records the occasion a write to a merged pull request's linked material creates: the material key
 * {@link LinkedWorkItemContentSource#currentClosingMaterialKey} reads now, deferred for a live delivery and recorded
 * as a discovery for a sync. The issue and pull-request listeners call it before the write commits, so the occasion
 * commits with the mirror it describes.
 *
 * <p>It only reads the pull request. An issue writer can hold an issue lock, and admission locks the pull request
 * before its issues, so {@link PullRequestPushCoalescer} takes the work lock when it drains the occasion, and capture
 * admission proves the material is still current.
 */
final class LinkedIssueOccasions {

    private final PullRequestRepository pullRequestRepository;
    private final WorkspaceResolver workspaceResolver;
    private final SignalRecorder signalRecorder;
    private final ReviewRepositoryPreparer repositoryPreparer;

    LinkedIssueOccasions(
            PullRequestRepository pullRequestRepository,
            WorkspaceResolver workspaceResolver,
            SignalRecorder signalRecorder,
            ReviewRepositoryPreparer repositoryPreparer) {
        this.pullRequestRepository = pullRequestRepository;
        this.workspaceResolver = workspaceResolver;
        this.signalRecorder = signalRecorder;
        this.repositoryPreparer = repositoryPreparer;
    }

    /** Every merged pull request that closes this issue. */
    void forIssue(long issueId, EventContext context) {
        for (Long pullRequestId : pullRequestRepository.findMergedClosingPullRequestIdsByIssueId(issueId)) {
            forPullRequest(pullRequestId, context);
        }
    }

    void forPullRequest(long pullRequestId, EventContext context) {
        var pullRequest =
                pullRequestRepository.findByIdWithAllForGate(pullRequestId).orElse(null);
        if (pullRequest == null || !pullRequest.isMerged() || pullRequest.getRepository() == null) return;
        var closing = pullRequestRepository.findClosingIssuesById(pullRequestId);
        for (Workspace workspace : workspaceResolver.resolveAllForRepository(
                pullRequest.getRepository().getNameWithOwner())) {
            if (repositoryPreparer.permittedRepositories(workspace.getId()).stream()
                    .noneMatch(repository -> repository
                            .getId()
                            .equals(pullRequest.getRepository().getId()))) continue;
            LinkedWorkItemContentSource.currentClosingMaterialKey(workspace.getId(), pullRequest, closing)
                    .ifPresent(key -> {
                        if (context.isWebhook()) {
                            signalRecorder.defer(key, context.occurredAt(), context.actorUserId());
                        } else {
                            signalRecorder.record(key, context.occurredAt(), DiscoveredVia.SYNC);
                        }
                    });
        }
    }
}
