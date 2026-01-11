package de.tum.in.www1.hephaestus.workspace;

import de.tum.in.www1.hephaestus.profile.ProfileCommentQueryRepository;
import de.tum.in.www1.hephaestus.profile.ProfilePullRequestQueryRepository;
import de.tum.in.www1.hephaestus.profile.ProfileReviewQueryRepository;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;

/**
 * Provides contribution metadata about workspace members by aggregating different activity sources.
 *
 * <p>Aggregates data from:
 * <ul>
 *   <li>Pull requests (authoring)</li>
 *   <li>Pull request reviews</li>
 *   <li>Issue comments</li>
 * </ul>
 */
@Service
public class WorkspaceContributionActivityService {

    private final ProfilePullRequestQueryRepository profilePullRequestQueryRepository;
    private final ProfileReviewQueryRepository profileReviewQueryRepository;
    private final ProfileCommentQueryRepository profileCommentQueryRepository;

    public WorkspaceContributionActivityService(
        ProfilePullRequestQueryRepository profilePullRequestQueryRepository,
        ProfileReviewQueryRepository profileReviewQueryRepository,
        ProfileCommentQueryRepository profileCommentQueryRepository
    ) {
        this.profilePullRequestQueryRepository = profilePullRequestQueryRepository;
        this.profileReviewQueryRepository = profileReviewQueryRepository;
        this.profileCommentQueryRepository = profileCommentQueryRepository;
    }

    /**
     * Finds the earliest contribution instant for a user in a workspace.
     *
     * <p>Considers all contribution types: PRs authored, reviews submitted, and issue comments.
     *
     * @param workspaceId the workspace to scope to
     * @param userId the user ID
     * @return the earliest contribution instant, or empty if no contributions exist
     */
    public Optional<Instant> findFirstContributionInstant(Long workspaceId, Long userId) {
        if (workspaceId == null || userId == null) {
            return Optional.empty();
        }

        Instant firstPullRequest = profilePullRequestQueryRepository.findEarliestCreatedAt(workspaceId, userId);
        Instant firstReview = profileReviewQueryRepository.findEarliestSubmissionInstant(workspaceId, userId);
        Instant firstIssueComment = profileCommentQueryRepository.findEarliestCreatedAt(workspaceId, userId);

        Instant firstContribution = Stream.of(firstPullRequest, firstReview, firstIssueComment)
            .filter(Objects::nonNull)
            .min(Instant::compareTo)
            .orElse(null);

        return Optional.ofNullable(firstContribution);
    }

    /**
     * Finds the earliest contribution instant for a user (by login) in a workspace.
     *
     * <p>This is a convenience method that first resolves the user by login. If the user
     * doesn't exist, returns empty.
     *
     * @param workspaceId the workspace to scope to
     * @param login the user's login (case-insensitive)
     * @param userId the resolved user ID (null if user not found)
     * @return the earliest contribution instant, or empty if user not found or no contributions exist
     */
    public Optional<Instant> findFirstContributionInstant(Long workspaceId, String login, Long userId) {
        if (userId == null) {
            return Optional.empty();
        }
        return findFirstContributionInstant(workspaceId, userId);
    }
}
