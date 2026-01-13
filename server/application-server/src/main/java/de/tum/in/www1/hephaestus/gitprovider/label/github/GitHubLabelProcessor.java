package de.tum.in.www1.hephaestus.gitprovider.label.github;

import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContext;
import de.tum.in.www1.hephaestus.gitprovider.common.events.DomainEvent;
import de.tum.in.www1.hephaestus.gitprovider.common.events.EventPayload;
import de.tum.in.www1.hephaestus.gitprovider.label.Label;
import de.tum.in.www1.hephaestus.gitprovider.label.LabelRepository;
import de.tum.in.www1.hephaestus.gitprovider.label.github.dto.GitHubLabelDTO;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Processor for GitHub labels.
 * <p>
 * This service handles the conversion of GitHubLabelDTO to Label entities,
 * persists them, and publishes appropriate domain events.
 * <p>
 * <b>Design Principles:</b>
 * <ul>
 * <li>Single processing path for all data sources (sync and webhooks)</li>
 * <li>Idempotent operations via upsert pattern</li>
 * <li>Domain events published for reactive feature development</li>
 * <li>Works exclusively with DTOs for complete field coverage</li>
 * </ul>
 * <p>
 * <b>Note on Label IDs:</b>
 * GitHub's GraphQL API does not expose databaseId for labels, only node_id.
 * This processor supports both ID-based lookup (for webhook events which provide databaseId)
 * and name-based lookup (for GraphQL sync where only name is reliably available).
 */
@Service
public class GitHubLabelProcessor {

    private static final Logger log = LoggerFactory.getLogger(GitHubLabelProcessor.class);

    private final LabelRepository labelRepository;
    private final ApplicationEventPublisher eventPublisher;

    public GitHubLabelProcessor(LabelRepository labelRepository, ApplicationEventPublisher eventPublisher) {
        this.labelRepository = labelRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Process a GitHub label DTO and persist it as a Label entity.
     * Uses ID-based lookup when id is available (webhooks), otherwise falls back to name-based lookup.
     * Publishes appropriate domain events based on what changed.
     * <p>
     * For new labels from GraphQL (which doesn't provide databaseId), a deterministic ID is generated
     * from the repository ID and label name to ensure consistency.
     *
     * @param dto the GitHub label DTO
     * @param repository the repository this label belongs to
     * @param context processing context with workspace information
     * @return the persisted Label entity
     */
    @Transactional
    public Label process(GitHubLabelDTO dto, Repository repository, ProcessingContext context) {
        if (dto == null || dto.name() == null) {
            log.warn("Label DTO is null or missing name, skipping");
            return null;
        }

        // Find existing label: prefer ID lookup (from webhooks), fall back to name lookup (for GraphQL)
        Optional<Label> existingOpt;
        if (dto.id() != null) {
            existingOpt = labelRepository.findById(dto.id());
        } else {
            existingOpt = labelRepository.findByRepositoryIdAndName(repository.getId(), dto.name());
        }
        boolean isNew = existingOpt.isEmpty();

        Label label = existingOpt.orElseGet(Label::new);

        // Set or update fields
        // For labels, all fields including description are always updated (null values are allowed)
        if (dto.id() != null) {
            label.setId(dto.id());
        } else if (isNew) {
            // Generate deterministic ID for new labels from GraphQL (which doesn't provide databaseId)
            label.setId(generateDeterministicId(repository.getId(), dto.name()));
        }
        label.setName(dto.name()); // name is always required
        if (dto.color() != null) {
            label.setColor(dto.color());
        }
        label.setDescription(dto.description()); // labels allow clearing description to null
        label.setRepository(repository);

        // Set GitHub timestamps (nullable - only available from GraphQL, not webhooks)
        if (dto.createdAt() != null) {
            label.setCreatedAt(dto.createdAt());
        }
        if (dto.updatedAt() != null) {
            label.setUpdatedAt(dto.updatedAt());
        }

        // Mark sync timestamp
        label.setLastSyncAt(Instant.now());

        Label saved = labelRepository.save(label);

        // Publish domain event with DTO payload (safe for async handling)
        EventPayload.LabelData labelData = EventPayload.LabelData.from(saved);
        if (isNew) {
            eventPublisher.publishEvent(
                new DomainEvent.LabelCreated(labelData, context.workspaceId(), repository.getId())
            );
        } else {
            eventPublisher.publishEvent(
                new DomainEvent.LabelUpdated(labelData, context.workspaceId(), repository.getId())
            );
        }

        return saved;
    }

    /**
     * Generates a deterministic ID for labels synced via GraphQL.
     * GitHub's GraphQL API doesn't expose databaseId for labels, so we generate
     * a consistent ID based on the repository ID and label name.
     * <p>
     * The generated ID is negative to avoid collision with actual GitHub databaseIds.
     *
     * @param repositoryId the repository's database ID
     * @param labelName the label's name
     * @return a deterministic negative Long ID
     */
    private Long generateDeterministicId(Long repositoryId, String labelName) {
        // Use bit shifting to combine repo ID and label name hash without collision.
        // The formula repositoryId * 31 + labelName.hashCode() can produce collisions.
        // Bit shifting separates components: repo ID in upper 32 bits, label name hash in lower 32 bits.
        // Negative IDs won't collide with GitHub's positive databaseIds.
        long combined = (repositoryId << 32) | (labelName.hashCode() & 0xFFFFFFFFL);
        return -combined;
    }

    /**
     * Delete a label by its ID.
     *
     * @param labelId the label database ID
     * @param context processing context with workspace information
     */
    @Transactional
    public void delete(Long labelId, ProcessingContext context) {
        if (labelId == null) {
            return;
        }

        labelRepository
            .findById(labelId)
            .ifPresent(label -> {
                Long repoId = label.getRepository() != null ? label.getRepository().getId() : null;
                labelRepository.delete(label);
                eventPublisher.publishEvent(
                    new DomainEvent.LabelDeleted(labelId, label.getName(), context.workspaceId(), repoId)
                );
                log.debug("Deleted label {} ({})", label.getName(), labelId);
            });
    }
}
