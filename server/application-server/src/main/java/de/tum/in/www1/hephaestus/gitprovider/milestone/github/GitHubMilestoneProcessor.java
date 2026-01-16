package de.tum.in.www1.hephaestus.gitprovider.milestone.github;

import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContext;
import de.tum.in.www1.hephaestus.gitprovider.common.events.DomainEvent;
import de.tum.in.www1.hephaestus.gitprovider.common.events.EventContext;
import de.tum.in.www1.hephaestus.gitprovider.common.events.EventPayload;
import de.tum.in.www1.hephaestus.gitprovider.milestone.Milestone;
import de.tum.in.www1.hephaestus.gitprovider.milestone.MilestoneRepository;
import de.tum.in.www1.hephaestus.gitprovider.milestone.github.dto.GitHubMilestoneDTO;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.github.dto.GitHubUserDTO;
import java.time.Instant;
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

    private static final Logger log = LoggerFactory.getLogger(GitHubMilestoneProcessor.class);

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
     * @param context processing context with scope information
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
            log.warn(
                "Skipped milestone processing: reason=nullDto, repoId={}",
                repository != null ? repository.getId() : null
            );
            return null;
        }

        // Find existing milestone: prefer ID lookup (from webhooks), fall back to number lookup (for GraphQL)
        Optional<Milestone> existingOpt;
        if (dto.id() != null) {
            existingOpt = milestoneRepository.findById(dto.id());
        } else if (dto.number() > 0) {
            existingOpt = milestoneRepository.findByNumberAndRepositoryId(dto.number(), repository.getId());
        } else {
            log.warn("Skipped milestone processing: reason=missingIdAndNumber, repoId={}", repository.getId());
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
        // Set issue counts if provided
        if (dto.openIssuesCount() != null) {
            milestone.setOpenIssuesCount(dto.openIssuesCount());
        }
        if (dto.closedIssuesCount() != null) {
            milestone.setClosedIssuesCount(dto.closedIssuesCount());
        }
        milestone.setRepository(repository);

        // Set creator if provided
        if (creatorDto != null) {
            User creator = findOrCreateUser(creatorDto);
            milestone.setCreator(creator);
        }

        // Mark sync timestamp
        milestone.setLastSyncAt(Instant.now());

        Milestone saved = milestoneRepository.save(milestone);

        // Publish domain event with EventPayload DTO
        EventPayload.MilestoneData milestoneData = EventPayload.MilestoneData.from(saved);
        EventContext eventContext = EventContext.from(context);
        if (isNew) {
            eventPublisher.publishEvent(new DomainEvent.MilestoneCreated(milestoneData, eventContext));
            log.debug("Created milestone: milestoneId={}, milestoneNumber={}", saved.getId(), saved.getNumber());
        } else {
            eventPublisher.publishEvent(new DomainEvent.MilestoneUpdated(milestoneData, eventContext));
            log.debug("Updated milestone: milestoneId={}, milestoneNumber={}", saved.getId(), saved.getNumber());
        }

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
        // Use bit shifting to combine repo ID and milestone number without collision.
        // The formula repositoryId * 31 + milestoneNumber can produce collisions
        // (e.g., repo=2,milestone=31 and repo=3,milestone=0 both = 62).
        // Bit shifting separates components: repo ID in upper 32 bits, milestone number in lower 32 bits.
        // Negative IDs won't collide with GitHub's positive databaseIds.
        long combined = (repositoryId << 32) | (milestoneNumber & 0xFFFFFFFFL);
        return -combined;
    }

    /**
     * Delete a milestone by its ID.
     *
     * @param milestoneId the milestone database ID
     * @param context processing context with scope information
     */
    @Transactional
    public void delete(Long milestoneId, ProcessingContext context) {
        if (milestoneId == null) {
            return;
        }

        milestoneRepository
            .findById(milestoneId)
            .ifPresent(milestone -> {
                milestoneRepository.delete(milestone);
                eventPublisher.publishEvent(
                    new DomainEvent.MilestoneDeleted(milestoneId, milestone.getTitle(), EventContext.from(context))
                );
                log.info("Deleted milestone: milestoneId={}, milestoneNumber={}", milestoneId, milestone.getNumber());
            });
    }

    /**
     * Converts a GitHub API milestone state string to Milestone.State enum.
     */
    private Milestone.State parseState(String state) {
        if (state == null) {
            return Milestone.State.OPEN;
        }
        return switch (state.toUpperCase()) {
            case "CLOSED" -> Milestone.State.CLOSED;
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
