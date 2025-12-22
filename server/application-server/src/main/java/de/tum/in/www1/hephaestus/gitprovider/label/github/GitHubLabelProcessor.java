package de.tum.in.www1.hephaestus.gitprovider.label.github;

import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContext;
import de.tum.in.www1.hephaestus.gitprovider.common.events.EntityEvents;
import de.tum.in.www1.hephaestus.gitprovider.label.Label;
import de.tum.in.www1.hephaestus.gitprovider.label.LabelRepository;
import de.tum.in.www1.hephaestus.gitprovider.label.github.dto.GitHubLabelDTO;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
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
 * <li>No hub4j types - works exclusively with DTOs</li>
 * </ul>
 */
@Service
public class GitHubLabelProcessor {

    private static final Logger logger = LoggerFactory.getLogger(GitHubLabelProcessor.class);

    private final LabelRepository labelRepository;
    private final ApplicationEventPublisher eventPublisher;

    public GitHubLabelProcessor(LabelRepository labelRepository, ApplicationEventPublisher eventPublisher) {
        this.labelRepository = labelRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Process a GitHub label DTO and persist it as a Label entity.
     * Publishes appropriate domain events based on what changed.
     *
     * @param dto the GitHub label DTO
     * @param repository the repository this label belongs to
     * @param context processing context with workspace information
     * @return the persisted Label entity
     */
    @Transactional
    public Label process(GitHubLabelDTO dto, Repository repository, ProcessingContext context) {
        if (dto == null || dto.id() == null) {
            logger.warn("Label DTO is null or missing id, skipping");
            return null;
        }

        Optional<Label> existingOpt = labelRepository.findById(dto.id());
        boolean isNew = existingOpt.isEmpty();

        Label label = existingOpt.orElseGet(Label::new);

        // Set or update fields
        label.setId(dto.id());
        label.setName(dto.name());
        label.setColor(dto.color());
        label.setDescription(dto.description());
        label.setRepository(repository);

        Label saved = labelRepository.save(label);

        // Publish domain event
        eventPublisher.publishEvent(
            new EntityEvents.LabelProcessed(saved, isNew, context.workspaceId(), repository.getId())
        );

        logger.debug("Processed label {} ({}): {}", saved.getName(), saved.getId(), isNew ? "created" : "updated");
        return saved;
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
                    new EntityEvents.LabelDeleted(labelId, label.getName(), context.workspaceId(), repoId)
                );
                logger.debug("Deleted label {} ({})", label.getName(), labelId);
            });
    }
}
