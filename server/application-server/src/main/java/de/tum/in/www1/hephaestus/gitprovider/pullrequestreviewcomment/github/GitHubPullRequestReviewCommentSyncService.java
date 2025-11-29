package de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewcomment.github;

import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestRepository;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.github.GitHubPullRequestConverter;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.PullRequestReviewRepository;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewcomment.PullRequestReviewComment;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewcomment.PullRequestReviewCommentRepository;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewthread.PullRequestReviewThread;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewthread.PullRequestReviewThreadRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.github.GitHubUserSyncService;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHPullRequestReviewComment;
import org.kohsuke.github.GHUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for synchronizing GitHub pull request review comments with the local database.
 */
@Service
public class GitHubPullRequestReviewCommentSyncService {

    private static final Logger logger = LoggerFactory.getLogger(GitHubPullRequestReviewCommentSyncService.class);

    private final PullRequestReviewCommentRepository pullRequestReviewCommentRepository;
    private final PullRequestReviewRepository pullRequestReviewRepository;
    private final PullRequestRepository pullRequestRepository;
    private final PullRequestReviewThreadRepository pullRequestReviewThreadRepository;
    private final GitHubPullRequestReviewCommentConverter pullRequestReviewCommentConverter;
    private final GitHubPullRequestConverter pullRequestConverter;
    private final GitHubUserSyncService userSyncService;

    public GitHubPullRequestReviewCommentSyncService(
        PullRequestReviewCommentRepository pullRequestReviewCommentRepository,
        PullRequestReviewRepository pullRequestReviewRepository,
        PullRequestRepository pullRequestRepository,
        PullRequestReviewThreadRepository pullRequestReviewThreadRepository,
        GitHubPullRequestReviewCommentConverter pullRequestReviewCommentConverter,
        GitHubPullRequestConverter pullRequestConverter,
        GitHubUserSyncService userSyncService
    ) {
        this.pullRequestReviewCommentRepository = pullRequestReviewCommentRepository;
        this.pullRequestReviewRepository = pullRequestReviewRepository;
        this.pullRequestRepository = pullRequestRepository;
        this.pullRequestReviewThreadRepository = pullRequestReviewThreadRepository;
        this.pullRequestReviewCommentConverter = pullRequestReviewCommentConverter;
        this.pullRequestConverter = pullRequestConverter;
        this.userSyncService = userSyncService;
    }

    /**
     * Synchronizes all review comments for the given list of GitHub pull requests.
     */
    @Transactional
    public void syncReviewCommentsOfAllPullRequests(List<GHPullRequest> pullRequests) {
        pullRequests.forEach(this::syncReviewCommentsOfPullRequest);
    }

    /**
     * Synchronizes all review comments for a specific GitHub pull request.
     */
    @Transactional
    public void syncReviewCommentsOfPullRequest(GHPullRequest pullRequest) {
        pullRequest
            .listReviewComments()
            .withPageSize(100)
            .forEach(comment -> processPullRequestReviewComment(comment, pullRequest));
    }

    /**
     * Processes a single GitHub pull request review comment.
     */
    @Transactional
    public PullRequestReviewComment processPullRequestReviewComment(GHPullRequestReviewComment ghComment) {
        return processPullRequestReviewComment(ghComment, null, null);
    }

    @Transactional
    public PullRequestReviewComment processPullRequestReviewComment(
        GHPullRequestReviewComment ghComment,
        GHPullRequest providedPullRequest
    ) {
        return processPullRequestReviewComment(ghComment, providedPullRequest, null);
    }

