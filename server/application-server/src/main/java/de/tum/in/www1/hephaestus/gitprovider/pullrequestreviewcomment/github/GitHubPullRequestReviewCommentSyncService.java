package de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewcomment.github;

import java.io.IOException;

import org.kohsuke.github.GHPullRequestReviewComment;
import org.kohsuke.github.GHUser;
import org.kohsuke.github.GitHub;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import jakarta.transaction.Transactional;

import de.tum.in.www1.hephaestus.gitprovider.common.DateUtil;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestRepository;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.github.GitHubPullRequestConverter;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.PullRequestReviewRepository;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewcomment.PullRequestReviewCommentRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.github.GitHubUserConverter;

@Service
public class GitHubPullRequestReviewCommentSyncService {

    private static final Logger logger = LoggerFactory.getLogger(GitHubPullRequestReviewCommentSyncService.class);

    private final GitHub github;
    private final PullRequestReviewCommentRepository pullRequestReviewCommentRepository;
    private final PullRequestReviewRepository pullRequestReviewRepository;
    private final PullRequestRepository pullRequestRepository;
    private final UserRepository userRepository;
    private final GitHubPullRequestReviewCommentConverter pullRequestReviewCommentConverter;
    private final GitHubPullRequestConverter pullRequestConverter;
    private final GitHubUserConverter userConverter;

    public GitHubPullRequestReviewCommentSyncService(
            GitHub github,
            PullRequestReviewCommentRepository pullRequestReviewCommentRepository,
            PullRequestReviewRepository pullRequestReviewRepository,
            PullRequestRepository pullRequestRepository,
            UserRepository userRepository,
            GitHubPullRequestReviewCommentConverter pullRequestReviewCommentConverter,
            GitHubPullRequestConverter pullRequestConverter,
            GitHubUserConverter userConverter) {
        this.github = github;
        this.pullRequestReviewCommentRepository = pullRequestReviewCommentRepository;
        this.pullRequestReviewRepository = pullRequestReviewRepository;
        this.pullRequestRepository = pullRequestRepository;
        this.userRepository = userRepository;
        this.pullRequestReviewCommentConverter = pullRequestReviewCommentConverter;
        this.pullRequestConverter = pullRequestConverter;
        this.userConverter = userConverter;
    }

    @Transactional
    public void processPullRequestReviewComment(GHPullRequestReviewComment ghPullRequestReviewComment) {
        var result = pullRequestReviewCommentRepository.findById(ghPullRequestReviewComment.getId())
                .map(pullRequestReviewComment -> {
                    try {
                        if (pullRequestReviewComment.getUpdatedAt()
                                .isBefore(
                                        DateUtil.convertToOffsetDateTime(ghPullRequestReviewComment.getUpdatedAt()))) {
                            return pullRequestReviewCommentConverter.update(ghPullRequestReviewComment,
                                    pullRequestReviewComment);
                        }
                        return pullRequestReviewComment;
                    } catch (IOException e) {
                        logger.error("Failed to update pull request review comment {}: {}",
                                ghPullRequestReviewComment.getId(), e.getMessage());
                        return null;
                    }
                }).orElseGet(
                        () -> {
                            var pullRequestReviewComment = pullRequestReviewCommentConverter
                                    .convert(ghPullRequestReviewComment);
                            return pullRequestReviewCommentRepository.save(pullRequestReviewComment);
                        });

        if (result == null) {
            return;
        }

        // Link pull request
        var pullRequest = ghPullRequestReviewComment.getParent();
        var resultPullRequest = pullRequestRepository.findById(pullRequest.getId())
                .orElseGet(() -> pullRequestRepository.save(pullRequestConverter.convert(pullRequest)));
        result.setPullRequest(resultPullRequest);

        // Link review
        var review = pullRequestReviewRepository.findById(ghPullRequestReviewComment.getPullRequestReviewId());
        if (review.isPresent()) {
            result.setReview(review.get());
        } else {
            // If review is not found, we cannot link the review comment and would need to
            // fetch the associated review
            logger.error("Failed to link review for pull request review comment {}: {}",
                    ghPullRequestReviewComment.getId(),
                    "Review not found");
        }

        // Link author
        try {
            GHUser user = ghPullRequestReviewComment.getUser();
            var resultAuthor = userRepository.findById(user.getId())
                    .orElseGet(() -> userRepository.save(userConverter.convert(user)));
            result.setAuthor(resultAuthor);
        } catch (IOException e) {
            logger.error("Failed to link author for pull request review comment {}: {}", ghPullRequestReviewComment.getId(),
                    e.getMessage());
        }
        pullRequestReviewCommentRepository.save(result);
    }
}