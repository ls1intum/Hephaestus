package de.tum.in.www1.hephaestus.gitprovider.commit.github;

import static de.tum.in.www1.hephaestus.core.LoggingUtils.sanitizeForLog;

import de.tum.in.www1.hephaestus.gitprovider.commit.Commit;
import de.tum.in.www1.hephaestus.gitprovider.commit.github.dto.GitHubCommitDTO;
import de.tum.in.www1.hephaestus.gitprovider.commit.github.dto.GitHubPushEventDTO;
import de.tum.in.www1.hephaestus.gitprovider.common.NatsMessageDeserializer;
import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContext;
import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContextFactory;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubEventType;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubMessageHandler;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Handles GitHub push webhook events for commit synchronization.
 * <p>
 * Push events are triggered when commits are pushed to a repository branch.
 * This handler processes the commits in the push payload and persists them
 * to the database.
 * <p>
 * <b>Behavior Notes:</b>
 * <ul>
 *   <li>Only processes commits pushed to the default branch by default</li>
 *   <li>Branch deletions (deleted=true) and force pushes may have empty commit lists</li>
 *   <li>The commit list may be truncated for pushes with many commits (GitHub limits to 20)</li>
 *   <li>For large pushes, the scheduled sync service should catch additional commits</li>
 * </ul>
 *
 * @see GitHubPushEventDTO
 * @see <a href="https://docs.github.com/en/webhooks/webhook-events-and-payloads#push">
 *      GitHub Push Event Documentation</a>
 */
@Component
public class GitHubCommitMessageHandler extends GitHubMessageHandler<GitHubPushEventDTO> {

    private static final Logger log = LoggerFactory.getLogger(GitHubCommitMessageHandler.class);

    private final ProcessingContextFactory contextFactory;
    private final GitHubCommitProcessor commitProcessor;

    public GitHubCommitMessageHandler(
        ProcessingContextFactory contextFactory,
        GitHubCommitProcessor commitProcessor,
        NatsMessageDeserializer deserializer,
        TransactionTemplate transactionTemplate
    ) {
        super(GitHubPushEventDTO.class, deserializer, transactionTemplate);
        this.contextFactory = contextFactory;
        this.commitProcessor = commitProcessor;
    }

    @Override
    public GitHubEventType getEventType() {
        return GitHubEventType.PUSH;
    }

    @Override
    protected void handleEvent(GitHubPushEventDTO event) {
        // Skip branch deletions - no commits to process
        if (event.deleted()) {
            log.debug(
                "Skipped push event: reason=branchDeleted, ref={}, repoName={}",
                event.ref(),
                event.repository() != null ? sanitizeForLog(event.repository().fullName()) : "unknown"
            );
            return;
        }

        // Skip if no commits (can happen with force pushes or tags)
        if (event.commits() == null || event.commits().isEmpty()) {
            log.debug(
                "Skipped push event: reason=noCommits, ref={}, repoName={}",
                event.ref(),
                event.repository() != null ? sanitizeForLog(event.repository().fullName()) : "unknown"
            );
            return;
        }

        String repoName = event.repository() != null ? sanitizeForLog(event.repository().fullName()) : "unknown";
        String branch = event.getBranchName();

        log.info(
            "Received push event: branch={}, commitCount={}, forced={}, repoName={}",
            branch,
            event.commits().size(),
            event.forced(),
            repoName
        );

        ProcessingContext context = contextFactory.forWebhookEvent(event).orElse(null);
        if (context == null) {
            return;
        }

        // Only process commits to the default branch
        String defaultBranch = event.repository() != null ? event.repository().defaultBranch() : null;
        if (!event.isDefaultBranch(defaultBranch)) {
            log.debug(
                "Skipped push event: reason=notDefaultBranch, branch={}, defaultBranch={}, repoName={}",
                branch,
                defaultBranch,
                repoName
            );
            return;
        }

        processCommits(event, context);
    }

    /**
     * Processes all commits from the push event.
     */
    private void processCommits(GitHubPushEventDTO event, ProcessingContext context) {
        List<GitHubCommitDTO> commitDTOs = GitHubCommitDTO.fromPushWebhook(event);
        int processed = 0;

        for (GitHubCommitDTO dto : commitDTOs) {
            Commit commit = commitProcessor.process(dto, context);
            if (commit != null) {
                processed++;
            }
        }

        log.info(
            "Processed push commits: processed={}, total={}, branch={}, repoName={}",
            processed,
            commitDTOs.size(),
            event.getBranchName(),
            event.repository() != null ? sanitizeForLog(event.repository().fullName()) : "unknown"
        );
    }
}
