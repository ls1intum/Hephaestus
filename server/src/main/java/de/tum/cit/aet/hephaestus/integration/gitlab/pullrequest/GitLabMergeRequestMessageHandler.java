package de.tum.cit.aet.hephaestus.integration.gitlab.pullrequest;

import static de.tum.cit.aet.hephaestus.core.LoggingUtils.sanitizeForLog;

import de.tum.cit.aet.hephaestus.integration.gitlab.common.GitLabEventAction;
import de.tum.cit.aet.hephaestus.integration.gitlab.common.GitLabEventType;
import de.tum.cit.aet.hephaestus.integration.gitlab.common.GitLabWebhookContextResolver;
import de.tum.cit.aet.hephaestus.integration.gitlab.pullrequest.dto.GitLabMergeRequestEventDTO;
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
 * Handles GitLab merge request webhook events.
 * <p>
 * Routes to {@link GitLabMergeRequestProcessor} based on the action:
 * <ul>
 *   <li>{@code open} / {@code update} → {@link GitLabMergeRequestProcessor#process}</li>
 *   <li>{@code close} → {@link GitLabMergeRequestProcessor#processClosed}</li>
 *   <li>{@code reopen} → {@link GitLabMergeRequestProcessor#processReopened}</li>
 *   <li>{@code merge} → {@link GitLabMergeRequestProcessor#processMerged}</li>
 *   <li>{@code approved} → {@link GitLabMergeRequestProcessor#processApproved}</li>
 *   <li>{@code unapproved} → {@link GitLabMergeRequestProcessor#processUnapproved}</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(prefix = "hephaestus.gitlab", name = "enabled", havingValue = "true")
public class GitLabMergeRequestMessageHandler extends AbstractIntegrationMessageHandler<GitLabMergeRequestEventDTO> {

    private static final Logger log = LoggerFactory.getLogger(GitLabMergeRequestMessageHandler.class);

    private final GitLabMergeRequestProcessor mergeRequestProcessor;
    private final GitLabWebhookContextResolver contextResolver;

    GitLabMergeRequestMessageHandler(
        GitLabMergeRequestProcessor mergeRequestProcessor,
        GitLabWebhookContextResolver contextResolver,
        NatsMessageDeserializer deserializer,
        TransactionTemplate transactionTemplate
    ) {
        super(
            IntegrationKind.GITLAB,
            GitLabEventType.MERGE_REQUEST.getValue(),
            GitLabMergeRequestEventDTO.class,
            deserializer,
            transactionTemplate
        );
        this.mergeRequestProcessor = mergeRequestProcessor;
        this.contextResolver = contextResolver;
    }

    @Override
    protected void handleEvent(GitLabMergeRequestEventDTO event) {
        if (event.objectAttributes() == null) {
            log.warn("Received merge request event with missing object_attributes");
            return;
        }

        if (event.project() == null) {
            log.warn("Received merge request event with missing project data");
            return;
        }

        if (event.isConfidential()) {
            log.debug("Skipped confidential merge request event: iid={}", event.objectAttributes().iid());
            return;
        }

        String projectPath = event.project().pathWithNamespace();
        if (projectPath == null || projectPath.isBlank()) {
            log.warn("Received merge request event with missing project path");
            return;
        }
        String safeProjectPath = sanitizeForLog(projectPath);
        GitLabEventAction action = event.actionType();

        log.info(
            "Processing merge request event: projectPath={}, iid={}, action={}",
            safeProjectPath,
            event.objectAttributes().iid(),
            action
        );

        ProcessingContext context = contextResolver.resolve(projectPath, action.getValue(), "merge request");
        if (context == null) {
            return;
        }

        switch (action) {
            case OPEN, UPDATE -> mergeRequestProcessor.process(event, context);
            case CLOSE -> mergeRequestProcessor.processClosed(event, context);
            case REOPEN -> mergeRequestProcessor.processReopened(event, context);
            case MERGE -> mergeRequestProcessor.processMerged(event, context);
            case APPROVED -> mergeRequestProcessor.processApproved(event, context);
            case UNAPPROVED -> mergeRequestProcessor.processUnapproved(event, context);
            case APPROVAL, UNAPPROVAL -> log.debug(
                "Skipped group-level approval rule event: projectPath={}, action={}",
                safeProjectPath,
                action
            );
            default -> log.debug("Unhandled merge request action: projectPath={}, action={}", safeProjectPath, action);
        }
    }
}
