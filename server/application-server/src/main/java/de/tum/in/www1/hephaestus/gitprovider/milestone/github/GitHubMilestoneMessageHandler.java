package de.tum.in.www1.hephaestus.gitprovider.milestone.github;

import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContext;
import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContextFactory;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubMessageHandler;
import de.tum.in.www1.hephaestus.gitprovider.milestone.github.dto.GitHubMilestoneDTO;
import de.tum.in.www1.hephaestus.gitprovider.milestone.github.dto.GitHubMilestoneEventDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles GitHub milestone webhook events.
 * <p>
 * Uses DTOs directly and delegates to {@link GitHubMilestoneProcessor}
 * for processing, ensuring a single source of truth for milestone processing logic.
 */
@Component
public class GitHubMilestoneMessageHandler extends GitHubMessageHandler<GitHubMilestoneEventDTO> {

    private static final Logger logger = LoggerFactory.getLogger(GitHubMilestoneMessageHandler.class);

    private final ProcessingContextFactory contextFactory;
    private final GitHubMilestoneProcessor milestoneProcessor;

    GitHubMilestoneMessageHandler(
        ProcessingContextFactory contextFactory,
        GitHubMilestoneProcessor milestoneProcessor
    ) {
        super(GitHubMilestoneEventDTO.class);
        this.contextFactory = contextFactory;
        this.milestoneProcessor = milestoneProcessor;
    }

    @Override
    protected String getEventKey() {
        return "milestone";
    }

    @Override
    @Transactional
    protected void handleEvent(GitHubMilestoneEventDTO event) {
        GitHubMilestoneDTO milestoneDto = event.milestone();

        if (milestoneDto == null) {
            logger.warn("Received milestone event with missing data");
            return;
        }

        logger.info(
            "Received milestone event: action={}, milestone={}, repo={}",
            event.action(),
            milestoneDto.title(),
            event.repository() != null ? event.repository().fullName() : "unknown"
        );

        ProcessingContext context = contextFactory.forWebhookEvent(event).orElse(null);
        if (context == null) {
            return;
        }

        if ("deleted".equals(event.action())) {
            milestoneProcessor.delete(milestoneDto.id(), context);
        } else {
            milestoneProcessor.process(milestoneDto, context.repository(), null, context);
        }
    }
}
