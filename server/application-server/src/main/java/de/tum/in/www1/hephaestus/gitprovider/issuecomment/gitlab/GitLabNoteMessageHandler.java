package de.tum.in.www1.hephaestus.gitprovider.issuecomment.gitlab;

import static de.tum.in.www1.hephaestus.core.LoggingUtils.sanitizeForLog;

import de.tum.in.www1.hephaestus.gitprovider.common.NatsMessageDeserializer;
import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContext;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabEventAction;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabEventType;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabMessageHandler;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabWebhookContextResolver;
import de.tum.in.www1.hephaestus.gitprovider.issuecomment.gitlab.dto.GitLabNoteEventDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/** Handles GitLab note webhook events, routing to the appropriate processor by noteable type. */
@Component
@ConditionalOnProperty(prefix = "hephaestus.gitlab", name = "enabled", havingValue = "true")
public class GitLabNoteMessageHandler extends GitLabMessageHandler<GitLabNoteEventDTO> {

    private static final Logger log = LoggerFactory.getLogger(GitLabNoteMessageHandler.class);

    private final GitLabIssueCommentProcessor issueCommentProcessor;
    private final GitLabWebhookContextResolver contextResolver;

    GitLabNoteMessageHandler(
        GitLabIssueCommentProcessor issueCommentProcessor,
        GitLabWebhookContextResolver contextResolver,
        NatsMessageDeserializer deserializer,
        TransactionTemplate transactionTemplate
    ) {
        super(GitLabNoteEventDTO.class, deserializer, transactionTemplate);
        this.issueCommentProcessor = issueCommentProcessor;
        this.contextResolver = contextResolver;
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

        switch (noteableType) {
            case "Issue" -> issueCommentProcessor.processIssueNote(event, context);
            case "MergeRequest" -> {
                if (event.isDiffNote()) {
                    log.debug(
                        "Skipped diff note: projectPath={}, noteId={}",
                        safeProjectPath,
                        event.objectAttributes().id()
                    );
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
}
