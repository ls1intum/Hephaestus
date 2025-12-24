package de.tum.in.www1.hephaestus.gitprovider.label.github;

import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContext;
import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContextFactory;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubMessageHandler;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubWebhookAction;
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

    private static final Logger logger = LoggerFactory.getLogger(GitHubLabelMessageHandler.class);

    private final ProcessingContextFactory contextFactory;
    private final GitHubLabelProcessor labelProcessor;

    GitHubLabelMessageHandler(ProcessingContextFactory contextFactory, GitHubLabelProcessor labelProcessor) {
        super(GitHubLabelEventDTO.class);
        this.contextFactory = contextFactory;
        this.labelProcessor = labelProcessor;
    }

    @Override
    protected String getEventKey() {
        return "label";
    }

    @Override
    @Transactional
    protected void handleEvent(GitHubLabelEventDTO event) {
        GitHubLabelDTO labelDto = event.label();

        if (labelDto == null) {
            logger.warn("Received label event with missing data");
            return;
        }

        logger.info(
            "Received label event: action={}, label={}, repo={}",
            event.action(),
            labelDto.name(),
            event.repository() != null ? event.repository().fullName() : "unknown"
        );

        ProcessingContext context = contextFactory.forWebhookEvent(event).orElse(null);
        if (context == null) {
            return;
        }

        if (event.isAction(GitHubWebhookAction.DELETED)) {
            labelProcessor.delete(labelDto.id(), context);
        } else {
            labelProcessor.process(labelDto, context.repository(), context);
        }
    }
}
