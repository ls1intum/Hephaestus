package de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewthread.github;

import static de.tum.in.www1.hephaestus.core.LoggingUtils.sanitizeForLog;

import de.tum.in.www1.hephaestus.gitprovider.common.NatsMessageDeserializer;
import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContext;
import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContextFactory;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubEventAction;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubEventType;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubMessageHandler;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.github.GitHubPullRequestProcessor;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewthread.github.dto.GitHubPullRequestReviewThreadEventDTO;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.github.GitHubUserProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Handles GitHub pull_request_review_thread webhook events.
 */
@Component
public class GitHubPullRequestReviewThreadMessageHandler
    extends GitHubMessageHandler<GitHubPullRequestReviewThreadEventDTO>
{

    private static final Logger log = LoggerFactory.getLogger(GitHubPullRequestReviewThreadMessageHandler.class);

    private final ProcessingContextFactory contextFactory;
    private final GitHubPullRequestProcessor prProcessor;
    private final GitHubPullRequestReviewThreadProcessor threadProcessor;
    private final GitHubUserProcessor userProcessor;

    GitHubPullRequestReviewThreadMessageHandler(
        ProcessingContextFactory contextFactory,
        GitHubPullRequestProcessor prProcessor,
        GitHubPullRequestReviewThreadProcessor threadProcessor,
        GitHubUserProcessor userProcessor,
        NatsMessageDeserializer deserializer,
        TransactionTemplate transactionTemplate
    ) {
        super(GitHubPullRequestReviewThreadEventDTO.class, deserializer, transactionTemplate);
        this.contextFactory = contextFactory;
        this.prProcessor = prProcessor;
        this.threadProcessor = threadProcessor;
        this.userProcessor = userProcessor;
    }

    @Override
    public GitHubEventType getEventType() {
        return GitHubEventType.PULL_REQUEST_REVIEW_THREAD;
    }

    @Override
    protected void handleEvent(GitHubPullRequestReviewThreadEventDTO event) {
        var threadDto = event.thread();
        var prDto = event.pullRequest();

        if (threadDto == null || prDto == null) {
            log.warn("Received pull_request_review_thread event with missing data: action={}", event.action());
            return;
        }

        // Thread ID is the first comment's database ID (see GitHubPullRequestReviewCommentSyncService)
        Long threadId = threadDto.getFirstCommentId();

        log.info(
            "Received pull_request_review_thread event: action={}, prNumber={}, threadId={}, repoName={}",
            event.action(),
            prDto.number(),
            threadId,
            event.repository() != null ? sanitizeForLog(event.repository().fullName()) : "unknown"
        );

        ProcessingContext context = contextFactory.forWebhookEvent(event).orElse(null);
        if (context == null) {
            return;
        }

        // Ensure PR exists
        prProcessor.process(prDto, context);

        // Delegate thread state changes to processor
        switch (event.actionType()) {
            case GitHubEventAction.PullRequestReviewThread.RESOLVED -> {
                // Thread ID is derived from the first comment. If no comments exist, we cannot process.
                if (threadId == null) {
                    log.warn(
                        "Skipped pull_request_review_thread event: reason=noCommentsInThread, action={}, prNumber={}, repoName={}",
                        event.action(),
                        prDto.number(),
                        event.repository() != null ? sanitizeForLog(event.repository().fullName()) : "unknown"
                    );
                    return;
                }
                // Ensure the sender (who resolved the thread) exists
                User resolvedBy = userProcessor.ensureExists(event.sender());
                threadProcessor.resolve(threadId, resolvedBy, context);
            }
            case GitHubEventAction.PullRequestReviewThread.UNRESOLVED -> {
                // Thread ID is derived from the first comment. If no comments exist, we cannot process.
                if (threadId == null) {
                    log.warn(
                        "Skipped pull_request_review_thread event: reason=noCommentsInThread, action={}, prNumber={}, repoName={}",
                        event.action(),
                        prDto.number(),
                        event.repository() != null ? sanitizeForLog(event.repository().fullName()) : "unknown"
                    );
                    return;
                }
                threadProcessor.unresolve(threadId, context);
            }
            default -> log.debug(
                "Skipped pull_request_review_thread event: reason=unhandledAction, action={}",
                event.action()
            );
        }
    }
}
