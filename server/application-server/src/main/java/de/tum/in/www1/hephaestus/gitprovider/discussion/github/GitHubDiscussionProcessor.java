package de.tum.in.www1.hephaestus.gitprovider.discussion.github;

import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContext;
import de.tum.in.www1.hephaestus.gitprovider.common.events.DomainEvent;
import de.tum.in.www1.hephaestus.gitprovider.common.events.EventContext;
import de.tum.in.www1.hephaestus.gitprovider.common.events.EventPayload;
import de.tum.in.www1.hephaestus.gitprovider.common.github.BaseGitHubProcessor;
import de.tum.in.www1.hephaestus.gitprovider.discussion.Discussion;
import de.tum.in.www1.hephaestus.gitprovider.discussion.DiscussionCategory;
import de.tum.in.www1.hephaestus.gitprovider.discussion.DiscussionCategoryRepository;
import de.tum.in.www1.hephaestus.gitprovider.discussion.DiscussionRepository;
import de.tum.in.www1.hephaestus.gitprovider.discussion.github.dto.GitHubDiscussionCategoryDTO;
import de.tum.in.www1.hephaestus.gitprovider.discussion.github.dto.GitHubDiscussionDTO;
import de.tum.in.www1.hephaestus.gitprovider.label.LabelRepository;
import de.tum.in.www1.hephaestus.gitprovider.milestone.MilestoneRepository;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import java.time.Instant;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Processor for GitHub Discussions.
 * <p>
 * This service handles the conversion of GitHubDiscussionDTO to Discussion entities,
 * persists them, and manages related entities (categories, labels).
 * <p>
 * <b>Design Principles:</b>
 * <ul>
 * <li>Single processing path for all data sources (sync and webhooks)</li>
 * <li>Idempotent operations via upsert pattern</li>
 * <li>Categories are resolved or created before the discussion</li>
 * </ul>
 */
@Service
public class GitHubDiscussionProcessor extends BaseGitHubProcessor {

    private static final Logger log = LoggerFactory.getLogger(GitHubDiscussionProcessor.class);

    private final DiscussionRepository discussionRepository;
    private final DiscussionCategoryRepository categoryRepository;
    private final ApplicationEventPublisher eventPublisher;

