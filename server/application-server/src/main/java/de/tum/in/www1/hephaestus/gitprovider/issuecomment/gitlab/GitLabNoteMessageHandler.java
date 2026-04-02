package de.tum.in.www1.hephaestus.gitprovider.issuecomment.gitlab;

import static de.tum.in.www1.hephaestus.core.LoggingUtils.sanitizeForLog;

import de.tum.in.www1.hephaestus.agent.job.BotCommandProcessor;
import de.tum.in.www1.hephaestus.gitprovider.common.NatsMessageDeserializer;
import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContext;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabEventAction;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabEventType;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabMessageHandler;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabWebhookContextResolver;
import de.tum.in.www1.hephaestus.gitprovider.issuecomment.gitlab.dto.GitLabNoteEventDTO;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewcomment.gitlab.GitLabDiffNoteWebhookProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/** Handles GitLab note webhook events, routing to the appropriate processor by noteable type. */
@Component
@ConditionalOnProperty(prefix = "hephaestus.gitlab", name = "enabled", havingValue = "true")
public class GitLabNoteMessageHandler extends GitLabMessageHandler<GitLabNoteEventDTO> {

    private static final Logger log = LoggerFactory.getLogger(GitLabNoteMessageHandler.class);

    private final GitLabIssueCommentProcessor issueCommentProcessor;
    private final GitLabDiffNoteWebhookProcessor diffNoteProcessor;
    private final GitLabWebhookContextResolver contextResolver;
    private final @Nullable BotCommandProcessor botCommandProcessor;

    GitLabNoteMessageHandler(
        GitLabIssueCommentProcessor issueCommentProcessor,
        GitLabDiffNoteWebhookProcessor diffNoteProcessor,
        GitLabWebhookContextResolver contextResolver,
        NatsMessageDeserializer deserializer,
        TransactionTemplate transactionTemplate,
        @Nullable BotCommandProcessor botCommandProcessor
    ) {
        super(GitLabNoteEventDTO.class, deserializer, transactionTemplate);
        this.issueCommentProcessor = issueCommentProcessor;
        this.diffNoteProcessor = diffNoteProcessor;
        this.contextResolver = contextResolver;
        this.botCommandProcessor = botCommandProcessor;
    }

    @Override
    public GitLabEventType getEventType() {
        return GitLabEventType.NOTE;
    }

    @Override
    protected void handleEvent(GitLabNoteEventDTO event) {
        if (event.objectAttributes() == null) {
            log.warn("Received note event with missing object_attributes");
            return;
        }

        if (event.project() == null) {
            log.warn("Received note event with missing project data");
            return;
        }

        // Skip system-generated notes (e.g., "closed this issue", "merged MR !2")
        if (event.isSystemNote()) {
            log.debug("Skipped system note: noteId={}", event.objectAttributes().id());
            return;
        }

        // Skip internal/confidential notes
        if (event.isInternalNote()) {
            log.debug("Skipped internal note: noteId={}", event.objectAttributes().id());
            return;
        }

        // Skip notes on confidential issues
        if (event.isConfidentialIssue()) {
            log.debug("Skipped note on confidential issue: noteId={}", event.objectAttributes().id());
            return;
        }

        String projectPath = event.project().pathWithNamespace();
        if (projectPath == null || projectPath.isBlank()) {
            log.warn("Received note event with missing project path");
            return;
        }
        String safeProjectPath = sanitizeForLog(projectPath);
        GitLabEventAction action = event.actionType();
        String noteableType = event.noteableType();

        if (action == GitLabEventAction.UNKNOWN) {
            log.debug(
                "Skipped note with unknown action: projectPath={}, noteId={}",
                safeProjectPath,
                event.objectAttributes().id()
            );
            return;
        }

        log.info(
            "Processing note event: projectPath={}, noteableType={}, noteId={}, action={}",
            safeProjectPath,
            noteableType,
            event.objectAttributes().id(),
            action
        );

        ProcessingContext context = contextResolver.resolve(projectPath, action.getValue(), "note");
        if (context == null) {
            return;
        }

        // Bot command detection: check for commands like "/hephaestus review" on MR notes.
        // Runs async and does not block normal comment processing below.
        if (
            "MergeRequest".equals(noteableType) &&
            action == GitLabEventAction.CREATE &&
            BotCommandProcessor.isBotCommand(event.objectAttributes().note())
        ) {
            handleBotCommand(event, context, safeProjectPath);
        }

        switch (noteableType) {
            case "Issue" -> issueCommentProcessor.processIssueNote(event, context);
            case "MergeRequest" -> {
                if (event.isDiffNote()) {
                    diffNoteProcessor.processDiffNote(event, context);
                } else {
                    issueCommentProcessor.processMergeRequestNote(event, context);
                }
            }
            case "Commit" -> log.debug(
                "Skipped commit note: projectPath={}, noteId={}",
                safeProjectPath,
                event.objectAttributes().id()
            );
            case null, default -> log.debug(
                "Skipped note with unsupported noteable type: projectPath={}, noteableType={}, noteId={}",
                safeProjectPath,
                noteableType,
                event.objectAttributes().id()
            );
        }
    }

    private void handleBotCommand(GitLabNoteEventDTO event, ProcessingContext context, String safeProjectPath) {
        if (botCommandProcessor == null) {
            log.debug(
                "Bot command ignored (agent NATS not enabled): projectPath={}, noteId={}",
                safeProjectPath,
                event.objectAttributes().id()
            );
            return;
        }

        var mr = event.mergeRequest();
        if (mr == null || mr.iid() == null) {
            log.warn(
                "Bot command on MR note but no embedded merge_request data: projectPath={}, noteId={}",
                safeProjectPath,
                event.objectAttributes().id()
            );
            return;
        }

        if (context.repository() == null) {
            log.warn(
                "Bot command: cannot resolve repository, projectPath={}, noteId={}",
                safeProjectPath,
                event.objectAttributes().id()
            );
            return;
        }

        log.info(
            "Bot command detected: command={}, projectPath={}, mrIid={}, author={}, noteId={}",
            event.objectAttributes().note().strip(),
            safeProjectPath,
            mr.iid(),
            event.user().username(),
            event.objectAttributes().id()
        );

        // Fire-and-forget: processCommand runs @Async in a new transaction
        botCommandProcessor.processCommand(
            context.repository().getId(),
            mr.iid(),
            event.objectAttributes().note(),
            event.user().username()
        );
    }
}
