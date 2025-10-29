package de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewthread.github;

import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubMessageHandler;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.github.GitHubPullRequestSyncService;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewcomment.PullRequestReviewCommentRepository;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewcomment.github.GitHubPullRequestReviewCommentSyncService;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewthread.PullRequestReviewThread;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewthread.PullRequestReviewThreadRepository;
import de.tum.in.www1.hephaestus.gitprovider.repository.github.GitHubRepositorySyncService;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.github.GitHubUserConverter;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import org.kohsuke.github.GHEvent;
import org.kohsuke.github.GHPullRequestReviewComment;
import org.kohsuke.github.GHUser;
import org.kohsuke.github.GHEventPayloadPullRequestReviewThread;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class GitHubPullRequestReviewThreadMessageHandler
    extends GitHubMessageHandler<GHEventPayloadPullRequestReviewThread> {

    private static final Logger logger = LoggerFactory.getLogger(GitHubPullRequestReviewThreadMessageHandler.class);

    private final PullRequestReviewThreadRepository pullRequestReviewThreadRepository;
    private final GitHubPullRequestReviewCommentSyncService pullRequestReviewCommentSyncService;
    private final GitHubPullRequestSyncService pullRequestSyncService;
    private final GitHubRepositorySyncService repositorySyncService;
    private final PullRequestReviewCommentRepository pullRequestReviewCommentRepository;
    private final UserRepository userRepository;
    private final GitHubUserConverter userConverter;

    public GitHubPullRequestReviewThreadMessageHandler(
        PullRequestReviewThreadRepository pullRequestReviewThreadRepository,
        GitHubPullRequestReviewCommentSyncService pullRequestReviewCommentSyncService,
        GitHubPullRequestSyncService pullRequestSyncService,
        GitHubRepositorySyncService repositorySyncService,
        PullRequestReviewCommentRepository pullRequestReviewCommentRepository,
        UserRepository userRepository,
        GitHubUserConverter userConverter
    ) {
        super(GHEventPayloadPullRequestReviewThread.class);
        this.pullRequestReviewThreadRepository = pullRequestReviewThreadRepository;
        this.pullRequestReviewCommentSyncService = pullRequestReviewCommentSyncService;
        this.pullRequestSyncService = pullRequestSyncService;
        this.repositorySyncService = repositorySyncService;
        this.pullRequestReviewCommentRepository = pullRequestReviewCommentRepository;
        this.userRepository = userRepository;
        this.userConverter = userConverter;
    }

    @Override
    @Transactional
    protected void handleEvent(GHEventPayloadPullRequestReviewThread payload) {
        var repository = payload.getRepository();
        var pullRequest = payload.getPullRequest();
        logger.info(
            "Received pull request review thread event for repository: {}, pull request: {}, action: {}",
            repository != null ? repository.getFullName() : "unknown",
            pullRequest != null ? pullRequest.getNumber() : "unknown",
            payload.getAction()
        );

        if (repository != null) {
            repositorySyncService.processRepository(repository);
        }

        final PullRequest storedPullRequest = pullRequest != null
            ? pullRequestSyncService.processPullRequest(pullRequest)
            : null;

        payload
            .getThread()
            .getComments()
            .forEach(pullRequestReviewCommentSyncService::processPullRequestReviewComment);

        long threadId = resolveThreadId(payload.getThread().getComments());
        if (threadId == 0) {
            logger.warn("Unable to resolve thread id for review thread event");
            return;
        }

        PullRequestReviewThread thread = pullRequestReviewThreadRepository
            .findById(threadId)
            .orElseGet(() -> initializeThread(threadId, storedPullRequest));

        if (storedPullRequest != null) {
            thread.setPullRequest(storedPullRequest);
        }

        Instant updatedAt = payload.getUpdatedAt();
        if (updatedAt != null) {
            thread.setUpdatedAt(updatedAt);
        }

        switch (payload.getAction()) {
            case "resolved" -> {
                thread.setState(PullRequestReviewThread.State.RESOLVED);
                thread.setResolvedAt(updatedAt);
            }
            case "unresolved" -> {
                thread.setState(PullRequestReviewThread.State.UNRESOLVED);
                thread.setResolvedAt(null);
            }
            default -> logger.warn("Unhandled pull request review thread action: {}", payload.getAction());
        }

        if (payload.getSender() != null) {
            linkLastActor(thread, payload.getSender());
        }

        pullRequestReviewThreadRepository.save(thread);
    }

    @Override
    protected GHEvent getHandlerEvent() {
        return GHEvent.PULL_REQUEST_REVIEW_THREAD;
    }

    private long resolveThreadId(List<GHPullRequestReviewComment> comments) {
        return comments
            .stream()
            .filter(comment -> comment.getInReplyToId() <= 0)
            .findFirst()
            .map(GHPullRequestReviewComment::getId)
            .orElseGet(() ->
                comments
                    .stream()
                    .min(Comparator.comparingLong(GHPullRequestReviewComment::getId))
                    .map(GHPullRequestReviewComment::getId)
                    .orElse(0L)
            );
    }

    private PullRequestReviewThread initializeThread(long threadId, PullRequest pullRequest) {
        PullRequestReviewThread thread = new PullRequestReviewThread();
        thread.setState(PullRequestReviewThread.State.UNRESOLVED);

        pullRequestReviewCommentRepository
            .findById(threadId)
            .ifPresent(comment -> {
                thread.setRootComment(comment);
                thread.setPullRequest(comment.getPullRequest());
                thread.setCreatedAt(comment.getCreatedAt());
                thread.addComment(comment);
            });

        if (pullRequest != null) {
            thread.setPullRequest(pullRequest);
        }

        return thread;
    }

    private void linkLastActor(PullRequestReviewThread thread, GHUser sender) {
        var actor = userRepository
            .findById(sender.getId())
            .orElseGet(() -> userRepository.save(userConverter.convert(sender)));
        thread.setLastActor(actor);
    }
}
