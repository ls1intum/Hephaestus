package de.tum.in.www1.hephaestus.gitprovider.label.github;

import de.tum.in.www1.hephaestus.gitprovider.common.GitProviderRepository;
import de.tum.in.www1.hephaestus.gitprovider.common.LabelIdUtils;
import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContext;
import de.tum.in.www1.hephaestus.gitprovider.common.events.DomainEvent;
import de.tum.in.www1.hephaestus.gitprovider.common.events.EventContext;
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
import org.springframework.transaction.annotation.Propagation;
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
    private final GitProviderRepository gitProviderRepository;
    private final ApplicationEventPublisher eventPublisher;

    public GitHubLabelProcessor(
        LabelRepository labelRepository,
        GitProviderRepository gitProviderRepository,
        ApplicationEventPublisher eventPublisher
    ) {
        this.labelRepository = labelRepository;
        this.gitProviderRepository = gitProviderRepository;
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
     * @param context processing context with scope information
     * @return the persisted Label entity
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Label process(GitHubLabelDTO dto, Repository repository, ProcessingContext context) {
        if (dto == null || dto.name() == null) {
            log.warn(
                "Skipped label processing: reason=nullOrMissingName, repoId={}",
                repository != null ? repository.getId() : null
            );
            return null;
        }

        // Always check by unique key (repository_id + name) FIRST - this is the constraint we enforce.
        // This handles the case where GraphQL sync created a label with a deterministic nativeId,
        // then NATS replay sends a webhook with the actual GitHub databaseId.
        Optional<Label> existingOpt = labelRepository.findByRepositoryIdAndName(repository.getId(), dto.name());

        // Fall back to nativeId lookup if name lookup didn't find it (handles label renames)
        if (existingOpt.isEmpty() && dto.id() != null) {
            existingOpt = labelRepository.findByNativeIdAndProviderId(dto.id(), context.providerId());
        }
        boolean isNew = existingOpt.isEmpty();

        Label label = existingOpt.orElseGet(Label::new);

        // Set or update fields
        // For labels, all fields including description are always updated (null values are allowed)
        // CRITICAL: NEVER change the nativeId/provider of an existing (managed) entity - Hibernate will throw
        // "identifier of an instance was altered" exception. Only set these for NEW entities.
        if (isNew) {
            long nativeId = dto.id() != null ? dto.id() : generateDeterministicId(repository.getId(), dto.name());
            label.setNativeId(nativeId);
            // CRITICAL: Use getReferenceById to get a proxy bound to the current session.
            // context.provider() returns a proxy from the outer session (REQUIRES_NEW suspends it),
            // which causes "Illegally attempted to associate proxy with two open sessions".
            label.setProvider(gitProviderRepository.getReferenceById(context.providerId()));
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
        EventContext eventContext = EventContext.from(context);
        if (isNew) {
            eventPublisher.publishEvent(new DomainEvent.LabelCreated(labelData, eventContext));
            log.debug("Created label: labelId={}, labelName={}", saved.getId(), saved.getName());
        } else {
            eventPublisher.publishEvent(new DomainEvent.LabelUpdated(labelData, eventContext));
            log.debug("Updated label: labelId={}, labelName={}", saved.getId(), saved.getName());
        }

        return saved;
    }

    private Long generateDeterministicId(Long repositoryId, String labelName) {
        return LabelIdUtils.generateDeterministicId(repositoryId, labelName);
    }

    /**
     * Delete a label by its database ID.
     * <p>
     * IMPORTANT: For bidirectional @ManyToMany relationships,
     * we must sync both sides of the relationship before deleting to avoid
     * constraint violations or stale references in the persistence context.
     *
     * @param labelId the label database ID (synthetic PK)
     * @param context processing context with scope information
     */
    @Transactional
    public void delete(Long labelId, ProcessingContext context) {
        if (labelId == null) {
            return;
        }

        labelRepository.findById(labelId).ifPresent(label -> deleteLabel(label, context));
    }

    /**
     * Delete a label by its native ID (provider-assigned ID).
     * Used by webhook handlers where only the native ID is available.
     *
     * @param nativeId the label's native ID from the provider
     * @param context processing context with scope information
     */
    @Transactional
    public void deleteByNativeId(Long nativeId, ProcessingContext context) {
        if (nativeId == null) {
            return;
        }

        labelRepository
            .findByNativeIdAndProviderId(nativeId, context.providerId())
            .ifPresent(label -> deleteLabel(label, context));
    }

    private void deleteLabel(Label label, ProcessingContext context) {
        // CRITICAL: Remove from all referencing Issues before deletion
        // to sync bidirectional ManyToMany relationship
        label.removeAllIssues();

        labelRepository.delete(label);
        eventPublisher.publishEvent(
            new DomainEvent.LabelDeleted(label.getId(), label.getName(), EventContext.from(context))
        );
        log.info("Deleted label: labelId={}, labelName={}", label.getId(), label.getName());
    }
}
