package de.tum.cit.aet.hephaestus.integration.gitlab.issue;

import static de.tum.cit.aet.hephaestus.core.LoggingUtils.sanitizeForLog;

import de.tum.cit.aet.hephaestus.integration.gitlab.common.GitLabEventAction;
import de.tum.cit.aet.hephaestus.integration.gitlab.common.GitLabEventType;
import de.tum.cit.aet.hephaestus.integration.gitlab.common.GitLabWebhookContextResolver;
import de.tum.cit.aet.hephaestus.integration.gitlab.issue.dto.GitLabIssueEventDTO;
import de.tum.cit.aet.hephaestus.integration.core.handler.AbstractIntegrationMessageHandler;
import de.tum.cit.aet.hephaestus.integration.scm.common.NatsMessageDeserializer;
import de.tum.cit.aet.hephaestus.integration.scm.common.ProcessingContext;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
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
public class GitLabIssueMessageHandler extends AbstractIntegrationMessageHandler<GitLabIssueEventDTO> {

    private static final Logger log = LoggerFactory.getLogger(GitLabIssueMessageHandler.class);

    private final GitLabIssueProcessor issueProcessor;
    private final GitLabWebhookContextResolver contextResolver;

    GitLabIssueMessageHandler(
        GitLabIssueProcessor issueProcessor,
        GitLabWebhookContextResolver contextResolver,
        NatsMessageDeserializer deserializer,
        TransactionTemplate transactionTemplate
    ) {
        super(
            IntegrationKind.GITLAB,
            GitLabEventType.ISSUE.getValue(),
            GitLabIssueEventDTO.class,
            deserializer,
            transactionTemplate
        );
        this.issueProcessor = issueProcessor;
        this.contextResolver = contextResolver;
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
