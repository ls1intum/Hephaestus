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
 * GitHub's GraphQL API does not expose databaseId for milestones, while webhooks do.
 * To avoid duplicates, this processor always uses number-based lookup within a repository
 * (milestone numbers are unique per repository). When the real GitHub databaseId is available
 * from webhooks, it updates the entity ID accordingly.
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
     * <p>
     * <b>IMPORTANT:</b> Always uses number-based lookup within the repository to find existing milestones.
     * This ensures that milestones created via webhooks (with real GitHub databaseId) and milestones
     * synced via GraphQL (which doesn't provide databaseId) are treated as the same entity.
     * <p>
     * When the real GitHub databaseId is available (from webhooks), it will be used as the entity ID
     * and will replace any previously generated deterministic ID.
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

        if (dto.number() <= 0) {
            log.warn("Skipped milestone processing: reason=invalidNumber, repoId={}", repository.getId());
            return null;
        }

        // Always look up by number within the repository to avoid duplicates.
        // The milestone number is unique within a repository and is the canonical identifier.
        Optional<Milestone> existingOpt = milestoneRepository.findByNumberAndRepositoryId(
            dto.number(),
            repository.getId()
        );
        boolean isNew = existingOpt.isEmpty();

        Milestone milestone = existingOpt.orElseGet(Milestone::new);

        // Handle ID assignment:
        // - If we have the real GitHub databaseId (from webhooks), always use it
        // - If existing milestone has a different ID (e.g., generated deterministic ID),
        //   delete it and create new with the real ID (Hibernate doesn't allow changing entity IDs)
        // - For new milestones without databaseId (GraphQL), generate a deterministic ID
        if (dto.id() != null) {
            // We have the real GitHub databaseId
            if (!isNew && !dto.id().equals(milestone.getId())) {
                // Existing milestone has a different ID (likely a generated deterministic ID).
                // Hibernate doesn't allow changing entity IDs, so we must delete and recreate.
                log.info(
                    "Migrating milestone from generated to real ID: oldId={}, newId={}, milestoneNumber={}",
                    milestone.getId(),
                    dto.id(),
                    dto.number()
                );
                milestoneRepository.delete(milestone);
                milestoneRepository.flush();
                milestone = new Milestone();
                isNew = true;
            }
            milestone.setId(dto.id());
        } else if (isNew) {
            // New milestone from GraphQL (no databaseId) - generate deterministic ID
            milestone.setId(generateDeterministicId(repository.getId(), dto.number()));
        }
        // else: existing milestone, keep its current ID (whether real or generated)
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
        // Set timestamps if provided
        if (dto.createdAt() != null) {
            milestone.setCreatedAt(dto.createdAt());
        }
        if (dto.updatedAt() != null) {
            milestone.setUpdatedAt(dto.updatedAt());
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
