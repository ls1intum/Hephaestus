package de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewthread.github;

import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestRepository;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.github.GitHubPullRequestConverter;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewcomment.PullRequestReviewComment;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewcomment.github.GitHubPullRequestReviewCommentSyncService;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewthread.PullRequestReviewThread;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewthread.PullRequestReviewThreadRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.github.GitHubUserConverter;
import jakarta.transaction.Transactional;
import java.io.IOException;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.kohsuke.github.GHEventPayloadPullRequestReviewThread;
import org.kohsuke.github.GHPullRequestReviewComment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

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
            PullRequestReviewComment persisted = commentSyncService.processPullRequestReviewComment(comment, ghPullRequest);
            if (persisted != null) {
                thread = persisted.getThread();
            }
        }

        if (thread == null) {
            logger.warn("Unable to resolve thread from comments, skipping state update");
            return null;
        }

        thread.setPullRequest(pullRequest);
        thread.setNodeId(threadPayload.getNodeId());
        thread.setUpdatedAt(determineTimestamp(threadPayload.getComments()));

        if ("resolved".equalsIgnoreCase(payload.getAction())) {
            thread.setState(PullRequestReviewThread.State.RESOLVED);
            thread.setResolvedAt(thread.getUpdatedAt());
        } else if ("unresolved".equalsIgnoreCase(payload.getAction())) {
            thread.setState(PullRequestReviewThread.State.UNRESOLVED);
            thread.setResolvedAt(null);
        }

        var sender = payload.getSender();
        if (sender != null) {
            var actor = userRepository
                .findById(sender.getId())
                .orElseGet(() -> userRepository.save(userConverter.convert(sender)));
            thread.setLastActor(actor);
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
}