    public GitHubDiscussionProcessor(
        UserRepository userRepository,
        LabelRepository labelRepository,
        MilestoneRepository milestoneRepository,
        DiscussionRepository discussionRepository,
        DiscussionCategoryRepository categoryRepository,
        ApplicationEventPublisher eventPublisher
    ) {
        super(userRepository, labelRepository, milestoneRepository);
        this.discussionRepository = discussionRepository;
        this.categoryRepository = categoryRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Process a GitHub discussion DTO and persist it as a Discussion entity.
     * <p>
     * Uses atomic upsert to prevent race conditions when concurrent threads
     * (e.g., sync and webhook) process the same discussion.
     *
     * @param dto     the discussion DTO
     * @param context the processing context with repository information
     * @return the created or updated Discussion entity, or null if processing failed
     */
    @Transactional
    public Discussion process(GitHubDiscussionDTO dto, ProcessingContext context) {
        Long dbId = dto.getDatabaseId();
        if (dbId == null) {
            log.warn("Skipped discussion processing: reason=missingDatabaseId, discussionNumber={}", dto.number());
            return null;
        }

        Repository repository = context.repository();

        // Check if this is an update (for logging purposes)
        boolean isNew = !discussionRepository.existsByRepositoryIdAndNumber(repository.getId(), dto.number());

        // Resolve related entities BEFORE the upsert
        User author = dto.author() != null ? findOrCreateUser(dto.author()) : null;
        User answerChosenBy = dto.answerChosenBy() != null ? findOrCreateUser(dto.answerChosenBy()) : null;
        DiscussionCategory category = dto.category() != null ? findOrCreateCategory(dto.category(), repository) : null;

        // Use atomic upsert to handle concurrent inserts
        // This uses ON CONFLICT (repository_id, number) DO UPDATE
        Instant now = Instant.now();
        Discussion.State state = dto.isClosed() ? Discussion.State.CLOSED : Discussion.State.OPEN;
        Discussion.StateReason stateReason = convertStateReason(dto.stateReason());
        Discussion.LockReason lockReason = convertLockReason(dto.activeLockReason());

        discussionRepository.upsertCore(
            dbId,
            repository.getId(),
            dto.number(),
            sanitize(dto.title()),
            sanitize(dto.body()),
            dto.htmlUrl(),
            state.name(),
            stateReason != null ? stateReason.name() : null,
            dto.locked(),
            lockReason != null ? lockReason.name() : null,
            dto.closedAt(),
            dto.answerChosenAt(),
            dto.commentsCount(),
            now,
            dto.createdAt(),
            dto.updatedAt(),
            author != null ? author.getId() : null,
            category != null ? category.getId() : null,
            answerChosenBy != null ? answerChosenBy.getId() : null
        );

        // Fetch the discussion to get a managed entity and handle relationships
        Discussion discussion = discussionRepository
            .findByRepositoryIdAndNumber(repository.getId(), dto.number())
            .orElseThrow(() ->
                new IllegalStateException(
                    "Discussion not found after upsert: repositoryId=" + repository.getId() + ", number=" + dto.number()
                )
            );

        // Handle ManyToMany relationships (labels) - these can't be done in the upsert
        boolean labelsChanged = updateLabels(dto.labels(), discussion.getLabels(), repository);

        // Save relationship changes
        if (labelsChanged) {
            discussion = discussionRepository.save(discussion);
        }

        // Note: answerComment is set separately after comments are processed

        // Publish domain events
        EventContext eventContext = EventContext.from(context);
        EventPayload.DiscussionData discussionData = EventPayload.DiscussionData.from(discussion);

        if (isNew) {
            log.debug("Created discussion: discussionId={}, discussionNumber={}", dbId, dto.number());
            eventPublisher.publishEvent(new DomainEvent.DiscussionCreated(discussionData, eventContext));
        } else {
            log.debug("Updated discussion: discussionId={}, discussionNumber={}", dbId, dto.number());
            eventPublisher.publishEvent(new DomainEvent.DiscussionUpdated(discussionData, Set.of(), eventContext));
        }

        return discussion;
    }

    /**
     * Find or create a discussion category using atomic upsert.
     * <p>
     * Categories use the node ID (String) as the primary key since GitHub's GraphQL
     * API doesn't expose databaseId for DiscussionCategory.
     * <p>
     * Uses atomic upsert to prevent race conditions when concurrent threads
     * (e.g., sync and webhook) try to create the same category simultaneously.
     */
    @Nullable
    private DiscussionCategory findOrCreateCategory(GitHubDiscussionCategoryDTO dto, Repository repository) {
        if (dto == null || dto.nodeId() == null) {
            return null;
        }

        String slug = dto.slug() != null ? dto.slug() : dto.name().toLowerCase().replace(' ', '-');

        // Use atomic upsert to handle concurrent inserts safely
        categoryRepository.upsertCategory(
            dto.nodeId(),
            dto.name(),
            slug,
            dto.emoji(),
            dto.description(),
            dto.isAnswerable(),
            repository.getId(),
            dto.createdAt(),
            dto.updatedAt()
        );

        // Fetch the managed entity after upsert
        return categoryRepository.findById(dto.nodeId()).orElse(null);
    }

    /**
     * Convert state reason string to Discussion.StateReason enum.
     */
    @Nullable
    private Discussion.StateReason convertStateReason(@Nullable String stateReason) {
        if (stateReason == null) {
            return null;
        }
        return switch (stateReason.toUpperCase()) {
            case "RESOLVED" -> Discussion.StateReason.RESOLVED;
            case "OUTDATED" -> Discussion.StateReason.OUTDATED;
            case "DUPLICATE" -> Discussion.StateReason.DUPLICATE;
            default -> Discussion.StateReason.UNKNOWN;
        };
    }

    /**
     * Convert lock reason string to Discussion.LockReason enum.
     */
    @Nullable
    private Discussion.LockReason convertLockReason(@Nullable String lockReason) {
        if (lockReason == null) {
            return null;
        }
        return switch (lockReason.toUpperCase()) {
            case "OFF_TOPIC" -> Discussion.LockReason.OFF_TOPIC;
            case "RESOLVED" -> Discussion.LockReason.RESOLVED;
            case "SPAM" -> Discussion.LockReason.SPAM;
            case "TOO_HEATED" -> Discussion.LockReason.TOO_HEATED;
            default -> null;
        };
    }
}
