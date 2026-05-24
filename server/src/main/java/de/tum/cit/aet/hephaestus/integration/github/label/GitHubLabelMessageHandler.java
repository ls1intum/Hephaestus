package de.tum.cit.aet.hephaestus.integration.github.label;

import static de.tum.cit.aet.hephaestus.core.LoggingUtils.sanitizeForLog;

import de.tum.cit.aet.hephaestus.gitprovider.common.NatsMessageDeserializer;
import de.tum.cit.aet.hephaestus.gitprovider.common.ProcessingContext;
import de.tum.cit.aet.hephaestus.gitprovider.common.ProcessingContextFactory;
import de.tum.cit.aet.hephaestus.integration.github.common.GitHubEventAction;
import de.tum.cit.aet.hephaestus.integration.github.common.GitHubEventType;
import de.tum.cit.aet.hephaestus.integration.github.common.GitHubMessageHandler;
import de.tum.cit.aet.hephaestus.integration.github.label.dto.GitHubLabelDTO;
import de.tum.cit.aet.hephaestus.integration.github.label.dto.GitHubLabelEventDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Handles GitHub label webhook events.
 */
@Component
public class GitHubLabelMessageHandler extends GitHubMessageHandler<GitHubLabelEventDTO> {

    private static final Logger log = LoggerFactory.getLogger(GitHubLabelMessageHandler.class);

    private final ProcessingContextFactory contextFactory;
    private final GitHubLabelProcessor labelProcessor;

    GitHubLabelMessageHandler(
        ProcessingContextFactory contextFactory,
        GitHubLabelProcessor labelProcessor,
        NatsMessageDeserializer deserializer,
        TransactionTemplate transactionTemplate
    ) {
        super(GitHubLabelEventDTO.class, deserializer, transactionTemplate);
        this.contextFactory = contextFactory;
        this.labelProcessor = labelProcessor;
    }

    @Override
    public GitHubEventType getEventType() {
        return GitHubEventType.LABEL;
    }

    @Override
    protected void handleEvent(GitHubLabelEventDTO event) {
        GitHubLabelDTO labelDto = event.label();

        if (labelDto == null) {
            log.warn("Received label event with missing data: action={}", event.action());
            return;
        }

        log.info(
            "Received label event: action={}, labelName={}, repoName={}",
            event.action(),
            sanitizeForLog(labelDto.name()),
            event.repository() != null ? sanitizeForLog(event.repository().fullName()) : "unknown"
        );

        ProcessingContext context = contextFactory.forWebhookEvent(event).orElse(null);
        if (context == null) {
            return;
        }

        if (event.actionType() == GitHubEventAction.Label.DELETED) {
            labelProcessor.deleteByNativeId(labelDto.id(), context);
        } else {
            labelProcessor.process(labelDto, context.repository(), context);
        }
    }
}
