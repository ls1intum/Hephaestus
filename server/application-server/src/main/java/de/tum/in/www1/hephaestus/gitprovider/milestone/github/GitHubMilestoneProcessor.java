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
 * <p>
 * <b>Note on Milestone IDs:</b>
 * GitHub's GraphQL API does not expose databaseId for milestones.
 * This processor supports both ID-based lookup (for webhook events which provide databaseId)
 * and number-based lookup (for GraphQL sync where only number is reliably available).
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
        ApplicationEventPublisher eventPublisher
    ) {
        this.milestoneRepository = milestoneRepository;
        this.userRepository = userRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Process a GitHub milestone DTO and persist it as a Milestone entity.
     * Uses ID-based lookup when id is available (webhooks), otherwise falls back to number-based lookup.
     * <p>
     * For new milestones from GraphQL (which doesn't provide databaseId), a deterministic ID is generated
     * from the repository ID and milestone number to ensure consistency.
     *
     * @param dto the GitHub milestone DTO
     * @param repository the repository this milestone belongs to
     * @param creatorDto optional creator user DTO
     * @param context processing context with workspace information
     * @return the persisted Milestone entity
     */
    @Transactional
    public Milestone process(
        GitHubMilestoneDTO dto,
        Repository repository,
        @Nullable GitHubUserDTO creatorDto,
        ProcessingContext context
    ) {
        if (dto == null) {
            logger.warn("Milestone DTO is null, skipping");
            return null;
        }

        // Find existing milestone: prefer ID lookup (from webhooks), fall back to number lookup (for GraphQL)
        Optional<Milestone> existingOpt;
        if (dto.id() != null) {
            existingOpt = milestoneRepository.findById(dto.id());
        } else if (dto.number() > 0) {
            existingOpt = milestoneRepository.findByNumberAndRepositoryId(dto.number(), repository.getId());
        } else {
            logger.warn("Milestone DTO is missing both id and number, skipping");
            return null;
        }
        boolean isNew = existingOpt.isEmpty();

        Milestone milestone = existingOpt.orElseGet(Milestone::new);

        // Set or update fields
        // For existing entities, only update non-null DTO values (preserve existing data)
        // For new entities, set all fields
        if (dto.id() != null) {
            milestone.setId(dto.id());
        } else if (isNew) {
            // Generate deterministic ID for new milestones from GraphQL (which doesn't provide databaseId)
            milestone.setId(generateDeterministicId(repository.getId(), dto.number()));
        }
        if (dto.number() > 0) {
            milestone.setNumber(dto.number());
        }
        if (dto.title() != null) {
            milestone.setTitle(dto.title());
        }
        if (isNew || dto.description() != null) {
            milestone.setDescription(dto.description());
        }
        if (dto.htmlUrl() != null) {
            milestone.setHtmlUrl(dto.htmlUrl());
        }
        if (isNew || dto.dueOn() != null) {
            milestone.setDueOn(dto.dueOn());
        }
        if (dto.state() != null) {
            milestone.setState(parseState(dto.state()));
        } else if (isNew) {
            // Default to OPEN for new milestones without state
            milestone.setState(Milestone.State.OPEN);
        }
        milestone.setRepository(repository);

        // Set creator if provided
        if (creatorDto != null) {
            User creator = findOrCreateUser(creatorDto);
            milestone.setCreator(creator);
        }

        Milestone saved = milestoneRepository.save(milestone);

        // Publish domain event
        eventPublisher.publishEvent(
            new EntityEvents.MilestoneProcessed(saved, isNew, context.workspaceId(), repository.getId())
        );

        logger.debug("Processed milestone {} ({}): {}", saved.getTitle(), saved.getId(), isNew ? "created" : "updated");
        return saved;
    }

    /**
     * Generates a deterministic ID for milestones synced via GraphQL.
     * GitHub's GraphQL API doesn't expose databaseId for milestones, so we generate
     * a consistent ID based on the repository ID and milestone number.
     * <p>
     * The generated ID is negative to avoid collision with actual GitHub databaseIds.
     *
     * @param repositoryId the repository's database ID
     * @param milestoneNumber the milestone's number
     * @return a deterministic negative Long ID
     */
    private Long generateDeterministicId(Long repositoryId, int milestoneNumber) {
        // Use a combination of repo ID and milestone number to generate a unique negative ID
        // Negative IDs won't collide with GitHub's positive databaseIds
        long hash = repositoryId * 31L + milestoneNumber;
        return -Math.abs(hash);
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

        milestoneRepository
            .findById(milestoneId)
            .ifPresent(milestone -> {
                Long repoId = milestone.getRepository() != null ? milestone.getRepository().getId() : null;
                milestoneRepository.delete(milestone);
                eventPublisher.publishEvent(
                    new EntityEvents.MilestoneDeleted(milestoneId, milestone.getTitle(), context.workspaceId(), repoId)
                );
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