    @Transactional
    public PullRequestReviewComment processPullRequestReviewComment(
        GHPullRequestReviewComment ghPullRequestReviewComment,
        GHPullRequest providedPullRequest,
        GHUser fallbackUser
    ) {
        var existing = pullRequestReviewCommentRepository.findById(ghPullRequestReviewComment.getId()).orElse(null);
        var result = existing != null
            ? updateIfNewer(ghPullRequestReviewComment, existing)
            : pullRequestReviewCommentConverter.convert(ghPullRequestReviewComment);

        if (result == null) {
            return null;
        }

        var pullRequest = resolvePullRequest(ghPullRequestReviewComment, providedPullRequest);
        if (pullRequest == null) {
            logger.warn(
                "Unable to determine pull request for review comment {}. Skipping.",
                ghPullRequestReviewComment.getId()
            );
            return null;
        }

        var resultPullRequest = pullRequestRepository
            .findById(pullRequest.getId())
            .orElseGet(() -> pullRequestRepository.save(pullRequestConverter.convert(pullRequest)));
        result.setPullRequest(resultPullRequest);

        // Link review if found - if not, it will be linked on next sync
        long reviewId = ghPullRequestReviewComment.getPullRequestReviewId();
        pullRequestReviewRepository
            .findById(reviewId)
            .ifPresentOrElse(result::setReview, () ->
                logger.debug(
                    "Review {} not found for comment {} - will be linked on next sync",
                    reviewId,
                    ghPullRequestReviewComment.getId()
                )
            );

        attachAuthor(ghPullRequestReviewComment, result, fallbackUser);
        attachInReplyTo(ghPullRequestReviewComment, result);

        PullRequestReviewThread thread = ensureThread(ghPullRequestReviewComment, resultPullRequest);
        result.setThread(thread);

        var persisted = pullRequestReviewCommentRepository.save(result);
        updateThreadMetadata(thread, persisted, ghPullRequestReviewComment);

        return persisted;
    }

    private PullRequestReviewComment updateIfNewer(GHPullRequestReviewComment source, PullRequestReviewComment target) {
        try {
            if (target.getUpdatedAt() == null || target.getUpdatedAt().isBefore(source.getUpdatedAt())) {
                return pullRequestReviewCommentConverter.update(source, target);
            }
            return target;
        } catch (IOException e) {
            logger.error("Failed to update pull request review comment {}: {}", source.getId(), e.getMessage());
            return null;
        }
    }

    private void attachAuthor(
        GHPullRequestReviewComment ghPullRequestReviewComment,
        PullRequestReviewComment comment,
        GHUser fallbackUser
    ) {
        GHUser user = null;
        try {
            user = ghPullRequestReviewComment.getUser();
        } catch (IOException | NullPointerException e) {
            logger.debug(
                "Failed to fetch author for review comment {}: {}",
                ghPullRequestReviewComment.getId(),
                e.getMessage()
            );
        }

        if (user == null) {
            user = fallbackUser;
        }

        if (user == null) {
            comment.setAuthor(null);
            return;
        }

        var resultAuthor = userSyncService.getOrCreateUser(user);
        comment.setAuthor(resultAuthor);
    }

    private void attachInReplyTo(
        GHPullRequestReviewComment ghPullRequestReviewComment,
        PullRequestReviewComment comment
    ) {
        long inReplyToId = ghPullRequestReviewComment.getInReplyToId();
        if (inReplyToId <= 0L) {
            comment.setInReplyTo(null);
            return;
        }

        Optional<PullRequestReviewComment> parent = pullRequestReviewCommentRepository.findById(inReplyToId);
        if (parent.isPresent()) {
            comment.setInReplyTo(parent.get());
        } else {
            // Expected when processing webhook events out of order - will be linked on next sync
            logger.debug(
                "Parent comment {} not found for reply {} - will be linked on next sync",
                inReplyToId,
                ghPullRequestReviewComment.getId()
            );
            comment.setInReplyTo(null);
        }
    }

