package de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewcomment.github;

import de.tum.in.www1.hephaestus.gitprovider.common.DateUtil;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestRepository;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.github.GitHubPullRequestConverter;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.PullRequestReviewRepository;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewcomment.PullRequestReviewComment;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewcomment.PullRequestReviewCommentRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.github.GitHubUserConverter;
import jakarta.transaction.Transactional;
import java.io.IOException;
import java.util.List;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHPullRequestReviewComment;
import org.kohsuke.github.GHUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class GitHubPullRequestReviewCommentSyncService {

    private static final Logger logger = LoggerFactory.getLogger(GitHubPullRequestReviewCommentSyncService.class);

    private final PullRequestReviewCommentRepository pullRequestReviewCommentRepository;
    private final PullRequestReviewRepository pullRequestReviewRepository;
    private final PullRequestRepository pullRequestRepository;
    private final UserRepository userRepository;
    private final GitHubPullRequestReviewCommentConverter pullRequestReviewCommentConverter;
    private final GitHubPullRequestConverter pullRequestConverter;
    private final GitHubUserConverter userConverter;

    public GitHubPullRequestReviewCommentSyncService(
        PullRequestReviewCommentRepository pullRequestReviewCommentRepository,
        PullRequestReviewRepository pullRequestReviewRepository,
        PullRequestRepository pullRequestRepository,
        UserRepository userRepository,
        GitHubPullRequestReviewCommentConverter pullRequestReviewCommentConverter,
        GitHubPullRequestConverter pullRequestConverter,
        GitHubUserConverter userConverter
    ) {
        this.pullRequestReviewCommentRepository = pullRequestReviewCommentRepository;
        this.pullRequestReviewRepository = pullRequestReviewRepository;
        this.pullRequestRepository = pullRequestRepository;
        this.userRepository = userRepository;
        this.pullRequestReviewCommentConverter = pullRequestReviewCommentConverter;
        this.pullRequestConverter = pullRequestConverter;
        this.userConverter = userConverter;
    }

    /**
     * Synchronizes all review comments for the given list of GitHub pull requests.
     *
     * @param pullRequests the list of GitHub pull requests to sync review comments
     *                     for
     */
    public void syncReviewCommentsOfAllPullRequests(List<GHPullRequest> pullRequests) {
        pullRequests.stream().forEach(this::syncReviewCommentsOfPullRequest);
    }

    /**
     * Synchronizes all review comments for a specific GitHub pull request.
     *
     * @param pullRequest the GitHub pull request to sync review comments for
     */
    public void syncReviewCommentsOfPullRequest(GHPullRequest pullRequest) {
        try {
            pullRequest.listReviewComments().withPageSize(100).forEach(this::processPullRequestReviewComment);
        } catch (IOException e) {
            logger.error(
                "Failed to fetch review comments for pull request {}: {}",
                pullRequest.getId(),
                e.getMessage()
            );
        }
    }

    /**
     * Processes a single GitHub pull request review comment by updating or creating
     * it in the local repository.
     * Links the comment to its parent pull request and review, as well as the
     * author.
     *
     * @param ghPullRequestReviewComment the GitHub pull request review comment to
     *                                   process
     * @return the updated or newly created PullRequestReviewComment entity, or
     *         {@code null} if an error occurred
     */
    @Transactional
    public PullRequestReviewComment processPullRequestReviewComment(
        GHPullRequestReviewComment ghPullRequestReviewComment
    ) {
        var result = pullRequestReviewCommentRepository
            .findById(ghPullRequestReviewComment.getId())
            .map(pullRequestReviewComment -> {
                try {
                    if (
                        pullRequestReviewComment.getUpdatedAt() == null ||
                        pullRequestReviewComment
                            .getUpdatedAt()
                            .isBefore(DateUtil.convertToOffsetDateTime(ghPullRequestReviewComment.getUpdatedAt()))
                    ) {
                        return pullRequestReviewCommentConverter.update(
                            ghPullRequestReviewComment,
                            pullRequestReviewComment
                        );
                    }
                    return pullRequestReviewComment;
                } catch (IOException e) {
                    logger.error(
                        "Failed to update pull request review comment {}: {}",
                        ghPullRequestReviewComment.getId(),
                        e.getMessage()
                    );
                    return null;
                }
            })
            .orElseGet(() -> pullRequestReviewCommentConverter.convert(ghPullRequestReviewComment));

        if (result == null) {
            return null;
        }

        // Link pull request
        var pullRequest = ghPullRequestReviewComment.getParent();
        var resultPullRequest = pullRequestRepository
            .findById(pullRequest.getId())
            .orElseGet(() -> pullRequestRepository.save(pullRequestConverter.convert(pullRequest)));
        result.setPullRequest(resultPullRequest);

        // Link review
        var review = pullRequestReviewRepository.findById(ghPullRequestReviewComment.getPullRequestReviewId());
        if (review.isPresent()) {
            result.setReview(review.get());
        } else {
            // If review is not found, we cannot link the review comment and would need to
            // fetch the associated review
            logger.error(
                "Failed to link review for pull request review comment {}: {}",
                ghPullRequestReviewComment.getId(),
                "Review not found"
            );
        }

        // Link author
        try {
            GHUser user = ghPullRequestReviewComment.getUser();
            var resultAuthor = userRepository
                .findById(user.getId())
                .orElseGet(() -> userRepository.save(userConverter.convert(user)));
            result.setAuthor(resultAuthor);
        } catch (IOException e) {
            logger.error(
                "Failed to link author for pull request review comment {}: {}",
                ghPullRequestReviewComment.getId(),
                e.getMessage()
            );
        }

        return pullRequestReviewCommentRepository.save(result);
    }
}
