package de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewcomment.github;

import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestRepository;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.github.GitHubPullRequestConverter;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.PullRequestReviewRepository;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewcomment.PullRequestReviewComment;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewcomment.PullRequestReviewCommentRepository;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewthread.PullRequestReviewThread;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewthread.PullRequestReviewThreadRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.github.GitHubUserConverter;
import jakarta.transaction.Transactional;
import java.io.IOException;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
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
    private final PullRequestReviewThreadRepository pullRequestReviewThreadRepository;

    public GitHubPullRequestReviewCommentSyncService(
        PullRequestReviewCommentRepository pullRequestReviewCommentRepository,
        PullRequestReviewRepository pullRequestReviewRepository,
        PullRequestRepository pullRequestRepository,
        UserRepository userRepository,
        GitHubPullRequestReviewCommentConverter pullRequestReviewCommentConverter,
        GitHubPullRequestConverter pullRequestConverter,
        GitHubUserConverter userConverter,
        PullRequestReviewThreadRepository pullRequestReviewThreadRepository
    ) {
        this.pullRequestReviewCommentRepository = pullRequestReviewCommentRepository;
        this.pullRequestReviewRepository = pullRequestReviewRepository;
        this.pullRequestRepository = pullRequestRepository;
        this.userRepository = userRepository;
        this.pullRequestReviewCommentConverter = pullRequestReviewCommentConverter;
        this.pullRequestConverter = pullRequestConverter;
        this.userConverter = userConverter;
        this.pullRequestReviewThreadRepository = pullRequestReviewThreadRepository;
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
        pullRequest.listReviewComments().withPageSize(100).forEach(this::processPullRequestReviewComment);
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
                        pullRequestReviewComment.getUpdatedAt().isBefore(ghPullRequestReviewComment.getUpdatedAt())
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
        resolveAuthor(ghPullRequestReviewComment)
            .ifPresentOrElse(
                ghUser -> {
                    var resultAuthor = userRepository
                        .findById(ghUser.getId())
                        .orElseGet(() -> userRepository.save(userConverter.convert(ghUser)));
                    result.setAuthor(resultAuthor);
                },
                () ->
                    logger.warn(
                        "Failed to resolve author for pull request review comment {}",
                        ghPullRequestReviewComment.getId()
                    )
            );

        linkParentComment(result, ghPullRequestReviewComment);

        PullRequestReviewComment savedComment = pullRequestReviewCommentRepository.save(result);

        linkThread(savedComment, ghPullRequestReviewComment);

        return savedComment;
    }

    private void linkParentComment(
        PullRequestReviewComment comment,
        GHPullRequestReviewComment ghPullRequestReviewComment
    ) {
        long inReplyToId = ghPullRequestReviewComment.getInReplyToId();
        if (inReplyToId > 0) {
            pullRequestReviewCommentRepository
                .findById(inReplyToId)
                .ifPresentOrElse(
                    comment::setInReplyTo,
                    () ->
                        logger.warn(
                            "Failed to link in-reply-to comment {} for pull request review comment {}",
                            inReplyToId,
                            ghPullRequestReviewComment.getId()
                        )
                );
        } else {
            comment.setInReplyTo(null);
        }
    }

    private void linkThread(
        PullRequestReviewComment comment,
        GHPullRequestReviewComment ghPullRequestReviewComment
    ) {
        long inReplyToId = ghPullRequestReviewComment.getInReplyToId();

        if (inReplyToId > 0) {
            pullRequestReviewThreadRepository
                .findById(inReplyToId)
                .ifPresentOrElse(
                    thread -> {
                        thread.getComments().add(comment);
                        thread.setUpdatedAt(resolveTimestamp(comment));
                        comment.setThread(thread);
                        pullRequestReviewCommentRepository.save(comment);
                    },
                    () ->
                        logger.warn(
                            "Failed to find thread {} for pull request review comment {}",
                            inReplyToId,
                            ghPullRequestReviewComment.getId()
                        )
                );
            return;
        }

        PullRequestReviewThread thread = pullRequestReviewThreadRepository.findById(comment.getId()).orElse(null);
        boolean isNewThread = thread == null;
        if (isNewThread) {
            thread = new PullRequestReviewThread();
            thread.setState(PullRequestReviewThread.State.UNRESOLVED);
            thread.setCreatedAt(comment.getCreatedAt());
        }

        thread.setRootComment(comment);
        thread.setPullRequest(comment.getPullRequest());
        thread.setUpdatedAt(resolveTimestamp(comment));
        thread.getComments().add(comment);

        thread = pullRequestReviewThreadRepository.save(thread);
        comment.setThread(thread);
        pullRequestReviewCommentRepository.save(comment);
    }

    private Instant resolveTimestamp(PullRequestReviewComment comment) {
        return comment.getUpdatedAt() != null ? comment.getUpdatedAt() : comment.getCreatedAt();
    }

    private Optional<GHUser> resolveAuthor(GHPullRequestReviewComment ghComment) {
        try {
            GHUser user = ghComment.getUser();
            if (user != null) {
                return Optional.of(user);
            }
        } catch (IOException e) {
            logger.warn(
                "I/O error while resolving author for pull request review comment {}: {}",
                ghComment.getId(),
                e.getMessage()
            );
        } catch (RuntimeException runtimeException) {
            logger.warn(
                "Runtime error while resolving author for pull request review comment {}: {}",
                ghComment.getId(),
                runtimeException.getMessage()
            );
        }

        try {
            Field userField = ghComment.getClass().getDeclaredField("user");
            userField.setAccessible(true);
            Object value = userField.get(ghComment);
            if (value instanceof GHUser ghUser) {
                return Optional.of(ghUser);
            }
        } catch (NoSuchFieldException | IllegalAccessException reflectionError) {
            logger.warn(
                "Failed to access embedded user for pull request review comment {}: {}",
                ghComment.getId(),
                reflectionError.getMessage()
            );
        }

        return Optional.empty();
    }
}
