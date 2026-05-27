package de.tum.cit.aet.hephaestus.integration.scm.github.milestone;

import static de.tum.cit.aet.hephaestus.core.LoggingUtils.sanitizeForLog;

import de.tum.cit.aet.hephaestus.integration.scm.github.common.GitHubEventAction;
import de.tum.cit.aet.hephaestus.integration.scm.github.common.GitHubEventType;
import de.tum.cit.aet.hephaestus.integration.scm.github.milestone.dto.GitHubMilestoneDTO;
import de.tum.cit.aet.hephaestus.integration.scm.github.milestone.dto.GitHubMilestoneEventDTO;
import de.tum.cit.aet.hephaestus.integration.core.handler.AbstractIntegrationMessageHandler;
import de.tum.cit.aet.hephaestus.integration.scm.common.NatsMessageDeserializer;
import de.tum.cit.aet.hephaestus.integration.scm.common.ProcessingContext;
import de.tum.cit.aet.hephaestus.integration.scm.github.common.ProcessingContextFactory;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Handles GitHub milestone webhook events.
 */
@Component
public class GitHubMilestoneMessageHandler extends AbstractIntegrationMessageHandler<GitHubMilestoneEventDTO> {

    private static final Logger log = LoggerFactory.getLogger(GitHubMilestoneMessageHandler.class);

    private final ProcessingContextFactory contextFactory;
    private final GitHubMilestoneProcessor milestoneProcessor;

    GitHubMilestoneMessageHandler(
        ProcessingContextFactory contextFactory,
        GitHubMilestoneProcessor milestoneProcessor,
        NatsMessageDeserializer deserializer,
        TransactionTemplate transactionTemplate
    ) {
        super(
            IntegrationKind.GITHUB,
            "repository." + GitHubEventType.MILESTONE.getValue(),
            GitHubMilestoneEventDTO.class,
            deserializer,
            transactionTemplate
        );
        this.contextFactory = contextFactory;
        this.milestoneProcessor = milestoneProcessor;
    }

    @Override
    protected void handleEvent(GitHubMilestoneEventDTO event) {
        GitHubMilestoneDTO milestoneDto = event.milestone();

        if (milestoneDto == null) {
            log.warn("Received milestone event with missing data: action={}", event.action());
            return;
        }

        log.info(
            "Received milestone event: action={}, milestoneTitle={}, repoName={}",
            event.action(),
            sanitizeForLog(milestoneDto.title()),
            event.repository() != null ? sanitizeForLog(event.repository().fullName()) : "unknown"
        );

        ProcessingContext context = contextFactory.forWebhookEvent(event).orElse(null);
        if (context == null) {
            return;
        }

        if (event.actionType() == GitHubEventAction.Milestone.DELETED) {
            milestoneProcessor.delete(milestoneDto.id(), context);
        } else {
            milestoneProcessor.process(milestoneDto, context.repository(), null, context);
        }
    }
}
