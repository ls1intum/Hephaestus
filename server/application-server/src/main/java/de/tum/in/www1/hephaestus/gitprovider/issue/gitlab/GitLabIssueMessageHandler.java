package de.tum.in.www1.hephaestus.gitprovider.issue.gitlab;

import static de.tum.in.www1.hephaestus.core.LoggingUtils.sanitizeForLog;

import de.tum.in.www1.hephaestus.gitprovider.common.NatsMessageDeserializer;
import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContext;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabEventAction;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabEventType;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabMessageHandler;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabWebhookContextResolver;
import de.tum.in.www1.hephaestus.gitprovider.issue.gitlab.dto.GitLabIssueEventDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Handles GitLab issue webhook events.
 * <p>
 * Processes both {@code event_type: "issue"} and {@code event_type: "confidential_issue"} payloads.
 * Both arrive on the same NATS subject ({@code object_kind: "issue"}).
 * <p>
 * Confidential issues are skipped entirely — they are never stored in the database.
 * <p>
 * Routes to {@link GitLabIssueProcessor} based on the action:
 * <ul>
 *   <li>{@code open} / {@code update} → {@link GitLabIssueProcessor#process}</li>
 *   <li>{@code close} → {@link GitLabIssueProcessor#processClosed}</li>
 *   <li>{@code reopen} → {@link GitLabIssueProcessor#processReopened}</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(prefix = "hephaestus.gitlab", name = "enabled", havingValue = "true")
public class GitLabIssueMessageHandler extends GitLabMessageHandler<GitLabIssueEventDTO> {

    private static final Logger log = LoggerFactory.getLogger(GitLabIssueMessageHandler.class);

    private final GitLabIssueProcessor issueProcessor;
    private final GitLabWebhookContextResolver contextResolver;

    GitLabIssueMessageHandler(
        GitLabIssueProcessor issueProcessor,
        GitLabWebhookContextResolver contextResolver,
        NatsMessageDeserializer deserializer,
        TransactionTemplate transactionTemplate
    ) {
        super(GitLabIssueEventDTO.class, deserializer, transactionTemplate);
        this.issueProcessor = issueProcessor;
        this.contextResolver = contextResolver;
    }

    @Override
    public GitLabEventType getEventType() {
        return GitLabEventType.ISSUE;
    }

    @Override
    protected void handleEvent(GitLabIssueEventDTO event) {
        if (event.objectAttributes() == null) {
            log.warn("Received issue event with missing object_attributes");
            return;
        }

        if (event.project() == null) {
            log.warn("Received issue event with missing project data");
            return;
        }

        // Skip confidential issues entirely
        if (event.isConfidential()) {
            log.debug("Skipped confidential issue event: iid={}", event.objectAttributes().iid());
            return;
        }

        String projectPath = event.project().pathWithNamespace();
        if (projectPath == null || projectPath.isBlank()) {
            log.warn("Received issue event with missing project path");
            return;
        }
        String safeProjectPath = sanitizeForLog(projectPath);
        GitLabEventAction action = event.actionType();

        log.info(
            "Processing issue event: projectPath={}, iid={}, action={}",
            safeProjectPath,
            event.objectAttributes().iid(),
            action
        );

        ProcessingContext context = contextResolver.resolve(projectPath, action.getValue(), "issue");
        if (context == null) {
            return;
        }

        switch (action) {
            case OPEN, UPDATE -> issueProcessor.process(event, context);
            case CLOSE -> issueProcessor.processClosed(event, context);
            case REOPEN -> issueProcessor.processReopened(event, context);
            default -> log.debug("Unhandled issue action: projectPath={}, action={}", safeProjectPath, action);
        }
    }
}
