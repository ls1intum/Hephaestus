package de.tum.in.www1.hephaestus.gitprovider.milestone.github;

import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContext;
import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContextFactory;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubMessageHandler;
import de.tum.in.www1.hephaestus.gitprovider.milestone.Milestone;
import de.tum.in.www1.hephaestus.gitprovider.milestone.MilestoneRepository;
import de.tum.in.www1.hephaestus.gitprovider.milestone.github.dto.GitHubMilestoneDTO;
import de.tum.in.www1.hephaestus.gitprovider.milestone.github.dto.GitHubMilestoneEventDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles GitHub milestone webhook events.
 * <p>
 * Uses DTOs directly (no hub4j) for complete field coverage.
 */
@Component
public class GitHubMilestoneMessageHandler extends GitHubMessageHandler<GitHubMilestoneEventDTO> {

    private static final Logger logger = LoggerFactory.getLogger(GitHubMilestoneMessageHandler.class);

    private final ProcessingContextFactory contextFactory;
    private final MilestoneRepository milestoneRepository;

    GitHubMilestoneMessageHandler(ProcessingContextFactory contextFactory, MilestoneRepository milestoneRepository) {
        super(GitHubMilestoneEventDTO.class);
        this.contextFactory = contextFactory;
        this.milestoneRepository = milestoneRepository;
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
            milestoneRepository.deleteById(milestoneDto.id());
        } else {
            processMilestone(milestoneDto, context);
        }
    }

    private Milestone processMilestone(GitHubMilestoneDTO dto, ProcessingContext context) {
        return milestoneRepository
            .findById(dto.id())
            .map(milestone -> {
                if (dto.title() != null) milestone.setTitle(dto.title());
                if (dto.description() != null) milestone.setDescription(dto.description());
                if (dto.state() != null) milestone.setState(mapState(dto.state()));
                milestone.setDueOn(dto.dueOn());
                return milestoneRepository.save(milestone);
            })
            .orElseGet(() -> {
                Milestone milestone = new Milestone();
                milestone.setId(dto.id());
                milestone.setNumber(dto.number());
                milestone.setTitle(dto.title());
                milestone.setDescription(dto.description());
                milestone.setState(mapState(dto.state()));
                milestone.setDueOn(dto.dueOn());
                milestone.setRepository(context.repository());
                return milestoneRepository.save(milestone);
            });
    }

    private Milestone.State mapState(String state) {
        if (state == null) return Milestone.State.OPEN;
        return switch (state.toLowerCase()) {
            case "closed" -> Milestone.State.CLOSED;
            default -> Milestone.State.OPEN;
        };
    }
}
