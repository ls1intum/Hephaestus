package de.tum.in.www1.hephaestus.gitprovider.label.gitlab;

import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContext;
import de.tum.in.www1.hephaestus.gitprovider.common.events.DomainEvent;
import de.tum.in.www1.hephaestus.gitprovider.common.events.EventContext;
import de.tum.in.www1.hephaestus.gitprovider.common.events.EventPayload;
import de.tum.in.www1.hephaestus.gitprovider.label.Label;
import de.tum.in.www1.hephaestus.gitprovider.label.LabelIdUtils;
import de.tum.in.www1.hephaestus.gitprovider.label.LabelRepository;
import de.tum.in.www1.hephaestus.gitprovider.label.gitlab.dto.GitLabLabelDTO;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Processor for GitLab labels.
 * <p>
 * Converts {@link GitLabLabelDTO} to {@link Label} entities, persists them, and publishes
 * domain events. Mirrors the {@link de.tum.in.www1.hephaestus.gitprovider.label.github.GitHubLabelProcessor}
 * pattern with GitLab-specific field mapping ({@code title} &rarr; {@code name}).
 * <p>
 * Uses deterministic negative IDs via {@link LabelIdUtils} since GitLab group-level labels
 * share the same global ID across all projects but are stored per-repository in the database.
 */
@Service
public class GitLabLabelProcessor {

    private static final Logger log = LoggerFactory.getLogger(GitLabLabelProcessor.class);

    private final LabelRepository labelRepository;
    private final ApplicationEventPublisher eventPublisher;

    public GitLabLabelProcessor(LabelRepository labelRepository, ApplicationEventPublisher eventPublisher) {
        this.labelRepository = labelRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Processes a GitLab label DTO and persists it as a Label entity.
     * <p>
     * Lookup is always by (repository_id, name) — the unique constraint.
     * For new labels, a deterministic negative ID is generated from (repositoryId, title).
     *
     * @param dto        the GitLab label DTO
     * @param repository the repository this label belongs to
     * @param context    processing context with scope information
     * @return the persisted Label entity, or null if the DTO is invalid
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Nullable
    public Label process(@Nullable GitLabLabelDTO dto, Repository repository, ProcessingContext context) {
        if (dto == null || dto.title() == null || dto.title().isBlank()) {
            log.warn(
                "Skipped label processing: reason=nullOrMissingTitle, repoId={}",
                repository != null ? repository.getId() : null
            );
            return null;
        }

        Optional<Label> existingOpt = labelRepository.findByRepositoryIdAndName(repository.getId(), dto.title());
        boolean isNew = existingOpt.isEmpty();

        Label label = existingOpt.orElseGet(Label::new);

        if (isNew) {
            label.setId(LabelIdUtils.generateDeterministicId(repository.getId(), dto.title()));
        }
        label.setName(dto.title());
        if (dto.color() != null) {
            label.setColor(dto.color());
        } else if (isNew) {
            label.setColor(""); // Label.color is @NonNull
        }
        label.setDescription(dto.description());
        label.setRepository(repository);

        if (dto.createdAt() != null) {
            label.setCreatedAt(parseIsoTimestamp(dto.createdAt()));
        }
        if (dto.updatedAt() != null) {
            label.setUpdatedAt(parseIsoTimestamp(dto.updatedAt()));
        }
        label.setLastSyncAt(Instant.now());

        Label saved = labelRepository.save(label);

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

    /**
     * Deletes a label by its ID, cleaning up bidirectional relationships first.
     *
     * @param labelId the label database ID
     * @param context processing context with scope information
     */
    @Transactional
    public void delete(Long labelId, ProcessingContext context) {
        if (labelId == null) {
            return;
        }

        labelRepository
            .findById(labelId)
            .ifPresent(label -> {
                label.removeAllIssues();
                labelRepository.delete(label);
                eventPublisher.publishEvent(
                    new DomainEvent.LabelDeleted(labelId, label.getName(), EventContext.from(context))
                );
                log.info("Deleted label: labelId={}, labelName={}", labelId, label.getName());
            });
    }

    @Nullable
    private static Instant parseIsoTimestamp(@Nullable String timestamp) {
        if (timestamp == null || timestamp.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(timestamp).toInstant();
        } catch (DateTimeParseException e) {
            log.warn("Could not parse timestamp: value={}", timestamp);
            return null;
        }
    }
}
