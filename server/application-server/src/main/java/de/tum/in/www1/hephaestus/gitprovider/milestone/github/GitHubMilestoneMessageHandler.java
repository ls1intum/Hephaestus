package de.tum.in.www1.hephaestus.gitprovider.milestone.github;

import static de.tum.in.www1.hephaestus.core.LoggingUtils.sanitizeForLog;

import de.tum.in.www1.hephaestus.gitprovider.common.NatsMessageDeserializer;
import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContext;
import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContextFactory;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubEventAction;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubEventType;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubMessageHandler;
import de.tum.in.www1.hephaestus.gitprovider.milestone.github.dto.GitHubMilestoneDTO;
import de.tum.in.www1.hephaestus.gitprovider.milestone.github.dto.GitHubMilestoneEventDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles GitHub milestone webhook events.
 */
@Component
public class GitHubMilestoneMessageHandler extends GitHubMessageHandler<GitHubMilestoneEventDTO> {

    private static final Logger log = LoggerFactory.getLogger(GitHubMilestoneMessageHandler.class);

    private final ProcessingContextFactory contextFactory;
    private final GitHubMilestoneProcessor milestoneProcessor;

    GitHubMilestoneMessageHandler(
        ProcessingContextFactory contextFactory,
        GitHubMilestoneProcessor milestoneProcessor,
        NatsMessageDeserializer deserializer
    ) {
        super(GitHubMilestoneEventDTO.class, deserializer);
        this.contextFactory = contextFactory;
        this.milestoneProcessor = milestoneProcessor;
    }

    @Override
    public GitHubEventType getEventType() {
        return GitHubEventType.MILESTONE;
    }

    @Override
    @Transactional
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