    private PullRequestReviewThread ensureThread(
        GHPullRequestReviewComment ghPullRequestReviewComment,
        de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest pullRequest
    ) {
        long rootCommentId = extractRootCommentId(ghPullRequestReviewComment);

        PullRequestReviewThread thread = pullRequestReviewThreadRepository
            .findById(rootCommentId)
            .orElseGet(() -> {
                PullRequestReviewThread newThread = new PullRequestReviewThread();
                newThread.setId(rootCommentId);
                newThread.setProviderThreadId(rootCommentId);
                newThread.setState(PullRequestReviewThread.State.UNRESOLVED);
                newThread.setPullRequest(pullRequest);
                return pullRequestReviewThreadRepository.save(newThread);
            });

        thread.setPullRequest(pullRequest);
        return thread;
    }

    private void updateThreadMetadata(
        PullRequestReviewThread thread,
        PullRequestReviewComment comment,
        GHPullRequestReviewComment ghPullRequestReviewComment
    ) {
        comment.setThread(thread);
        thread.getComments().removeIf(existing -> existing.getId() != null && existing.getId().equals(comment.getId()));
        thread.getComments().add(comment);

        if (ghPullRequestReviewComment.getInReplyToId() <= 0L) {
            thread.setRootComment(comment);
            thread.setProviderThreadId(comment.getId());
            thread.setPath(comment.getPath());
            thread.setLine(comment.getLine());
            thread.setStartLine(comment.getStartLine());
            thread.setSide(comment.getSide());
            thread.setStartSide(comment.getStartSide());
        }

        if (thread.getPath() == null) {
            thread.setPath(comment.getPath());
        }

        if (
            thread.getCreatedAt() == null ||
            (comment.getCreatedAt() != null && comment.getCreatedAt().isBefore(thread.getCreatedAt()))
        ) {
            thread.setCreatedAt(comment.getCreatedAt());
        }

        var updatedTimestamp = comment.getUpdatedAt() != null ? comment.getUpdatedAt() : comment.getCreatedAt();
        if (updatedTimestamp != null) {
            thread.setUpdatedAt(updatedTimestamp);
        }

        pullRequestReviewThreadRepository.save(thread);
    }

    @Transactional
    public void deletePullRequestReviewComment(long commentId) {
        pullRequestReviewCommentRepository
            .findById(commentId)
            .ifPresent(comment -> {
                PullRequestReviewThread thread = null;
                if (comment.getThread() != null) {
                    thread = pullRequestReviewThreadRepository
                        .findWithCommentsById(comment.getThread().getId())
                        .orElse(null);
                }

                if (thread != null) {
                    var wasRootComment =
                        thread.getRootComment() != null && thread.getRootComment().getId().equals(commentId);
                    if (wasRootComment) {
                        thread.setRootComment(null);
                    }

                    boolean commentRemoved = thread
                        .getComments()
                        .removeIf(existing -> existing.getId() != null && existing.getId().equals(commentId));

                    if (wasRootComment || commentRemoved) {
                        pullRequestReviewThreadRepository.save(thread);
                        pullRequestReviewThreadRepository.flush();
                    }
                }

                pullRequestReviewCommentRepository.delete(comment);
                pullRequestReviewCommentRepository.flush();

                if (thread != null && pullRequestReviewCommentRepository.countByThreadId(thread.getId()) == 0) {
                    pullRequestReviewThreadRepository.delete(thread);
                    pullRequestReviewThreadRepository.flush();
                }
            });
    }

    private GHPullRequest resolvePullRequest(
        GHPullRequestReviewComment ghPullRequestReviewComment,
        GHPullRequest providedPullRequest
    ) {
        if (providedPullRequest != null) {
            return providedPullRequest;
        }

        var parent = ghPullRequestReviewComment.getParent();
        if (parent == null) {
            logger.warn(
                "GitHub did not include pull request details for review comment {}",
                ghPullRequestReviewComment.getId()
            );
        }
        return parent;
    }

    private long extractRootCommentId(GHPullRequestReviewComment ghPullRequestReviewComment) {
        long inReplyToId = ghPullRequestReviewComment.getInReplyToId();
        if (inReplyToId <= 0L) {
            return ghPullRequestReviewComment.getId();
        }
        return inReplyToId;
    }
}
