package de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewthread.github;

import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestRepository;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.github.GitHubPullRequestConverter;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewcomment.PullRequestReviewComment;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewcomment.github.GitHubPullRequestReviewCommentSyncService;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewthread.PullRequestReviewThread;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewthread.PullRequestReviewThreadRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.github.GitHubUserConverter;
import java.io.IOException;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.kohsuke.github.GHEventPayloadPullRequestReviewThread;
import org.kohsuke.github.GHPullRequestReviewComment;
import org.kohsuke.github.GHUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GitHubPullRequestReviewThreadSyncService {

    private static final Logger logger = LoggerFactory.getLogger(GitHubPullRequestReviewThreadSyncService.class);

    private final GitHubPullRequestReviewCommentSyncService commentSyncService;
    private final PullRequestReviewThreadRepository pullRequestReviewThreadRepository;
    private final PullRequestRepository pullRequestRepository;
    private final GitHubPullRequestConverter pullRequestConverter;
    private final UserRepository userRepository;
    private final GitHubUserConverter userConverter;

    public GitHubPullRequestReviewThreadSyncService(
        GitHubPullRequestReviewCommentSyncService commentSyncService,
        PullRequestReviewThreadRepository pullRequestReviewThreadRepository,
        PullRequestRepository pullRequestRepository,
        GitHubPullRequestConverter pullRequestConverter,
        UserRepository userRepository,
        GitHubUserConverter userConverter
    ) {
        this.commentSyncService = commentSyncService;
        this.pullRequestReviewThreadRepository = pullRequestReviewThreadRepository;
        this.pullRequestRepository = pullRequestRepository;
        this.pullRequestConverter = pullRequestConverter;
        this.userRepository = userRepository;
        this.userConverter = userConverter;
    }

    @Transactional
    public PullRequestReviewThread processThreadEvent(GHEventPayloadPullRequestReviewThread payload) {
        var threadPayload = payload.getThread();
        if (threadPayload == null || threadPayload.getComments() == null || threadPayload.getComments().isEmpty()) {
            logger.warn("Received pull request review thread event without comments");
            return null;
        }

        var ghPullRequest = payload.getPullRequest();
        var pullRequest = pullRequestRepository
            .findById(ghPullRequest.getId())
            .orElseGet(() -> pullRequestRepository.save(pullRequestConverter.convert(ghPullRequest)));

        PullRequestReviewThread thread = null;
        var sortedComments = threadPayload
            .getComments()
            .stream()
            .sorted((left, right) -> Long.compare(left.getInReplyToId(), right.getInReplyToId()))
            .toList();

        for (GHPullRequestReviewComment comment : sortedComments) {
            PullRequestReviewComment persisted = commentSyncService.processPullRequestReviewComment(
                comment,
                ghPullRequest
            );
            if (persisted != null) {
                thread = persisted.getThread();
            }
        }

        if (thread == null) {
            logger.warn("Unable to resolve thread from comments, skipping state update");
            return null;
        }

        thread.setPullRequest(pullRequest);
        updateThreadMetadata(thread, threadPayload);
        thread.setUpdatedAt(determineTimestamp(threadPayload.getComments()));

        if ("resolved".equalsIgnoreCase(payload.getAction())) {
            thread.setState(PullRequestReviewThread.State.RESOLVED);
            thread.setResolvedAt(thread.getUpdatedAt());
            attachResolvedBy(thread, threadPayload.getResolvedBy());
        } else if ("unresolved".equalsIgnoreCase(payload.getAction())) {
            thread.setState(PullRequestReviewThread.State.UNRESOLVED);
            thread.setResolvedAt(null);
            thread.setResolvedBy(null);
        }

        return pullRequestReviewThreadRepository.save(thread);
    }

    private Instant determineTimestamp(List<GHPullRequestReviewComment> comments) {
        return comments
            .stream()
            .map(comment -> {
                try {
                    return Optional.ofNullable(comment.getUpdatedAt()).orElse(comment.getCreatedAt());
                } catch (IOException e) {
                    logger.warn("Failed to read timestamps for comment {}: {}", comment.getId(), e.getMessage());
                    return (Instant) null;
                }
            })
            .filter(java.util.Objects::nonNull)
            .max(Comparator.naturalOrder())
            .orElse(Instant.now());
    }

    private void updateThreadMetadata(
        PullRequestReviewThread thread,
        GHEventPayloadPullRequestReviewThread.Thread data
    ) {
        if (data.getId() != null) {
            thread.setProviderThreadId(data.getId());
        }
        if (data.getNodeId() != null) {
            thread.setNodeId(data.getNodeId());
        }

        if (data.getPath() != null) {
            thread.setPath(data.getPath());
        }

        if (data.getLine() != null) {
            thread.setLine(data.getLine());
        }

        if (data.getStartLine() != null) {
            thread.setStartLine(data.getStartLine());
        }

        if (data.getSide() != null) {
            thread.setSide(resolveSide(data.getSide()));
        }

        if (data.getStartSide() != null) {
            thread.setStartSide(resolveSide(data.getStartSide()));
        }

        if (data.getOutdated() != null) {
            thread.setOutdated(data.getOutdated());
        }

        if (data.getCollapsed() != null) {
            thread.setCollapsed(data.getCollapsed());
        }

        if (thread.getPath() == null && thread.getRootComment() != null) {
            thread.setPath(thread.getRootComment().getPath());
        }
        if (thread.getLine() == null && thread.getRootComment() != null) {
            thread.setLine(thread.getRootComment().getLine());
        }
        if (thread.getStartLine() == null && thread.getRootComment() != null) {
            thread.setStartLine(thread.getRootComment().getStartLine());
        }
        if (thread.getSide() == null && thread.getRootComment() != null) {
            thread.setSide(thread.getRootComment().getSide());
        }
        if (thread.getStartSide() == null && thread.getRootComment() != null) {
            thread.setStartSide(thread.getRootComment().getStartSide());
        }
        if (thread.getProviderThreadId() == null && thread.getRootComment() != null) {
            thread.setProviderThreadId(thread.getRootComment().getId());
        }
    }

    private PullRequestReviewComment.Side resolveSide(String value) {
        if (value == null) {
            return null;
        }
        try {
            return PullRequestReviewComment.Side.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return PullRequestReviewComment.Side.UNKNOWN;
        }
    }

    private void attachResolvedBy(PullRequestReviewThread thread, GHUser resolver) {
        if (resolver == null) {
            thread.setResolvedBy(null);
            return;
        }

        var existing = userRepository.findById(resolver.getId());
        if (existing.isPresent()) {
            thread.setResolvedBy(existing.get());
            return;
        }

        var converted = userConverter.convert(resolver);
        thread.setResolvedBy(userRepository.save(converted));
    }
}
