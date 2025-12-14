package de.tum.in.www1.hephaestus.workspace;

import de.tum.in.www1.hephaestus.gitprovider.issuecomment.IssueCommentRepository;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.PullRequestReviewRepository;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;

/**
 * Provides contribution metadata about workspace members by aggregating different activity sources.
 */
@Service
public class WorkspaceContributionActivityService {

    private final PullRequestReviewRepository pullRequestReviewRepository;
    private final IssueCommentRepository issueCommentRepository;

    public WorkspaceContributionActivityService(
        PullRequestReviewRepository pullRequestReviewRepository,
        IssueCommentRepository issueCommentRepository
    ) {
        this.pullRequestReviewRepository = pullRequestReviewRepository;
        this.issueCommentRepository = issueCommentRepository;
    }

    public Optional<Instant> findFirstContributionInstant(Long workspaceId, Long userId) {
        if (workspaceId == null || userId == null) {
            return Optional.empty();
        }

        Instant firstReview = pullRequestReviewRepository.findEarliestSubmissionInstant(workspaceId, userId);
        Instant firstIssueComment = issueCommentRepository.findEarliestCreatedAt(workspaceId, userId);

        Instant firstContribution = Stream.of(firstReview, firstIssueComment)
            .filter(Objects::nonNull)
            .min(Instant::compareTo)
            .orElse(null);

        return Optional.ofNullable(firstContribution);
    }
}
