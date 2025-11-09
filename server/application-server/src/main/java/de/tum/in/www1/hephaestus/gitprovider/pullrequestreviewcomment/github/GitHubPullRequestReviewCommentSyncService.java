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
    private final PullRequestReviewThreadRepository pullRequestReviewThreadRepository;
    private final UserRepository userRepository;
    private final GitHubPullRequestReviewCommentConverter pullRequestReviewCommentConverter;
    private final GitHubPullRequestConverter pullRequestConverter;
    private final GitHubUserConverter userConverter;

    public GitHubPullRequestReviewCommentSyncService(
        PullRequestReviewCommentRepository pullRequestReviewCommentRepository,
        PullRequestReviewRepository pullRequestReviewRepository,
        PullRequestRepository pullRequestRepository,
        PullRequestReviewThreadRepository pullRequestReviewThreadRepository,
        UserRepository userRepository,
        GitHubPullRequestReviewCommentConverter pullRequestReviewCommentConverter,
        GitHubPullRequestConverter pullRequestConverter,
        GitHubUserConverter userConverter
    ) {
        this.pullRequestReviewCommentRepository = pullRequestReviewCommentRepository;
        this.pullRequestReviewRepository = pullRequestReviewRepository;
        this.pullRequestRepository = pullRequestRepository;
        this.pullRequestReviewThreadRepository = pullRequestReviewThreadRepository;
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
        pullRequest
            .listReviewComments()
            .withPageSize(100)
            .forEach(comment -> processPullRequestReviewComment(comment, pullRequest));
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

        pullRequestReviewRepository
            .findById(ghPullRequestReviewComment.getPullRequestReviewId())
            .ifPresentOrElse(result::setReview, () ->
                logger.error(
                    "Failed to link review for pull request review comment {}: {}",
                    ghPullRequestReviewComment.getId(),
                    "Review not found"
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
            logger.error(
                "Failed to fetch author via API for pull request review comment {}: {}",
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

        var resultAuthor = userRepository.findById(user.getId()).orElse(null);
        if (resultAuthor == null) {
            resultAuthor = userRepository.save(userConverter.convert(user));
        }
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
            logger.warn(
                "Parent review comment {} not found for reply {}",
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
        if (!thread.getComments().contains(comment)) {
            thread.getComments().add(comment);
        }

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
                var thread = comment.getThread();
                boolean isRootComment =
                    thread != null &&
                    thread.getRootComment() != null &&
                    thread.getRootComment().getId().equals(commentId);

                if (isRootComment) {
                    pullRequestReviewThreadRepository.delete(thread);
                    pullRequestReviewThreadRepository.flush();
                } else {
                    if (thread != null) {
                        thread.getComments().remove(comment);
                    }
                    comment.setThread(null);
                    pullRequestReviewCommentRepository.delete(comment);
                    pullRequestReviewCommentRepository.flush();
                    if (thread != null && pullRequestReviewCommentRepository.countByThreadId(thread.getId()) == 0) {
                        pullRequestReviewThreadRepository.delete(thread);
                        pullRequestReviewThreadRepository.flush();
                    }
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
