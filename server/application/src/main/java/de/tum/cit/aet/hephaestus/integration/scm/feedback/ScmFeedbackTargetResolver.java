package de.tum.cit.aet.hephaestus.integration.scm.feedback;

import de.tum.cit.aet.hephaestus.core.privacy.spi.PersonSourceNamespace;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.core.security.ScmOrigin;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionService;
import de.tum.cit.aet.hephaestus.integration.core.connection.SourceProviderNamespaces;
import de.tum.cit.aet.hephaestus.integration.core.spi.FeedbackNotSentException;
import de.tum.cit.aet.hephaestus.integration.core.spi.FeedbackTarget;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemCaptureException;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.Issue;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.IssueRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.item.ScmItemAccess;
import de.tum.cit.aet.hephaestus.integration.scm.domain.item.ScmItemEvents;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequest;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequestRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequestreview.PullRequestReviewRepository;
import de.tum.cit.aet.hephaestus.workspace.RepositoryToMonitorRepository;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnServerRole
@RequiredArgsConstructor
public class ScmFeedbackTargetResolver {
    private final ScmItemAccess items;
    private final IssueRepository issues;
    private final PullRequestRepository pullRequests;
    private final PullRequestReviewRepository reviews;
    private final RepositoryToMonitorRepository monitors;
    private final ConnectionService connections;

    @Transactional(readOnly = true)
    public String serverUrl(FeedbackTarget target, IntegrationKind provider) {
        subject(target, provider);
        var connection = connections
                .findActive(target.item().workspaceId(), provider)
                .orElseThrow(() -> new FeedbackNotSentException("SCM connection is unavailable"));
        return PersonSourceNamespace.from(
                        provider.name(), SourceProviderNamespaces.configuredUrl(connection.getConfig()))
                .orElseThrow(() -> new FeedbackNotSentException("SCM origin is unavailable"))
                .serverUrl();
    }

    @Transactional(readOnly = true)
    public String subject(FeedbackTarget target, IntegrationKind provider) {
        try {
            items.require(target.item());
        } catch (ItemCaptureException e) {
            throw new FeedbackNotSentException("Feedback item is unavailable", e);
        }
        if (target.integration().kind() != provider
                || target.integration().workspaceId() != target.item().workspaceId())
            throw new FeedbackNotSentException("Feedback integration does not match the workspace");
        long sourceId = Objects.requireNonNull(target.item().sourceId(), "SCM work has a source id");
        Issue work;
        if (target.item().kind().equals(ScmItemEvents.ISSUE))
            work = issues.findByIdWithAuthorAndRepository(sourceId)
                    .orElseThrow(() -> new FeedbackNotSentException("Issue is unavailable"));
        else if (target.item().kind().equals(ScmItemEvents.PULL_REQUEST))
            work = pullRequests
                    .findByIdWithAuthorAndRepository(sourceId)
                    .orElseThrow(() -> new FeedbackNotSentException("Pull request is unavailable"));
        else if (target.item().kind().equals(ScmItemEvents.PULL_REQUEST_REVIEW)) {
            var review =
                    reviews.findById(sourceId).orElseThrow(() -> new FeedbackNotSentException("Review is unavailable"));
            work = Objects.requireNonNull(review.getPullRequest(), "Review has no pull request");
            var parent = items.parent(target.item(), ScmItemEvents.PULL_REQUEST);
            if (!Objects.equals(parent.sourceId(), work.getId()))
                throw new FeedbackNotSentException("Review parent does not match the item");
        } else throw new FeedbackNotSentException("Item does not support SCM feedback");
        if (target.item().kind().equals(ScmItemEvents.ISSUE) && work instanceof PullRequest)
            throw new FeedbackNotSentException("Pull request cannot answer as an issue");
        var repository = work.getRepository();
        if (repository == null
                || work.getDeletedAt() != null
                || repository.getProvider().kind() != provider
                || !monitors.existsByWorkspaceIdAndNameWithOwner(
                        target.item().workspaceId(), repository.getNameWithOwner()))
            throw new FeedbackNotSentException("Workspace cannot access feedback target");
        var connection = connections
                .findActive(target.item().workspaceId(), provider)
                .orElseThrow(() -> new FeedbackNotSentException("SCM connection is unavailable"));
        if ((target.integration().connectionId() != null
                        && !target.integration().connectionId().equals(connection.getId()))
                || (target.integration().instanceKey() != null
                        && !target.integration().instanceKey().equals(connection.getInstanceKey())))
            throw new FeedbackNotSentException("SCM connection changed");
        var namespace = PersonSourceNamespace.from(
                provider.name(), SourceProviderNamespaces.configuredUrl(connection.getConfig()));
        if (namespace.isEmpty()
                || !ScmOrigin.of(namespace.get().serverUrl())
                        .equals(ScmOrigin.of(repository.getProvider().getServerUrl())))
            throw new FeedbackNotSentException("Feedback target belongs to another provider instance");
        String path = repository.getNameWithOwner();
        if (provider == IntegrationKind.GITHUB)
            return path + (work instanceof PullRequest ? "#" : "/issues/") + work.getNumber();
        return path + (work instanceof PullRequest ? "!" : "#") + work.getNumber();
    }
}
