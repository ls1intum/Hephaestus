package de.tum.in.www1.hephaestus.gitprovider.label.github;

import static de.tum.in.www1.hephaestus.core.LoggingUtils.sanitizeForLog;

import de.tum.in.www1.hephaestus.gitprovider.common.NatsMessageDeserializer;
import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContext;
import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContextFactory;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubEventAction;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubEventType;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubMessageHandler;
import de.tum.in.www1.hephaestus.gitprovider.label.github.dto.GitHubLabelDTO;
import de.tum.in.www1.hephaestus.gitprovider.label.github.dto.GitHubLabelEventDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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
        NatsMessageDeserializer deserializer
    ) {
        super(GitHubLabelEventDTO.class, deserializer);
        this.contextFactory = contextFactory;
        this.labelProcessor = labelProcessor;
    }

    @Override
    public GitHubEventType getEventType() {
        return GitHubEventType.LABEL;
    }

    @Override
    @Transactional
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
            labelProcessor.delete(labelDto.id(), context);
        } else {
            labelProcessor.process(labelDto, context.repository(), context);
        }
    }
}
