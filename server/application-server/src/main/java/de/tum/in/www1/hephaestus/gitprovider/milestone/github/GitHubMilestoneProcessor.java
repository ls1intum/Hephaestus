package de.tum.in.www1.hephaestus.gitprovider.milestone.github;

import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContext;
import de.tum.in.www1.hephaestus.gitprovider.common.events.EntityEvents;
import de.tum.in.www1.hephaestus.gitprovider.milestone.Milestone;
import de.tum.in.www1.hephaestus.gitprovider.milestone.MilestoneRepository;
import de.tum.in.www1.hephaestus.gitprovider.milestone.github.dto.GitHubMilestoneDTO;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.github.dto.GitHubUserDTO;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Processor for GitHub milestones.
 * <p>
 * This service handles the conversion of GitHubMilestoneDTO to Milestone entities,
 * persists them, and publishes appropriate domain events.
 */
@Service
public class GitHubMilestoneProcessor {

    private static final Logger logger = LoggerFactory.getLogger(GitHubMilestoneProcessor.class);

    private final MilestoneRepository milestoneRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    public GitHubMilestoneProcessor(
            MilestoneRepository milestoneRepository,
            UserRepository userRepository,
            ApplicationEventPublisher eventPublisher) {
        this.milestoneRepository = milestoneRepository;
        this.userRepository = userRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Process a GitHub milestone DTO and persist it as a Milestone entity.
     *
     * @param dto the GitHub milestone DTO
     * @param repository the repository this milestone belongs to
     * @param creatorDto optional creator user DTO
     * @param context processing context with workspace information
     * @return the persisted Milestone entity
     */
    @Transactional
    public Milestone process(GitHubMilestoneDTO dto, Repository repository, 
            @Nullable GitHubUserDTO creatorDto, ProcessingContext context) {
        if (dto == null || dto.id() == null) {
            logger.warn("Milestone DTO is null or missing id, skipping");
            return null;
        }

        Optional<Milestone> existingOpt = milestoneRepository.findById(dto.id());
        boolean isNew = existingOpt.isEmpty();

        Milestone milestone = existingOpt.orElseGet(Milestone::new);

        // Set or update fields
        milestone.setId(dto.id());
        milestone.setNumber(dto.number());
        milestone.setTitle(dto.title());
        milestone.setDescription(dto.description());
        milestone.setHtmlUrl(dto.htmlUrl());
        milestone.setDueOn(dto.dueOn());
        milestone.setState(parseState(dto.state()));
        milestone.setRepository(repository);

        // Set creator if provided
        if (creatorDto != null) {
            User creator = findOrCreateUser(creatorDto);
            milestone.setCreator(creator);
        }

        Milestone saved = milestoneRepository.save(milestone);

        // Publish domain event
        eventPublisher.publishEvent(new EntityEvents.MilestoneProcessed(
                saved,
                isNew,
                context.workspaceId(),
                repository.getId()));

        logger.debug("Processed milestone {} ({}): {}", saved.getTitle(), saved.getId(), isNew ? "created" : "updated");
        return saved;
    }

    /**
     * Delete a milestone by its ID.
     *
     * @param milestoneId the milestone database ID
     * @param context processing context with workspace information
     */
    @Transactional
    public void delete(Long milestoneId, ProcessingContext context) {
        if (milestoneId == null) {
            return;
        }

        milestoneRepository.findById(milestoneId).ifPresent(milestone -> {
            Long repoId = milestone.getRepository() != null ? milestone.getRepository().getId() : null;
            milestoneRepository.delete(milestone);
            eventPublisher.publishEvent(new EntityEvents.MilestoneDeleted(
                    milestoneId,
                    milestone.getTitle(),
                    context.workspaceId(),
                    repoId));
            logger.debug("Deleted milestone {} ({})", milestone.getTitle(), milestoneId);
        });
    }

    private Milestone.State parseState(String state) {
        if (state == null) {
            return Milestone.State.OPEN;
        }
        return switch (state.toLowerCase()) {
            case "closed" -> Milestone.State.CLOSED;
            default -> Milestone.State.OPEN;
        };
    }

    @Nullable
    private User findOrCreateUser(GitHubUserDTO dto) {
        if (dto == null) {
            return null;
        }
        Long userId = dto.getDatabaseId();
        if (userId == null) {
            return null;
        }
        return userRepository
                .findById(userId)
                .orElseGet(() -> {
                    User user = new User();
                    user.setId(userId);
                    user.setLogin(dto.login());
                    user.setAvatarUrl(dto.avatarUrl());
                    // Use login as fallback for name if null (name is @NonNull)
                    user.setName(dto.name() != null ? dto.name() : dto.login());
                    return userRepository.save(user);
                });
    }
}
